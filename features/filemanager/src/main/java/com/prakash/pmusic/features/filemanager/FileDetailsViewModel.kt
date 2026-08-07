package com.prakash.pmusic.features.filemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prakash.pmusic.domain.model.AudioFileDetails
import com.prakash.pmusic.domain.model.Song
import com.prakash.pmusic.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State for the per-song file-details screen.
 *
 * The selected song is observed reactively from Room (so metadata edits and
 * the final delete are reflected immediately), while the container details
 * MediaStore does not index are extracted once on demand.
 */
@HiltViewModel
class FileDetailsViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _songId = MutableStateFlow<Long?>(null)

    /** The selected song; null while loading, for unknown ids, or once deleted. */
    val song: StateFlow<Song?> = _songId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else libraryRepository.observeSong(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _fileDetails = MutableStateFlow<AudioFileDetails?>(null)
    val fileDetails: StateFlow<AudioFileDetails?> = _fileDetails.asStateFlow()

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    /** Loads [songId] and kicks off the on-demand container extraction. */
    fun open(songId: Long) {
        if (_songId.value == songId) return
        _songId.value = songId
        _deleted.value = false
        _fileDetails.value = null
        viewModelScope.launch {
            val song = song.value ?: libraryRepository.observeSong(songId).firstOrNull()
            song?.let { _fileDetails.value = libraryRepository.readFileDetails(it) }
        }
    }

    /** Called after the MediaStore deletion was confirmed; purges Room. */
    fun onDeleteConfirmed() {
        val current = song.value ?: return
        viewModelScope.launch {
            _deleting.value = true
            libraryRepository.deleteSongsFromDatabase(listOf(current))
            _deleting.value = false
            _deleted.value = true
        }
    }

    /** Called when the user cancels the system delete request. */
    fun onDeleteCancelled() {
        _deleting.value = false
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
