package com.prakash.pmusic.data.lyrics

import com.prakash.pmusic.domain.model.LyricLine
import com.prakash.pmusic.domain.model.Lyrics

/**
 * Parses the standard LRC format used by `.lrc` lyric files: timestamped
 * lines like `[mm:ss.xx] line text`, multiple timestamps per line, the
 * `[offset:+/-ms]` metadata tag, and plain (unsynced) lines without brackets.
 */
object LrcParser {

    private val TIMESTAMP = Regex("""^(\d+):(\d{1,2})(?:\.(\d{1,3}))?$""")

    fun parse(text: String): Lyrics {
        val lines = mutableListOf<LyricLine>()
        var offsetMs = 0L

        text.lineSequence().forEach { raw ->
            val line = raw.trimEnd('\r')
            var rest = line
            val timestamps = mutableListOf<Long>()
            while (rest.startsWith('[')) {
                val close = rest.indexOf(']')
                if (close < 0) break
                val tag = rest.substring(1, close)
                val timestamp = parseTimestamp(tag)
                if (timestamp != null) {
                    timestamps += timestamp
                } else if (tag.startsWith("offset:", ignoreCase = true)) {
                    offsetMs = tag.substringAfter(':').trim().toLongOrNull() ?: offsetMs
                }
                rest = rest.substring(close + 1)
            }
            val content = rest.trim()
            if (content.isEmpty()) return@forEach
            if (timestamps.isNotEmpty()) {
                timestamps.forEach { lines += LyricLine(timestampMs = it, text = content) }
            } else {
                lines += LyricLine(timestampMs = null, text = content)
            }
        }

        if (offsetMs != 0L && lines.isNotEmpty()) {
            return Lyrics(
                lines.map { line ->
                    line.copy(timestampMs = line.timestampMs?.plus(offsetMs)?.coerceAtLeast(0L))
                }
            )
        }
        return Lyrics(lines)
    }

    private fun parseTimestamp(tag: String): Long? {
        val match = TIMESTAMP.matchEntire(tag.trim()) ?: return null
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val fraction = match.groupValues[3].padEnd(3, '0').toLong()
        return minutes * 60_000L + seconds * 1_000L + fraction
    }
}
