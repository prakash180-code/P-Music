package com.prakash.pmusic.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prakash.pmusic.domain.model.AppPreferences
import com.prakash.pmusic.domain.model.DiagnosticsSnapshot
import com.prakash.pmusic.domain.model.PlaybackState
import com.prakash.pmusic.domain.repository.PlaybackController
import com.prakash.pmusic.domain.repository.PlaybackLogger
import com.prakash.pmusic.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Playback Diagnostics screen.
 *
 * Bridges three live sources:
 *  - [PlaybackController.diagnosticsState]: the live service/session/player
 *    snapshot (service alive, media session, player state, position, audio
 *    session, last error / event / health / stall).
 *  - [PlaybackLogger]: reads / exports / clears the persistent playback log.
 *  - [PreferencesRepository]: the "Playback Debug Logging" toggle.
 *
 * Reading and exporting the log run on the IO dispatcher so a large log never
 * blocks the UI. Export content is fetched and handed to the UI, which shares
 * it through the Android share sheet as `p_music_playback_log.txt`.
 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val playbackLogger: PlaybackLogger,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    /** Live playback-stack diagnostics snapshot. */
    val diagnostics: StateFlow<DiagnosticsSnapshot> = playbackController.diagnosticsState

    /** Playback state (for convenience position/isPlaying fields). */
    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    /** Persisted preferences (for the debug-logging toggle). */
    val preferences: StateFlow<AppPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppPreferences())

    private val _logContent = MutableStateFlow<String?>(null)
    val logContent: StateFlow<String?> = _logContent.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Toggles verbose DEBUG playback logging (also flips the logger level). */
    fun setDebugLogging(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setPlaybackDebugLogging(enabled)
        }
    }

    /** Loads the current (rotated log) contents off the main thread. */
    fun viewLogs() {
        viewModelScope.launch {
            _loading.value = true
            _logContent.value = null
            val content = withContext(Dispatchers.IO) { playbackLogger.read() }
            _logContent.value = content.ifBlank { "(log is empty)" }
            _loading.value = false
        }
    }

    /** Loads the full merged log for export off the main thread. */
    fun loadForExport(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) { playbackLogger.export() }
            onReady(content)
        }
    }

    /** Clears all playback log files. */
    fun clearLogs() {
        playbackLogger.clear()
        _logContent.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
