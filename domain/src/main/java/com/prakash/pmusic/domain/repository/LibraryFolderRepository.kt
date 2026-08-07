package com.prakash.pmusic.domain.repository

import com.prakash.pmusic.domain.model.DetectedFolder
import com.prakash.pmusic.domain.model.FolderRules
import com.prakash.pmusic.domain.model.LibraryFolder
import com.prakash.pmusic.domain.model.LibraryFolderType
import kotlinx.coroutines.flow.Flow

/**
 * Contract for managing the library folder rules.
 *
 * The folder manager lets users decide which folders are scanned. Rules are
 * stored in Room (so they participate in Android Auto Backup and come back on
 * restore), evaluated recursively, and applied by the MediaStore scanner
 * before any metadata work so excluded content is never read, cached or
 * written to the library.
 *
 * Behaviour summary:
 * - No enabled INCLUDED folders → the whole device is scanned except
 *   enabled EXCLUDED folders.
 * - At least one enabled INCLUDED folder → only those folders are scanned
 *   (still respecting EXCLUDED).
 * - Turning a folder into an exclusion (or removing an inclusion)
 *   immediately purges its songs from the database.
 */
interface LibraryFolderRepository {

    /** Reactive list of all configured folders (regardless of type/enabled). */
    fun observeFolders(): Flow<List<LibraryFolder>>

    /** Adds a folder rule; returns null when it duplicates an existing one. */
    suspend fun addFolder(
        folderPath: String,
        displayName: String,
        type: LibraryFolderType
    ): LibraryFolder?

    /** Enables/disables a rule, purging affected songs when it newly hides content. */
    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** Moves a rule between the INCLUDED and EXCLUDED lists. */
    suspend fun setType(id: Long, type: LibraryFolderType)

    /** Renames the display name shown in the folder manager. */
    suspend fun renameFolder(id: Long, displayName: String)

    /** Removes the rule; songs under a removed INCLUDED folder are purged. */
    suspend fun removeFolder(id: Long)

    /** Refreshes the persisted song count and last-scanned stamp. */
    suspend fun refreshFolderStats(id: Long)

    /** The enabled rules, as a snapshot for scanner decisions. */
    suspend fun rulesSnapshot(): FolderRules

    /** True once any folder rule exists (used to skip the first-run wizard). */
    suspend fun hasFolders(): Boolean

    /** Immediate recursive removal of every song under [folderPath]. */
    suspend fun purgeFolder(folderPath: String): Int

    /**
     * Detects folders that mostly contain non-music audio (voice recordings,
     * call recordings, short clips) that are not already configured.
     */
    suspend fun discoverNonMusicFolders(limit: Int = 20): List<DetectedFolder>
}
