package com.prakash.pmusic.core.database.di

import android.content.Context
import androidx.room.Room
import com.prakash.pmusic.core.database.Migrations
import com.prakash.pmusic.core.database.PMusicDatabase
import com.prakash.pmusic.core.database.dao.PlaylistDao
import com.prakash.pmusic.core.database.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides the Room database and its DAOs as singletons.
 *
 * The instance lives in [SingletonComponent], so every feature shares one
 * connection pool and database file (`pmusic.db`). Version bumps are applied
 * through explicit migrations so installed libraries and playlists survive.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PMusicDatabase =
        Room.databaseBuilder(
            context,
            PMusicDatabase::class.java,
            "pmusic.db"
        ).addMigrations(Migrations.MIGRATION_1_2).build()

    @Provides
    fun provideSongDao(database: PMusicDatabase): SongDao = database.songDao()

    @Provides
    fun providePlaylistDao(database: PMusicDatabase): PlaylistDao = database.playlistDao()
}
