package com.prakash.pmusic.features.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prakash.pmusic.domain.model.DetectedFolder
import com.prakash.pmusic.domain.model.LibraryFolderType
import com.prakash.pmusic.domain.model.LibraryScanState
import com.prakash.pmusic.domain.repository.LibraryFolderRepository
import com.prakash.pmusic.domain.repository.LibraryRepository
import com.prakash.pmusic.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State for the first-run folder-exclusion wizard.
 *
 * Appears only once (tracked by the `folderWizardShown` preference), only
 * while no folder rules exist, and only after the first MediaStore scan
 * completes so detection sees real folders. "Exclude recommended" adds every
 * detected folder as an EXCLUDED rule and re-scans, which immediately hides
 * those songs from the library.
 */
@HiltViewModel
class FolderWizardViewModel @Inject constructor(
    private val folderRepository: LibraryFolderRepository,
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _detected = MutableStateFlow<List<DetectedFolder>>(emptyList())
    val detected: StateFlow<List<DetectedFolder>> = _detected.asStateFlow()

    /** Whether the wizard dialog should be visible right now. */
    val show: StateFlow<Boolean> = combine(
        preferencesRepository.preferences,
        _detected
    ) { preferences, detected ->
        !preferences.folderWizardShown && detected.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    init {
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            if (prefs.folderWizardShown) return@launch
            if (folderRepository.hasFolders()) return@launch
            // Wait until the first scan finishes so the detector sees real data.
            libraryRepository.scanState.filterIsInstance<LibraryScanState.Complete>().first()
            _detected.value = folderRepository.discoverNonMusicFolders()
        }
    }

    /** Excludes every detected folder, then re-scans to purge their songs. */
    fun excludeRecommended() {
        val detectedNow = _detected.value
        if (detectedNow.isEmpty()) return
        viewModelScope.launch {
            detectedNow.forEach {
                folderRepository.addFolder(it.folderPath, it.displayName, LibraryFolderType.EXCLUDED)
            }
            preferencesRepository.setFolderWizardShown(true)
            _detected.value = emptyList()
            libraryRepository.scanLibrary(force = true)
        }
    }

    /** Closes the wizard and opens the folder manager for manual review. */
    fun openFolderManager() {
        viewModelScope.launch {
            preferencesRepository.setFolderWizardShown(true)
            _detected.value = emptyList()
        }
    }

    /** Skips the wizard entirely. */
    fun skip() {
        viewModelScope.launch {
            preferencesRepository.setFolderWizardShown(true)
            _detected.value = emptyList()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
