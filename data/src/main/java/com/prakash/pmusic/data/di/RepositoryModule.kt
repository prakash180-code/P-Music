package com.prakash.pmusic.data.di

import com.prakash.pmusic.data.repository.LibraryRepositoryImpl
import com.prakash.pmusic.data.repository.PlaylistRepositoryImpl
import com.prakash.pmusic.data.repository.PreferencesRepositoryImpl
import com.prakash.pmusic.domain.repository.LibraryRepository
import com.prakash.pmusic.domain.repository.PlaylistRepository
import com.prakash.pmusic.domain.repository.PreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the `:data` repository implementations to their domain contracts so
 * features only ever see the interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository
}
