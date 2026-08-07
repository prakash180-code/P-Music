package com.prakash.pmusic.domain.model

/**
 * Repeat behaviour of the playback queue. Maps 1:1 to the Media3 repeat modes.
 */
enum class RepeatMode {
    /** Play the queue once, then stop. */
    OFF,

    /** Repeat the current song. */
    ONE,

    /** Repeat the whole queue. */
    ALL
}
