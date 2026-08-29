package com.prakash.pmusic.data.repository

import com.prakash.pmusic.domain.model.RepeatMode
import com.prakash.pmusic.domain.model.Song

/**
 * Serializes a playback queue of [Song]s to a single string for Room storage,
 * and back. Pure Kotlin so it is unit-testable without Android.
 *
 * Format: one song per line (records separated by `\n`); within a line, fields
 * are separated by the unit separator character `\u001F` (essentially never
 * typed in titles/artists or file paths). Newlines, the field separator and the
 * escape character are all escaped inside field values, so a song can contain
 * any text (commas, colons, slashes, quotes, `\n`) without ambiguity.
 *
 * Only the fields needed to reproduce a playable library [Song] and its
 * metadata are stored; derived/user state (favorites, play counts) is not part
 * of the persisted playback session.
 */
object PlaybackStateCodec {

    /** ASCII record separator used to delimit songs. */
    private const val RECORD_SEP = '\n'
    private const val FIELD_SEP = '\u001F'
    /** ASCII escape char; raw values use escape + a tag letter below. */
    private const val ESC = '\u001E'
    private const val ESC_NL = "\u001En"
    private const val ESC_FS = "\u001Ef"
    private const val ESC_ESC = "\u001Ee"

    /** Encodes [songs] into a single storage string (empty when no songs). */
    fun encodeQueue(songs: List<Song>): String =
        songs.joinToString(RECORD_SEP.toString(), transform = ::encodeSong)

    /** Decodes a stored queue string back into [Song]s (empty when empty). */
    fun decodeQueue(raw: String): List<Song> =
        if (raw.isBlank()) emptyList() else raw.split(RECORD_SEP).mapNotNull(::decodeSong)

    /** Encodes a [RepeatMode] to a stable storage token. */
    fun encodeRepeatMode(mode: RepeatMode): String = mode.name

    /** Decodes a stored repeat-mode token, falling back to [RepeatMode.OFF]. */
    fun decodeRepeatMode(raw: String): RepeatMode =
        runCatching { RepeatMode.valueOf(raw) }.getOrDefault(RepeatMode.OFF)

    private fun encodeSong(song: Song): String = listOf(
        song.id.toString(),
        song.title,
        song.artist,
        song.artistId.toString(),
        song.album,
        song.albumId.toString(),
        song.albumArtist.orEmpty(),
        song.trackNumber.toString(),
        song.discNumber.toString(),
        song.year.toString(),
        song.genre,
        song.durationMs.toString(),
        song.sizeBytes.toString(),
        song.mimeType,
        song.path,
        song.dateAdded.toString(),
        song.dateModified.toString(),
        song.composer.orEmpty(),
        song.playCount.toString(),
        song.skipCount.toString(),
        song.lastPlayedAt?.toString().orEmpty(),
        if (song.isFavorite) "1" else "0",
        song.bitrate.toString(),
        song.sampleRate.toString(),
        song.channels.toString(),
        song.artPath.orEmpty()
    ).joinToString(FIELD_SEP.toString(), transform = ::escape)

    @Suppress("CyclomaticComplexMethod")
    private fun decodeSong(raw: String): Song? {
        val fields = raw.split(FIELD_SEP).map(::unescape)
        if (fields.size < 26) return null
        val id = fields[0].toLongOrNull() ?: return null
        return Song(
            id = id,
            title = fields[1],
            artist = fields[2],
            artistId = at(fields, 3)?.toLongOrNull() ?: 0L,
            album = fields[4],
            albumId = at(fields, 5)?.toLongOrNull() ?: 0L,
            albumArtist = at(fields, 6)?.takeIf { it.isNotEmpty() },
            trackNumber = at(fields, 7)?.toIntOrNull() ?: 0,
            discNumber = at(fields, 8)?.toIntOrNull() ?: 0,
            year = at(fields, 9)?.toIntOrNull() ?: 0,
            genre = at(fields, 10).orEmpty(),
            durationMs = at(fields, 11)?.toLongOrNull() ?: 0L,
            sizeBytes = at(fields, 12)?.toLongOrNull() ?: 0L,
            mimeType = at(fields, 13).orEmpty(),
            path = at(fields, 14).orEmpty(),
            dateAdded = at(fields, 15)?.toLongOrNull() ?: 0L,
            dateModified = at(fields, 16)?.toLongOrNull() ?: 0L,
            composer = at(fields, 17)?.takeIf { it.isNotEmpty() },
            playCount = at(fields, 18)?.toIntOrNull() ?: 0,
            skipCount = at(fields, 19)?.toIntOrNull() ?: 0,
            lastPlayedAt = at(fields, 20)?.toLongOrNull(),
            isFavorite = at(fields, 21) == "1",
            bitrate = at(fields, 22)?.toIntOrNull() ?: 0,
            sampleRate = at(fields, 23)?.toIntOrNull() ?: 0,
            channels = at(fields, 24)?.toIntOrNull() ?: 0,
            artPath = at(fields, 25)?.takeIf { it.isNotEmpty() }
        )
    }

    private fun at(fields: List<String>, index: Int): String? =
        fields.getOrNull(index)

    /** Escapes newlines, field separators and the escape char inside a value. */
    private fun escape(value: String): String {
        val out = StringBuilder(value.length + 4)
        value.forEach { c ->
            when (c) {
                '\n' -> out.append(ESC_NL)
                FIELD_SEP -> out.append(ESC_FS)
                ESC -> out.append(ESC_ESC)
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    private fun unescape(value: String): String {
        if (!value.contains(ESC)) return value
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            if (value[i] == ESC && i + 1 < value.length) {
                when (value[i + 1]) {
                    'n' -> { out.append('\n'); i += 2; continue }
                    'f' -> { out.append(FIELD_SEP); i += 2; continue }
                    'e' -> { out.append(ESC); i += 2; continue }
                }
            }
            out.append(value[i])
            i += 1
        }
        return out.toString()
    }
}
