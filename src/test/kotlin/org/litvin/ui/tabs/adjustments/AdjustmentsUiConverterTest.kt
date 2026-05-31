package org.litvin.ui.tabs.adjustments

import org.litvin.adjustments.AdjustmentsV1
import kotlin.test.Test
import kotlin.test.assertEquals

class AdjustmentsUiConverterTest {
    @Test
    fun zeroSlidersMapToModelIdentityValues() {
        val model = AdjustmentsUiConverter.slidersToModel(
            brightness = 0,
            contrast = 0,
            saturation = 0,
            temperature = 0,
            tint = 0
        )

        assertEquals(1.0f, model.brightness)
        assertEquals(1.0f, model.contrast)
        assertEquals(1.0f, model.saturation)
        assertEquals(0.0f, model.whiteBalance?.temperature)
        assertEquals(0.0f, model.whiteBalance?.tint)
    }

    @Test
    fun modelDefaultsMapToZeroSliders() {
        val sliders = AdjustmentsUiConverter.modelToSliderValues(AdjustmentsV1())

        assertEquals(0, sliders.brightness)
        assertEquals(0, sliders.contrast)
        assertEquals(0, sliders.saturation)
        assertEquals(0, sliders.temperature)
        assertEquals(0, sliders.tint)
    }

    @Test
    fun contrastSliderDoesNotChangeModelBrightness() {
        val model = AdjustmentsUiConverter.slidersToModel(
            brightness = 0,
            contrast = 100,
            saturation = 0,
            temperature = 0,
            tint = 0
        )

        assertEquals(1.0f, model.brightness)
        assertEquals(2.0f, model.contrast)
    }
}
