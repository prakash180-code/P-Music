package com.prakash.pmusic.features.folders

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prakash.pmusic.domain.model.DetectedFolder
import com.prakash.pmusic.domain.model.FolderRulesMatcher
import com.prakash.pmusic.domain.model.FolderStats
import com.prakash.pmusic.domain.model.LibraryFolder
import com.prakash.pmusic.domain.model.LibraryFolderType
import com.prakash.pmusic.domain.model.LibraryScanState
import com.prakash.pmusic.domain.model.Song
import com.prakash.pmusic.domain.repository.LibraryFolderRepository
import com.prakash.pmusic.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Presentation shape of one folder row. */
data class FolderUi(
    val id: Long,
    val path: String,
    val name: String,
    /** True when this folder is scanned; false when it is excluded. */
    val included: Boolean,
    val songCount: Int,
    val totalSizeBytes: Long,
    val totalDurationMs: Long,
    val lastModified: Long,
    val lastScanned: Long?,
    val dateAdded: Long
)

/** Sort orders for the folder list. */
enum class FolderSort { NAME, SONGS, MODIFIED, SIZE }

/** Full state of the folder-manager screen. */
data class FolderManagerUiState(
    val folders: List<FolderUi> = emptyList(),
    val restrictedMode: Boolean = false,
    val searchQuery: String = "",
    val sort: FolderSort = FolderSort.NAME,
    val suggestions: List<DetectedFolder> = emptyList(),
    val suggestionsDismissed: Boolean = false,
    val statsFolder: FolderUi? = null,
    val busy: Boolean = false,
    val message: String? = null
)

/**
 * State for the Library Folder Manager.
 *
 * The screen shows every folder that currently holds songs (derived from the
 * live song table) plus any configured rules, so excluded folders stay visible
 * and can be switched back on. Each folder is scanned by default; flipping its
 * switch off creates an EXCLUDED rule (recursive) whose songs are purged by a
 * reconciliation rescan, and flipping it back on removes the rule so a rescan
 * pulls the songs back.
 */
