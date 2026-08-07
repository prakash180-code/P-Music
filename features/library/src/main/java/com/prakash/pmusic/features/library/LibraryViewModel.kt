package com.prakash.pmusic.features.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prakash.pmusic.domain.model.Album
import com.prakash.pmusic.domain.model.Artist
import com.prakash.pmusic.domain.model.Genre
import com.prakash.pmusic.domain.model.LibraryScanState
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Library screen.
 *
 * All read paths are Room-backed [Flow]s promoted to [StateFlow] so the UI
 * observes reactively without extra plumbing. Playback is delegated to the
 * injected (singleton) [PlaybackController], which is the same instance the
 * activity connects on startup — queue and play/pause stay in sync.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val playbackController: PlaybackController
) : ViewModel() {

    /** Every indexed song, in the scanner's natural (album) order. */
    val songs: StateFlow<List<Song>> = libraryRepository.observeSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val albums: StateFlow<List<Album>> = libraryRepository.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val artists: StateFlow<List<Artist>> = libraryRepository.observeArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val genres: StateFlow<List<Genre>> = libraryRepository.observeGenres()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Home-section preview lists. */
    val recentlyAdded: StateFlow<List<Song>> = libraryRepository.observeRecentlyAdded(HOME_SECTION_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val mostPlayed: StateFlow<List<Song>> = libraryRepository.observeMostPlayed(HOME_SECTION_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val favorites: StateFlow<List<Song>> = libraryRepository.observeFavoriteSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Live scan progress so the header can surface scanning/complete states. */
    val scanState: StateFlow<LibraryScanState> = libraryRepository.scanState

    /** Live playback snapshot for the now-playing highlight. */
    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    private val _selection = MutableStateFlow<LibrarySelection>(LibrarySelection.None)
    val selection: StateFlow<LibrarySelection> = _selection.asStateFlow()

    /**
     * Resolved detail for the open album/artist/genre (null on the browser).
     * Derived from the selection plus the reactive library lists so the title
     * and song list always match the latest scan state.
     */
    val detail: StateFlow<LibraryDetail?> = combine(
        _selection,
        songs,
        albums,
        artists,
        genres
    ) { selection, songs, albums, artists, genres ->
        when (selection) {
            LibrarySelection.None -> null
            is LibrarySelection.Album ->
                LibraryDetail(
                    title = albums.firstOrNull { it.id == selection.id }?.name ?: "Album",
                    songs = songs.filter { it.albumId == selection.id }
                )
            is LibrarySelection.Artist ->
                LibraryDetail(
                    title = artists.firstOrNull { it.id == selection.id }?.name ?: "Artist",
                    songs = songs.filter { it.artistId == selection.id }
                )
            is LibrarySelection.Genre ->
                LibraryDetail(
                    title = selection.name,
                    songs = songs.filter { it.genre == selection.name }
                )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Opens the detail screen for [albumId]. */
    fun openAlbum(albumId: Long) {
        _selection.value = LibrarySelection.Album(albumId)
    }

    /** Opens the detail screen for [artistId]. */
    fun openArtist(artistId: Long) {
        _selection.value = LibrarySelection.Artist(artistId)
    }

    /** Opens the detail screen for a genre. */
    fun openGenre(name: String) {
        _selection.value = LibrarySelection.Genre(name)
    }

    /** Returns to the album/artist/genre browser. */
    fun closeDetail() {
        _selection.value = LibrarySelection.None
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

    /** Forces a full rescan of the MediaStore library. */
    fun refresh() {
        viewModelScope.launch {
            libraryRepository.scanLibrary(force = true)
        }
    }

    private companion object {
        /** Stop collecting flows shortly after the UI stops observing. */
        const val STOP_TIMEOUT_MS = 5_000L

        /** Item cap for the horizontal home-section rows. */
        const val HOME_SECTION_LIMIT = 30
    }
}

/** What the Library destination is currently showing. */
sealed interface LibrarySelection {
    data object None : LibrarySelection
    data class Album(val id: Long) : LibrarySelection
    data class Artist(val id: Long) : LibrarySelection
    data class Genre(val name: String) : LibrarySelection
}

/** Resolved detail screen state: a title and the songs it contains. */
data class LibraryDetail(
    val title: String,
    val songs: List<Song>
)
