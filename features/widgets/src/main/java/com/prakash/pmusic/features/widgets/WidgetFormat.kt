package com.prakash.pmusic.features.widgets

/**
 * Pure formatting helpers for the playback widget.
 *
 * Kept free of Android dependencies (other than the [R] constant used by
 * [playPauseIcon]) so the time/progress math is unit-testable on the JVM.
 */
object WidgetFormat {

    /**
     * Formats a millisecond duration as `m:ss` or `h:mm:ss`.
     * Negative values are treated as 0:00.
     */
    fun Long.asPlaybackTime(): String {
        if (this < 0) return "0:00"
        val totalSeconds = this / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    /**
     * `position / duration`, e.g. `1:23 / 3:45`. When the duration is unknown
     * (streaming or unset) only the position is shown.
     */
    fun formatPosition(positionMs: Long, durationMs: Long): String {
        val position = positionMs.asPlaybackTime()
        return if (durationMs > 0) {
            "$position / ${durationMs.asPlaybackTime()}"
        } else {
            position
        }
    }

    /**
     * Progress as a [0..1000] bucket for a horizontal progress bar. Unknown or
     * empty durations yield 0.
     */
    fun progressFraction(positionMs: Long, durationMs: Long): Int {
        if (durationMs <= 0) return 0
        val fraction = positionMs.toDouble() / durationMs.toDouble()
        return (fraction * 1000).toInt().coerceIn(0, 1000)
    }

    /** Play/pause button icon for the widget. */
    fun playPauseIcon(isPlaying: Boolean): Int =
        if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
}
