package com.prakash.pmusic.core.media

import androidx.media3.common.Player
import com.prakash.pmusic.domain.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the domain <-> Media3 mapping helpers.
 *
 * Only the pure (framework-free) parts are unit tested; MediaItem creation
 * depends on android.net.Uri and is exercised on-device.
 */
class PlaybackMappersTest {

    @Test
    fun `player repeat mode ints map to domain enums`() {
        assertEquals(RepeatMode.OFF, Player.REPEAT_MODE_OFF.toDomainRepeatMode())
        assertEquals(RepeatMode.ONE, Player.REPEAT_MODE_ONE.toDomainRepeatMode())
        assertEquals(RepeatMode.ALL, Player.REPEAT_MODE_ALL.toDomainRepeatMode())
        assertEquals(RepeatMode.OFF, 99.toDomainRepeatMode())
    }

    @Test
    fun `domain enums map back to player repeat mode ints`() {
        assertEquals(Player.REPEAT_MODE_OFF, RepeatMode.OFF.toPlayerRepeatMode())
        assertEquals(Player.REPEAT_MODE_ONE, RepeatMode.ONE.toPlayerRepeatMode())
        assertEquals(Player.REPEAT_MODE_ALL, RepeatMode.ALL.toPlayerRepeatMode())
    }

    @Test
    fun `repeat mode round trip is lossless`() {
        RepeatMode.entries.forEach { mode ->
            assertEquals(mode, mode.toPlayerRepeatMode().toDomainRepeatMode())
        }
    }
}
