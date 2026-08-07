package com.prakash.pmusic.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the folder-rule matcher: recursive containment, exclude-always-wins,
 * restricted (include-only) mode, normalization and case-insensitivity.
 */
class FolderRulesTest {

    // --- isUnder ---

    @Test
    fun `file inside folder matches`() {
        assertTrue(FolderRulesMatcher.isUnder("/storage/emulated/0/Music/Tamil/a.mp3", "/storage/emulated/0/Music/Tamil"))
    }

    @Test
    fun `file in sub folder matches recursively`() {
        assertTrue(
            FolderRulesMatcher.isUnder(
                "/storage/emulated/0/Music/Tamil/Deep/a.mp3",
                "/storage/emulated/0/Music/Tamil"
            )
        )
    }

    @Test
    fun `file outside folder does not match`() {
        assertFalse(
            FolderRulesMatcher.isUnder(
                "/storage/emulated/0/Music/English/a.mp3",
                "/storage/emulated/0/Music/Tamil"
            )
        )
    }

    @Test
    fun `sibling with shared prefix does not match`() {
        assertFalse(
            FolderRulesMatcher.isUnder("/storage/emulated/0/Recordings2/a.mp3", "/storage/emulated/0/Recordings")
        )
    }

    @Test
    fun `matching is case insensitive`() {
        assertTrue(
            FolderRulesMatcher.isUnder("/storage/emulated/0/Music/tamil/a.mp3", "/storage/emulated/0/music/TAMIL")
        )
    }

    @Test
    fun `trailing slash is normalized`() {
        assertTrue(FolderRulesMatcher.isUnder("/storage/emulated/0/Music/a.mp3", "/storage/emulated/0/Music/"))
    }

    // --- isAllowed with no rules ---

    @Test
    fun `empty rules allow everything`() {
        assertTrue(FolderRulesMatcher.isAllowed("/storage/emulated/0/Music/a.mp3", FolderRules.EMPTY))
    }

    // --- excluded always wins ---

    @Test
    fun `excluded folder hides its files`() {
        val rules = FolderRules(excluded = listOf("/storage/emulated/0/Recordings"))
        assertFalse(FolderRulesMatcher.isAllowed("/storage/emulated/0/Recordings/Calls/c1.m4a", rules))
    }

    @Test
    fun `excluded folder still hides files under included folders`() {
        val rules = FolderRules(
            included = listOf("/storage/emulated/0/Music"),
            excluded = listOf("/storage/emulated/0/Music/Recordings")
        )
        assertFalse(FolderRulesMatcher.isAllowed("/storage/emulated/0/Music/Recordings/c1.m4a", rules))
        assertTrue(FolderRulesMatcher.isAllowed("/storage/emulated/0/Music/Songs/s1.mp3", rules))
    }

    @Test
    fun `unrelated folders are allowed when unrestricted`() {
        val rules = FolderRules(excluded = listOf("/storage/emulated/0/Recordings"))
        assertTrue(FolderRulesMatcher.isAllowed("/storage/emulated/0/Music/s1.mp3", rules))
    }

    // --- restricted (include-only) mode ---

    @Test
    fun `restricted mode blocks everything outside included folders`() {
        val rules = FolderRules(included = listOf("/storage/emulated/0/Music"))
        assertFalse(FolderRulesMatcher.isAllowed("/storage/emulated/0/Downloads/d1.mp3", rules))
        assertTrue(FolderRulesMatcher.isAllowed("/storage/emulated/0/Music/d1.mp3", rules))
    }

    @Test
    fun `restricted mode is flagged`() {
        assertFalse(FolderRules.EMPTY.isRestricted)
        assertTrue(FolderRules(included = listOf("/a")).isRestricted)
    }

    // --- normalization ---

    @Test
    fun `backslash paths normalize`() {
        assertEquals("C:/Music", FolderRulesMatcher.normalize("C:\\Music\\"))
        assertTrue(FolderRulesMatcher.isUnder("C:/Music/a.mp3", "C:\\Music"))
    }
}
