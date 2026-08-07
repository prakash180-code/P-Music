package com.prakash.pmusic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.prakash.pmusic.core.database.entity.SongEntity
import com.prakash.pmusic.core.database.model.AlbumProjection
import com.prakash.pmusic.core.database.model.ArtistProjection
import com.prakash.pmusic.core.database.model.GenreProjection
import com.prakash.pmusic.core.database.model.SongMeta
import com.prakash.pmusic.core.database.model.SongPath
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for the songs table and all derived views.
 *
 * Read paths return [Flow] so the UI stays in sync with scans automatically.
 * Bulk writes are intentionally kept small (chunked) to avoid blowing past
 * SQLite's parameter limit on very large libraries.
 */
@Dao
interface SongDao {

    // --- Reactive reads ---

    @Query("SELECT COUNT(*) FROM songs")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    fun observeAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    fun observeSong(id: Long): Flow<SongEntity?>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE")
    fun observeFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecentlyPlayed(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY playCount DESC LIMIT :limit")
    fun observeMostPlayed(limit: Int): Flow<List<SongEntity>>

    // --- Smart-playlist queries (derived contents, never stored) ---

    @Query("SELECT * FROM songs WHERE playCount = 0 ORDER BY title COLLATE NOCASE")
    fun observeNeverPlayed(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY title COLLATE NOCASE")
    fun observeByGenre(genre: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artistId = :artistId ORDER BY title COLLATE NOCASE")
    fun observeByArtist(artistId: Long): Flow<List<SongEntity>>

    // --- Derived groupings (single source of truth: the songs table) ---

    @Query(
        """
        SELECT albumId AS id, album AS name,
               COALESCE(MAX(albumArtist), MAX(artist)) AS artist,
               MAX(year) AS year, COUNT(*) AS songCount,
               SUM(durationMs) AS durationMs, MAX(artPath) AS artPath
        FROM songs
        GROUP BY albumId
        ORDER BY name COLLATE NOCASE
        """
    )
    fun observeAlbums(): Flow<List<AlbumProjection>>

    @Query(
        """
        SELECT artistId AS id, artist AS name,
               COUNT(DISTINCT albumId) AS albumCount, COUNT(*) AS songCount
        FROM songs
        GROUP BY artistId
        ORDER BY name COLLATE NOCASE
        """
    )
    fun observeArtists(): Flow<List<ArtistProjection>>

    @Query(
        """
        SELECT genre AS name, COUNT(*) AS songCount
        FROM songs
        WHERE genre IS NOT NULL AND genre <> ''
        GROUP BY genre
        ORDER BY name COLLATE NOCASE
        """
    )
    fun observeGenres(): Flow<List<GenreProjection>>

    // --- Writes ---

    /** Bulk upsert; caller chunks the list for large scans. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    // --- User state ---

    @Query("SELECT id, isFavorite, playCount, lastPlayedAt FROM songs")
    suspend fun getSongMeta(): List<SongMeta>

    @Query("SELECT id FROM songs")
    suspend fun getAllIds(): List<Long>

    /** Id + path pairs, used by the folder manager for recursive purges. */
    @Query("SELECT id, path FROM songs")
    suspend fun getAllSongPaths(): List<SongPath>

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun setFavorite(songId: Long, isFavorite: Boolean)

    /** Records a play: bumps playCount and refreshes lastPlayedAt. */
    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :playedAt WHERE id = :songId")
    suspend fun recordPlay(songId: Long, playedAt: Long)

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun count(): Int
}
