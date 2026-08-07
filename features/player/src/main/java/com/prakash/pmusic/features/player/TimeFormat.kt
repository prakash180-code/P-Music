package com.prakash.pmusic.features.player

/**
 * Formats a millisecond duration for the transport UI (m:ss or h:mm:ss).
 *
 * Kept framework-free so it can be unit-tested and reused by any screen that
 * renders playback time.
 */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
