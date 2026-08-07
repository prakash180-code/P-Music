package com.prakash.pmusic.data.lyrics

import com.prakash.pmusic.domain.model.LyricLine
import com.prakash.pmusic.domain.model.Lyrics

/**
 * Extracts lyrics embedded in an ID3v2 tag (typically MP3): the USLT
 * (unsynchronized lyrics) frame for plain text and the SYLT (synchronized
 * lyrics) frame for timestamped lines. Supports v2.3 and v2.4 tags, extended
 * headers and the four standard text encodings. The caller passes a bounded
 * prefix of the file — an ID3 tag always lives at the very start of an MP3.
 */
object Id3LyricsParser {

    fun parse(data: ByteArray): Lyrics? {
        if (data.size < 10) return null
        if (data[0] != 'I'.code.toByte() || data[1] != 'D'.code.toByte() || data[2] != '3'.code.toByte()) {
            return null
        }
        val major = data[3].toInt()
        if (major !in 2..4) return null
        val flags = data[5].toInt()
        val tagSize = synchsafe(data, 6)
        val end = minOf(data.size, 10 + tagSize)
        var offset = 10

        if (flags and 0x40 != 0) {
            // Extended header.
            if (major == 4 && offset + 4 <= end) {
                // v2.4 size is synchsafe and includes itself.
                offset += synchsafe(data, offset).coerceAtLeast(4)
            } else if (major == 3 && offset + 4 <= end) {
                // v2.3 size is big-endian and excludes itself (and the flags).
                offset += 4 + be(data, offset).coerceAtLeast(0)
            }
        }

        val synced = mutableListOf<LyricLine>()
        val plain = mutableListOf<String>()
        while (offset + 10 <= end) {
            val frameId = String(data, offset, 4, Charsets.ISO_8859_1)
            if (frameId[0] == '\u0000') break
            val frameSize = if (major == 4) synchsafe(data, offset + 4) else be(data, offset + 4)
            if (frameSize < 0) break
            val frameStart = offset + 10
            val frameEnd = minOf(frameStart + frameSize, end)
            when (frameId) {
                "USLT" -> parseUslt(data, frameStart, frameEnd)?.let { plain += it }
                "SYLT" -> parseSylt(data, frameStart, frameEnd)?.let { synced += it }
            }
            offset = frameEnd
        }

        if (synced.isNotEmpty()) return Lyrics(synced)
        val body = plain.filter { it.isNotBlank() }.joinToString("\n\n").trim()
        if (body.isNotEmpty()) return Lyrics(listOf(LyricLine(timestampMs = null, text = body)))
        return null
    }

    /**
     * USLT frame: encoding (1), language (3), null-terminated content
     * descriptor, then the lyric text.
     */
    private fun parseUslt(data: ByteArray, start: Int, end: Int): String? {
        if (end - start < 4) return null
        val encoding = data[start].toInt()
        val terminator = terminatorLength(encoding)
        var pos = start + 4
        val descriptorEnd = findTerminator(data, pos, end, encoding) ?: return null
        pos = descriptorEnd + terminator
        if (pos > end) return null
        return decodeText(data, pos, end - pos, encoding)
    }

    /**
     * SYLT frame: encoding (1), language (3), timestamp format (1),
     * content type (1), null-terminated content descriptor, then repeated
     * (text, 4-byte big-endian timestamp) pairs. Only format 2
     * (milliseconds) is returned; MPEG-frame timestamps (format 1) are
     * unmappable without bitrate and are skipped.
     */
    private fun parseSylt(data: ByteArray, start: Int, end: Int): List<LyricLine>? {
        if (end - start < 6) return null
        val encoding = data[start].toInt()
        if (data[start + 4].toInt() != 2) return null
        val terminator = terminatorLength(encoding)
        var pos = start + 6
        val descriptorEnd = findTerminator(data, pos, end, encoding) ?: return null
        pos = descriptorEnd + terminator

        val lines = mutableListOf<LyricLine>()
        while (pos + terminator + 4 <= end) {
            val textEnd = findTerminator(data, pos, end, encoding) ?: break
            val text = decodeText(data, pos, textEnd - pos, encoding)
            pos = textEnd + terminator
            if (pos + 4 > end) break
            val timestamp = be(data, pos).toLong() and 0xffff_ffffL
            pos += 4
            lines += LyricLine(timestampMs = timestamp, text = text)
        }
        return lines
    }

    private fun findTerminator(data: ByteArray, from: Int, to: Int, encoding: Int): Int? {
        if (encoding == 1) {
            // UTF-16 text is 2-byte aligned; scanning byte-by-byte would
            // mistake a low byte (0x00) for the first terminator half.
            var i = from
            while (i + 1 < to) {
                if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) return i
                i += 2
            }
            return null
        }
        var i = from
        while (i < to) {
            if (data[i].toInt() == 0) return i
            i++
        }
        return null
    }

    private fun terminatorLength(encoding: Int): Int = if (encoding == 1) 2 else 1

    private fun decodeText(data: ByteArray, from: Int, length: Int, encoding: Int): String {
        val bytes = data.copyOfRange(from, from + length)
        return when (encoding) {
            0 -> String(bytes, Charsets.ISO_8859_1)
            1 -> {
                // Honor an explicit BOM; otherwise infer endianness from the
                // first byte pair (lyrics are almost always ASCII-heavy), as
                // not every writer repeats the BOM before the lyric text.
                when {
                    length >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                        String(bytes, 2, length - 2, Charsets.UTF_16LE)
                    length >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                        String(bytes, 2, length - 2, Charsets.UTF_16BE)
                    length >= 2 && bytes[0] == 0.toByte() -> String(bytes, Charsets.UTF_16BE)
                    else -> String(bytes, Charsets.UTF_16LE)
                }
            }
            2 -> String(bytes, Charsets.UTF_16BE)
            else -> String(bytes, Charsets.UTF_8)
        }
    }

    private fun synchsafe(data: ByteArray, at: Int): Int =
        ((data[at].toInt() and 0x7f) shl 21) or
            ((data[at + 1].toInt() and 0x7f) shl 14) or
            ((data[at + 2].toInt() and 0x7f) shl 7) or
            (data[at + 3].toInt() and 0x7f)

    private fun be(data: ByteArray, at: Int): Int =
        ((data[at].toInt() and 0xff) shl 24) or
            ((data[at + 1].toInt() and 0xff) shl 16) or
            ((data[at + 2].toInt() and 0xff) shl 8) or
            (data[at + 3].toInt() and 0xff)
}
