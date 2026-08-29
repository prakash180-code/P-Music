package com.prakash.pmusic.data.repository

import com.prakash.pmusic.domain.model.RepeatMode
import com.prakash.pmusic.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackStateCodecTest {

    private val song = Song(
        id = 1000029117L,
        title = "Doli-Doli MassTamilan.com",
        artist = "G. V. Prakash Kumar",
        artistId = 55L,
        album = "Doli",
        albumId = 9L,
        albumArtist = "GVP",
        trackNumber = 7,
        discNumber = 1,
        year = 2019,
        genre = "Comedy",
        durationMs = 320000L,
        sizeBytes = 8423451L,
        mimeType = "audio/mpeg",
        path = "/storage/emulated/0/Download/Doli-Doli-MassTamilan.com.mp3",
        dateAdded = 1620000000000L,
        dateModified = 1620000100000L,
        composer = "GV Prakash",
        playCount = 4,
        skipCount = 1,
        lastPlayedAt = 1720000000000L,
        isFavorite = true,
        bitrate = 256,
        sampleRate = 44100,
        channels = 2,
        artPath = "/storage/emulated/0/album_art/Doli.jpg"
    )

    @Test
    fun `round trips a single song`() {
        val round = PlaybackStateCodec.decodeQueue(PlaybackStateCodec.encodeQueue(listOf(song)))
        assertEquals(1, round.size)
        assertEquals(song, round[0])
    }

    @Test
    fun `round trips a queue preserving order and index metadata`() {
        val song2 = song.copy(id = 2L, title = "Second")
        val song3 = song.copy(id = 3L, title = "Third")
        val queue = listOf(song, song2, song3)
        val round = PlaybackStateCodec.decodeQueue(PlaybackStateCodec.encodeQueue(queue))
        assertEquals(queue, round)
    }

    @Test
    fun `handles special characters in titles and paths`() {
        val tricky = song.copy(
            title = "A, 'quoted' title [ft. Artist] (2024) — remix",
            artist = "Artist 1 & Artist 2 / feat. X",
            album = "Album: The 'Best' of, Vol. 1",
            path = "/storage/emulated/0/Music/Artist/My, file (1).mp3",
            albumArtist = null,
            composer = "Solo, Composer \"A\"",
            genre = "Rock, Pop"
        )
        val round = PlaybackStateCodec.decodeQueue(PlaybackStateCodec.encodeQueue(listOf(tricky)))
        assertEquals(listOf(tricky), round)
    }

    @Test
    fun `handles newlines inside a field`() {
        val newline = song.copy(title = "Line1\nLine2\nLine3")
        val round = PlaybackStateCodec.decodeQueue(PlaybackStateCodec.encodeQueue(listOf(newline)))
        assertEquals(listOf(newline), round)
    }

    @Test
    fun `empty queue encodes to blank and decodes to empty`() {
        assertEquals("", PlaybackStateCodec.encodeQueue(emptyList()))
        assertEquals(emptyList<Song>(), PlaybackStateCodec.decodeQueue(""))
        assertEquals(emptyList<Song>(), PlaybackStateCodec.decodeQueue("   "))
    }

    @Test
    fun `nullables round trip`() {
        val nullable = song.copy(
            albumArtist = null,
            composer = null,
            lastPlayedAt = null,
            artPath = null,
            isFavorite = false
        )
        val round = PlaybackStateCodec.decodeQueue(PlaybackStateCodec.encodeQueue(listOf(nullable)))
        assertEquals(1, round.size)
        assertEquals(nullable, round[0])
        assertNull(round[0].albumArtist)
        assertNull(round[0].composer)
        assertNull(round[0].lastPlayedAt)
        assertNull(round[0].artPath)
    }

    @Test
    fun `malformed lines are dropped`() {
        val queue = PlaybackStateCodec.encodeQueue(listOf(song))
        val corrupt = "$queue\nnot-enough-fields"
        val round = PlaybackStateCodec.decodeQueue(corrupt)
        assertEquals(1, round.size)
        assertEquals(song, round[0])
    }

    @Test
    fun `a field containing the escape char survives`() {
        val escaped = song.copy(title = "Title\u001Ewith\u001Eescapes")
        val round = PlaybackStateCodec.decodeQueue(PlaybackStateCodec.encodeQueue(listOf(escaped)))
        assertEquals(1, round.size)
        assertNotNull(round[0])
        assertEquals("Title\u001Ewith\u001Eescapes", round[0].title)
    }

    @Test
    fun `repeat mode codec round trips all values and falls back gracefully`() {
        RepeatMode.entries.forEach { mode ->
            assertEquals(mode, PlaybackStateCodec.decodeRepeatMode(PlaybackStateCodec.encodeRepeatMode(mode)))
        }
        assertEquals(RepeatMode.OFF, PlaybackStateCodec.decodeRepeatMode("bogus"))
    }
}
