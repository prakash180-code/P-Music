package com.prakash.pmusic.features.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prakash.pmusic.domain.model.PlaybackState
import com.prakash.pmusic.domain.model.RepeatMode
import com.prakash.pmusic.domain.repository.LibraryRepository
import com.prakash.pmusic.domain.repository.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Now Playing screen and Mini Player.
 *
 * It observes the shared [PlaybackController] singleton and translates the
 * high-level transport actions the UI cares about (toggle shuffle, cycle
 * repeat, cycle speed) into controller calls, so the composables stay simple
 * and stateless.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    /** Live playback snapshot (current song, position, mode flags, queue). */
    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    /**
     * Whether the currently playing song is favorited, kept fresh from Room so
     * toggling it anywhere (player, notification, library) updates everywhere.
     */
    val currentFavorite: StateFlow<Boolean> = playbackController.playbackState
        .map { it.currentSong?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(false)
            } else {
                libraryRepository.observeSong(id).map { it?.isFavorite ?: false }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** Toggles the favorite flag on the currently playing song. */
    fun toggleCurrentFavorite() {
        val song = playbackState.value.currentSong ?: return
        viewModelScope.launch {
            libraryRepository.setFavorite(song.id, !currentFavorite.value)
        }
    }

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

    /**
     * Called after the user confirmed the system delete request for the
     * currently playing song: purges the Room row and removes the song from
     * the player (playback continues with the next item).
     */
    fun onSongDeleted() {
        val song = playbackState.value.currentSong ?: return
        viewModelScope.launch {
            libraryRepository.deleteSongsFromDatabase(listOf(song))
            playbackController.removeCurrentSong()
        }
    }

    private companion object {
        /** Stop collecting flows shortly after the UI stops observing. */
        const val STOP_TIMEOUT_MS = 5_000L

        /** Supported speed presets, ascending. */
        val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

        /** Float tolerance when comparing speeds. */
        const val SPEED_EPSILON = 0.01f
    }
}
