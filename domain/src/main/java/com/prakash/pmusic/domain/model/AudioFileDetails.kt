package com.prakash.pmusic.domain.model

/**
 * On-demand audio file details that MediaStore does not index.
 *
 * Sample rate and channel count are not MediaStore columns (the scanner stores
 * them as 0), so they are enriched lazily by reading the track's container
 * headers (see `:data`). Bitrate is included too, since the MediaStore value
 * is often absent. A zero field means the value could not be determined.
 */
data class AudioFileDetails(
    val sampleRateHz: Int,
    val channelCount: Int,
    val bitrate: Int,
    val mimeType: String?
)
