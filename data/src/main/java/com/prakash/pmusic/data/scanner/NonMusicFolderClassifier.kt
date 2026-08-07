package com.prakash.pmusic.data.scanner

/**
 * Pure classification used by the non-music folder detection.
 *
 * A folder is a "suggestion" when its name matches well-known non-music audio
 * locations (call/voice recordings, messaging apps, system sounds) or when it
 * holds mostly very short clips (a strong signal of recordings/notifications
 * rather than songs). Kept framework-free so it can be unit-tested.
 */
object NonMusicFolderClassifier {

    private val KNOWN_NAMES = listOf(
        "callrecording",
        "callrecordings",
        "call recorder",
        "call recordings",
        "recorder",
        "records",
        "recordings",
        "voice recorder",
        "voice recorders",
        "voicerecorder",
        "voice notes",
        "voicenotes",
        "voice memos",
        "whatsapp",
        "whatsapp audio",
        "telegram",
        "telegram audio",
        "signal audio",
        "messenger audio",
        "instagram audio",
        "podcast",
        "podcasts",
        "notifications",
        "notification",
        "ringtones",
        "ringtone",
        "alarms",
        "alarm",
        "audio notes",
        "audiomemos",
        "sound recorder"
    )

    /**
     * Folders with fewer than [MIN_SONG_COUNT_FOR_SHORT_CLIPS] files are never
     * flagged by the duration heuristic (a lone short file is likely a real
     * song clip or a stray ringtone, not a recording folder).
     */
    const val MIN_SONG_COUNT_FOR_SHORT_CLIPS = 3

    /** Average duration below which a folder is considered short-clip audio. */
    const val SHORT_CLIP_THRESHOLD_MS = 15_000L

    /** Durations in this band count as "song-shaped" (1–10 min): real music, not clips or meetings. */
    const val SONG_LIKE_MIN_DURATION_MS = 60_000L
    const val SONG_LIKE_MAX_DURATION_MS = 600_000L

    /**
     * A name-matched "recording" folder that recursively holds this many
     * song-shaped files is a music container, not a stray recordings folder.
     * Suggesting it for exclusion would silently remove a real music
     * collection, so these are never suggested.
     */
    const val MAX_SONG_LIKE_FILES_FOR_SUGGESTION = 20

    /** True when [durationMs] falls in the song-shaped (1–10 min) band. */
    fun isSongLike(durationMs: Long): Boolean =
        durationMs >= SONG_LIKE_MIN_DURATION_MS && durationMs < SONG_LIKE_MAX_DURATION_MS

    /**
     * True when a folder recursively holds enough song-shaped files that it is
     * clearly a music collection and must not be suggested for exclusion.
     */
    fun isMusicContainer(recursiveSongLikeCount: Int): Boolean =
        recursiveSongLikeCount >= MAX_SONG_LIKE_FILES_FOR_SUGGESTION

    /** True when [folderName]/[averageDurationMs] look like non-music audio. */
    fun isNonMusic(folderName: String, songCount: Int, averageDurationMs: Long): Boolean {
        val normalized = folderName.trim().lowercase().replace('_', ' ')
        if (KNOWN_NAMES.any { normalized.contains(it) }) return true
        return songCount >= MIN_SONG_COUNT_FOR_SHORT_CLIPS &&
            averageDurationMs < SHORT_CLIP_THRESHOLD_MS
    }
}
