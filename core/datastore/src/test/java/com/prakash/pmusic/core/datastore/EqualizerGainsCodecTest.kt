package com.prakash.pmusic.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerGainsCodecTest {

    @Test
    fun `encode joins gains with commas`() {
        assertEquals("0,300,-150", EqualizerGainsCodec.encode(listOf(0, 300, -150)))
    }

    @Test
    fun `encode empty list`() {
        assertEquals("", EqualizerGainsCodec.encode(emptyList()))
    }

    @Test
    fun `decode round-trips encoded gains`() {
        val gains = listOf(-1200, 0, 800, 1500)
        assertEquals(gains, EqualizerGainsCodec.decode(EqualizerGainsCodec.encode(gains)))
    }

    @Test
    fun `decode tolerates whitespace`() {
        assertEquals(listOf(1, 2, 3), EqualizerGainsCodec.decode("1, 2, 3"))
    }

    @Test
    fun `decode drops malformed entries`() {
        assertEquals(listOf(100, 200), EqualizerGainsCodec.decode("100,abc,200,,x"))
    }

    @Test
    fun `decode empty string`() {
        assertTrue(EqualizerGainsCodec.decode("").isEmpty())
    }

    @Test
    fun `decode missing raw value`() {
        assertTrue(EqualizerGainsCodec.decode(" ").isEmpty())
    }
}
