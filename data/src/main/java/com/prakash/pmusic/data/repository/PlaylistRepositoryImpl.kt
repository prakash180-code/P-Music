package com.prakash.pmusic.data.repository

import com.prakash.pmusic.core.database.dao.PlaylistDao
import com.prakash.pmusic.core.database.dao.SongDao
import com.prakash.pmusic.core.database.entity.PlaylistEntity
import com.prakash.pmusic.core.database.entity.SongEntity
import com.prakash.pmusic.core.database.model.PlaylistProjection
import com.prakash.pmusic.data.mapper.toDomain
import com.prakash.pmusic.domain.model.Playlist
import com.prakash.pmusic.domain.model.SmartPlaylistRule
import com.prakash.pmusic.domain.model.Song
import com.prakash.pmusic.domain.repository.PlaylistRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation of [PlaylistRepository].
 *
 * Reads are direct Room [Flow]s mapped to domain models; writes delegate to
 * the DAO's transactional helpers so position bookkeeping stays consistent.
 *
 * Smart playlists (rows with a non-null [PlaylistEntity.rule]) never touch
 * the playlist_songs join table: their songs and counts are derived live from
 * the songs table through [SongDao] whenever the library changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<Playlist>> =
        playlistDao.observePlaylists().flatMapLatest { projections ->
            val smart = projections.filter { it.rule != null }
            if (smart.isEmpty()) {
                flowOf(projections.map { it.toDomain() })
            } else {
                val countFlows = smart.fold<PlaylistProjection, Flow<Map<Long, Int>>>(flowOf(emptyMap())) { acc, projection ->
                    combine(acc, smartSongs(projection.rule!!)) { counts, songs ->
                        counts + (projection.id to songs.size)
                    }
                }
                countFlows.map { smartCounts ->
                    projections.map { projection -> projection.toDomain(smartCount = smartCounts[projection.id]) }
                }
            }
        }

    override fun observePlaylist(playlistId: Long): Flow<Playlist?> =
        playlistDao.observeRule(playlistId).flatMapLatest { rule ->
            val projection = playlistDao.observePlaylist(playlistId)
            if (rule == null) {
                projection.map { it?.toDomain() }
            } else {
                combine(projection, smartSongs(rule)) { row, songs ->
                    row?.let { it.toDomain(smartCount = songs.size) }
                }
            }
        }

    override fun observePlaylistSongs(playlistId: Long): Flow<List<Song>> =
        playlistDao.observeRule(playlistId).flatMapLatest { rule ->
            if (rule == null) {
                playlistDao.observePlaylistSongs(playlistId)
            } else {
                smartSongs(rule)
            }
        }.map { list -> list.map { it.toDomain() } }

    override suspend fun createPlaylist(name: String, rule: String?): Long {
        val now = System.currentTimeMillis()
        return playlistDao.insert(PlaylistEntity(name = name, createdAt = now, updatedAt = now, rule = rule))
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

    /** The live derived song list for a smart-playlist rule, empty when malformed. */
    private fun smartSongs(rule: String): Flow<List<SongEntity>> = when (val parsed = SmartPlaylistRule.parse(rule)) {
        is SmartPlaylistRule.Favorites -> songDao.observeFavoriteSongs()
        is SmartPlaylistRule.MostPlayed -> songDao.observeMostPlayed(SMART_PLAYLIST_LIMIT)
        is SmartPlaylistRule.RecentlyAdded -> songDao.observeRecentlyAdded(SMART_PLAYLIST_LIMIT)
        is SmartPlaylistRule.RecentlyPlayed -> songDao.observeRecentlyPlayed(SMART_PLAYLIST_LIMIT)
        is SmartPlaylistRule.NeverPlayed -> songDao.observeNeverPlayed()
        is SmartPlaylistRule.AllSongs -> songDao.observeAllSongs()
        is SmartPlaylistRule.Genre -> songDao.observeByGenre(parsed.name)
        is SmartPlaylistRule.Artist -> songDao.observeByArtist(parsed.artistId)
        null -> flowOf(emptyList())
    }

    private companion object {
        /** Cap for unbounded play-count / date-based smart lists. */
        const val SMART_PLAYLIST_LIMIT = 100
    }
}
