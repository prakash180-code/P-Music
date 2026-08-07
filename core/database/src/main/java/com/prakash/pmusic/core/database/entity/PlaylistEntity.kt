package com.prakash.pmusic.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a user-created playlist.
 *
 * [updatedAt] drives list ordering (most recently touched first) so new
 * playlists and edits surface at the top without extra bookkeeping.
 *
 * A null [rule] marks a manual playlist whose contents live in the
 * playlist_songs join table; a non-null value marks a smart playlist whose
 * contents are derived from the encoded [SmartPlaylistRule].
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val rule: String? = null
)
