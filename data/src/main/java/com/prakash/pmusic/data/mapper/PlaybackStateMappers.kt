package com.prakash.pmusic.data.mapper

import com.prakash.pmusic.core.database.entity.PlaybackStateEntity
import com.prakash.pmusic.data.repository.PlaybackStateCodec
import com.prakash.pmusic.domain.model.SavedPlaybackState

/**
 * Maps between the persisted [PlaybackStateEntity] and the pure domain model
 * [SavedPlaybackState].
 */
fun PlaybackStateEntity.toDomain(): SavedPlaybackState = SavedPlaybackState(
    queue = PlaybackStateCodec.decodeQueue(queueJson),
    currentQueueIndex = currentQueueIndex,
    mediaId = mediaId,
    mediaUri = mediaUri,
    songTitle = songTitle,
    artist = artist,
    album = album,
    albumArtworkPath = albumArtworkPath,
    positionMs = positionMs,
    durationMs = durationMs,
    playbackSpeed = playbackSpeed,
    repeatMode = PlaybackStateCodec.decodeRepeatMode(repeatMode),
    shuffleEnabled = shuffleEnabled,
    wasPlaying = wasPlaying,
    savedAtNanos = savedAtNanos
)

fun SavedPlaybackState.toEntity(): PlaybackStateEntity = PlaybackStateEntity(
    id = 1,
    queueJson = PlaybackStateCodec.encodeQueue(queue),
    currentQueueIndex = currentQueueIndex,
    mediaId = mediaId,
    mediaUri = mediaUri,
    songTitle = songTitle,
    artist = artist,
    album = album,
    albumArtworkPath = albumArtworkPath,
    positionMs = positionMs,
    durationMs = durationMs,
    playbackSpeed = playbackSpeed,
    repeatMode = PlaybackStateCodec.encodeRepeatMode(repeatMode),
    shuffleEnabled = shuffleEnabled,
    wasPlaying = wasPlaying,
    savedAtNanos = savedAtNanos
)
