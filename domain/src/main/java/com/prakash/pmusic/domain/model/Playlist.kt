package com.prakash.pmusic.domain.model

/**
 * A user-created playlist persisted in Room.
 *
 * The id is assigned by the database on creation. [songCount] is derived from
 * the playlist_songs join table so the list screen never loads full songs.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songCount: Int
)
