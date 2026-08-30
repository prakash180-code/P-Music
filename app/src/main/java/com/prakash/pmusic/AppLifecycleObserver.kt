package com.prakash.pmusic

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.prakash.pmusic.domain.model.PlaybackLogLevel
import com.prakash.pmusic.domain.repository.PlaybackController
import com.prakash.pmusic.domain.repository.PlaybackLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks app foreground/background transitions via [ProcessLifecycleOwner] and
 * writes an APP_FOREGROUND / APP_BACKGROUND diagnostic snapshot each time so the
 * playback log shows exactly what the player was doing when the app went away
 * (the key window for the background-stop bug).
 *
 * Diagnostics only — it never controls playback.
 */
@Singleton
class AppLifecycleObserver @Inject constructor(
    private val playbackController: PlaybackController,
    private val playbackLogger: PlaybackLogger
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        snapshot("APP_FOREGROUND")
    }

    override fun onStop(owner: LifecycleOwner) {
        snapshot("APP_BACKGROUND")
    }

    private fun snapshot(event: String) {
        val state = playbackController.playbackState.value
        val diagnostics = playbackController.diagnosticsState.value
        playbackLogger.log(
            PlaybackLogLevel.INFO,
            "APP",
            event,
            "playerState=${diagnostics.playerState} isPlaying=${state.isPlaying} " +
                "playWhenReady=${state.isPlaying || diagnostics.playWhenReady} " +
                "position=${state.positionMs} serviceAlive=${diagnostics.serviceAlive} " +
                "player=${diagnostics.servicePlayerInstance}"
        )
    }

    companion object {
        /** Registers [observer] to the process lifecycle. */
        fun register(observer: AppLifecycleObserver) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        }
    }
}
