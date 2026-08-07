package com.prakash.pmusic.domain.model

/**
 * A grouping of songs by genre. The genre "id" is derived from the genre
 * name (stable hash) because MediaStore genre ids are device-specific; the
 * name is the actual grouping key.
 */
data class Genre(
    val id: Long,
    val name: String,
    val songCount: Int
)
