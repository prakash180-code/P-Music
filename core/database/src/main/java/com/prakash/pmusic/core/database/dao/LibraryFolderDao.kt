package com.prakash.pmusic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.prakash.pmusic.core.database.entity.LibraryFolderEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for the library folder rules.
 *
 * The scanner reads rules through [getAll] (single snapshot per scan) while
 * the folder-manager UI observes [observeAll] reactively.
 */
@Dao
interface LibraryFolderDao {

    @Query("SELECT * FROM library_folders ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<LibraryFolderEntity>>

    @Query("SELECT * FROM library_folders WHERE folderPath = :folderPath")
    suspend fun getByPath(folderPath: String): LibraryFolderEntity?

    @Query("SELECT * FROM library_folders")
    suspend fun getAll(): List<LibraryFolderEntity>

    @Query("SELECT * FROM library_folders WHERE id = :id")
    suspend fun getById(id: Long): LibraryFolderEntity?

    @Query("SELECT COUNT(*) FROM library_folders")
    suspend fun count(): Int

    /** @return the auto-generated row id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: LibraryFolderEntity): Long

    @Query("UPDATE library_folders SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE library_folders SET type = :type WHERE id = :id")
    suspend fun setType(id: Long, type: String)

    @Query("UPDATE library_folders SET songCount = :songCount, lastScanned = :lastScanned WHERE id = :id")
    suspend fun updateStats(id: Long, songCount: Int, lastScanned: Long)

    @Query("DELETE FROM library_folders WHERE id = :id")
    suspend fun deleteById(id: Long)
}
