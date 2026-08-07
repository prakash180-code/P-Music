package com.prakash.pmusic.domain.repository

import com.prakash.pmusic.domain.model.AppPreferences
import com.prakash.pmusic.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Contract for reading and updating user preferences.
 *
 * Exposes the current preferences as a reactive [Flow] and mutation methods
 * that persist through the DataStore layer. Implementations live in `:data`.
 */
interface PreferencesRepository {

    val preferences: Flow<AppPreferences>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setRescanOnLaunch(enabled: Boolean)

    suspend fun setDefaultPlaybackSpeed(speed: Float)

    suspend fun setEqualizerEnabled(enabled: Boolean)

    /** Persists per-band gains in millibels (band count/order is device-specific). */
    suspend fun setEqualizerBandGainsMb(gains: List<Int>)

    /** Persists the selected preset index, or -1 for a custom curve. */
    suspend fun setEqualizerPresetIndex(index: Int)

    /** Marks the first-run folder-exclusion wizard as shown (or skipped). */
    suspend fun setFolderWizardShown(shown: Boolean)
}
