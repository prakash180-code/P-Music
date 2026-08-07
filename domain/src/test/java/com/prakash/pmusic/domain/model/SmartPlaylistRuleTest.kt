package com.prakash.pmusic.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the smart-playlist rule codec: every rule round-trips through its
 * encoded string, labels render as expected, and malformed or foreign strings
 * parse to null instead of crashing.
 */
class SmartPlaylistRuleTest {

    @Test
    fun `favorites rule round-trips`() {
        val rule = SmartPlaylistRule.Favorites
        assertEquals(SmartPlaylistRule.Favorites, SmartPlaylistRule.parse(rule.encode))
    }

    @Test
    fun `most played rule round-trips`() {
        val rule = SmartPlaylistRule.MostPlayed
        assertEquals(SmartPlaylistRule.MostPlayed, SmartPlaylistRule.parse(rule.encode))
    }

    @Test
    fun `recently added rule round-trips`() {
        val rule = SmartPlaylistRule.RecentlyAdded
        assertEquals(SmartPlaylistRule.RecentlyAdded, SmartPlaylistRule.parse(rule.encode))
    }

    @Test
    fun `recently played rule round-trips`() {
        val rule = SmartPlaylistRule.RecentlyPlayed
        assertEquals(SmartPlaylistRule.RecentlyPlayed, SmartPlaylistRule.parse(rule.encode))
    }

    @Test
    fun `never played rule round-trips`() {
        val rule = SmartPlaylistRule.NeverPlayed
        assertEquals(SmartPlaylistRule.NeverPlayed, SmartPlaylistRule.parse(rule.encode))
    }

    @Test
    fun `genre rule round-trips with colons inside the name`() {
        val rule = SmartPlaylistRule.Genre("Electronic: Downtempo")
        val parsed = SmartPlaylistRule.parse(rule.encode)
        assertTrue(parsed is SmartPlaylistRule.Genre)
        assertEquals("Electronic: Downtempo", (parsed as SmartPlaylistRule.Genre).name)
    }

    @Test
    fun `artist rule round-trips including id and name`() {
        val rule = SmartPlaylistRule.Artist(artistId = 42L, artistName = "AC/DC")
        val parsed = SmartPlaylistRule.parse(rule.encode)
        assertTrue(parsed is SmartPlaylistRule.Artist)
        assertEquals(42L, (parsed as SmartPlaylistRule.Artist).artistId)
        assertEquals("AC/DC", parsed.artistName)
    }

    @Test
    fun `artist rule survives a colon inside the artist name`() {
        val rule = SmartPlaylistRule.Artist(artistId = 7L, artistName = "Outer: Space")
        val parsed = SmartPlaylistRule.parse(rule.encode)
        assertEquals("Outer: Space", (parsed as SmartPlaylistRule.Artist).artistName)
        assertEquals(7L, parsed.artistId)
    }

    @Test
    fun `unknown strings parse to null`() {
        assertNull(SmartPlaylistRule.parse("favorites_extra"))
        assertNull(SmartPlaylistRule.parse(""))
        assertNull(SmartPlaylistRule.parse("random"))
    }

    @Test
    fun `artist rule with a non-numeric id parses to null`() {
        assertNull(SmartPlaylistRule.parse("artist:abc:Someone"))
    }

    @Test
    fun `artist rule with a blank name parses to null`() {
        assertNull(SmartPlaylistRule.parse("artist:5:"))
    }

    @Test
    fun `labels describe the rule`() {
        assertEquals("Favorites", SmartPlaylistRule.Favorites.label)
        assertEquals("Most played", SmartPlaylistRule.MostPlayed.label)
        assertEquals("Genre · Rock", SmartPlaylistRule.Genre("Rock").label)
        assertEquals("Artist · AC/DC", SmartPlaylistRule.Artist(1L, "AC/DC").label)
    }
}
