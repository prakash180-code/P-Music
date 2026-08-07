package com.prakash.pmusic.core.datastore

/**
 * Encodes per-band equalizer gains for storage as a single DataStore string.
 *
 * Values are millibels (1/100 dB), joined by commas, e.g. `0,300,-150`.
 * The band count is device-specific, so decode tolerates any length and drops
 * malformed entries rather than failing.
 */
object EqualizerGainsCodec {

    fun encode(gains: List<Int>): String = gains.joinToString(separator = ",")

    fun decode(raw: String): List<Int> =
        raw.split(',').mapNotNull { entry -> entry.trim().toIntOrNull() }
}
