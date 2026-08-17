package com.prakash.pmusic.data.repository

import com.prakash.pmusic.core.datastore.UserPreferencesDataStore
import com.prakash.pmusic.domain.model.AppPreferences
import com.prakash.pmusic.domain.model.ThemeMode
import com.prakash.pmusic.domain.repository.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * DataStore-backed implementation of [PreferencesRepository].
 */
@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val dataStore: UserPreferencesDataStore
) : PreferencesRepository {

    override val preferences: Flow<AppPreferences> = dataStore.preferences

    override suspend fun setThemeMode(mode: ThemeMode) = dataStore.setThemeMode(mode)

    override suspend fun setDynamicColor(enabled: Boolean) = dataStore.setDynamicColor(enabled)

    override suspend fun setRescanOnLaunch(enabled: Boolean) = dataStore.setRescanOnLaunch(enabled)

    override suspend fun setDefaultPlaybackSpeed(speed: Float) = dataStore.setDefaultPlaybackSpeed(speed)

    override suspend fun setEqualizerEnabled(enabled: Boolean) = dataStore.setEqualizerEnabled(enabled)

    override suspend fun setEqualizerBandGainsMb(gains: List<Int>) = dataStore.setEqualizerBandGainsMb(gains)

    override suspend fun setEqualizerPresetIndex(index: Int) = dataStore.setEqualizerPresetIndex(index)

    override suspend fun setFolderWizardShown(shown: Boolean) = dataStore.setFolderWizardShown(shown)

    override suspend fun setLastMediaStoreVersion(version: String) =
        dataStore.setLastMediaStoreVersion(version)
}
