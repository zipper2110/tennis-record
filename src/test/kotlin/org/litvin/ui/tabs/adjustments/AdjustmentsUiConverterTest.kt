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
            shadows = 0,
            highlights = 0,
            temperature = 0
        )

        assertEquals(1.0f, model.brightness)
        assertEquals(1.0f, model.contrast)
        assertEquals(1.0f, model.saturation)
        assertEquals(0.0f, model.shadows)
        assertEquals(0.0f, model.highlights)
        assertEquals(0.0f, model.whiteBalance?.temperature)
    }

    @Test
    fun modelDefaultsMapToZeroSliders() {
        val sliders = AdjustmentsUiConverter.modelToSliderValues(AdjustmentsV1())

        assertEquals(0, sliders.brightness)
        assertEquals(0, sliders.contrast)
        assertEquals(0, sliders.saturation)
        assertEquals(0, sliders.shadows)
        assertEquals(0, sliders.highlights)
        assertEquals(0, sliders.temperature)
    }

    @Test
    fun contrastSliderDoesNotChangeModelBrightness() {
        val model = AdjustmentsUiConverter.slidersToModel(
            brightness = 0,
            contrast = 100,
            saturation = 0,
            shadows = 0,
            highlights = 0,
            temperature = 0
        )

        assertEquals(1.0f, model.brightness)
        assertEquals(2.0f, model.contrast)
    }

    @Test
    fun `every integer slider value survives a model round trip`() {
        for (value in -100..100) {
            val model = AdjustmentsUiConverter.slidersToModel(
                brightness = value,
                contrast = value,
                saturation = value,
                shadows = value,
                highlights = value,
                temperature = value,
            )

            val sliders = AdjustmentsUiConverter.modelToSliderValues(model)

            assertEquals(value, sliders.brightness, "brightness at $value")
            assertEquals(value, sliders.contrast, "contrast at $value")
            assertEquals(value, sliders.saturation, "saturation at $value")
            assertEquals(value, sliders.shadows, "shadows at $value")
            assertEquals(value, sliders.highlights, "highlights at $value")
            assertEquals(value, sliders.temperature, "temperature at $value")
        }
    }
}
