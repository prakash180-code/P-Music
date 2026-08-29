package com.prakash.pmusic.domain.repository

import com.prakash.pmusic.domain.model.SavedPlaybackState

/**
 * Contract for persisting and loading the last playback session.
 *
 * The playback service is the source of truth: it writes state periodically and
 * on key events, and reads it back when the app reconnects so the previous
 * song, queue, position and mode flags can be restored. Implementing modules
 * use durable storage (Room) so the state survives process death and device
 * restarts.
 */
interface PlaybackStateRepository {

    /**
     * Atomically persists [state] as the app's last playback session.
     *
     * Safe to call frequently; implementations must avoid excessive disk
     * churn for the write sizes involved.
     */
    suspend fun save(state: SavedPlaybackState)

    /**
     * Loads the last persisted playback session, or null if none has been
     * saved yet.
     */
    suspend fun load(): SavedPlaybackState?

    /** Removes any persisted playback session (e.g. when the file is gone). */
    suspend fun clear()
}
