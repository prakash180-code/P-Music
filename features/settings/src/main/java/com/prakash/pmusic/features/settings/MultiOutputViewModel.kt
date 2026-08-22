package com.prakash.pmusic.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prakash.pmusic.domain.model.AppPreferences
import com.prakash.pmusic.domain.model.MultiOutputCapability
import com.prakash.pmusic.domain.model.MultiOutputDevice
import com.prakash.pmusic.domain.model.MultiOutputState
import com.prakash.pmusic.domain.repository.PlaybackController
import com.prakash.pmusic.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel behind the Multi-Output Audio screen.
 *
 * Exposes the connected outputs, the probed capabilities and the live session
 * state from the shared [PlaybackController], plus the persisted toggles
 * through [PreferencesRepository]. Opening the screen triggers one capability
 * probe so the support summary is fresh.
 */
@HiltViewModel
class MultiOutputViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val devices: StateFlow<List<MultiOutputDevice>> = playbackController.multiOutputDevices

    val capabilities: StateFlow<MultiOutputCapability?> =
        playbackController.multiOutputCapabilities

    val multiOutputState: StateFlow<MultiOutputState> = playbackController.multiOutputState

    val preferences: StateFlow<AppPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppPreferences())

    init {
        refreshCapabilities()
    }

    /** Starts routing to the selected outputs (after an honest probe). */
    fun start(deviceIds: Set<String>) = playbackController.startMultiOutput(deviceIds)

    /** Stops multi-output routing. */
    fun stop() = playbackController.stopMultiOutput()

    /** Sets the volume of one active output (0..100). */
    fun setVolume(deviceId: String, volumePercent: Int) =
        playbackController.setMultiOutputVolume(deviceId, volumePercent)

    /** Re-runs the capability probe. */
    fun refreshCapabilities() = playbackController.refreshMultiOutputCapabilities()

    fun setAutoIncludeNewOutputs(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoIncludeNewOutputs(enabled) }
    }

    fun setRememberOutputSelection(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setRememberOutputSelection(enabled) }
    }

    private companion object {
        /** Stop collecting flows shortly after the UI stops observing. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
