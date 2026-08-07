package com.prakash.pmusic.domain.model

/**
 * A single audio track in the user's library.
 *
 * The primary key matches the MediaStore audio id so the scanner can upsert
 * rows idempotently. Fields mirror the MediaStore projection columns that the
 * library scanner reads, plus user-modifiable state (favorite, play stats).
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val albumArtist: String?,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val genre: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    /** Absolute file path (MediaStore DATA column). Used for file operations. */
    val path: String,
    val dateAdded: Long,
    val dateModified: Long,
    val composer: String?,
    val playCount: Int,
    val skipCount: Int,
    val lastPlayedAt: Long?,
    val isFavorite: Boolean,
    val bitrate: Int,
    val sampleRate: Int,
    val channels: Int,
    /** Absolute path of the album artwork (used for notification/player art). */
    val artPath: String?
)
