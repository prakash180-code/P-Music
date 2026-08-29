package com.prakash.pmusic.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the persisted last-playback session.
 *
 * A single logical row (id always 1) stores the serialized queue plus the
 * current-song and mode flags needed to restore the player after a restart.
 * The queue is stored as JSON (see PlaybackStateCodec in `:data`) so no extra
 * join table is required, and the whole row is rewritten atomically on each
 * save.
 */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = 1,
    val queueJson: String,
    val currentQueueIndex: Int,
    val mediaId: Long?,
    val mediaUri: String?,
    val songTitle: String?,
    val artist: String?,
    val album: String?,
    val albumArtworkPath: String?,
    val positionMs: Long,
    val durationMs: Long,
    val playbackSpeed: Float,
    val repeatMode: String,
    val shuffleEnabled: Boolean,
    val wasPlaying: Boolean,
    val savedAtNanos: Long
)
