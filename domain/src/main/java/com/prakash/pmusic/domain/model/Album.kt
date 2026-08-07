package com.prakash.pmusic.domain.model

/**
 * A grouping of songs by album. Albums are derived from song data (see the
 * DAO projections) rather than stored as separate rows, which keeps the
 * database in a single source of truth and avoids sync drift.
 */
data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val year: Int,
    val songCount: Int,
    val durationMs: Long,
    /** Path to the album artwork, if any (embedded art is resolved later). */
    val artPath: String?
)
