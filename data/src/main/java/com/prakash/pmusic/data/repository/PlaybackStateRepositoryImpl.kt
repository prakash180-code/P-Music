package com.prakash.pmusic.data.repository

import com.prakash.pmusic.core.database.dao.PlaybackStateDao
import com.prakash.pmusic.data.mapper.toDomain
import com.prakash.pmusic.data.mapper.toEntity
import com.prakash.pmusic.domain.model.SavedPlaybackState
import com.prakash.pmusic.domain.repository.PlaybackStateRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Room-backed implementation of [PlaybackStateRepository].
 *
 * Stores a single row in the `playback_state` table. Because writes are
 * infrequent (a handful per minute) and only touch one small row, a plain
 * REPLACE upsert on the IO dispatcher is more than sufficient — there is no
 * need for batching or debouncing beyond what the controller already does.
 */
@Singleton
class PlaybackStateRepositoryImpl @Inject constructor(
    private val dao: PlaybackStateDao
) : PlaybackStateRepository {

    override suspend fun save(state: SavedPlaybackState) {
        if (state.isEmpty) return
        withContext(Dispatchers.IO) {
            dao.upsert(state.toEntity())
        }
    }

    override suspend fun load(): SavedPlaybackState? =
        withContext(Dispatchers.IO) {
            dao.get()?.toDomain()
        }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            dao.clear()
        }
    }
}
