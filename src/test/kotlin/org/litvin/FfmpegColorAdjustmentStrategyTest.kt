package org.litvin

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.WhiteBalanceV1
import kotlin.test.Test
import kotlin.test.assertEquals

class FfmpegColorAdjustmentStrategyTest {
    @Test
    fun `maps brightness and contrast for the export debug values`() {
        val values = FfmpegColorAdjustmentStrategy.map(
            AdjustmentsV1(
                brightness = 1.3f,
                contrast = 0.4f,
                saturation = 1.0f,
                whiteBalance = WhiteBalanceV1(),
            )
        )

        assertEquals(0.117, values.brightness, 0.0001)
        assertEquals(0.44, values.contrast, 0.0001)
    }
}
