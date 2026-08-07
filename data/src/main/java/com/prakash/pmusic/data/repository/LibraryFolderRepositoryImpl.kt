package com.prakash.pmusic.data.repository

import com.prakash.pmusic.core.database.dao.LibraryFolderDao
import com.prakash.pmusic.core.database.dao.SongDao
import com.prakash.pmusic.data.mapper.toDomain
import com.prakash.pmusic.data.mapper.toEntity
import com.prakash.pmusic.data.mapper.toFolderType
import com.prakash.pmusic.data.scanner.NonMusicFolderDetector
import com.prakash.pmusic.domain.model.DetectedFolder
import com.prakash.pmusic.domain.model.FolderRulesMatcher
import com.prakash.pmusic.domain.model.LibraryFolder
import com.prakash.pmusic.domain.model.LibraryFolderType
import com.prakash.pmusic.domain.repository.LibraryFolderRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed implementation of [LibraryFolderRepository].
 *
 * Rule changes persist immediately; turning a folder into an exclusion (or
 * removing an inclusion) purges its songs from the database right away, so
 * excluded content disappears before the next scan runs.
 */
@Singleton
class LibraryFolderRepositoryImpl @Inject constructor(
    private val folderDao: LibraryFolderDao,
    private val songDao: SongDao,
    private val detector: NonMusicFolderDetector
) : LibraryFolderRepository {

    override fun observeFolders(): Flow<List<LibraryFolder>> =
        folderDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun addFolder(
        folderPath: String,
        displayName: String,
        type: LibraryFolderType
    ): LibraryFolder? = withContext(Dispatchers.IO) {
        val normalized = FolderRulesMatcher.normalize(folderPath)
        if (normalized.isEmpty()) return@withContext null
        if (folderDao.getByPath(normalized) != null) return@withContext null
        val entity = com.prakash.pmusic.core.database.entity.LibraryFolderEntity(
            id = 0,
            folderPath = normalized,
            displayName = displayName.ifBlank { normalized.substringAfterLast('/') },
            type = type.name,
            enabled = true,
            recursive = true,
            lastScanned = null,
            songCount = 0,
            dateAdded = System.currentTimeMillis()
        )
        val id = folderDao.upsert(entity)
        folderDao.getById(id)?.toDomain()
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        val folder = folderDao.getById(id) ?: return
        folderDao.setEnabled(id, enabled)
        val isIncluded = folder.type.toFolderType() == LibraryFolderType.INCLUDED
        // Excluding a folder, or turning an inclusion off, hides its content.
        if (enabled && !isIncluded) purgeFolder(folder.folderPath)
        if (!enabled && isIncluded) purgeFolder(folder.folderPath)
    }

    override suspend fun setType(id: Long, type: LibraryFolderType) {
        val folder = folderDao.getById(id) ?: return
        val wasIncluded = folder.type.toFolderType() == LibraryFolderType.INCLUDED
        folderDao.setType(id, type.name)
        if (wasIncluded && type != LibraryFolderType.INCLUDED) {
            // The folder stopped being an inclusion: its content is hidden.
            purgeFolder(folder.folderPath)
        }
    }

    override suspend fun removeFolder(id: Long) {
        val folder = folderDao.getById(id) ?: return
        val wasIncluded = folder.type.toFolderType() == LibraryFolderType.INCLUDED
        folderDao.deleteById(id)
        if (wasIncluded) purgeFolder(folder.folderPath)
    }

    override suspend fun hasFolders(): Boolean = folderDao.count() > 0

    private suspend fun purgeFolder(folderPath: String): Int {
        val normalized = FolderRulesMatcher.normalize(folderPath)
        if (normalized.isEmpty()) return 0
        val matches = songDao.getAllSongPaths()
            .filter { FolderRulesMatcher.isUnder(it.path, normalized) }
            .map { it.id }
        matches.chunked(900).forEach { chunk -> songDao.deleteByIds(chunk) }
        return matches.size
    }

    override suspend fun discoverNonMusicFolders(limit: Int): List<DetectedFolder> {
        val configured = folderDao.getAll().map { it.folderPath }
        return detector.discover(limit)
            .filter { detected ->
                configured.none { FolderRulesMatcher.isUnder(detected.folderPath, it) }
            }
    }
}
