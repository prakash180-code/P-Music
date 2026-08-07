package com.prakash.pmusic.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.prakash.pmusic.domain.model.AppPreferences
import com.prakash.pmusic.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// Top-level delegate so the DataStore instance is created once per process.
private val Context.pmusicDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pmusic_preferences"
)

/**
 * Typed wrapper around DataStore<Preferences>.
 *
 * Holds every persisted preference key and exposes a single [Flow] snapshot
 * of [AppPreferences]. Corruption is tolerated by falling back to empty
 * preferences so the app always starts.
 */
@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val RESCAN_ON_LAUNCH = booleanPreferencesKey("rescan_on_launch")
        val DEFAULT_PLAYBACK_SPEED = floatPreferencesKey("default_playback_speed")
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val EQUALIZER_BAND_GAINS = stringPreferencesKey("equalizer_band_gains")
        val EQUALIZER_PRESET_INDEX = intPreferencesKey("equalizer_preset_index")
    }

    /** Reactive snapshot of the current preferences. */
    val preferences: Flow<AppPreferences> = context.pmusicDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { stored -> stored.toAppPreferences() }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.pmusicDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.pmusicDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setRescanOnLaunch(enabled: Boolean) {
        context.pmusicDataStore.edit { it[Keys.RESCAN_ON_LAUNCH] = enabled }
    }

    suspend fun setDefaultPlaybackSpeed(speed: Float) {
        context.pmusicDataStore.edit { it[Keys.DEFAULT_PLAYBACK_SPEED] = speed }
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        context.pmusicDataStore.edit { it[Keys.EQUALIZER_ENABLED] = enabled }
    }

    suspend fun setEqualizerBandGainsMb(gains: List<Int>) {
        context.pmusicDataStore.edit { it[Keys.EQUALIZER_BAND_GAINS] = EqualizerGainsCodec.encode(gains) }
    }

    suspend fun setEqualizerPresetIndex(index: Int) {
        context.pmusicDataStore.edit { it[Keys.EQUALIZER_PRESET_INDEX] = index }
    }

    private fun Preferences.toAppPreferences(): AppPreferences {
        val themeName = this[Keys.THEME_MODE] ?: return AppPreferences()
        return AppPreferences(
            themeMode = runCatching { ThemeMode.valueOf(themeName) }
                .getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = this[Keys.DYNAMIC_COLOR] ?: true,
            rescanOnLaunch = this[Keys.RESCAN_ON_LAUNCH] ?: false,
            defaultPlaybackSpeed = this[Keys.DEFAULT_PLAYBACK_SPEED] ?: 1f,
            equalizerEnabled = this[Keys.EQUALIZER_ENABLED] ?: false,
            equalizerBandGainsMb = this[Keys.EQUALIZER_BAND_GAINS]
                ?.let(EqualizerGainsCodec::decode) ?: emptyList(),
            equalizerPresetIndex = this[Keys.EQUALIZER_PRESET_INDEX] ?: -1
        )
    }
}
