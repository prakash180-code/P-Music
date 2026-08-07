package com.prakash.pmusic.core.database.model

/**
 * Projection of the album aggregation query (GROUP BY albumId).
 *
 * Albums are derived on the fly from the song table; nothing is duplicated.
 */
data class AlbumProjection(
    val id: Long,
    val name: String,
    val artist: String,
    val year: Int,
    val songCount: Int,
    val durationMs: Long,
    val artPath: String?
)

/**
 * Projection of the artist aggregation query (GROUP BY artistId).
 */
data class ArtistProjection(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val songCount: Int
)

/**
 * Projection of the genre aggregation query (GROUP BY genre).
 */
data class GenreProjection(
    val name: String,
    val songCount: Int
)

/**
 * Playlist row joined with its song count for the playlists list screen.
 *
 * [songCount] comes from the playlist_songs join table and is only meaningful
 * for manual playlists (a smart playlist's live count is derived from its
 * rule by the repository).
 */
data class PlaylistProjection(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songCount: Int,
    val rule: String? = null
)

/**
 * Lightweight per-song state read during a scan so favorites and play stats
 * survive the REPLACE-based re-index.
 */
data class SongMeta(
    val id: Long,
    val isFavorite: Boolean,
    val playCount: Int,
    val lastPlayedAt: Long?
)
