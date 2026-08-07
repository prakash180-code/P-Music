package com.prakash.pmusic.features.filemanager

import com.prakash.pmusic.domain.model.AudioFileDetails
import com.prakash.pmusic.domain.model.Song
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure formatting helpers for the file-details screen.
 *
 * Every function is deterministic and framework-free so the rendering rules
 * are covered by unit tests. Null returns mean "unknown — hide the row".
 */

/** Details to display, merging indexed values with on-demand extraction. */
data class ResolvedFileDetails(
    val sampleRateHz: Int,
    val channelCount: Int,
    val bitrate: Int,
    val mimeType: String?
)

/**
 * Prefers the freshly extracted container values and falls back to whatever
 * the scanner indexed, so the screen never shows zeros that hide real data.
 */
fun resolveFileDetails(song: Song, extracted: AudioFileDetails?): ResolvedFileDetails =
    ResolvedFileDetails(
        sampleRateHz = extracted?.sampleRateHz?.takeIf { it > 0 } ?: song.sampleRate,
        channelCount = extracted?.channelCount?.takeIf { it > 0 } ?: song.channels,
        bitrate = extracted?.bitrate?.takeIf { it > 0 } ?: song.bitrate,
        mimeType = extracted?.mimeType ?: song.mimeType.ifBlank { null }
    )

/** Human-readable rows for the metadata card (label to value). */
fun detailRows(song: Song, resolved: ResolvedFileDetails): List<Pair<String, String>> =
    buildList {
        add("Format" to (resolved.mimeType ?: "Unknown"))
        add("Size" to formatBytes(song.sizeBytes))
        add("Duration" to formatDuration(song.durationMs))
        formatBitrate(resolved.bitrate)?.let { add("Bitrate" to it) }
        formatSampleRate(resolved.sampleRateHz)?.let { add("Sample rate" to it) }
        formatChannelCount(resolved.channelCount)?.let { add("Channels" to it) }
        song.year.takeIf { it > 0 }?.let { add("Year" to it.toString()) }
        song.genre
            .takeIf { it.isNotBlank() && !it.equals(UNKNOWN, ignoreCase = true) }
            ?.let { add("Genre" to it) }
        song.trackNumber.takeIf { it > 0 }?.let { add("Track" to it.toString()) }
        add("Plays" to song.playCount.toString())
        formatDate(song.dateAdded)?.let { add("Date added" to it) }
        formatDate(song.dateModified)?.let { add("Date modified" to it) }
        song.path.takeIf { it.isNotBlank() }?.let { add("Path" to it) }
    }

/** "3.2 MB", "845 KB", "512 B"; "—" for missing sizes. */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}

/** "3:45" or "1:02:07". */
fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/** "320 kbps"; null when unknown. */
fun formatBitrate(bitrate: Int): String? =
    if (bitrate > 0) "${bitrate / 1000} kbps" else null

/** "44.1 kHz"; null when unknown. */
fun formatSampleRate(hz: Int): String? {
    if (hz <= 0) return null
    return if (hz % 1000 == 0) {
        "${hz / 1000} kHz"
    } else {
        String.format(Locale.US, "%.1f kHz", hz / 1000.0)
    }
}

/** "Mono", "Stereo", "5.1", "3 channels"; null when unknown. */
fun formatChannelCount(channels: Int): String? = when (channels) {
    0 -> null
    1 -> "Mono"
    2 -> "Stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$channels channels"
}

/** "Aug 7, 2026" from epoch seconds; null when missing/invalid. */
fun formatDate(epochSeconds: Long): String? {
    if (epochSeconds <= 0) return null
    return runCatching {
        Instant.ofEpochSecond(epochSeconds)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .format(DATE_FORMATTER)
    }.getOrNull()
}

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

private const val UNKNOWN = "Unknown"
