package com.prakash.pmusic.features.filemanager

import com.prakash.pmusic.domain.model.AudioFileDetails
import com.prakash.pmusic.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileFormatTest {

    private fun song(
        sizeBytes: Long = 0,
        durationMs: Long = 0,
        bitrate: Int = 0,
        sampleRate: Int = 0,
        channels: Int = 0,
        mimeType: String = "",
        year: Int = 0,
        genre: String = "",
        trackNumber: Int = 0,
        playCount: Int = 0,
        dateAdded: Long = 0,
        dateModified: Long = 0,
        path: String = ""
    ) = Song(
        id = 1L,
        title = "Song",
        artist = "Artist",
        artistId = 1L,
        album = "Album",
        albumId = 1L,
        albumArtist = null,
        trackNumber = trackNumber,
        discNumber = 1,
        year = year,
        genre = genre,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        path = path,
        dateAdded = dateAdded,
        dateModified = dateModified,
        composer = null,
        playCount = playCount,
        skipCount = 0,
        lastPlayedAt = null,
        isFavorite = false,
        bitrate = bitrate,
        sampleRate = sampleRate,
        channels = channels,
        artPath = null
    )

    // --- formatBytes ---

    @Test
    fun `formatBytes formats sizes in human units`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("3.2 MB", formatBytes((3.2 * 1024 * 1024).toLong()))
        assertEquals("1.25 GB", formatBytes((1.25 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `formatBytes returns dash for missing size`() {
        assertEquals("—", formatBytes(0))
        assertEquals("—", formatBytes(-1))
    }

    // --- formatDuration ---

    @Test
    fun `formatDuration uses mm-ss under an hour`() {
        assertEquals("03:45", formatDuration(225_000))
        assertEquals("00:00", formatDuration(0))
    }

    @Test
    fun `formatDuration adds hours when over an hour`() {
        assertEquals("1:02:07", formatDuration(3_727_000))
    }

    // --- formatBitrate ---

    @Test
    fun `formatBitrate converts to kbps`() {
        assertEquals("320 kbps", formatBitrate(320_000))
        assertEquals("128 kbps", formatBitrate(128_000))
    }

    @Test
    fun `formatBitrate is null when unknown`() {
        assertNull(formatBitrate(0))
    }

    // --- formatSampleRate ---

    @Test
    fun `formatSampleRate formats kilohertz`() {
        assertEquals("44.1 kHz", formatSampleRate(44_100))
        assertEquals("48 kHz", formatSampleRate(48_000))
        assertEquals("96 kHz", formatSampleRate(96_000))
    }

    @Test
    fun `formatSampleRate is null when unknown`() {
        assertNull(formatSampleRate(0))
    }

    // --- formatChannelCount ---

    @Test
    fun `formatChannelCount names common layouts`() {
        assertEquals("Mono", formatChannelCount(1))
        assertEquals("Stereo", formatChannelCount(2))
        assertEquals("5.1", formatChannelCount(6))
        assertEquals("7.1", formatChannelCount(8))
        assertEquals("3 channels", formatChannelCount(3))
    }

    @Test
    fun `formatChannelCount is null when unknown`() {
        assertNull(formatChannelCount(0))
    }

    // --- formatDate ---

    @Test
    fun `formatDate renders epoch seconds`() {
        assertEquals("Aug 7, 2026", formatDate(1_786_060_800))
    }

    @Test
    fun `formatDate is null when missing`() {
        assertNull(formatDate(0))
    }

    // --- resolveFileDetails ---

    @Test
    fun `resolveFileDetails prefers extracted values`() {
        val s = song(bitrate = 128_000, sampleRate = 44_100, channels = 2, mimeType = "audio/mpeg")
        val extracted = AudioFileDetails(sampleRateHz = 96_000, channelCount = 6, bitrate = 0, mimeType = null)
        val resolved = resolveFileDetails(s, extracted)
        assertEquals(96_000, resolved.sampleRateHz)
        assertEquals(6, resolved.channelCount)
        assertEquals(128_000, resolved.bitrate)
        assertEquals("audio/mpeg", resolved.mimeType)
    }

    @Test
    fun `resolveFileDetails falls back to indexed values when extraction fails`() {
        val s = song(bitrate = 128_000, sampleRate = 44_100, channels = 2, mimeType = "audio/mpeg")
        val resolved = resolveFileDetails(s, extracted = null)
        assertEquals(44_100, resolved.sampleRateHz)
        assertEquals(2, resolved.channelCount)
        assertEquals(128_000, resolved.bitrate)
        assertEquals("audio/mpeg", resolved.mimeType)
    }

    // --- detailRows ---

    @Test
    fun `detailRows renders core file info`() {
        val s = song(
            sizeBytes = 5 * 1024 * 1024,
            durationMs = 225_000,
            bitrate = 320_000,
            sampleRate = 44_100,
            channels = 2,
            mimeType = "audio/mpeg",
            year = 2020,
            genre = "Rock",
            trackNumber = 3,
            playCount = 7,
            dateAdded = 1_786_060_800,
            dateModified = 1_786_060_800,
            path = "/storage/emulated/0/Music/track.mp3"
        )
        val rows = detailRows(s, resolveFileDetails(s, null))
        assertEquals(
            listOf(
                "Format" to "audio/mpeg",
                "Size" to "5.0 MB",
                "Duration" to "03:45",
                "Bitrate" to "320 kbps",
                "Sample rate" to "44.1 kHz",
                "Channels" to "Stereo",
                "Year" to "2020",
                "Genre" to "Rock",
                "Track" to "3",
                "Plays" to "7",
                "Date added" to "Aug 7, 2026",
                "Date modified" to "Aug 7, 2026",
                "Path" to "/storage/emulated/0/Music/track.mp3"
            ),
            rows
        )
    }

    @Test
    fun `detailRows hides unknown and Unknown-genre rows`() {
        val s = song(genre = "Unknown")
        val rows = detailRows(s, resolveFileDetails(s, null))
        assertEquals(
            listOf("Format" to "Unknown", "Size" to "—", "Duration" to "00:00", "Plays" to "0"),
            rows
        )
    }
}
