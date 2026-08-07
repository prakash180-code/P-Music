package com.prakash.pmusic.data.mapper

import com.prakash.pmusic.core.database.entity.SongEntity
import com.prakash.pmusic.core.database.model.AlbumProjection
import com.prakash.pmusic.core.database.model.ArtistProjection
import com.prakash.pmusic.core.database.model.GenreProjection
import com.prakash.pmusic.domain.model.Genre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies that persistence-to-domain mapping is lossless and stable.
 */
class LibraryMappersTest {

    @Test
    fun `song entity maps to domain song`() {
        val entity = SongEntity(
            id = 42L,
            title = "Title",
            artist = "Artist",
            artistId = 7L,
            album = "Album",
            albumId = 8L,
            albumArtist = "Album Artist",
            trackNumber = 3,
            discNumber = 1,
            year = 2020,
            genre = "Rock",
            durationMs = 210_000,
            sizeBytes = 5_000_000,
            mimeType = "audio/mpeg",
            path = "/storage/emulated/0/Music/song.mp3",
            dateAdded = 1_000L,
            dateModified = 2_000L,
            composer = "Composer",
            playCount = 12,
            skipCount = 1,
            lastPlayedAt = 3_000L,
            isFavorite = true,
            bitrate = 320_000,
            sampleRate = 44_100,
            channels = 2,
            artPath = "/storage/emulated/0/AlbumArt.jpg"
        )

        val song = entity.toDomain()

        assertEquals(42L, song.id)
        assertEquals("Title", song.title)
        assertEquals("Artist", song.artist)
        assertEquals("Album", song.album)
        assertEquals(2020, song.year)
        assertEquals(210_000L, song.durationMs)
        assertEquals(12, song.playCount)
        assertEquals(3_000L, song.lastPlayedAt)
        assertEquals(true, song.isFavorite)
        assertEquals(44_100, song.sampleRate)
        assertEquals("Album Artist", song.albumArtist)
    }

    @Test
    fun `song nullables survive mapping`() {
        val entity = SongEntity(
            id = 1L, title = "T", artist = "A", artistId = 0L, album = "Al",
            albumId = 0L, albumArtist = null, trackNumber = 0, discNumber = 0,
            year = 0, genre = "", durationMs = 0L, sizeBytes = 0L, mimeType = "",
            path = "", dateAdded = 0L, dateModified = 0L, composer = null,
            playCount = 0, skipCount = 0, lastPlayedAt = null, isFavorite = false,
            bitrate = 0, sampleRate = 0, channels = 0, artPath = null
        )

        val song = entity.toDomain()

        assertNull(song.albumArtist)
        assertNull(song.composer)
        assertNull(song.lastPlayedAt)
    }

    @Test
    fun `album projection maps to domain album`() {
        val projection = AlbumProjection(
            id = 5L, name = "Dark Side", artist = "Pink Floyd",
            year = 1973, songCount = 10, durationMs = 3_000_000, artPath = null
        )

        val album = projection.toDomain()

        assertEquals(5L, album.id)
        assertEquals("Dark Side", album.name)
        assertEquals("Pink Floyd", album.artist)
        assertEquals(10, album.songCount)
        assertNull(album.artPath)
    }

    @Test
    fun `artist projection maps to domain artist`() {
        val projection = ArtistProjection(id = 3L, name = "Daft Punk", albumCount = 2, songCount = 24)

        val artist = projection.toDomain()

        assertEquals(3L, artist.id)
        assertEquals("Daft Punk", artist.name)
        assertEquals(2, artist.albumCount)
        assertEquals(24, artist.songCount)
    }

    @Test
    fun `genre id is stable for the same name`() {
        val projection = GenreProjection(name = "Electronic", songCount = 9)

        val genre: Genre = projection.toDomain()

        assertEquals(9, genre.songCount)
        // Id must be deterministic so favorites/pins survive re-aggregation.
        assertEquals("Electronic".hashCode().toLong(), genre.id)
    }
}
