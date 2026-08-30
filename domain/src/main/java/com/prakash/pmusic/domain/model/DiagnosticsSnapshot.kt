package com.prakash.pmusic.domain.model

/**
 * Live snapshot of the playback stack for the Diagnostics screen.
 *
 * This is a pure-framework model the UI can render directly; the controller
 * fills it from the real ExoPlayer / MediaSession / service each time it
 * changes. It exists so diagnostics do not depend on Media3 types.
 */
data class DiagnosticsSnapshot(
    val serviceAlive: Boolean = false,
    val mediaSessionAlive: Boolean = false,
    val playerAlive: Boolean = false,
    /** Raw ExoPlayer object identity (System.identityHashCode), or null. */
    val playerInstance: Int? = null,
    /** Raw MediaController identity seen by the UI, or null. */
    val controllerInstance: Int? = null,
    /** PLAYER.playbackState: IDLE/BUFFERING/READY/ENDED/UNKNOWN. */
    val playerState: String = "UNKNOWN",
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val suppressionReason: Int = 0,
    val currentMediaItemIndex: Int = -1,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val audioSessionId: Int = -1,
    /** Last AUDIO_FOCUS_* token observed, or null. */
    val audioFocus: String? = null,
    /** Last player error message, or null when none. */
    val lastError: String? = null,
    /** Last recorded playback event (short), or null. */
    val lastPlaybackEvent: String? = null,
    /** Last PLAYBACK_HEALTH entry (short), or null. */
    val lastHealthCheck: String? = null,
    /** Last detected stall (short), or null. */
    val lastStall: String? = null,
    /** Current player identity of the service-side ExoPlayer, or null. */
    val servicePlayerInstance: Int? = null
)
