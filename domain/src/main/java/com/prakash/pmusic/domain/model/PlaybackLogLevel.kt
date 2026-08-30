package com.prakash.pmusic.domain.model

/**
 * Log severity levels used by the persistent playback logger.
 *
 * Order matters: a logger configured at a given level accepts that level and
 * every level more severe than it. The default is [INFO]; [DEBUG] is only
 * turned on by the user from Settings (extremely verbose position samples).
 */
enum class PlaybackLogLevel(val label: String) {
    DEBUG("DEBUG"),
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR");

    /** True when this level is severe (or more severe) than [threshold]. */
    fun isEnabledFor(threshold: PlaybackLogLevel): Boolean = ordinal >= threshold.ordinal
}
