package com.prakash.pmusic.domain.repository

import com.prakash.pmusic.domain.model.Playlist
import com.prakash.pmusic.domain.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Contract for user-created playlists.
 *
 * Reads are cold [Flow]s backed by Room so the UI stays in sync with writes
 * made anywhere (including future multi-device sync). Mutations are simple
 * suspend calls; implementations live in the `:data` module.
 */
interface PlaylistRepository {

    /** All playlists, most recently updated first. */
    fun observePlaylists(): Flow<List<Playlist>>

    /** A single playlist by id, or null once it has been deleted. */
    fun observePlaylist(playlistId: Long): Flow<Playlist?>

    /**
     * Songs in [playlistId]: the stored (user-ordered) sequence for manual
     * playlists, or the live derived result of the playlist's smart rule.
     */
    fun observePlaylistSongs(playlistId: Long): Flow<List<Song>>

    /**
     * Creates a playlist with the given (trimmed) name and returns its id.
     *
     * When [rule] is null the playlist is manual (empty until songs are added);
     * otherwise it is a smart playlist whose contents are derived from the
     * rule and update automatically.
     */
    suspend fun createPlaylist(name: String, rule: String? = null): Long

    suspend fun renamePlaylist(playlistId: Long, name: String)

    suspend fun deletePlaylist(playlistId: Long)

    /** Appends [songId] to the end of the playlist unless already present. */
    suspend fun addSong(playlistId: Long, songId: Long)

    suspend fun removeSong(playlistId: Long, songId: Long)

    /**
     * Reorders the playlist so its songs follow exactly [orderedSongIds].
     * Any song currently in the playlist but missing from the list is removed.
     */
    suspend fun reorderSongs(playlistId: Long, orderedSongIds: List<Long>)
}
