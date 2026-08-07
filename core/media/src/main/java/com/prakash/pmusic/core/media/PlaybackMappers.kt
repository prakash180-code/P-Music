package com.prakash.pmusic.core.media

import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.prakash.pmusic.domain.model.RepeatMode
import com.prakash.pmusic.domain.model.Song
import java.io.File

/**
 * Maps between domain models and Media3 types.
 *
 * Top-level functions so they are trivially unit-testable and importable.
 */

/**
 * Builds a playable [MediaItem] from a domain [Song].
 *
 * - `mediaId` is the song id (used to seek back to the row in the UI).
 * - the domain [Song] itself is attached as a tag, so the controller can
 *   recover the full model without re-querying the database.
 * - MediaMetadata is populated so the media notification and lock-screen
 *   controls show title, artist, album and artwork.
 */
fun Song.toMediaItem(): MediaItem {
    val contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        .buildUpon()
        .appendPath(id.toString())
        .build()

    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(contentUri)
        .setTag(this)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artPath?.let { Uri.fromFile(File(it)) })
                .build()
        )
        .build()
}

/** Recovers the domain [Song] attached to a [MediaItem] tag. */
fun MediaItem.toSong(): Song? = localConfiguration?.tag as? Song

/** Maps a Media3 player repeat-mode int to the domain enum. */
fun Int.toDomainRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
    else -> RepeatMode.OFF
}

/** Maps the domain repeat-mode enum to the Media3 player int. */
fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
}
