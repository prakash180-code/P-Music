package com.prakash.pmusic.data.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the non-music folder heuristics: well-known names win on their own,
 * short-clip folders only count above the minimum song count, and normal song
 * folders are never flagged.
 */
class NonMusicFolderClassifierTest {

    @Test
    fun `call recordings folder is detected by name`() {
        assertTrue(NonMusicFolderClassifier.isNonMusic("Call Recordings", 1, 3_000L))
    }

    @Test
    fun `whatsapp audio folder is detected by name`() {
        assertTrue(NonMusicFolderClassifier.isNonMusic("WhatsApp Audio", 50, 60_000L))
    }

    @Test
    fun `recorder name is detected case-insensitively`() {
        assertTrue(NonMusicFolderClassifier.isNonMusic("VOICE_RECORDER", 2, 45_000L))
    }

    @Test
    fun `underscores are treated as spaces`() {
        assertTrue(NonMusicFolderClassifier.isNonMusic("Voice_Notes", 1, 10_000L))
    }

    @Test
    fun `short clips flag a folder above the minimum count`() {
        assertTrue(NonMusicFolderClassifier.isNonMusic("Misc", 10, 12_000L))
    }

    @Test
    fun `single short file is not flagged`() {
        assertFalse(NonMusicFolderClassifier.isNonMusic("Misc", 1, 12_000L))
    }

    @Test
    fun `long recordings with unknown name are not flagged`() {
        assertFalse(NonMusicFolderClassifier.isNonMusic("Backup", 40, 300_000L))
    }

    @Test
    fun `normal song folder is not flagged`() {
        assertFalse(NonMusicFolderClassifier.isNonMusic("Tamil Hits", 120, 240_000L))
    }

    @Test
    fun `name matching does not flag unrelated words`() {
        assertFalse(NonMusicFolderClassifier.isNonMusic("Harmony", 5, 180_000L))
    }

    @Test
    fun `records name is detected`() {
        assertTrue(NonMusicFolderClassifier.isNonMusic("Recorder records", 4, 480_000L))
    }

    @Test
    fun `one minute is song shaped`() {
        assertTrue(NonMusicFolderClassifier.isSongLike(60_000L))
    }

    @Test
    fun `nine minutes is song shaped`() {
        assertTrue(NonMusicFolderClassifier.isSongLike(540_000L))
    }

    @Test
    fun `short clip is not song shaped`() {
        assertFalse(NonMusicFolderClassifier.isSongLike(30_000L))
    }

    @Test
    fun `ten minute recording is not song shaped`() {
        assertFalse(NonMusicFolderClassifier.isSongLike(600_000L))
    }

    @Test
    fun `empty folder is not a music container`() {
        assertFalse(NonMusicFolderClassifier.isMusicContainer(0))
    }

    @Test
    fun `few song shaped files are not a music container`() {
        assertFalse(NonMusicFolderClassifier.isMusicContainer(19))
    }

    @Test
    fun `library sized song count is a music container`() {
        assertTrue(NonMusicFolderClassifier.isMusicContainer(500))
    }

    @Test
    fun `boundary song count is a music container`() {
        assertTrue(NonMusicFolderClassifier.isMusicContainer(20))
    }
}
