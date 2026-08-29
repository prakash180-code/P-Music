package com.prakash.pmusic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.prakash.pmusic.core.database.entity.PlaybackStateEntity

/**
 * Data access object for the persisted last-playback session.
 *
 * The table holds at most one row (id 1), written in full on every save and
 * read back for restoration or cleared when invalid.
 */
@Dao
interface PlaybackStateDao {

    @Query("SELECT * FROM playback_state WHERE id = 1")
    suspend fun get(): PlaybackStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackStateEntity)

    @Query("DELETE FROM playback_state WHERE id = 1")
    suspend fun clear()
}
