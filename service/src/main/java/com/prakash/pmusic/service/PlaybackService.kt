package com.prakash.pmusic.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.prakash.pmusic.domain.model.ACTION_OPEN_NOW_PLAYING
import com.prakash.pmusic.domain.repository.LibraryRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The single playback host of P-Music.
 *
 * Decisions:
 * - Extends [MediaSessionService] (the Media3-recommended base) instead of a
 *   plain Service. Media3 then handles audio focus, becoming-noisy
 *   (headphones unplugged), Bluetooth/headset transport controls, and the
 *   foreground notification automatically through the [MediaSession].
 * - The [ExoPlayer] is created here and owned by the session; UI code never
 *   touches it directly. The app talks to it through a [MediaController]
 *   (see [Media3PlaybackController]).
 * - [DefaultMediaNotificationProvider] renders the playback notification
 *   (play/pause/seek/artwork) without any custom notification code, extended
 *   with a favorite toggle. Media button taps are routed through the
 *   manifest-declared MediaButtonReceiver (see the `:receiver` module).
 * - Tapping the notification content opens the Now Playing screen through
 *   [ACTION_OPEN_NOW_PLAYING], which MainActivity exposes as an intent-filter.
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class, ExperimentalCoroutinesApi::class)
class PlaybackService : MediaSessionService() {

    companion object {
        // Actions used by the home-screen playback widget. The widget's
        // transport buttons are service PendingIntents (a BroadcastReceiver
        // cannot bind to a service on modern Android), so the commands are
        // executed here in onStartCommand on the session's player.
        const val ACTION_PLAY_PAUSE = "com.prakash.pmusic.service.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.prakash.pmusic.service.action.NEXT"
        const val ACTION_PREVIOUS = "com.prakash.pmusic.service.action.PREVIOUS"

        /** Custom session command behind the notification favorite button. */
        const val ACTION_TOGGLE_FAVORITE = "com.prakash.pmusic.service.action.TOGGLE_FAVORITE"

        private const val TAG = "PMusicPlaybackService"
    }

    @Inject
    lateinit var equalizerEngine: AudioFxEqualizerEngine

    @Inject
    lateinit var libraryRepository: LibraryRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Id of the current media item's song, read from the MediaItem mediaId. */
    private val currentSongId = MutableStateFlow<Long?>(null)

    /** Whether the current song is favorited; drives the notification heart. */
    private val currentFavorite = MutableStateFlow(false)

    private var mediaSession: MediaSession? = null

    /**
     * Reports the audio session the ExoPlayer actually created. Confirms the
     * generated id to the equalizer (and catches any device that overrides
     * it), and tracks the current song so the favorite heart follows it.
     */
    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            Log.i(TAG, "onAudioSessionIdChanged($audioSessionId)")
            equalizerEngine.setAudioSessionId(audioSessionId)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            // The MediaItem tag (a Song) is not parcelable, so it is dropped
            // when items cross the controller/session boundary. The mediaId
            // string is preserved, and library songs store their Room id there.
            val songId = player.currentMediaItem?.mediaId?.toLongOrNull()
            if (currentSongId.value != songId) {
                currentSongId.value = songId
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = mediaSession?.player
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> if (player != null) {
                if (player.isPlaying) player.pause() else player.play()
            }
            ACTION_NEXT -> player?.seekToNextMediaItem()
            ACTION_PREVIOUS -> player?.seekToPreviousMediaItem()
            else -> return super.onStartCommand(intent, flags, startId)
        }
        return START_STICKY
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()

        // A stable audio session lets the equalizer effect bind to this
        // player. The id must come from AudioManager.generateAudioSessionId():
        // passing a hard-coded constant makes AudioTrack.Builder throw
        // "Cannot create AudioTrack" on modern Android. The same id is given
        // to the equalizer so it binds to exactly this player.
        val audioSessionId =
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
        equalizerEngine.setAudioSessionId(audioSessionId)
        Log.i(TAG, "assigning player audio session $audioSessionId")

        val player = ExoPlayer.Builder(this).build().apply {
            setAudioSessionId(audioSessionId)
            // Default audio attributes with focus handling let Media3
            // request/relinquish audio focus on our behalf.
            setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            // Pause when headphones are unplugged.
            setHandleAudioBecomingNoisy(true)
            addListener(playerListener)
        }

        // Tapping the notification opens the Now Playing screen.
        val openPlayerIntent = Intent(ACTION_OPEN_NOW_PLAYING).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            openPlayerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Registers the custom command with the session so the notification
        // heart's PendingIntent can reach the callback below.
        val favoriteCommand = CommandButton.Builder(CommandButton.ICON_HEART_UNFILLED)
            .setSessionCommand(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
            .setDisplayName("Add to favorites")
            .build()

        val sessionCallback = object : MediaSession.Callback {
            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == ACTION_TOGGLE_FAVORITE) {
                    toggleFavorite(session)
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(sessionCallback)
            .setCustomLayout(listOf(favoriteCommand))
            .build()

        val notificationProvider = FavoriteNotificationProvider(this) {
            currentFavorite.value
        }.apply {
            // Media3 1.6+ configures the small icon on the provider.
            setSmallIcon(R.drawable.ic_notification)
        }
        setMediaNotificationProvider(notificationProvider)

        // Keep the notification heart in sync with the library (toggling the
        // favorite anywhere — player, library, notification — updates it).
        serviceScope.launch {
            currentSongId
                .flatMapLatest { id ->
                    if (id == null) flowOf(null) else libraryRepository.observeSong(id)
                }
                .collect { song ->
                    currentFavorite.value = song?.isFavorite ?: false
                    mediaSession?.let { onUpdateNotification(it, false) }
                }
        }
    }

    /** Toggles the favorite flag of the song shown by [session]'s player. */
    private fun toggleFavorite(session: MediaSession) {
        val songId = session.player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        serviceScope.launch {
            libraryRepository.setFavorite(songId, !currentFavorite.value)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Do not linger as a ghost process when the user swipes the app away
        // while nothing is playing.
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
