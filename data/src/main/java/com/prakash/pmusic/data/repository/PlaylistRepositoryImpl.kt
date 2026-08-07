package com.prakash.pmusic.data.repository

import com.prakash.pmusic.core.database.dao.PlaylistDao
import com.prakash.pmusic.core.database.entity.PlaylistEntity
import com.prakash.pmusic.data.mapper.toDomain
import com.prakash.pmusic.domain.model.Playlist
import com.prakash.pmusic.domain.model.Song
import com.prakash.pmusic.domain.repository.PlaylistRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation of [PlaylistRepository].
 *
 * Reads are direct Room [Flow]s mapped to domain models; writes delegate to
 * the DAO's transactional helpers so position bookkeeping stays consistent.
 */
@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<Playlist>> =
        playlistDao.observePlaylists().map { list -> list.map { it.toDomain() } }

    override fun observePlaylist(playlistId: Long): Flow<Playlist?> =
        playlistDao.observePlaylist(playlistId).map { it?.toDomain() }

    override fun observePlaylistSongs(playlistId: Long): Flow<List<Song>> =
        playlistDao.observePlaylistSongs(playlistId).map { list -> list.map { it.toDomain() } }

    override suspend fun createPlaylist(name: String): Long {
        val now = System.currentTimeMillis()
        return playlistDao.insert(PlaylistEntity(name = name, createdAt = now, updatedAt = now))
    }

    override suspend fun renamePlaylist(playlistId: Long, name: String) {
        playlistDao.rename(playlistId, name, System.currentTimeMillis())
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.delete(playlistId)
    }

    override suspend fun addSong(playlistId: Long, songId: Long) {
        playlistDao.addSongIfAbsent(playlistId, songId)
        playlistDao.touch(playlistId, System.currentTimeMillis())
    }

    override suspend fun removeSong(playlistId: Long, songId: Long) {
        playlistDao.removeAndRenumber(playlistId, songId)
        playlistDao.touch(playlistId, System.currentTimeMillis())
    }

    override suspend fun reorderSongs(playlistId: Long, orderedSongIds: List<Long>) {
        playlistDao.replaceOrder(playlistId, orderedSongIds)
        playlistDao.touch(playlistId, System.currentTimeMillis())
    }
}
