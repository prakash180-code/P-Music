package com.prakash.pmusic.service

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.prakash.pmusic.core.media.contentUri
import com.prakash.pmusic.core.media.toDomainRepeatMode
import com.prakash.pmusic.core.media.toMediaItem
import com.prakash.pmusic.core.media.toPlayerRepeatMode
import com.prakash.pmusic.core.media.toSong
import android.database.Cursor
import com.prakash.pmusic.domain.model.DiagnosticsSnapshot
import com.prakash.pmusic.domain.model.EqualizerState
import com.prakash.pmusic.domain.model.MultiOutputCapability
import com.prakash.pmusic.domain.model.MultiOutputDevice
import com.prakash.pmusic.domain.model.MultiOutputState
import com.prakash.pmusic.domain.model.PlaybackLogLevel
import com.prakash.pmusic.domain.model.PlaybackState
import com.prakash.pmusic.domain.model.RepeatMode
import com.prakash.pmusic.domain.model.SavedPlaybackState
import com.prakash.pmusic.domain.model.Song
import com.prakash.pmusic.domain.repository.LibraryRepository
import com.prakash.pmusic.domain.repository.PlaybackController
import com.prakash.pmusic.domain.repository.PlaybackLogger
import com.prakash.pmusic.domain.repository.PlaybackStateRepository
import com.prakash.pmusic.domain.repository.PreferencesRepository
import com.prakash.pmusic.service.audio.MultiOutputEngine
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
import kotlinx.coroutines.withContext

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
    private val playbackStateRepository: PlaybackStateRepository,
    private val equalizerEngine: AudioFxEqualizerEngine,
    private val multiOutputEngine: MultiOutputEngine,
    private val playbackLogger: PlaybackLogger
) : PlaybackController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _diagnosticsState = MutableStateFlow(DiagnosticsSnapshot())
    override val diagnosticsState: StateFlow<DiagnosticsSnapshot> = _diagnosticsState.asStateFlow()

    override val equalizerState: StateFlow<EqualizerState> = equalizerEngine.state

    override val multiOutputDevices: StateFlow<List<MultiOutputDevice>> =
        multiOutputEngine.devices

    override val multiOutputCapabilities: StateFlow<MultiOutputCapability?> =
        multiOutputEngine.capabilities

    override val multiOutputState: StateFlow<MultiOutputState> = multiOutputEngine.state

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

    /** Guards against re-restoring while a restoration is in flight. */
    private var restoring = false

    /** Last time a periodic save ran (monotonic ms), to throttle disk writes. */
    private var lastPeriodicSaveMs = 0L

    /** The last song id that was persisted as current, to detect song changes. */
    private var lastSavedSongId: Long? = null

    /** Monotonic time (ms) of the last PLAYBACK_HEALTH log, to throttle it. */
    private var lastHealthLogMs = 0L

    /** Last position from the health loop, used to detect a stalled player. */
    private var lastHealthPositionMs = 0L

    /** Monotonic time when the position last advanced, for stall detection. */
    private var lastAdvanceElapsedMs = 0L

    /** Whether a stall is currently being reported (avoid log spam). */
    private var stallReported = false

    /** Raw identity of the service-side ExoPlayer, reported via diagnostics. */
    @Volatile
    private var servicePlayerInstance: Int? = null

    /** Identity of the current MediaController (System.identityHashCode). */
    @Volatile
    private var controllerInstance: Int? = null

    /** Whether the playback service process/host is currently alive. */
    @Volatile
    private var serviceAlive = false

    /** Whether the service-side MediaSession is currently connected/alive. */
    @Volatile
    private var mediaSessionAlive = false

    /** The audio session id reported by the service-side player (-1 unknown). */
    @Volatile
    private var reportedAudioSessionId: Int = -1

    /**
     * Called by [PlaybackService] so the controller (and the diagnostics UI)
     * can reflect service/session/player liveness without the UI reaching into
     * the service. [identity] is System.identityHashCode of the service player.
     */
    fun reportServiceState(alive: Boolean, sessionAlive: Boolean, playerIdentity: Int?) {
        serviceAlive = alive
        mediaSessionAlive = sessionAlive
        servicePlayerInstance = playerIdentity
        publishDiagnostics(controller)
    }

    /** Reports the actual audio session id from the service-side player. */
    fun reportAudioSession(audioSessionId: Int) {
        reportedAudioSessionId = audioSessionId
        publishDiagnostics(controller)
    }

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
            logMeaningfulChanges(events)

            // Persist periodically while playing (throttled) and immediately on
            // any media-item transition so the saved "current song" never lags
            // behind what is actually playing.
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                saveOnEvent(player)
            } else {
                maybePeriodicSave(player)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "onPlayerError", error)
            playbackLogger.error(COMPONENT, "PLAYER_ERROR", error)
            _diagnosticsState.update {
                it.copy(lastError = "${error.errorCodeName} (${error.errorCode})")
            }
        }
    }

    /**
     * Logs discrete, meaningful playback changes (state / playWhenReady /
     * suppression / item transitions / errors) at INFO, keeping the log
     * readable. Verbose per-ms position sampling happens only in the health
     * loop and only when DEBUG logging is enabled.
     */
    private fun logMeaningfulChanges(events: Player.Events) {
        val player = controller ?: return
        val meaningful =
            events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                events.contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                events.contains(Player.EVENT_PLAYER_ERROR)
        if (!meaningful) return
        val state = when (player.playbackState) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN"
        }
        val eventNames = listOf(
            events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) to "STATE",
            events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) to "PWY",
            events.contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED) to "SUPPRESSION",
            events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) to "TRANSITION",
            events.contains(Player.EVENT_IS_PLAYING_CHANGED) to "IS_PLAYING",
            events.contains(Player.EVENT_PLAYER_ERROR) to "ERROR"
        ).filter { it.first }.joinToString(",") { it.second }
        playbackLogger.log(
            PlaybackLogLevel.INFO,
            COMPONENT,
            "PLAYER_CHANGED",
            "events=$eventNames state=$state isPlaying=${player.isPlaying} " +
                "playWhenReady=${player.playWhenReady} suppression=${player.playbackSuppressionReason} " +
                "position=${player.currentPosition} buffered=${player.bufferedPosition} " +
                "mediaItemIndex=${player.currentMediaItemIndex} controller=${controllerInstance} " +
                "player=$servicePlayerInstance"
        )
    }

    override fun connect() {
        // Recreate a stale/dead controller instead of reusing one whose
        // session link has broken (e.g. after the service restarted while
        // the app stayed alive). Without this the controller keeps returning
        // the default snapshot and playback appears frozen/stuttering.
        val current = controller
        if (current != null) {
            if (current.isConnected) return
            Log.w(TAG, "connect() dropping stale controller")
            playbackLogger.log(
                PlaybackLogLevel.WARN, COMPONENT, "CONTROLLER_RECREATED",
                "reason=stale(dead) previous=${controllerInstance}"
            )
            current.removeListener(playerListener)
            runCatching { current.release() }
            controller = null
            controllerInstance = null
        }
        Log.d(TAG, "connect() building MediaController")
        playbackLogger.log(PlaybackLogLevel.INFO, COMPONENT, "CONTROLLER_CONNECT", "building MediaController")
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val connected = runCatching { future.get() }.getOrNull()
                if (connected == null) {
                    Log.e(TAG, "connect() failed")
                    playbackLogger.log(
                        PlaybackLogLevel.ERROR, COMPONENT, "CONTROLLER_CONNECT_FAILED",
                        "MediaController connection returned null"
                    )
                    return@addListener
                }
                Log.d(TAG, "connect() connected: $connected")
                connected.addListener(playerListener)
                controller = connected
                controllerInstance = System.identityHashCode(connected)
                playbackLogger.log(
                    PlaybackLogLevel.INFO, COMPONENT, "CONTROLLER_CONNECTED",
                    "controller=$controllerInstance"
                )
                publishDiagnostics(connected)
                syncState(connected)
                pendingExternalUri?.let { uri ->
                    pendingExternalUri = null
                    playExternalAudioNow(connected, uri)
                }
                // Restore the last playback session (queue/song/position/modes)
                // only when there is no fresh external URI to play instead.
                if (pendingExternalUri == null && queue.isEmpty()) {
                    restoreLastSession(connected)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    /**
     * Loads the last persisted session and, if the current file still exists,
     * restores the queue, current song, position and mode flags onto the player
     * — remaining paused (playback never auto-starts on app open).
     */
    private fun restoreLastSession(player: Player) {
        if (restoring) return
        restoring = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) { playbackStateRepository.load() }
            if (saved == null || saved.isEmpty) {
                restoring = false
                return@launch
            }
            restore(player, saved)
            restoring = false
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun restore(player: Player, saved: SavedPlaybackState) {
        // Verify the current media file still exists; if it is gone, the saved
        // session is stale and must be dropped so we never show a dead player.
        if (!mediaExists(saved.mediaUri)) {
            Log.i(TAG, "restore: current media no longer exists, clearing saved state")
            scope.launch { playbackStateRepository.clear() }
            return
        }

        // Rebuild the queue, dropping any songs whose files have disappeared,
        // and re-derive the current index so it stays valid.
        val restored = saved.queue.filter { mediaExists(it.contentUri().toString()) }
        if (restored.isEmpty()) {
            scope.launch { playbackStateRepository.clear() }
            return
        }
        val requestedIndex = saved.currentQueueIndex.coerceIn(0, saved.queue.size - 1)
        val requestedId = saved.queue.getOrNull(requestedIndex)?.id
        val index = restored.indexOfFirst { it.id == requestedId }
            .takeIf { it >= 0 } ?: 0

        // Position validation: clamp to a sane range and reset to the start when
        // the saved position is out of bounds or effectively at the end.
        val duration = restored.getOrNull(index)?.durationMs
            ?.takeIf { it > 0L } ?: saved.durationMs
        val position = if (saved.positionMs in 0L until duration) saved.positionMs else 0L

        queue = restored
        val items = restored.map { it.toMediaItem() }
        runCatching {
            player.setMediaItems(items, index, position)
            player.shuffleModeEnabled = saved.shuffleEnabled
            player.repeatMode = saved.repeatMode.toPlayerRepeatMode()
            player.setPlaybackSpeed(saved.playbackSpeed)
            player.prepare()
            // Deliberately do NOT call play(): restoration stays paused.
        }
        lastRecordedSongId = restored.getOrNull(index)?.id
        lastSavedSongId = restored.getOrNull(index)?.id
        Log.i(
            TAG,
            "restore: restored ${restored.size} songs at index $index, " +
                "position ${position}ms, speed ${saved.playbackSpeed}, " +
                "shuffle=${saved.shuffleEnabled}, repeat=${saved.repeatMode}"
        )
        syncState(player)
        Log.i(
            TAG,
            "restore: after sync currentMediaItemIndex=${player.currentMediaItemIndex} " +
                "currentSong=${_playbackState.value.currentSong?.title}"
        )
        playbackLogger.log(
            PlaybackLogLevel.INFO, COMPONENT, "SESSION_RESTORED",
            "restored=${restored.size} index=$index position=${position}ms " +
                "speed=${saved.playbackSpeed} shuffle=${saved.shuffleEnabled} " +
                "repeat=${saved.repeatMode} remainingPaused=true"
        )
    }

    /** True when the content resolver can still resolve [uri] to a row. */
    private fun mediaExists(uriString: String?): Boolean {
        val uri = uriString?.let(Uri::parse) ?: return false
        if (uri.scheme.isNullOrBlank()) return false
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
        val resolver = context.contentResolver
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(uri, arrayOf("_id"), null, null, null)
            cursor != null && cursor.moveToFirst()
        } catch (_: Exception) {
            false
        } finally {
            cursor?.close()
        }
    }

    override fun disconnect() {
        positionTicker?.cancel()
        positionTicker = null
        if (controller != null) {
            playbackLogger.log(
                PlaybackLogLevel.INFO, COMPONENT, "CONTROLLER_DISCONNECT",
                "controller=$controllerInstance player=$servicePlayerInstance"
            )
        }
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        controllerInstance = null
        _playbackState.value = PlaybackState()
        _diagnosticsState.value = DiagnosticsSnapshot()
        pendingExternalUri = null
        externalPlayback = false
        lastRecordedSongId = null
        lastSavedSongId = null
        restoring = false
    }

    /**
     * Persists the current playback session immediately. Called by the UI for
     * a final flush and by the service before it is torn down, so the last
     * known good song/position survive an app or service kill.
     */
    fun flushPlaybackState() {
        val player = controller ?: return
        if (externalPlayback) return
        if (player.mediaItemCount == 0) {
            scope.launch { playbackStateRepository.clear() }
            return
        }
        val snapshot = snapshotSavedState(player)
        if (snapshot.isEmpty) {
            scope.launch { playbackStateRepository.clear() }
        } else {
            scope.launch { playbackStateRepository.save(snapshot) }
        }
    }

    /** Builds a [SavedPlaybackState] snapshot of the current player state. */
    private fun snapshotSavedState(player: Player): SavedPlaybackState {
        val index = player.currentMediaItemIndex
        val current = queue.getOrNull(index)
        if (current == null) return SavedPlaybackState()
        val position = player.currentPosition.takeIf { it > 0L } ?: 0L
        val duration = player.duration.takeIf { it > 0L } ?: current.durationMs
        return SavedPlaybackState(
            queue = queue,
            currentQueueIndex = index,
            mediaId = current.id,
            mediaUri = current.contentUri().toString(),
            songTitle = current.title,
            artist = current.artist,
            album = current.album,
            albumArtworkPath = current.artPath,
            positionMs = position,
            durationMs = duration,
            playbackSpeed = player.playbackParameters.speed,
            repeatMode = player.repeatMode.toDomainRepeatMode(),
            shuffleEnabled = player.shuffleModeEnabled,
            wasPlaying = player.isPlaying,
            savedAtNanos = System.nanoTime()
        )
    }

    /** Throttled periodic save while playing (every [SAVE_INTERVAL_MS]). */
    private fun maybePeriodicSave(player: Player) {
        if (externalPlayback) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPeriodicSaveMs < SAVE_INTERVAL_MS) return
        lastPeriodicSaveMs = now
        val snapshot = snapshotSavedState(player)
        if (snapshot.isEmpty) return
        val id = snapshot.mediaId
        // Always persist the latest position, but only emit a fresh disk write
        // for song changes at most; the position updates ride along each save.
        if (id != null && lastSavedSongId != id) {
            lastSavedSongId = id
        }
        scope.launch { playbackStateRepository.save(snapshot) }
    }

    /** Saves state in response to a discrete event (pause/seek/song change). */
    private fun saveOnEvent(player: Player) {
        val snapshot = snapshotSavedState(player)
        if (snapshot.isEmpty) {
            scope.launch { playbackStateRepository.clear() }
        } else {
            lastSavedSongId = snapshot.mediaId
            scope.launch { playbackStateRepository.save(snapshot) }
        }
    }

    override fun playSong(song: Song) {
        val player = controller
        Log.d(TAG, "playSong(title=${song.title}, controller=${if (player == null) "null" else "ok"})")
        if (player == null) {
            playbackLogger.log(
                PlaybackLogLevel.WARN, COMPONENT, "PLAY_COMMAND_DROPPED",
                "action=playSong title=${song.title} reason=controllerNotConnected"
            )
            return
        }
        playbackLogger.log(
            PlaybackLogLevel.INFO, COMPONENT, "PLAY_COMMAND",
            "action=playSong title=${song.title} state=${stateName(player)} " +
                "isPlaying=${player.isPlaying} playWhenReady=${player.playWhenReady}"
        )
        queue = listOf(song)
        externalPlayback = false
        player.setMediaItem(song.toMediaItem())
        player.prepare()
        player.setPlaybackSpeed(defaultPlaybackSpeed)
        player.play()
        saveOnEvent(player)
    }

    override fun playQueue(queue: List<Song>, startIndex: Int) {
        val player = controller ?: return
        if (queue.isEmpty()) return
        playbackLogger.log(
            PlaybackLogLevel.INFO, COMPONENT, "PLAY_COMMAND",
            "action=playQueue size=${queue.size} startIndex=$startIndex " +
                "state=${stateName(player)} isPlaying=${player.isPlaying}"
        )
        this.queue = queue
        externalPlayback = false
        val items: List<MediaItem> = queue.map { it.toMediaItem() }
        val safeIndex = startIndex.coerceIn(0, items.size - 1)
        player.setMediaItems(items, safeIndex, 0L)
        player.prepare()
        player.setPlaybackSpeed(defaultPlaybackSpeed)
        player.play()
        saveOnEvent(player)
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
        playbackLogger.log(
            PlaybackLogLevel.INFO, COMPONENT, "PLAY_COMMAND",
            "action=playExternalAudio uri=$uriString"
        )
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
        val player = controller
        if (player != null) {
            playbackLogger.log(
                PlaybackLogLevel.INFO, COMPONENT, "PAUSE_COMMAND",
                "isPlaying=${player.isPlaying} playWhenReady=${player.playWhenReady} state=${stateName(player)}"
            )
            player.pause()
            playbackLogger.log(
                PlaybackLogLevel.INFO, COMPONENT, "PAUSE_COMMAND_RESULT",
                "isPlaying=${player.isPlaying} playWhenReady=${player.playWhenReady} state=${stateName(player)}"
            )
            saveOnEvent(player)
        }
    }

    override fun togglePlayPause() {
        val player = controller ?: return
        val wasPlaying = player.isPlaying
        playbackLogger.log(
            PlaybackLogLevel.INFO, COMPONENT, "TOGGLE_COMMAND",
            "wasPlaying=$wasPlaying playWhenReady=${player.playWhenReady} state=${stateName(player)}"
        )
        if (wasPlaying) {
            player.pause()
        } else {
            player.play()
        }
        playbackLogger.log(
            PlaybackLogLevel.INFO, COMPONENT, "TOGGLE_COMMAND_RESULT",
            "isPlaying=${player.isPlaying} playWhenReady=${player.playWhenReady} state=${stateName(player)}"
        )
        // Persist on pause; on resume the periodic ticker keeps state fresh.
        if (!player.isPlaying) saveOnEvent(player)
    }

    override fun seekTo(positionMs: Long) {
        val player = controller
        if (player != null) {
            playbackLogger.log(
                PlaybackLogLevel.INFO, COMPONENT, "SEEK_COMMAND",
                "to=$positionMs from=${player.currentPosition} state=${stateName(player)}"
            )
            player.seekTo(positionMs)
            saveOnEvent(player)
        }
    }

    override fun next() {
        val player = controller
        if (player != null) {
            playbackLogger.log(
                PlaybackLogLevel.INFO, COMPONENT, "NEXT_COMMAND",
                "index=${player.currentMediaItemIndex} size=${player.mediaItemCount}"
            )
            player.seekToNextMediaItem()
            saveOnEvent(player)
        }
    }

    override fun previous() {
        val player = controller
        if (player != null) {
            playbackLogger.log(
                PlaybackLogLevel.INFO, COMPONENT, "PREV_COMMAND",
                "index=${player.currentMediaItemIndex} size=${player.mediaItemCount}"
            )
            player.seekToPreviousMediaItem()
            saveOnEvent(player)
        }
    }

    override fun jumpToQueueIndex(index: Int) {
        val player = controller ?: return
        if (queue.isEmpty() || index !in queue.indices) return
        playbackLogger.log(
            PlaybackLogLevel.INFO, COMPONENT, "JUMP_COMMAND",
            "toIndex=$index fromIndex=${player.currentMediaItemIndex} state=${stateName(player)}"
        )
        player.seekTo(index, 0L)
        player.setPlaybackSpeed(defaultPlaybackSpeed)
        player.play()
        saveOnEvent(player)
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
            // Nothing left to play: drop the persisted session too.
            scope.launch { playbackStateRepository.clear() }
            lastSavedSongId = null
        } else {
            // The player auto-advances to the item that takes the removed
            // one's place, keeping playback going.
            player.removeMediaItem(index)
            queue = queue.filterIndexed { i, _ -> i != index }
            saveOnEvent(player)
        }
        syncState(player)
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
        playbackLogger.log(PlaybackLogLevel.INFO, COMPONENT, "SHUFFLE_CHANGED", "enabled=$enabled")
        controller?.let { saveOnEvent(it) }
    }

    override fun setRepeatMode(mode: RepeatMode) {
        controller?.repeatMode = mode.toPlayerRepeatMode()
        playbackLogger.log(PlaybackLogLevel.INFO, COMPONENT, "REPEAT_CHANGED", "mode=$mode")
        controller?.let { saveOnEvent(it) }
    }

    override fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        controller?.let { saveOnEvent(it) }
    }

    override fun setEqualizerEnabled(enabled: Boolean) = equalizerEngine.setEnabled(enabled)

    override fun setEqualizerBandGain(band: Int, gainMb: Int) =
        equalizerEngine.setBandGain(band, gainMb)

    override fun selectEqualizerPreset(presetIndex: Int) =
        equalizerEngine.selectPreset(presetIndex)

    override fun resetEqualizer() = equalizerEngine.reset()

    override fun startMultiOutput(deviceIds: Set<String>) = multiOutputEngine.start(deviceIds)

    override fun stopMultiOutput() = multiOutputEngine.stop()

    override fun setMultiOutputVolume(deviceId: String, volumePercent: Int) =
        multiOutputEngine.setVolume(deviceId, volumePercent)

    override fun refreshMultiOutputCapabilities() = multiOutputEngine.refreshCapabilities()

    private fun stateName(player: Player): String = when (player.playbackState) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN"
    }

    /** Publishes the live DiagnosticsSnapshot derived from the player/state. */
    private fun publishDiagnostics(player: Player?) {
        val connected = controller
        _diagnosticsState.value = DiagnosticsSnapshot(
            serviceAlive = serviceAlive,
            mediaSessionAlive = mediaSessionAlive,
            playerAlive = connected != null && connected.playbackState != Player.STATE_IDLE,
            playerInstance = connected?.let { System.identityHashCode(it) },
            controllerInstance = controllerInstance,
            playerState = connected?.let { stateName(it) } ?: "UNKNOWN",
            isPlaying = connected?.isPlaying ?: false,
            playWhenReady = connected?.playWhenReady ?: false,
            suppressionReason = connected?.playbackSuppressionReason ?: 0,
            currentMediaItemIndex = connected?.currentMediaItemIndex ?: -1,
            positionMs = connected?.currentPosition?.coerceAtLeast(0L) ?: 0L,
            bufferedPositionMs = connected?.bufferedPosition?.coerceAtLeast(0L) ?: 0L,
            durationMs = connected?.duration?.coerceAtLeast(0L) ?: 0L,
            audioSessionId = reportedAudioSessionId,
            servicePlayerInstance = servicePlayerInstance
        )
    }

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
        publishDiagnostics(player)
    }

    /** Starts/stops the position ticker based on the play state. */
    private fun syncPositionTicker(player: Player) {
        if (player.isPlaying) {
            if (positionTicker != null) return
            positionTicker = scope.launch {
                while (isActive) {
                    val current = controller
                    if (current != null) {
                        val pos = current.currentPosition.coerceAtLeast(0L)
                        val dur = current.duration.coerceAtLeast(0L)
                        _playbackState.update { state ->
                            state.copy(positionMs = pos, durationMs = dur)
                        }
                        updateHealthAndStall(pos)
                    }
                    delay(HEALTH_TICK_MS)
                }
            }
        } else {
            positionTicker?.cancel()
            positionTicker = null
        }
    }

    /**
     * Periodic PLAYBACK_HEALTH snapshot (throttled to ~1/s) and PLAYBACK_STALLED
     * detection: isPlaying true but position not advancing for ~10 s. This is
     * the primary observable for the background-stop bug. It only observes and
     * logs — it never auto-restarts playback.
     */
    private fun updateHealthAndStall(positionMs: Long) {
        val player = controller ?: return
        val now = android.os.SystemClock.elapsedRealtime()

        // Advance tracking: the position changed (or jumped), so reset the clock.
        if (positionMs != lastHealthPositionMs) {
            lastHealthPositionMs = positionMs
            lastAdvanceElapsedMs = now
            stallReported = false
        }

        // Anticipated progress if audio were actually advancing.
        val advancedSinceStallCheck = (now - lastAdvanceElapsedMs) >= STALL_THRESHOLD_MS

        // Throttle the verbose health entry to ~1/s.
        if (now - lastHealthLogMs >= HEALTH_LOG_INTERVAL_MS) {
            lastHealthLogMs = now
            val state = stateName(player)
            val health =
                "isPlaying=${player.isPlaying} state=$state " +
                    "playWhenReady=${player.playWhenReady} position=$positionMs " +
                    "buffered=${player.bufferedPosition} audioSession=$reportedAudioSessionId " +
                    "player=$servicePlayerInstance controller=$controllerInstance " +
                    "serviceAlive=${serviceAlive} mediaSessionAlive=${mediaSessionAlive}"
            playbackLogger.log(PlaybackLogLevel.DEBUG, COMPONENT, "PLAYBACK_HEALTH", health)
        }

        // Stall: claims playing but the position has not advanced for >=10s.
        if (player.isPlaying && player.playbackState == Player.STATE_READY && advancedSinceStallCheck) {
            if (!stallReported) {
                stallReported = true
                val detail =
                    "lastPosition=$lastHealthPositionMs currentPosition=$positionMs " +
                        "elapsedSinceAdvance=${now - lastAdvanceElapsedMs}ms state=${stateName(player)} " +
                        "suppression=${player.playbackSuppressionReason} " +
                        "audioSession=$reportedAudioSessionId player=$servicePlayerInstance " +
                        "controller=$controllerInstance serviceAlive=$serviceAlive " +
                        "mediaSessionAlive=$mediaSessionAlive"
                playbackLogger.log(
                    PlaybackLogLevel.ERROR, COMPONENT, "PLAYBACK_STALLED", detail
                )
                _diagnosticsState.update {
                    it.copy(lastStall = detail)
                }
            }
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
        /** How often the playback position is persisted to Room while playing. */
        const val SAVE_INTERVAL_MS = 3_000L
        /** Frequency of the PLAYBACK_HEALTH + stall detector loop (ms). */
        const val HEALTH_TICK_MS = 500L
        /** How often a verbose HEALTH entry is written (ms). */
        const val HEALTH_LOG_INTERVAL_MS = 1_000L
        /** Position must fail to advance this long before flagging a stall (ms). */
        const val STALL_THRESHOLD_MS = 10_000L
        const val TAG = "PMusicPlayback"
        /** Component tag used in the playback diagnostics log. */
        const val COMPONENT = "CONTROLLER"
    }
}
