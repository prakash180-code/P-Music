package com.prakash.pmusic.data.lyrics

import com.prakash.pmusic.domain.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses timestamped lines`() {
        val lyrics = LrcParser.parse("[00:12.00]First line\n[00:15.50]Second line\n")
        assertEquals(
            listOf(
                LyricLine(12_000L, "First line"),
                LyricLine(15_500L, "Second line")
            ),
            lyrics.lines
        )
    }

    @Test
    fun `parses multiple timestamps per line`() {
        val lyrics = LrcParser.parse("[00:12.00][00:24.00]Hello\n")
        assertEquals(
            listOf(LyricLine(12_000L, "Hello"), LyricLine(24_000L, "Hello")),
            lyrics.lines
        )
    }

    @Test
    fun `applies offset to timestamps`() {
        val lyrics = LrcParser.parse("[offset:500]\n[00:10.00]Line\n")
        assertEquals(listOf(LyricLine(10_500L, "Line")), lyrics.lines)
    }

    @Test
    fun `offset never produces negative timestamps`() {
        val lyrics = LrcParser.parse("[offset:-1000]\n[00:00.50]Line\n")
        assertEquals(listOf(LyricLine(0L, "Line")), lyrics.lines)
    }

    @Test
    fun `keeps unsynced lines with null timestamps`() {
        val lyrics = LrcParser.parse("Plain first\n[00:05.00]Timed\nPlain last\n")
        assertEquals(
            listOf(
                LyricLine(null, "Plain first"),
                LyricLine(5_000L, "Timed"),
                LyricLine(null, "Plain last")
            ),
            lyrics.lines
        )
    }

    @Test
    fun `skips metadata and empty lines`() {
        val lyrics = LrcParser.parse("[ti:Title]\n[ar:Artist]\n\n[00:01.00]Go\n")
        assertEquals(listOf(LyricLine(1_000L, "Go")), lyrics.lines)
    }

    @Test
    fun `parses minute and fraction formats`() {
        val lyrics = LrcParser.parse("[01:02]Whole\n[00:00.5]Half\n")
        assertEquals(
            listOf(LyricLine(62_000L, "Whole"), LyricLine(500L, "Half")),
            lyrics.lines
        )
    }

    @Test
    fun `empty input yields no lines`() {
        assertEquals(0, LrcParser.parse("").lines.size)
    }

    @Test
    fun `handles crlf line endings`() {
        val lyrics = LrcParser.parse("[00:01.00]One\r\n[00:02.00]Two\r\n")
        assertEquals(
            listOf(LyricLine(1_000L, "One"), LyricLine(2_000L, "Two")),
            lyrics.lines
        )
    }
}
