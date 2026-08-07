package com.prakash.pmusic.data.lyrics

import com.prakash.pmusic.domain.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Id3LyricsParserTest {

    @Test
    fun `returns null when data too short`() {
        assertNull(Id3LyricsParser.parse(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `returns null when no id3 tag present`() {
        assertNull(Id3LyricsParser.parse(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)))
    }

    @Test
    fun `parses v23 latin1 uslt as plain lyrics`() {
        val tag = id3(3, frame("USLT", usltLatin1("eng", "Hello\nWorld")))
        val lyrics = Id3LyricsParser.parse(tag)
        assertNotNull(lyrics)
        assertEquals(listOf(LyricLine(null, "Hello\nWorld")), lyrics!!.lines)
    }

    @Test
    fun `parses v23 utf8 sylt with millisecond timestamps`() {
        val tag = id3(
            3,
            frame("SYLT", sylt(encoding = 3, entries = listOf("One" to 1_000L, "Two" to 3_500L)))
        )
        val lyrics = Id3LyricsParser.parse(tag)
        assertNotNull(lyrics)
        assertEquals(listOf(LyricLine(1_000L, "One"), LyricLine(3_500L, "Two")), lyrics!!.lines)
    }

    @Test
    fun `parses utf16 uslt with bom`() {
        val tag = id3(3, frame("USLT", usltUtf16("lyric text")))
        val lyrics = Id3LyricsParser.parse(tag)
        assertNotNull(lyrics)
        assertEquals(listOf(LyricLine(null, "lyric text")), lyrics!!.lines)
    }

    @Test
    fun `parses utf16 uslt when the body omits its own bom`() {
        val tag = id3(3, frame("USLT", usltUtf16NoBodyBom("lyric text")))
        val lyrics = Id3LyricsParser.parse(tag)
        assertNotNull(lyrics)
        assertEquals(listOf(LyricLine(null, "lyric text")), lyrics!!.lines)
    }

    @Test
    fun `prefers synced sylt over plain uslt`() {
        val tag = id3(
            3,
            frame("USLT", usltLatin1("eng", "plain")) +
                frame("SYLT", sylt(3, listOf("A" to 500L)))
        )
        val lyrics = Id3LyricsParser.parse(tag)
        assertEquals(listOf(LyricLine(500L, "A")), lyrics!!.lines)
    }

    @Test
    fun `joins multiple uslt frames`() {
        val tag = id3(
            3,
            frame("USLT", usltLatin1("eng", "First")) +
                frame("USLT", usltLatin1("eng", "Second"))
        )
        val lyrics = Id3LyricsParser.parse(tag)
        assertEquals(listOf(LyricLine(null, "First\n\nSecond")), lyrics!!.lines)
    }

    @Test
    fun `parses v24 synchsafe frame sizes`() {
        val tag = id3(4, frame("USLT", usltLatin1("eng", "v2.4 text"), synchsafe = true))
        val lyrics = Id3LyricsParser.parse(tag)
        assertEquals(listOf(LyricLine(null, "v2.4 text")), lyrics!!.lines)
    }

    @Test
    fun `returns null when only an unsupported sylt format is present`() {
        val tag = id3(3, frame("SYLT", syltFormat1(listOf("A" to 10))))
        assertNull(Id3LyricsParser.parse(tag))
    }

    private fun id3(version: Int, frames: ByteArray): ByteArray {
        val header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            version.toByte(), 0, 0
        )
        val size = frames.size
        return header + byteArrayOf(
            ((size shr 21) and 0x7f).toByte(),
            ((size shr 14) and 0x7f).toByte(),
            ((size shr 7) and 0x7f).toByte(),
            (size and 0x7f).toByte()
        ) + frames
    }

    private fun frame(id: String, data: ByteArray, synchsafe: Boolean = false): ByteArray {
        val idBytes = id.toByteArray(Charsets.ISO_8859_1)
        val size = data.size
        val sizeBytes = if (synchsafe) {
            byteArrayOf(
                ((size shr 21) and 0x7f).toByte(),
                ((size shr 14) and 0x7f).toByte(),
                ((size shr 7) and 0x7f).toByte(),
                (size and 0x7f).toByte()
            )
        } else {
            byteArrayOf(
                ((size shr 24) and 0xff).toByte(),
                ((size shr 16) and 0xff).toByte(),
                ((size shr 8) and 0xff).toByte(),
                (size and 0xff).toByte()
            )
        }
        return idBytes + sizeBytes + byteArrayOf(0, 0) + data
    }

    private fun usltLatin1(language: String, text: String): ByteArray {
        val enc = byteArrayOf(0)
        val lang = language.toByteArray(Charsets.ISO_8859_1)
        val descriptor = byteArrayOf(0)
        val body = text.toByteArray(Charsets.ISO_8859_1)
        return enc + lang + descriptor + body
    }

    private fun usltUtf16(text: String): ByteArray {
        val enc = byteArrayOf(1)
        val lang = "eng".toByteArray(Charsets.ISO_8859_1)
        // Empty descriptor: BOM + UTF-16 terminator.
        val descriptor = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0)
        // Spec-compliant body carries its own BOM.
        val body = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        return enc + lang + descriptor + body
    }

    private fun usltUtf16NoBodyBom(text: String): ByteArray {
        val enc = byteArrayOf(1)
        val lang = "eng".toByteArray(Charsets.ISO_8859_1)
        val descriptor = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0)
        return enc + lang + descriptor + text.toByteArray(Charsets.UTF_16LE)
    }

    private fun sylt(encoding: Byte, entries: List<Pair<String, Long>>): ByteArray {
        // encoding, language, timestamp format 2 (ms), content type, empty descriptor.
        var body = byteArrayOf(encoding) + "eng".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(2, 1, 0)
        entries.forEach { (text, timestamp) ->
            body += text.toByteArray(Charsets.UTF_8)
            body += byteArrayOf(0)
            body += timestampBytes(timestamp)
        }
        return body
    }

    private fun syltFormat1(entries: List<Pair<String, Long>>): ByteArray {
        var body = byteArrayOf(3) + "eng".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(1, 1, 0)
        entries.forEach { (text, timestamp) ->
            body += text.toByteArray(Charsets.UTF_8)
            body += byteArrayOf(0)
            body += timestampBytes(timestamp)
        }
        return body
    }

    private fun timestampBytes(value: Long): ByteArray = byteArrayOf(
        ((value shr 24) and 0xff).toByte(),
        ((value shr 16) and 0xff).toByte(),
        ((value shr 8) and 0xff).toByte(),
        (value and 0xff).toByte()
    )
}
