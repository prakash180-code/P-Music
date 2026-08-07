package com.prakash.pmusic.domain.model

/**
 * Aggregate statistics for the songs that live inside one folder.
 *
 * Used by the folder-manager cards ("View statistics" and the per-folder
 * counts) and by the scanner to refresh the persisted folder stats.
 */
data class FolderSongStats(
    val songCount: Int,
    val totalSizeBytes: Long,
    val totalDurationMs: Long,
    /** The most recent file modification time inside the folder. */
    val lastModified: Long
)

/**
 * Pure helpers for deriving per-folder statistics from the song table.
 *
 * Both functions are side-effect free so the folder manager can recompute
 * card stats reactively whenever the library changes.
 */
object FolderStats {

    /** Songs that live inside [folderPath] or any of its sub-folders. */
    fun songsUnder(songs: List<Song>, folderPath: String): List<Song> =
        songs.filter { FolderRulesMatcher.isUnder(it.path, folderPath) }

    /** Aggregate statistics for the songs under [folderPath]. */
    fun forFolder(songs: List<Song>, folderPath: String): FolderSongStats {
        val matches = songsUnder(songs, folderPath)
        if (matches.isEmpty()) return FolderSongStats(0, 0L, 0L, 0L)
        return FolderSongStats(
            songCount = matches.size,
            totalSizeBytes = matches.sumOf { it.sizeBytes },
            totalDurationMs = matches.sumOf { it.durationMs },
            lastModified = matches.maxOf { it.dateModified }
        )
    }
}
