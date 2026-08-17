package com.prakash.pmusic.data.scanner

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.prakash.pmusic.domain.model.DetectedFolder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans the MediaStore audio catalogue once and groups files by parent
 * directory, flagging folders that mostly hold non-music audio (voice/call
 * recordings, notifications, short clips).
 *
 * Used by the first-run wizard ("We found folders that usually don't contain
 * music") and the smart suggestions in the folder manager.
 */
@Singleton
class NonMusicFolderDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    private val requiredPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    /**
     * Returns up to [limit] detected folders, largest first. Empty when media
     * permission is missing or the device has no matching folders.
     */
    suspend fun discover(limit: Int = 20): List<DetectedFolder> =
        withContext(Dispatchers.IO) {
            val granted = ContextCompat.checkSelfPermission(context, requiredPermission) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return@withContext emptyList()

            // parent path -> list of file durations inside it
            val byFolder = HashMap<String, MutableList<Long>>()
            // every audio file as (path, duration) for the recursive subtree pass
            val files = ArrayList<Pair<String, Long>>()
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR " +
                    "${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%'",
                null,
                null
            )?.use { cursor ->
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    val duration = cursor.getLong(durationCol)
                    files.add(path to duration)
                    val parent = File(path).parent ?: continue
                    byFolder.getOrPut(parent) { ArrayList() }
                        .add(duration)
                }
            }

            // folder path -> number of song-shaped files anywhere below it.
            // A name-matched "recording" folder that recursively holds a real
            // music collection (e.g. the library sitting under a folder called
            // "Recordings") must never be suggested for exclusion.
            val songLikeBelow = HashMap<String, Int>()
            files.forEach { (path, duration) ->
                if (!NonMusicFolderClassifier.isSongLike(duration)) return@forEach
                var dir = File(path).parent ?: return@forEach
                while (true) {
                    songLikeBelow.merge(dir, 1, Int::plus)
                    val parent = File(dir).parent ?: break
                    dir = parent
                }
            }

            byFolder.asSequence()
                .map { (path, durations) ->
                    val name = File(path).name.ifEmpty { path }
                    DetectedFolder(
                        folderPath = path,
                        displayName = name,
                        songCount = durations.size,
                        averageDurationMs = if (durations.isEmpty()) 0L
                        else durations.sum() / durations.size
                    )
                }
                .filter { it.songCount > 0 }
                .filter {
                    !NonMusicFolderClassifier.isMusicContainer(
                        songLikeBelow[it.folderPath] ?: 0
                    )
                }
                .filter {
                    NonMusicFolderClassifier.isNonMusic(
                        folderName = it.displayName,
                        songCount = it.songCount,
                        averageDurationMs = it.averageDurationMs
                    )
                }
                .sortedByDescending { it.songCount }
                .take(limit)
                .toList()
        }
}
