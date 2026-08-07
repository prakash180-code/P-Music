package com.prakash.pmusic.data.repository

import com.prakash.pmusic.core.database.dao.SongDao
import com.prakash.pmusic.data.mapper.toDomain
import com.prakash.pmusic.data.scanner.MediaLibraryScanner
import com.prakash.pmusic.data.scanner.ScanOutcome
import com.prakash.pmusic.domain.model.Album
import com.prakash.pmusic.domain.model.Artist
import com.prakash.pmusic.domain.model.Genre
import com.prakash.pmusic.domain.model.LibraryScanState
import com.prakash.pmusic.domain.model.Song
import com.prakash.pmusic.domain.repository.LibraryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation of [LibraryRepository].
 *
 * Reads are direct Room [Flow]s mapped to domain models; the scan state is
 * held in a [StateFlow] so the UI can reflect progress. The first scan runs
 * lazily from the app UI once media permission is granted.
 */
@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val scanner: MediaLibraryScanner
) : LibraryRepository {

    private val _scanState = MutableStateFlow<LibraryScanState>(LibraryScanState.Idle)
    override val scanState: StateFlow<LibraryScanState> = _scanState.asStateFlow()

    override fun observeSongs(): Flow<List<Song>> =
        songDao.observeAllSongs().map { list -> list.map { it.toDomain() } }

    override fun observeAlbums(): Flow<List<Album>> =
        songDao.observeAlbums().map { list -> list.map { it.toDomain() } }

    override fun observeArtists(): Flow<List<Artist>> =
        songDao.observeArtists().map { list -> list.map { it.toDomain() } }

    override fun observeGenres(): Flow<List<Genre>> =
        songDao.observeGenres().map { list -> list.map { it.toDomain() } }

    override fun observeFavoriteSongs(): Flow<List<Song>> =
        songDao.observeFavoriteSongs().map { list -> list.map { it.toDomain() } }

    override fun observeRecentlyAdded(limit: Int): Flow<List<Song>> =
        songDao.observeRecentlyAdded(limit).map { list -> list.map { it.toDomain() } }

    override fun observeRecentlyPlayed(limit: Int): Flow<List<Song>> =
        songDao.observeRecentlyPlayed(limit).map { list -> list.map { it.toDomain() } }

    override fun observeMostPlayed(limit: Int): Flow<List<Song>> =
        songDao.observeMostPlayed(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun scanLibrary(force: Boolean) {
        // Skip redundant rescans on every cold start unless requested.
        if (!force && songDao.count() > 0) return

        _scanState.value = LibraryScanState.Scanning
        try {
            when (val outcome = scanner.scan()) {
                is ScanOutcome.Success ->
                    _scanState.value = LibraryScanState.Complete(
                        songCount = outcome.songCount,
                        removedCount = outcome.removedCount
                    )
                ScanOutcome.NoPermission ->
                    _scanState.value = LibraryScanState.NoPermission
                is ScanOutcome.Failure ->
                    _scanState.value = LibraryScanState.Failed(outcome.message)
            }
        } catch (exception: Exception) {
            _scanState.value = LibraryScanState.Failed(exception.message ?: "Scan failed")
        }
    }

    override suspend fun setFavorite(songId: Long, isFavorite: Boolean) {
        songDao.setFavorite(songId, isFavorite)
    }

    override suspend fun recordPlay(songId: Long) {
        songDao.recordPlay(songId, System.currentTimeMillis())
    }
}