@HiltViewModel
class FolderManagerViewModel @Inject constructor(
    private val folderRepository: LibraryFolderRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private var lastRules: List<LibraryFolder> = emptyList()
    private var lastFolderUis: List<FolderUi> = emptyList()

    private val _uiState = MutableStateFlow(FolderManagerUiState())

    /** Reactive full state of the screen, recomputed as data and UI change. */
    val uiState: StateFlow<FolderManagerUiState> = _uiState.asStateFlow()

    private fun deriveFolders(rules: List<LibraryFolder>, songs: List<Song>) {
        lastRules = rules
        val ruleByPath = rules.associateBy { FolderRulesMatcher.normalize(it.folderPath) }
        val songFolders = songs
            .map { it.path.substringBeforeLast('/') }
            .filter { it.isNotBlank() }
        val restrictedMode = rules.any { it.enabled && it.type == LibraryFolderType.INCLUDED }
        val folderUis = (songFolders + ruleByPath.keys).toSet().map { path ->
            val rule = ruleByPath[path]
            val stats = FolderStats.forFolder(songs, path)
            FolderUi(
                id = rule?.id ?: 0L,
                path = path,
                name = rule?.displayName?.ifBlank { null }
                    ?: path.substringAfterLast('/').ifBlank { path },
                included = rule == null ||
                    rule.type != LibraryFolderType.EXCLUDED ||
                    !rule.enabled,
                songCount = stats.songCount,
                totalSizeBytes = stats.totalSizeBytes,
                totalDurationMs = stats.totalDurationMs,
                lastModified = stats.lastModified,
                lastScanned = rule?.lastScanned,
                dateAdded = rule?.dateAdded ?: 0L
            )
        }
        lastFolderUis = folderUis
        _uiState.update { state ->
            state.copy(
                folders = applySortAndFilter(folderUis, state.searchQuery, state.sort),
                restrictedMode = restrictedMode
            )
        }
    }

    private fun applySortAndFilter(
        folderUis: List<FolderUi>,
        query: String,
        sort: FolderSort
    ): List<FolderUi> {
        val filtered = if (query.isBlank()) folderUis else folderUis.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.path.contains(query, ignoreCase = true)
        }
        return when (sort) {
            FolderSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            FolderSort.SONGS -> filtered.sortedByDescending { it.songCount }
            FolderSort.MODIFIED -> filtered.sortedByDescending { it.lastModified }
            FolderSort.SIZE -> filtered.sortedByDescending { it.totalSizeBytes }
        }
    }

    init {
        refreshSuggestions()
        // Re-detect after every scan completes so new recordings show up and
        // excluded ones disappear.
        viewModelScope.launch {
            libraryRepository.scanState
                .filter { it is LibraryScanState.Complete }
                .collect { refreshSuggestions() }
        }
        // Recompute the folder rows whenever the rules or the song table
        // change (scans, watcher updates, rule mutations).
        combine(
            folderRepository.observeFolders(),
            libraryRepository.observeSongs()
        ) { folders, songs -> deriveFolders(folders, songs) }
            .launchIn(viewModelScope)
    }

    fun onSearch(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                folders = applySortAndFilter(lastFolderUis, query, state.sort)
            )
        }
    }

    fun onSetSort(sort: FolderSort) {
        _uiState.update { state ->
            state.copy(
                sort = sort,
                folders = applySortAndFilter(lastFolderUis, state.searchQuery, sort)
            )
        }
    }

    /**
     * Flips one folder's include/exclude switch. Turning a folder off excludes
     * it recursively; turning it back on removes the exclusion so a rescan
     * brings its songs back.
     */
    fun onToggle(folder: FolderUi) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val rule = lastRules.firstOrNull {
                FolderRulesMatcher.normalize(it.folderPath) == folder.path
            }
            when {
                rule == null -> {
                    // Every folder is scanned by default, so the only meaningful
                    // flip is off -> add an exclusion.
                    folderRepository.addFolder(
                        folder.path,
                        folder.name,
                        LibraryFolderType.EXCLUDED
                    )
                }
                folder.included -> {
                    // The folder has a rule but is still scanned (a disabled
                    // exclusion or an old inclusion): activate the exclusion.
                    when {
                        rule.type == LibraryFolderType.EXCLUDED && !rule.enabled ->
                            folderRepository.setEnabled(rule.id, true)
                        rule.type == LibraryFolderType.INCLUDED ->
                            folderRepository.setType(rule.id, LibraryFolderType.EXCLUDED)
                    }
                }
                else -> {
                    // Turning on: drop the exclusion so the folder is scanned.
                    folderRepository.removeFolder(rule.id)
                }
            }
            rescan()
        }
    }

    /** Entry point for the SAF folder picker: adds the folder as excluded. */
    fun onAddFolder(uri: Uri?) {
        val uriString = uri?.toString() ?: return
        val path = TreePathResolver.resolve(uriString)
        if (path == null) {
            _uiState.update { it.copy(message = "Couldn't open that folder. Try an internal or SD-card folder.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val added = folderRepository.addFolder(
                path,
                path.substringAfterLast('/').ifBlank { path },
                LibraryFolderType.EXCLUDED
            )
            if (added == null) {
                _uiState.update { it.copy(message = "That folder is already configured.") }
            } else {
                _uiState.update { it.copy(message = "Folder excluded.") }
            }
            rescan()
        }
    }

    /** Re-scans the library so the folder's stats and contents refresh. */
    fun onRefreshFolder() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            rescan()
        }
    }

    fun onStatsRequest(folder: FolderUi) {
        _uiState.update { it.copy(statsFolder = folder) }
    }

    fun onStatsClose() {
        _uiState.update { it.copy(statsFolder = null) }
    }

    fun onExcludeSuggestion(detected: DetectedFolder) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            folderRepository.addFolder(detected.folderPath, detected.displayName, LibraryFolderType.EXCLUDED)
            _uiState.update { state ->
                state.copy(
                    suggestions = state.suggestions.filterNot { it.folderPath == detected.folderPath }
                )
            }
            rescan()
        }
    }

    fun onDismissSuggestions() {
        _uiState.update { it.copy(suggestionsDismissed = true) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun rescan() {
        libraryRepository.scanLibrary(force = true)
        _uiState.update { it.copy(busy = false) }
    }

    private fun refreshSuggestions() {
        viewModelScope.launch {
            _uiState.update { it.copy(suggestions = folderRepository.discoverNonMusicFolders()) }
        }
    }
}
