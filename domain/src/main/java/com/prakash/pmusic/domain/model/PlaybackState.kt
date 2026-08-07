package com.prakash.pmusic.domain.model

/**
 * Immutable snapshot of the playback engine, published to the UI via a
 * [kotlinx.coroutines.flow.StateFlow]. Everything here is framework-free so
 * the UI and ViewModels never depend on Media3 types.
 */
data class PlaybackState(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    /** Index of the current song within [queue]. -1 when nothing is loaded. */
    val queueIndex: Int = -1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1f,
    /** True once the app has connected to the media session. */
    val isConnected: Boolean = false
)
