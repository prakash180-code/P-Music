package com.prakash.pmusic.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prakash.pmusic.domain.model.Album
import com.prakash.pmusic.domain.model.Artist
import com.prakash.pmusic.domain.model.Genre
import com.prakash.pmusic.domain.model.PlaybackState
import com.prakash.pmusic.domain.model.Song
import com.prakash.pmusic.domain.repository.LibraryRepository
import com.prakash.pmusic.domain.repository.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Search screen.
 *
 * The query is debounced and matched in memory against the reactive library
 * lists (songs/albums/artists/genres), which keeps results instant for local
 * libraries without extra database round-trips. Recent searches are kept for
 * the session (committed on the IME search action or when a result is tapped).
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val playbackController: PlaybackController
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val songs: StateFlow<List<Song>> = libraryRepository.observeSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val albums: StateFlow<List<Album>> = libraryRepository.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val artists: StateFlow<List<Artist>> = libraryRepository.observeArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val genres: StateFlow<List<Genre>> = libraryRepository.observeGenres()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Live playback snapshot for the now-playing highlight. */
    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    /** Grouped search results for the current (debounced) query. */
    val results: StateFlow<SearchResults> = combine(
        _query.debounce(DEBOUNCE_MS),
        songs,
        albums,
        artists,
        genres
    ) { query, songs, albums, artists, genres ->
        val term = query.trim()
        if (term.isEmpty()) {
            SearchResults.EMPTY
        } else {
            SearchResults(
                songs = songs.filter { song ->
                    song.title.contains(term, ignoreCase = true) ||
                        song.artist.contains(term, ignoreCase = true) ||
                        song.album.contains(term, ignoreCase = true)
                },
                albums = albums.filter {
                    it.name.contains(term, ignoreCase = true) || it.artist.contains(term, ignoreCase = true)
                },
                artists = artists.filter { it.name.contains(term, ignoreCase = true) },
                genres = genres.filter { it.name.contains(term, ignoreCase = true) }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SearchResults.EMPTY)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** Records the current query as a recent search. */
    fun commitQuery() {
        val term = _query.value.trim()
        if (term.isEmpty()) return
        _recentSearches.value = (listOf(term) + _recentSearches.value.filterNot {
            it.equals(term, ignoreCase = true)
        }).take(MAX_RECENT_SEARCHES)
    }

    /** Re-runs a previous search term. */
    fun applyRecent(recent: String) {
        _query.value = recent
        commitQuery()
    }

    fun removeRecent(recent: String) {
        _recentSearches.value = _recentSearches.value.filterNot { it == recent }
    }

    fun clearRecent() {
        _recentSearches.value = emptyList()
    }

    /** Plays a single song, replacing the current queue. */
    fun playSong(song: Song) = playbackController.playSong(song)

    /** Plays [songs] as a queue starting at [startIndex]. */
    fun playQueue(songs: List<Song>, startIndex: Int) {
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

        /** How long to wait before recomputing results while typing. */
        const val DEBOUNCE_MS = 250L

        /** Cap on stored recent searches. */
        const val MAX_RECENT_SEARCHES = 8
    }
}

/** Grouped search results for one query. */
data class SearchResults(
    val songs: List<Song>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val genres: List<Genre>
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && genres.isEmpty()

    companion object {
        val EMPTY = SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
    }
}
