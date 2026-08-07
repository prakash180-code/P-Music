package com.prakash.pmusic.features.player

import androidx.lifecycle.ViewModel
import com.prakash.pmusic.domain.model.PlaybackState
import com.prakash.pmusic.domain.model.RepeatMode
import com.prakash.pmusic.domain.repository.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the Now Playing screen and Mini Player.
 *
 * It observes the shared [PlaybackController] singleton and translates the
 * high-level transport actions the UI cares about (toggle shuffle, cycle
 * repeat, cycle speed) into controller calls, so the composables stay simple
 * and stateless.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController
) : ViewModel() {

    /** Live playback snapshot (current song, position, mode flags, queue). */
    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    fun togglePlayPause() = playbackController.togglePlayPause()

    fun next() = playbackController.next()

    fun previous() = playbackController.previous()

    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    /** Toggles shuffle on/off. */
    fun toggleShuffle() {
        playbackController.setShuffleEnabled(!playbackState.value.shuffleEnabled)
    }

    /** Cycles repeat: OFF -> ALL -> ONE -> OFF. */
    fun cycleRepeatMode() {
        val next = when (playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        playbackController.setRepeatMode(next)
    }

    /** Cycles playback speed through the supported presets. */
    fun cyclePlaybackSpeed() {
        val current = playbackState.value.playbackSpeed
        val next = PLAYBACK_SPEEDS.firstOrNull { it > current + SPEED_EPSILON }
            ?: PLAYBACK_SPEEDS.first()
        playbackController.setPlaybackSpeed(next)
    }

    /** Jumps the queue to [index] and starts playing. */
    fun jumpToQueueIndex(index: Int) = playbackController.jumpToQueueIndex(index)

    private companion object {
        /** Supported speed presets, ascending. */
        val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

        /** Float tolerance when comparing speeds. */
        const val SPEED_EPSILON = 0.01f
    }
}
