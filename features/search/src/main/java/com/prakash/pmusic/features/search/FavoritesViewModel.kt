package com.prakash.pmusic.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prakash.pmusic.domain.model.PlaybackState
import com.prakash.pmusic.domain.model.Song
import com.prakash.pmusic.domain.repository.LibraryRepository
import com.prakash.pmusic.domain.repository.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Favorites destination.
 *
 * Favorites live on the Song model and are exposed reactively by the Room DAO,
 * so toggling a favorite anywhere in the app updates this list immediately.
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val playbackController: PlaybackController
) : ViewModel() {

    val favorites: StateFlow<List<Song>> = libraryRepository.observeFavoriteSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Live playback snapshot for the now-playing highlight. */
    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    /** Plays a single song, replacing the current queue. */
    fun playSong(song: Song) = playbackController.playSong(song)

    /** Plays the favorites list as a queue starting at [startIndex]. */
    fun playQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        playbackController.playQueue(songs, startIndex)
    }

    /** Toggles the favorite flag on [song]. */
    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            libraryRepository.setFavorite(song.id, !song.isFavorite)
        }
    }

    private companion object {
        /** Stop collecting flows shortly after the UI stops observing. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
