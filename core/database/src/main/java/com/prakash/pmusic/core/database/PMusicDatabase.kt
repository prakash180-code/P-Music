package com.prakash.pmusic.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.prakash.pmusic.core.database.dao.PlaylistDao
import com.prakash.pmusic.core.database.dao.SongDao
import com.prakash.pmusic.core.database.entity.PlaylistEntity
import com.prakash.pmusic.core.database.entity.PlaylistSongEntity
import com.prakash.pmusic.core.database.entity.SongEntity

/**
 * The P-Music Room database.
 *
 * Version 1 ships the songs table. Version 2 adds user playlists. Every
 * version bump ships a [Migrations] object rather than falling back to
 * destructive recreation, so user data is never lost.
 */
@Database(
    entities = [SongEntity::class, PlaylistEntity::class, PlaylistSongEntity::class],
    version = 2,
    exportSchema = true
)
abstract class PMusicDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    abstract fun playlistDao(): PlaylistDao
}
