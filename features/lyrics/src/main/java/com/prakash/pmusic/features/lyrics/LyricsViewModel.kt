package com.prakash.pmusic.features.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prakash.pmusic.domain.model.Lyrics
import com.prakash.pmusic.domain.model.Song
import com.prakash.pmusic.domain.repository.LyricsRepository
import com.prakash.pmusic.domain.repository.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state for the lyrics overlay. */
data class LyricsUiState(
    /** Id of the song whose lyrics are shown; -1 when nothing is playing. */
    val songId: Long = -1,
    val loading: Boolean = true,
    /** Null once loading finishes without any lyrics found. */
    val lyrics: Lyrics? = null
)

/**
 * Loads lyrics for the current song through [LyricsRepository] whenever the
 * song changes. The live playback state is exposed separately so the screen
 * can highlight synced lines against the playhead.
 */
@HiltViewModel
class LyricsViewModel @Inject constructor(
    playbackController: PlaybackController,
    private val lyricsRepository: LyricsRepository
) : ViewModel() {

    val playbackState = playbackController.playbackState

    private val _uiState = MutableStateFlow(LyricsUiState())
    val uiState: StateFlow<LyricsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** Reloads when the current song changes; a no-op while it stays the same. */
    fun onCurrentSong(song: Song?) {
        val songId = song?.id ?: -1
        val current = _uiState.value
        if (current.songId == songId && (!current.loading || current.lyrics != null)) return
        loadJob?.cancel()
        _uiState.value = LyricsUiState(songId = songId, loading = true)
        loadJob = viewModelScope.launch {
            val lyrics = song?.let { lyricsRepository.loadLyrics(it) }
            _uiState.value = LyricsUiState(songId = songId, loading = false, lyrics = lyrics)
        }
    }
}
