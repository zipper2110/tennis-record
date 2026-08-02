package org.litvin

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SessionSettingsTest {
    @Test
    fun playbackSpeedPresetsIncludeOnePointFiveAndDefaultToNormalSpeed() {
        assertContentEquals(
            floatArrayOf(2.0f, 1.5f, 1.25f, 1.0f, 0.5f),
            SessionSettings.speedPresets,
        )
        assertEquals(1.0f, SessionSettings.toRate(SessionSettings.playbackSpeedIndex))
    }
}
