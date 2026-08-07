package com.prakash.pmusic.data.mapper

import com.prakash.pmusic.core.database.entity.LibraryFolderEntity
import com.prakash.pmusic.domain.model.LibraryFolder
import com.prakash.pmusic.domain.model.LibraryFolderType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the Room ↔ domain mapping for library folders, including the
 * defensive fallback for unknown type strings in legacy rows.
 */
class LibraryFolderMappersTest {

    private val entity = LibraryFolderEntity(
        id = 7,
        folderPath = "/storage/emulated/0/Music/Tamil",
        displayName = "Tamil",
        type = "INCLUDED",
        enabled = true,
        recursive = true,
        lastScanned = 1_700_000_000_000L,
        songCount = 42,
        dateAdded = 1_600_000_000_000L
    )

    @Test
    fun `entity maps to domain`() {
        val domain = entity.toDomain()
        assertEquals(7L, domain.id)
        assertEquals("/storage/emulated/0/Music/Tamil", domain.folderPath)
        assertEquals("Tamil", domain.displayName)
        assertEquals(LibraryFolderType.INCLUDED, domain.type)
        assertEquals(true, domain.enabled)
        assertEquals(true, domain.recursive)
        assertEquals(1_700_000_000_000L, domain.lastScanned)
        assertEquals(42, domain.songCount)
        assertEquals(1_600_000_000_000L, domain.dateAdded)
    }

    @Test
    fun `domain maps to entity`() {
        val domain = LibraryFolder(
            id = 7,
            folderPath = "/storage/emulated/0/Music/Tamil",
            displayName = "Tamil",
            type = LibraryFolderType.EXCLUDED,
            enabled = false,
            recursive = true,
            lastScanned = null,
            songCount = 0,
            dateAdded = 1_600_000_000_000L
        )
        val mapped = domain.toEntity()
        assertEquals(7L, mapped.id)
        assertEquals("EXCLUDED", mapped.type)
        assertEquals(false, mapped.enabled)
        assertEquals(null, mapped.lastScanned)
    }

    @Test
    fun `round trip preserves values`() {
        assertEquals(entity, entity.toDomain().toEntity())
    }

    @Test
    fun `unknown type string falls back to included`() {
        assertEquals(
            LibraryFolderType.INCLUDED,
            LibraryFolderEntity(
                id = 1,
                folderPath = "/x",
                displayName = "x",
                type = "SOMEDAY",
                enabled = true,
                recursive = true,
                lastScanned = null,
                songCount = 0,
                dateAdded = 0L
            ).toDomain().type
        )
    }
}
