package com.prakash.pmusic.data.scanner

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.prakash.pmusic.core.database.dao.SongDao
import com.prakash.pmusic.core.database.entity.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of one scan pass.
 */
sealed interface ScanOutcome {
    data class Success(val songCount: Int, val removedCount: Int) : ScanOutcome
    data object NoPermission : ScanOutcome
    data class Failure(val message: String) : ScanOutcome
}

/**
 * Reads the MediaStore audio catalogue and syncs it into the Room database.
 *
 * Performance notes (100k+ song libraries):
 * - The whole pass runs on [Dispatchers.IO].
 * - Upserts are chunked (1 000 rows) to keep transactions bounded.
 * - Stale-row deletion is done with bounded `IN (...)` chunks (900 ids) to
 *   stay under SQLite's parameter limit.
 * - Genre assignment uses one members query per genre instead of per song.
 */
@Singleton
class MediaLibraryScanner @Inject constructor(
    private val songDao: SongDao,
    @ApplicationContext private val context: Context
) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    private val requiredPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, requiredPermission) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun scan(): ScanOutcome = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            return@withContext ScanOutcome.NoPermission
        }
        try {
            performScan()
        } catch (exception: Exception) {
            ScanOutcome.Failure(exception.message ?: "Unknown scan error")
        }
    }

    private suspend fun performScan(): ScanOutcome {
        val albumArt = queryAlbumArtPaths()
        val genreBySong = queryGenres()
        val existingMeta = songDao.getSongMeta().associateBy { it.id }
        val scannedIds = ArrayList<Long>(1024)
        val batch = ArrayList<SongEntity>(CHUNK_SIZE)

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DISC_NUMBER,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.COMPOSER,
            MediaStore.Audio.Media.BITRATE
        )

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            MediaStore.Audio.Media.IS_MUSIC + " != 0",
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumArtistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val discCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISC_NUMBER)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val composerCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.COMPOSER)
            val bitrateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                scannedIds += id
                val meta = existingMeta[id]

                batch += SongEntity(
                    id = id,
                    title = cursor.getString(titleCol) ?: UNKNOWN,
                    artist = cursor.getString(artistCol) ?: UNKNOWN,
                    artistId = cursor.getLong(artistIdCol),
                    album = cursor.getString(albumCol) ?: UNKNOWN,
                    albumId = albumId,
                    albumArtist = cursor.getString(albumArtistCol),
                    trackNumber = cursor.getInt(trackCol),
                    discNumber = cursor.getInt(discCol),
                    year = cursor.getInt(yearCol),
                    genre = genreBySong[id] ?: UNKNOWN,
                    durationMs = cursor.getLong(durationCol),
                    sizeBytes = cursor.getLong(sizeCol),
                    mimeType = cursor.getString(mimeCol) ?: "",
                    path = cursor.getString(dataCol) ?: "",
                    dateAdded = cursor.getLong(addedCol),
                    dateModified = cursor.getLong(modifiedCol),
                    composer = cursor.getString(composerCol),
                    // Carry over user state that REPLACE would otherwise reset.
                    playCount = meta?.playCount ?: 0,
                    skipCount = 0,
                    lastPlayedAt = meta?.lastPlayedAt,
                    isFavorite = meta?.isFavorite ?: false,
                    // Sample rate / channel count are not MediaStore columns;
                    // they can be enriched later on demand via MediaMetadataRetriever.
                    bitrate = cursor.getInt(bitrateCol),
                    sampleRate = 0,
                    channels = 0,
                    artPath = albumArt[albumId]
                )

                if (batch.size >= CHUNK_SIZE) {
                    songDao.upsertAll(batch)
                    batch.clear()
                }
            }
        }

        if (batch.isNotEmpty()) {
            songDao.upsertAll(batch)
        }

        val removedCount = removeStaleSongs(scannedIds)
        return ScanOutcome.Success(songCount = scannedIds.size, removedCount = removedCount)
    }

    /** Deletes rows whose MediaStore id no longer exists (files were removed). */
    private suspend fun removeStaleSongs(scannedIds: List<Long>): Int {
        if (scannedIds.isEmpty()) {
            songDao.deleteAll()
            return songDao.getAllIds().size
        }
        val dbIds = songDao.getAllIds().toHashSet()
        val stale = dbIds - scannedIds.toHashSet()
        stale.chunked(DELETE_CHUNK_SIZE).forEach { chunk -> songDao.deleteByIds(chunk) }
        return stale.size
    }

    /** albumId -> absolute path of the album artwork. */
    private fun queryAlbumArtPaths(): Map<Long, String?> {
        val result = HashMap<Long, String?>()
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM_ART
        )
        contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val artCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART)
            while (cursor.moveToNext()) {
                result[cursor.getLong(idCol)] = cursor.getString(artCol)
            }
        }
        return result
    }

    /** songId -> genre name. One members query per genre, not per song. */
    private fun queryGenres(): Map<Long, String> {
        val result = HashMap<Long, String>()
        contentResolver.query(
            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
            null,
            null,
            null
        )?.use { genres ->
            val idCol = genres.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
            val nameCol = genres.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
            while (genres.moveToNext()) {
                val genreId = genres.getLong(idCol)
                val genreName = genres.getString(nameCol) ?: continue
                val membersUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                    genreId
                ).buildUpon()
                    .appendPath(MediaStore.Audio.Genres.Members.CONTENT_DIRECTORY)
                    .build()

                contentResolver.query(
                    membersUri,
                    arrayOf(MediaStore.Audio.Genres.Members._ID),
                    null,
                    null,
                    null
                )?.use { members ->
                    val memberIdCol =
                        members.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members._ID)
                    while (members.moveToNext()) {
                        result.putIfAbsent(members.getLong(memberIdCol), genreName)
                    }
                }
            }
        }
        return result
    }

    private companion object {
        const val CHUNK_SIZE = 1_000
        const val DELETE_CHUNK_SIZE = 900
        const val UNKNOWN = "Unknown"
    }
}
