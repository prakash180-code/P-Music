package com.prakash.pmusic.features.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prakash.pmusic.domain.model.PlaybackState
import com.prakash.pmusic.domain.model.Playlist
import com.prakash.pmusic.domain.model.Song
import com.prakash.pmusic.domain.repository.LibraryRepository
import com.prakash.pmusic.domain.repository.PlaybackController
import com.prakash.pmusic.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Playlists tab.
 *
 * Owns the list of playlists, the currently open playlist (detail) and its
 * ordered songs, plus the all-songs list used by the add-songs picker. All
 * read paths are Room-backed flows promoted to [StateFlow]; mutations run on
 * the [viewModelScope] and the UI updates reactively.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val libraryRepository: LibraryRepository,
    private val playbackController: PlaybackController
) : ViewModel() {

    /** All playlists, most recently updated first. */
    val playlists: StateFlow<List<Playlist>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Every indexed song, used by the add-songs picker. */
    val allSongs: StateFlow<List<Song>> = libraryRepository.observeSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Live playback snapshot for the now-playing highlight. */
    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    private val _pickerOpen = MutableStateFlow(false)
    val isPickerOpen: StateFlow<Boolean> = _pickerOpen.asStateFlow()

    /** The currently open playlist, or null on the list screen. */
    val selectedPlaylist: StateFlow<Playlist?> =
        combine(_selectedPlaylistId, playlists) { id, list ->
            list.firstOrNull { it.id == id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Songs of the open playlist in stored order. */
    val playlistSongs: StateFlow<List<Song>> = _selectedPlaylistId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else playlistRepository.observePlaylistSongs(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init {
        // Close the detail whenever the open playlist disappears (deleted or
        // removed through any other path) so the UI never shows a stale id.
        viewModelScope.launch {
            combine(_selectedPlaylistId, playlists) { id, list -> id to list }
                .collect { (id, list) ->
                    if (id != null && list.none { it.id == id }) {
                        _selectedPlaylistId.value = null
                    }
                }
        }
    }

    fun openPlaylist(id: Long) {
        _selectedPlaylistId.value = id
    }

    fun closePlaylist() {
        _selectedPlaylistId.value = null
    }

    fun showPicker() {
        _pickerOpen.value = true
    }

    fun hidePicker() {
        _pickerOpen.value = false
    }

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            playlistRepository.createPlaylist(trimmed)
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            playlistRepository.renamePlaylist(id, trimmed)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(id)
            if (_selectedPlaylistId.value == id) {
                _selectedPlaylistId.value = null
            }
        }
    }

    fun addSong(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            playlistRepository.addSong(playlistId, songId)
        }
    }

    fun removeSong(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            playlistRepository.removeSong(playlistId, songId)
        }
    }

    /** Moves a song by one position (used by the move up/down menu). */
    fun moveSong(playlistId: Long, fromIndex: Int, toIndex: Int) {
        val songs = playlistSongs.value
        if (toIndex !in songs.indices) return
        val ordered = songs.map { it.id }.toMutableList()
        val moved = ordered.removeAt(fromIndex)
        ordered.add(toIndex, moved)
        reorder(playlistId, ordered)
    }

    /** Persists a full reorder (used by drag and drop). */
    fun reorder(playlistId: Long, orderedSongIds: List<Long>) {
        viewModelScope.launch {
            playlistRepository.reorderSongs(playlistId, orderedSongIds)
        }
    }

    /** Plays the open playlist starting at [startIndex]. */
    fun playPlaylist(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        playbackController.playQueue(songs, startIndex)
    }

    private companion object {
        /** Stop collecting flows shortly after the UI stops observing. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
