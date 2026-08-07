package com.prakash.pmusic.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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
 *   (play/pause/seek/artwork) without any custom notification code. Media
 *   button taps are routed through the manifest-declared MediaButtonReceiver
 *   (see the `:receiver` module).
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    companion object {
        // Actions used by the home-screen playback widget. The widget's
        // transport buttons are service PendingIntents (a BroadcastReceiver
        // cannot bind to a service on modern Android), so the commands are
        // executed here in onStartCommand on the session's player.
        const val ACTION_PLAY_PAUSE = "com.prakash.pmusic.service.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.prakash.pmusic.service.action.NEXT"
        const val ACTION_PREVIOUS = "com.prakash.pmusic.service.action.PREVIOUS"

        private const val TAG = "PMusicPlaybackService"
    }

    @Inject
    lateinit var equalizerEngine: AudioFxEqualizerEngine

    private var mediaSession: MediaSession? = null

    /**
     * Reports the audio session the ExoPlayer actually created. Confirms the
     * generated id to the equalizer (and catches any device that overrides
     * it).
     */
    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            Log.i(TAG, "onAudioSessionIdChanged($audioSessionId)")
            equalizerEngine.setAudioSessionId(audioSessionId)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (player.playbackState == Player.STATE_READY) {
                val reported = (player as? ExoPlayer)?.audioSessionId
                Log.d(TAG, "onEvents() session reported by player: $reported")
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

        mediaSession = MediaSession.Builder(this, player).build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .build()
            .apply {
                // Media3 1.6+ configures the small icon on the provider.
                setSmallIcon(R.drawable.ic_notification)
            }
        setMediaNotificationProvider(notificationProvider)
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
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
