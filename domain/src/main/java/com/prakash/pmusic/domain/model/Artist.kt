package com.prakash.pmusic.domain.model

/**
 * A grouping of songs by artist. Like [Album], artists are derived from the
 * song table via aggregation queries.
 */
data class Artist(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val songCount: Int
)
