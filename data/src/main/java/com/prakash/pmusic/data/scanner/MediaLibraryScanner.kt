package com.prakash.pmusic.data.scanner

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.prakash.pmusic.core.database.dao.LibraryFolderDao
import com.prakash.pmusic.core.database.dao.SongDao
import com.prakash.pmusic.core.database.entity.LibraryFolderEntity
import com.prakash.pmusic.core.database.entity.SongEntity
import com.prakash.pmusic.data.mapper.toFolderType
import com.prakash.pmusic.domain.model.FolderRules
import com.prakash.pmusic.domain.model.FolderRulesMatcher
import com.prakash.pmusic.domain.model.LibraryFolderType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
 * Folder rules (see [LibraryFolderDao]) are applied before any metadata work:
 * files inside an enabled EXCLUDED folder (or outside every enabled INCLUDED
 * folder when restricted mode is on) are skipped while still reading the
 * cursor, so they are never inserted, never genre/art-enriched, and their
 * stale rows are removed by the pass below.
 *
 * Performance notes (100k+ song libraries):
 * - The whole pass runs on [Dispatchers.IO].
 * - Two lightweight cursor passes: the first collects allowed ids (2
 *   columns), the second streams the full projection for allowed rows only.
 * - Upserts are chunked (1 000 rows) to keep transactions bounded.
 * - Stale-row deletion is done with bounded `IN (...)` chunks (900 ids).
 * - Genre assignment uses one members query per genre instead of per song,
 *   restricted to allowed songs.
 */
@Singleton
class MediaLibraryScanner @Inject constructor(
    private val songDao: SongDao,
    private val folderDao: LibraryFolderDao,
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
        val folders = folderDao.getAll()
        val rules = rulesFrom(folders)
        val existingMeta = songDao.getSongMeta().associateBy { it.id }

        // Pass 1: decide which MediaStore ids may enter the library, without
        // building any entity or enriching metadata for skipped files.
        val allowedIds = collectAllowedIds(rules)

        // Metadata enrichment happens only for allowed songs.
        val genreBySong = queryGenres(allowedIds)
        val albumArt = queryAlbumArtPaths(allowedIds)

        // Pass 2: stream the full projection and build entities for allowed rows.
        val scannedIds = upsertAllowedRows(allowedIds, existingMeta, genreBySong, albumArt)

        val removedCount = removeStaleSongs(scannedIds)
        updateFolderStats(folders)
        return ScanOutcome.Success(songCount = scannedIds.size, removedCount = removedCount)
    }

    /** Enabled INCLUDED / EXCLUDED folder paths as a decision snapshot. */
    private fun rulesFrom(folders: List<LibraryFolderEntity>): FolderRules {
        val enabled = folders.filter { it.enabled }
        return FolderRules(
            included = enabled.filter { it.type.toFolderType() == LibraryFolderType.INCLUDED }
                .map { it.folderPath },
            excluded = enabled.filter { it.type.toFolderType() == LibraryFolderType.EXCLUDED }
                .map { it.folderPath }
        )
    }

    /** Pass 1: the set of song ids whose file path passes the folder rules. */
    private fun collectAllowedIds(rules: FolderRules): HashSet<Long> {
        val allowed = HashSet<Long>()
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA),
            audioSelection(),
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol)
                if (path.isNullOrBlank()) continue
                if (FolderRulesMatcher.isAllowed(path, rules)) {
                    allowed.add(cursor.getLong(idCol))
                }
            }
        }
        return allowed
    }

    /** Pass 2: upsert allowed rows in chunks; returns the scanned id list. */
    private suspend fun upsertAllowedRows(
        allowedIds: Set<Long>,
        existingMeta: Map<Long, com.prakash.pmusic.core.database.model.SongMeta>,
        genreBySong: Map<Long, String>,
        albumArt: Map<Long, String?>
    ): List<Long> {
        val scannedIds = ArrayList<Long>(allowedIds.size)
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
            audioSelection(),
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
                if (id !in allowedIds) continue
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
        return scannedIds
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

    /** Refreshes per-folder song counts and last-scanned stamps after a scan. */
    private suspend fun updateFolderStats(folders: List<LibraryFolderEntity>) {
        val enabled = folders.filter { it.enabled }
        if (enabled.isEmpty()) return
        val now = System.currentTimeMillis()
        val paths = songDao.getAllSongPaths()
        enabled.forEach { folder ->
            val count = paths.count { FolderRulesMatcher.isUnder(it.path, folder.folderPath) }
            folderDao.updateStats(folder.id, count, now)
        }
    }

    /** albumId -> absolute path of the album artwork. */
    private fun queryAlbumArtPaths(allowedIds: Set<Long>): Map<Long, String?> {
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

        // ALBUM_ART is null on many modern/OEM MediaStore implementations.
        // Extract one embedded picture per allowed album into private cache.
        val sampledAlbums = HashSet<Long>()
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.ALBUM_ID),
            audioSelection(),
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                if (mediaId !in allowedIds || albumId <= 0L) continue
                if (result[albumId].isUsableArtwork() || !sampledAlbums.add(albumId)) continue
                extractEmbeddedArtwork(mediaId, albumId)?.let { result[albumId] = it }
            }
        }
        return result
    }

    private fun String?.isUsableArtwork(): Boolean = when {
        this.isNullOrBlank() -> false
        startsWith("content://") || startsWith("file://") -> true
        else -> File(this).isFile
    }

    private fun extractEmbeddedArtwork(mediaId: Long, albumId: Long): String? {
        val artworkDir = File(context.cacheDir, "album-art")
        val target = File(artworkDir, "$albumId.jpg")
        if (target.isFile && target.length() > 0L) return target.absolutePath

        val retriever = MediaMetadataRetriever()
        return try {
            val mediaUri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                mediaId
            )
            val picture = contentResolver.openFileDescriptor(mediaUri, "r")?.use { descriptor ->
                retriever.setDataSource(descriptor.fileDescriptor)
                retriever.embeddedPicture
            } ?: return null
            artworkDir.mkdirs()
            target.writeBytes(picture)
            target.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * songId -> genre name, restricted to [allowedIds] so excluded songs are
     * never enriched. One members query per genre, not per song.
     */
    private fun queryGenres(allowedIds: Set<Long>): Map<Long, String> {
        if (allowedIds.isEmpty()) return emptyMap()
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
                        val memberId = members.getLong(memberIdCol)
                        if (memberId in allowedIds) {
                            result.putIfAbsent(memberId, genreName)
                        }
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

        /** Include OEM rows that have not classified a valid audio MIME yet. */
        fun audioSelection(): String =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR " +
                "${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%'"
    }
}
