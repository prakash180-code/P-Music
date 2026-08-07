package com.prakash.pmusic.features.statistics

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Statistics screen.
 *
 * Aggregates are derived in memory from the reactive song list (the single
 * source of truth in Room), so every count updates live after a rescan or a
 * favorite toggle. Most-played and recently-played lists come straight from
 * the repository's indexed flows.
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val playbackController: PlaybackController
) : ViewModel() {

    val state: StateFlow<StatisticsState> = combine(
        libraryRepository.observeSongs(),
        libraryRepository.observeMostPlayed(TOP_LIST_SIZE),
        libraryRepository.observeRecentlyPlayed(TOP_LIST_SIZE)
    ) { songs, mostPlayed, recentlyPlayed ->
        StatisticsState(
            songCount = songs.size,
            albumCount = songs.map { it.albumId }.distinct().size,
            artistCount = songs.map { it.artistId }.distinct().size,
            genreCount = songs.map { it.genre }.filter { it.isNotBlank() }.distinct().size,
            favoriteCount = songs.count { it.isFavorite },
            totalPlayCount = songs.sumOf { it.playCount.toLong() },
            unplayedCount = songs.count { it.playCount == 0 },
            totalDurationMs = songs.sumOf { it.durationMs },
            mostPlayed = mostPlayed,
            recentlyPlayed = recentlyPlayed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StatisticsState())

    /** Live playback snapshot for the now-playing highlight. */
    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    /** Plays a single song, replacing the current queue. */
    fun playSong(song: Song) = playbackController.playSong(song)

    /** Plays [songs] as a queue starting at [startIndex]. */
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

        /** How many entries to show in the Most played / Recently played lists. */
        const val TOP_LIST_SIZE = 5
    }
}

/** Derived statistics for the whole library. */
data class StatisticsState(
    val songCount: Int = 0,
    val albumCount: Int = 0,
    val artistCount: Int = 0,
    val genreCount: Int = 0,
    val favoriteCount: Int = 0,
    val totalPlayCount: Long = 0,
    val unplayedCount: Int = 0,
    val totalDurationMs: Long = 0,
    val mostPlayed: List<Song> = emptyList(),
    val recentlyPlayed: List<Song> = emptyList()
)
