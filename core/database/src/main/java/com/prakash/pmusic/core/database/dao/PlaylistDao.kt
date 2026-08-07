package com.prakash.pmusic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.prakash.pmusic.core.database.entity.PlaylistEntity
import com.prakash.pmusic.core.database.entity.PlaylistSongEntity
import com.prakash.pmusic.core.database.entity.SongEntity
import com.prakash.pmusic.core.database.model.PlaylistProjection
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for the playlists and playlist_songs tables.
 *
 * Reads return [Flow] so the UI reactively reflects every mutation. Multi-row
 * writes (add-with-position, removal renumbering, full reorder) run inside
 * [Transaction] methods so they are atomic and the position column never
 * transiently duplicates or gaps inconsistently.
 */
@Dao
interface PlaylistDao {

    // --- Reactive reads ---

    /** All playlists with song counts, most recently updated first. */
    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt,
               p.updatedAt AS updatedAt, COUNT(ps.songId) AS songCount
        FROM playlists p
        LEFT JOIN playlist_songs ps ON ps.playlistId = p.id
        GROUP BY p.id
        ORDER BY p.updatedAt DESC
        """
    )
    fun observePlaylists(): Flow<List<PlaylistProjection>>

    /** A single playlist with its song count, or null once deleted. */
    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt,
               p.updatedAt AS updatedAt, COUNT(ps.songId) AS songCount
        FROM playlists p
        LEFT JOIN playlist_songs ps ON ps.playlistId = p.id
        WHERE p.id = :playlistId
        GROUP BY p.id
        """
    )
    fun observePlaylist(playlistId: Long): Flow<PlaylistProjection?>

    /** Songs of a playlist in stored position order. */
    @Query(
        """
        SELECT s.* FROM playlist_songs ps
        JOIN songs s ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
        """
    )
    fun observePlaylistSongs(playlistId: Long): Flow<List<SongEntity>>

    // --- Playlist writes ---

    /** Inserts a playlist and returns its generated id. */
    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun delete(playlistId: Long)

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, name: String, updatedAt: Long)

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun touch(playlistId: Long, updatedAt: Long)

    // --- Playlist_songs writes ---

    @Insert
    suspend fun insertSong(entry: PlaylistSongEntity)

    @Query("SELECT MAX(position) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int?

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun contains(playlistId: Long, songId: Long): Int

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearSongs(playlistId: Long)

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun songIdsInOrder(playlistId: Long): List<Long>

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun setPosition(playlistId: Long, songId: Long, position: Int)

    /** Appends [songId] at the end unless it is already in the playlist. */
    @Transaction
    suspend fun addSongIfAbsent(playlistId: Long, songId: Long) {
        if (contains(playlistId, songId) == 0) {
            insertSong(PlaylistSongEntity(playlistId, songId, (maxPosition(playlistId) ?: -1) + 1))
        }
    }

    /** Removes [songId] and renumbers the remaining rows to stay dense. */
    @Transaction
    suspend fun removeAndRenumber(playlistId: Long, songId: Long) {
        removeSong(playlistId, songId)
        songIdsInOrder(playlistId).forEachIndexed { index, id ->
            setPosition(playlistId, id, index)
        }
    }

    /** Replaces the whole ordering with [orderedSongIds]. */
    @Transaction
    suspend fun replaceOrder(playlistId: Long, orderedSongIds: List<Long>) {
        clearSongs(playlistId)
        orderedSongIds.forEachIndexed { index, songId ->
            insertSong(PlaylistSongEntity(playlistId, songId, index))
        }
    }
}
