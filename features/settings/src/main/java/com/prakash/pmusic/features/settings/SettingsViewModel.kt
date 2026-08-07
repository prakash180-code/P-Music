package com.prakash.pmusic.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prakash.pmusic.domain.model.AppPreferences
import com.prakash.pmusic.domain.model.ThemeMode
import com.prakash.pmusic.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings destination.
 *
 * Exposes the persisted [AppPreferences] reactively and forwards each setting
 * change to the [PreferencesRepository], which writes through DataStore. The
 * theme reacts immediately because MainActivity also observes this flow.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<AppPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppPreferences())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColor(enabled) }
    }

    fun setRescanOnLaunch(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setRescanOnLaunch(enabled) }
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        viewModelScope.launch { preferencesRepository.setDefaultPlaybackSpeed(speed) }
    }

    private companion object {
        /** Stop collecting flows shortly after the UI stops observing. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Playback speed presets offered in Settings, matching the Now Playing cycle. */
val PLAYBACK_SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
