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

    /** Stores the MediaStore version seen after a completed library scan. */
    suspend fun setLastMediaStoreVersion(version: String)

    /** Toggles automatic inclusion of newly connected outputs. */
    suspend fun setAutoIncludeNewOutputs(enabled: Boolean)

    /** Toggles whether the last multi-output selection is remembered. */
    suspend fun setRememberOutputSelection(enabled: Boolean)

    /** Persists the device ids of the last started multi-output selection. */
    suspend fun setRememberedOutputIds(ids: Set<String>)

    /** Enables or disables verbose DEBUG playback logging. */
    suspend fun setPlaybackDebugLogging(enabled: Boolean)
}
