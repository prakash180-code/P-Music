package com.prakash.pmusic.domain.model

/**
 * A persistence-friendly snapshot of the last playback session.
 *
 * This is the single persisted source of truth for the player's last state so
 * that, when the app is reopened, the previous song, queue, position and mode
 * flags can be restored. It deliberately mirrors [PlaybackState] but with the
 * [Song]s serialized by their stable identifiers (MediaStore id + content Uri),
 * never by title alone, because multiple files can share a title.
 *
 * `nanoTime` is used for the updated-at stamp so it is cheap to write on every
 * periodic save without depending on wall-clock time.
 */
data class SavedPlaybackState(
    /** The current queue, in order (queue positions are preserved). */
    val queue: List<Song> = emptyList(),
    /** Index of the current song within [queue]. -1 when nothing is loaded. */
    val currentQueueIndex: Int = -1,
    /** Primary identifier: MediaStore audio id of the current song. */
    val mediaId: Long? = null,
    /** Content/display Uri of the current song. */
    val mediaUri: String? = null,
    val songTitle: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtworkPath: String? = null,
    /** Playback position in ms to resume from (validated at restore time). */
    val positionMs: Long = 0L,
    /** Track duration in ms, when known. */
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    /** When false the player is restored paused; playback never auto-starts. */
    val wasPlaying: Boolean = false,
    /** Monotonic timestamp of the last save. */
    val savedAtNanos: Long = 0L
) {
    val isEmpty: Boolean get() = queue.isEmpty() || mediaId == null
}
