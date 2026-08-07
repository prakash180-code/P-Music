package com.prakash.pmusic.features.widgets

import com.prakash.pmusic.features.widgets.WidgetFormat.asPlaybackTime
import com.prakash.pmusic.features.widgets.WidgetFormat.formatPosition
import com.prakash.pmusic.features.widgets.WidgetFormat.playPauseIcon
import com.prakash.pmusic.features.widgets.WidgetFormat.progressFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WidgetFormatTest {

    @Test
    fun asPlaybackTime_formatsMinutesAndSeconds() {
        assertEquals("0:00", 0L.asPlaybackTime())
        assertEquals("0:05", 5_000L.asPlaybackTime())
        assertEquals("1:05", 65_000L.asPlaybackTime())
        assertEquals("10:00", 600_000L.asPlaybackTime())
        assertEquals("59:59", 3_599_000L.asPlaybackTime())
    }

    @Test
    fun asPlaybackTime_addsHoursWhenNeeded() {
        assertEquals("1:00:00", 3_600_000L.asPlaybackTime())
        assertEquals("2:03:07", 7_387_000L.asPlaybackTime())
    }

    @Test
    fun asPlaybackTime_clampsNegativeToZero() {
        assertEquals("0:00", (-1L).asPlaybackTime())
    }

    @Test
    fun formatPosition_unknownDurationShowsPositionOnly() {
        assertEquals("1:05", formatPosition(65_000L, 0L))
        assertEquals("1:05", formatPosition(65_000L, -1L))
    }

    @Test
    fun formatPosition_knownDurationShowsBoth() {
        assertEquals("1:05 / 3:30", formatPosition(65_000L, 210_000L))
    }

    @Test
    fun progressFraction_scalesToThousandths() {
        assertEquals(0, progressFraction(0L, 100_000L))
        assertEquals(500, progressFraction(50_000L, 100_000L))
        assertEquals(1000, progressFraction(100_000L, 100_000L))
    }

    @Test
    fun progressFraction_clampsOutOfRange() {
        assertEquals(1000, progressFraction(200_000L, 100_000L))
        assertEquals(0, progressFraction(-10L, 100_000L))
        assertEquals(0, progressFraction(50_000L, 0L))
    }

    @Test
    fun playPauseIcon_switchesWithState() {
        assertNotEquals(playPauseIcon(true), playPauseIcon(false))
    }
}
