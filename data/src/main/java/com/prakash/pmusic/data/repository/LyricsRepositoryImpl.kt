package com.prakash.pmusic.data.repository

import com.prakash.pmusic.data.lyrics.Id3LyricsParser
import com.prakash.pmusic.data.lyrics.LrcParser
import com.prakash.pmusic.domain.model.Lyrics
import com.prakash.pmusic.domain.model.Song
import com.prakash.pmusic.domain.repository.LyricsRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads lyrics from offline sources, in order: an LRC sidecar file sitting
 * next to the audio file (`<song>.lrc`), then ID3 USLT/SYLT tags embedded at
 * the start of the file. Both reads are bounded — the ID3 tag is parsed from
 * the first chunk of the file only — and run on the IO dispatcher.
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor() : LyricsRepository {

    override suspend fun loadLyrics(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        lrcSidecar(song.path) ?: embeddedId3(song.path)
    }

    private fun lrcSidecar(audioPath: String): Lyrics? {
        val lrc = File(audioPath.substringBeforeLast('.', audioPath) + ".lrc")
        if (!lrc.isFile) return null
        return runCatching { LrcParser.parse(lrc.readText(Charsets.UTF_8)) }
            .getOrNull()
            ?.takeIf { it.lines.isNotEmpty() }
    }

    private fun embeddedId3(audioPath: String): Lyrics? {
        val file = File(audioPath)
        if (!file.isFile) return null
        return runCatching { Id3LyricsParser.parse(readPrefix(file, ID3_PREFIX_BYTES)) }.getOrNull()
    }

    private fun readPrefix(file: File, max: Int): ByteArray =
        file.inputStream().use { stream ->
            val buffer = ByteArray(max)
            val read = stream.read(buffer)
            if (read <= 0) ByteArray(0) else buffer.copyOf(read)
        }

    private companion object {
        /** Enough to cover any realistic ID3 tag; parsing stops at data bounds anyway. */
        const val ID3_PREFIX_BYTES = 256 * 1024
    }
}
