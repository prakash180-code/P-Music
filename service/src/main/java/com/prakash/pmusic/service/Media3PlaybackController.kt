package com.prakash.pmusic.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.prakash.pmusic.core.media.toDomainRepeatMode
import com.prakash.pmusic.core.media.toMediaItem
import com.prakash.pmusic.core.media.toPlayerRepeatMode
import com.prakash.pmusic.core.media.toSong
import com.prakash.pmusic.domain.model.EqualizerState
import com.prakash.pmusic.domain.model.PlaybackState
import com.prakash.pmusic.domain.model.RepeatMode
import com.prakash.pmusic.domain.model.Song
import com.prakash.pmusic.domain.repository.LibraryRepository
import com.prakash.pmusic.domain.repository.PlaybackController
import com.prakash.pmusic.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * UI-facing playback controller that talks to [PlaybackService] through a
 * [MediaController].
 *
 * Decisions:
 * - The controller is connected asynchronously; state stays at the default
 *   snapshot until the session is reachable, so the UI never blocks. The
 *   connection future's callback runs on the main executor instead of a
 *   blocking `.get()`, because Media3's controller handshake itself runs on
 *   the main looper (a blocking wait would deadlock).
 * - A lightweight ticker updates position/duration while playing (every
 *   500 ms) so a seek bar can stay smooth without churning the database.
 * - The queue is tracked locally in the app and its index is read from the
 *   player, which keeps the mapping consistent after next/prev/shuffle.
 */
