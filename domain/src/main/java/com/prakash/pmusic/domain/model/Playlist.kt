package com.prakash.pmusic.domain.model

/**
 * A user-created playlist persisted in Room.
 *
 * The id is assigned by the database on creation. [songCount] is derived from
 * the playlist_songs join table (manual playlists) or from the live result of
 * [rule] (smart playlists) so the list screen never loads full songs.
 *
 * A null [rule] means a manual playlist whose contents are stored in
 * playlist_songs; a non-null value is a smart playlist whose contents are
 * derived from [SmartPlaylistRule] and update automatically with the library.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songCount: Int,
    val rule: String? = null
) {
    /** True when the contents are derived from a rule rather than stored. */
    val isSmart: Boolean get() = rule != null
}
