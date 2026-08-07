package com.prakash.pmusic.data.mapper

import com.prakash.pmusic.core.database.model.PlaylistProjection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies that playlist persistence-to-domain mapping is lossless.
 */
class PlaylistMappersTest {

    @Test
    fun `playlist projection maps to domain playlist`() {
        val projection = PlaylistProjection(
            id = 11L,
            name = "Road Trip",
            createdAt = 1_000L,
            updatedAt = 2_000L,
            songCount = 14
        )

        val playlist = projection.toDomain()

        assertEquals(11L, playlist.id)
        assertEquals("Road Trip", playlist.name)
        assertEquals(1_000L, playlist.createdAt)
        assertEquals(2_000L, playlist.updatedAt)
        assertEquals(14, playlist.songCount)
    }

    @Test
    fun `empty playlist keeps a zero song count`() {
        val projection = PlaylistProjection(
            id = 3L,
            name = "Empty",
            createdAt = 0L,
            updatedAt = 0L,
            songCount = 0
        )

        assertEquals(0, projection.toDomain().songCount)
    }
}
