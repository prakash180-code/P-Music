package com.prakash.pmusic.domain.repository

import com.prakash.pmusic.domain.model.Lyrics
import com.prakash.pmusic.domain.model.Song

/**
 * Loads lyrics for a track from offline sources: a `.lrc` sidecar file next
 * to the audio file, then ID3 USLT/SYLT tags embedded in the file itself.
 * Returns null when the track has no lyrics anywhere.
 */
interface LyricsRepository {
    suspend fun loadLyrics(song: Song): Lyrics?
}
