package com.prakash.pmusic.domain.repository

import com.prakash.pmusic.domain.model.Album
import com.prakash.pmusic.domain.model.Artist
import com.prakash.pmusic.domain.model.AudioFileDetails
import com.prakash.pmusic.domain.model.Genre
import com.prakash.pmusic.domain.model.LibraryScanState
import com.prakash.pmusic.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for reading the music library and driving background scans.
 *
 * All read paths are cold [Flow]s backed by Room, so the UI always observes
 * the latest indexed state reactively. Implementations live in the `:data`
 * module; features depend only on this interface.
 */
interface LibraryRepository {

    /** Current scan progress (see [LibraryScanState]). */
    val scanState: StateFlow<LibraryScanState>

    fun observeSongs(): Flow<List<Song>>

    /** Reactive single-song read (null once the row is deleted). */
    fun observeSong(songId: Long): Flow<Song?>

    fun observeAlbums(): Flow<List<Album>>

    fun observeArtists(): Flow<List<Artist>>

    fun observeGenres(): Flow<List<Genre>>

    fun observeFavoriteSongs(): Flow<List<Song>>

    fun observeRecentlyAdded(limit: Int = 50): Flow<List<Song>>

    fun observeRecentlyPlayed(limit: Int = 50): Flow<List<Song>>

    fun observeMostPlayed(limit: Int = 50): Flow<List<Song>>

    /**
     * Runs a MediaStore scan and syncs the Room database.
     *
     * @param force when true, rescans even if the database is already
     * populated; when false the scan is skipped unless the database is empty.
     */
    suspend fun scanLibrary(force: Boolean)

    /** Marks a song as favorite or removes the favorite flag. */
    suspend fun setFavorite(songId: Long, isFavorite: Boolean)

    /** Records a play on [songId]: bumps its play count and last-played time. */
    suspend fun recordPlay(songId: Long)

    /**
     * Reads the audio file's container headers to enrich the details that
     * MediaStore does not index (sample rate, channel count, bitrate).
     *
     * Returns null when the file cannot be read or no audio track is found,
     * so the caller can fall back to the indexed values.
     */
    suspend fun readFileDetails(song: Song): AudioFileDetails?

    /**
     * Removes [songs] from the Room library after their files have been
     * deleted from MediaStore. Playlist membership cascades automatically.
     */
    suspend fun deleteSongsFromDatabase(songs: List<Song>)
}
