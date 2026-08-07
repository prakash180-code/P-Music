package com.prakash.pmusic.domain.model

/**
 * How a configured folder participates in scanning.
 *
 * [INCLUDED] folders opt the library *in*: when at least one enabled
 * INCLUDED folder exists, only those folders are scanned. [EXCLUDED] folders
 * always win: a file under an enabled EXCLUDED folder is never scanned, even
 * when it also lives under an INCLUDED folder.
 */
enum class LibraryFolderType {
    INCLUDED,
    EXCLUDED
}

/**
 * A user-configured library folder rule.
 *
 * Rules are evaluated recursively: a file matches a folder when it sits
 * directly inside it or anywhere below it. [recursive] is always true for
 * matching but is stored so the future media types (videos, audiobooks,
 * podcasts, downloads) can opt into non-recursive handling.
 */
data class LibraryFolder(
    val id: Long,
    val folderPath: String,
    val displayName: String,
    val type: LibraryFolderType,
    val enabled: Boolean,
    val recursive: Boolean,
    val lastScanned: Long?,
    val songCount: Int,
    val dateAdded: Long
)

/**
 * A folder that appears to contain mostly non-music audio (voice recordings,
 * call recordings, notification sounds, very short clips). Produced by the
 * non-music folder detection used by the first-run wizard and the smart
 * suggestions, both of which offer to exclude it.
 */
data class DetectedFolder(
    val folderPath: String,
    val displayName: String,
    val songCount: Int,
    val averageDurationMs: Long
)
