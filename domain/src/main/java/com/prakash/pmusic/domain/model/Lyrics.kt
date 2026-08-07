package com.prakash.pmusic.domain.model

/** One line of lyrics, optionally timestamped for synced display. */
data class LyricLine(
    /** Start time in milliseconds, or null for unsynced text. */
    val timestampMs: Long?,
    val text: String
)

/**
 * Lyrics for a single track, loaded from an LRC sidecar file or embedded ID3
 * USLT/SYLT tags. Lines with a [LyricLine.timestampMs] are synced to playback
 * and can be highlighted as the song advances; lines without one are plain
 * text (an entire unsynced lyric body is a single line).
 */
data class Lyrics(
    val lines: List<LyricLine>
)