@Singleton
class Media3PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val libraryRepository: LibraryRepository,
    private val equalizerEngine: AudioFxEqualizerEngine
) : PlaybackController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    override val equalizerState: StateFlow<EqualizerState> = equalizerEngine.state

    @Volatile
    private var controller: MediaController? = null

    private var queue: List<Song> = emptyList()
    private var pendingExternalUri: String? = null
    private var externalPlayback = false
    private var positionTicker: Job? = null

    /** Speed applied whenever a new playback session starts. */
    private var defaultPlaybackSpeed: Float = 1f

    /** Id of the song most recently recorded as played (dedupes events). */
    private var lastRecordedSongId: Long? = null

    init {
        scope.launch {
            preferencesRepository.preferences.collect { prefs ->
                defaultPlaybackSpeed = prefs.defaultPlaybackSpeed
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncState(player)
            syncPositionTicker(player)
            recordPlayIfNeeded(player)
            if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                equalizerEngine.notifyPlaybackActive()
            }
        }
    }

    override fun connect() {
        if (controller != null) return
        Log.d(TAG, "connect() building MediaController")
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val connected = runCatching { future.get() }.getOrNull()
                if (connected == null) {
                    Log.e(TAG, "connect() failed")
                    return@addListener
                }
                Log.d(TAG, "connect() connected: $connected")
                connected.addListener(playerListener)
                controller = connected
                syncState(connected)
                pendingExternalUri?.let { uri ->
                    pendingExternalUri = null
                    playExternalAudioNow(connected, uri)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    override fun disconnect() {
        positionTicker?.cancel()
        positionTicker = null
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        _playbackState.value = PlaybackState()
        pendingExternalUri = null
        externalPlayback = false
        lastRecordedSongId = null
    }

    override fun playSong(song: Song) {
        val player = controller
        Log.d(TAG, "playSong(title=${song.title}, controller=${if (player == null) "null" else "ok"})")
        if (player == null) return
        queue = listOf(song)
        externalPlayback = false
        player.setMediaItem(song.toMediaItem())
        player.prepare()
        player.setPlaybackSpeed(defaultPlaybackSpeed)
        player.play()
    }

    override fun playQueue(queue: List<Song>, startIndex: Int) {
        val player = controller ?: return
        if (queue.isEmpty()) return
        this.queue = queue
        externalPlayback = false
        val items: List<MediaItem> = queue.map { it.toMediaItem() }
        val safeIndex = startIndex.coerceIn(0, items.size - 1)
        player.setMediaItems(items, safeIndex, 0L)
        player.prepare()
        player.setPlaybackSpeed(defaultPlaybackSpeed)
        player.play()
    }

    override fun playExternalAudio(uri: String) {
        val player = controller
        if (player == null) {
            pendingExternalUri = uri
            return
        }
        playExternalAudioNow(player, uri)
    }

    /** Plays a URI directly; it may not have a MediaStore/Room row yet. */
    private fun playExternalAudioNow(player: MediaController, uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme.isNullOrBlank()) return

        val song = externalSong(uri)
        queue = listOf(song)
        externalPlayback = true
        lastRecordedSongId = null
        player.setMediaItem(
            MediaItem.Builder()
                .setMediaId(uriString)
                .setUri(uri)
                .setTag(song)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .build()
                )
                .build()
        )
        player.prepare()
        player.setPlaybackSpeed(defaultPlaybackSpeed)
        player.play()
    }

    /** Builds enough metadata for the mini-player while MediaStore catches up. */
    private fun externalSong(uri: Uri): Song {
        val displayName = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        val fallbackName = uri.lastPathSegment
            ?.let(Uri::decode)
            ?.substringAfterLast(':')
            ?.substringAfterLast('/')
            ?.ifBlank { null }
            ?: "Audio"
        val fileName = displayName?.ifBlank { null } ?: fallbackName
        val title = fileName.substringBeforeLast('.', fileName).ifBlank { "Audio" }

        return Song(
            id = uri.toString().hashCode().toLong(),
            title = title,
            artist = "Unknown",
            artistId = 0L,
            album = "Unknown",
            albumId = 0L,
            albumArtist = null,
            trackNumber = 0,
            discNumber = 0,
            year = 0,
            genre = "Unknown",
            durationMs = 0L,
            sizeBytes = 0L,
            mimeType = context.contentResolver.getType(uri) ?: "audio/*",
            path = uri.toString(),
            dateAdded = 0L,
            dateModified = 0L,
            composer = null,
            playCount = 0,
            skipCount = 0,
            lastPlayedAt = null,
            isFavorite = false,
            bitrate = 0,
            sampleRate = 0,
            channels = 0,
            artPath = null
        )
    }

    override fun pause() {
        controller?.pause()
    }

    override fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    override fun next() {
        controller?.seekToNextMediaItem()
    }

    override fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    override fun jumpToQueueIndex(index: Int) {
        val player = controller ?: return
        if (queue.isEmpty() || index !in queue.indices) return
        player.seekTo(index, 0L)
        player.setPlaybackSpeed(defaultPlaybackSpeed)
        player.play()
    }

    override fun removeCurrentSong() {
        val player = controller ?: return
        val index = player.currentMediaItemIndex
        if (index < 0 || player.mediaItemCount == 0) return
        if (player.mediaItemCount == 1) {
            // Last item: stop and clear the queue instead of lingering on the
            // now-deleted song.
            player.stop()
            player.clearMediaItems()
            queue = emptyList()
        } else {
            // The player auto-advances to the item that takes the removed
            // one's place, keeping playback going.
            player.removeMediaItem(index)
            queue = queue.filterIndexed { i, _ -> i != index }
        }
        syncState(player)
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    override fun setRepeatMode(mode: RepeatMode) {
        controller?.repeatMode = mode.toPlayerRepeatMode()
    }

    override fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    override fun setEqualizerEnabled(enabled: Boolean) = equalizerEngine.setEnabled(enabled)

    override fun setEqualizerBandGain(band: Int, gainMb: Int) =
        equalizerEngine.setBandGain(band, gainMb)

    override fun selectEqualizerPreset(presetIndex: Int) =
        equalizerEngine.selectPreset(presetIndex)

    override fun resetEqualizer() = equalizerEngine.reset()

    private fun syncState(player: Player) {
        val currentIndex = player.currentMediaItemIndex
        val hasItem = currentIndex >= 0
        // The MediaItem tag is not reliably delivered through the media
        // session, so the domain Song is recovered from the locally tracked
        // queue (which the app owns) and only falls back to the tag.
        val currentSong = if (hasItem) {
            queue.getOrNull(currentIndex) ?: player.currentMediaItem?.toSong()
        } else {
            null
        }
        _playbackState.value = PlaybackState(
            currentSong = currentSong,
            queue = queue,
            queueIndex = if (hasItem) currentIndex else -1,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode.toDomainRepeatMode(),
            playbackSpeed = player.playbackParameters.speed,
            isConnected = true
        )
    }

    /** Starts/stops the position ticker based on the play state. */
    private fun syncPositionTicker(player: Player) {
        if (player.isPlaying) {
            if (positionTicker != null) return
            positionTicker = scope.launch {
                while (isActive) {
                    _playbackState.update { state ->
                        val current = controller
                        if (current != null) {
                            state.copy(
                                positionMs = current.currentPosition.coerceAtLeast(0L),
                                durationMs = current.duration.coerceAtLeast(0L)
                            )
                        } else {
                            state
                        }
                    }
                    delay(POSITION_TICK_MS)
                }
            }
        } else {
            positionTicker?.cancel()
            positionTicker = null
        }
    }

    /**
     * Records a play for the current song once it is actually audible
     * (STATE_READY + playing). Fires once per song because the last recorded
     * id guards against the repeated events the listener receives.
     */
    private fun recordPlayIfNeeded(player: Player) {
        if (externalPlayback) return
        if (player.playbackState != Player.STATE_READY || !player.isPlaying) return
        val index = player.currentMediaItemIndex
        if (index < 0) return
        val song = queue.getOrNull(index) ?: return
        if (song.id == lastRecordedSongId) return
        lastRecordedSongId = song.id
        scope.launch { runCatching { libraryRepository.recordPlay(song.id) } }
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
        const val TAG = "PMusicPlayback"
    }
}
