package org.litvin.media

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.ui.tabs.adjustments.AdjustmentsUiConverter
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class CalibratedVlcColorPreviewAdjustmentStrategyTest {
    private val strategy = CalibratedVlcColorPreviewAdjustmentStrategy

    @Test
    fun identityMapsToVlcIdentity() {
        val mapped = strategy.map(AdjustmentsV1())

        assertClose(1.0f, mapped.brightness)
        assertClose(1.0f, mapped.contrast)
        assertClose(1.0f, mapped.saturation)
        assertClose(0.0f, mapped.hue)
        assertClose(1.0f, mapped.gamma)
    }

    @Test
    fun positiveContrastUsesCalibratedVlcMix() {
        val mapped = strategy.map(model(contrast = 100))

        assertClose(0.60f, mapped.brightness)
        assertClose(1.50f, mapped.contrast)
        assertClose(0.70f, mapped.saturation)
    }

    @Test
    fun negativeContrastUsesCalibratedVlcMix() {
        val mapped = strategy.map(model(contrast = -50))

        assertClose(1.26f, mapped.brightness)
        assertClose(0.65f, mapped.contrast)
        assertClose(1.81f, mapped.saturation)
    }

    @Test
    fun directBrightnessAndSaturationComposeWithContrastCalibration() {
        val mapped = strategy.map(model(brightness = 20, contrast = 100, saturation = 10))

        assertClose(0.80f, mapped.brightness)
        assertClose(1.50f, mapped.contrast)
        assertClose(0.80f, mapped.saturation)
    }

    @Test
    fun negativeContrastCanUseVlcSaturationRangeAboveTwo() {
        val mapped = strategy.map(model(contrast = -100))

        assertClose(1.52f, mapped.brightness)
        assertClose(0.3f, mapped.contrast)
        assertClose(2.62f, mapped.saturation)
    }

    @Test
    fun whiteBalanceTemperatureMapsToVlcHueDegreesAndSaturationNudge() {
        val warm = strategy.map(model(temperature = 50))
        val cool = strategy.map(model(temperature = -50))

        assertClose(90.0f, warm.hue)
        assertClose(1.025f, warm.saturation)
        assertClose(-90.0f, cool.hue)
        assertClose(0.975f, cool.saturation)
    }

    @Test
    fun whiteBalanceTintMapsToGammaAndSmallHueOffset() {
        val magenta = strategy.map(model(tint = 50))
        val green = strategy.map(model(tint = -50))

        assertClose(6.0f, magenta.hue)
        assertClose(1.1f, magenta.gamma)
        assertClose(-6.0f, green.hue)
        assertClose(0.9f, green.gamma)
    }

    private fun model(
        brightness: Int = 0,
        contrast: Int = 0,
        saturation: Int = 0,
        temperature: Int = 0,
        tint: Int = 0,
    ): AdjustmentsV1 {
        return AdjustmentsUiConverter.slidersToModel(
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            temperature = temperature,
            tint = tint,
        )
    }

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 0.0001f, "Expected $expected, was $actual")
    }
}
