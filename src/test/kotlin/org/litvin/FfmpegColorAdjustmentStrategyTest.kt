package org.litvin

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.WhiteBalanceV1
import org.litvin.ui.tabs.adjustments.AdjustmentsUiConverter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FfmpegColorAdjustmentStrategyTest {

    private fun fromSliders(
        brightness: Int = 0,
        contrast: Int = 0,
        saturation: Int = 0,
        shadows: Int = 0,
        highlights: Int = 0,
        temperature: Int = 0,
    ): FfmpegColorAdjustments = FfmpegColorAdjustmentStrategy.map(
        AdjustmentsUiConverter.slidersToModel(brightness, contrast, saturation, shadows, highlights, temperature)
    )

    @Test
    fun `identity sliders produce identity filter values`() {
        val values = fromSliders()

        assertEquals(0.0, values.brightness, 1e-6)
        assertEquals(1.0, values.contrast, 1e-6)
        assertEquals(1.0, values.saturation, 1e-6)
        assertEquals(0.0, values.hueDegrees, 1e-6)
        assertEquals(0.0, values.shadowsLift, 1e-6)
        assertEquals(0.0, values.highlightsLift, 1e-6)
        assertTrue(!values.hasEqualizerAdjustments && !values.hasHueAdjustments && !values.hasToneAdjustments)
    }

    @Test
    fun `brightness slider spans the full eq brightness range`() {
        assertEquals(-1.0, fromSliders(brightness = -100).brightness, 1e-6)
        assertEquals(-0.5, fromSliders(brightness = -50).brightness, 1e-6)
        assertEquals(0.25, fromSliders(brightness = 25).brightness, 1e-6)
        assertEquals(1.0, fromSliders(brightness = 100).brightness, 1e-6)
    }

    @Test
    fun `contrast slider maps linearly onto eq contrast`() {
        assertEquals(0.0, fromSliders(contrast = -100).contrast, 1e-6)
        assertEquals(0.5, fromSliders(contrast = -50).contrast, 1e-6)
        assertEquals(1.5, fromSliders(contrast = 50).contrast, 1e-6)
        assertEquals(2.0, fromSliders(contrast = 100).contrast, 1e-6)
    }

    @Test
    fun `saturation slider maps linearly onto eq saturation`() {
        assertEquals(0.0, fromSliders(saturation = -100).saturation, 1e-6)
        assertEquals(0.5, fromSliders(saturation = -50).saturation, 1e-6)
        assertEquals(1.5, fromSliders(saturation = 50).saturation, 1e-6)
        assertEquals(2.0, fromSliders(saturation = 100).saturation, 1e-6)
    }

    @Test
    fun `shadows and highlights map onto opposite ends of the luma curve`() {
        val lifted = fromSliders(shadows = 100)
        assertEquals(63.75, lifted.shadowsLift, 1e-6)
        assertEquals(0.0, lifted.highlightsLift, 1e-6)
        assertTrue(lifted.hasToneAdjustments)
        assertTrue(!lifted.hasEqualizerAdjustments, "tone controls must not touch the eq filter")

        val recovered = fromSliders(highlights = -100)
        assertEquals(0.0, recovered.shadowsLift, 1e-6)
        assertEquals(-63.75, recovered.highlightsLift, 1e-6)

        assertEquals(31.875, fromSliders(shadows = 50).shadowsLift, 1e-6)
        assertEquals(-15.9375, fromSliders(highlights = -25).highlightsLift, 1e-6)
    }

    @Test
    fun `the tone curve stays strictly increasing at the slider extremes`() {
        for (shadows in listOf(-100, 0, 100)) {
            for (highlights in listOf(-100, 0, 100)) {
                val values = fromSliders(shadows = shadows, highlights = highlights)
                var previous = toneCurve(values, 0)
                for (code in 1..255) {
                    val current = toneCurve(values, code)
                    assertTrue(
                        current >= previous,
                        "tone curve decreases at code $code for shadows=$shadows highlights=$highlights"
                    )
                    previous = current
                }
            }
        }
    }

    @Test
    fun `white balance drives hue only`() {
        val warm = fromSliders(temperature = 100)
        assertEquals(20.0, warm.hueDegrees, 1e-6)
        assertEquals(1.0, warm.saturation, 1e-6)
        assertEquals(0.0, warm.brightness, 1e-6)
        assertTrue(!warm.hasToneAdjustments)

        assertEquals(-10.0, fromSliders(temperature = -50).hueDegrees, 1e-6)
    }

    @Test
    fun `every slider combination stays inside the ffmpeg eq, hue and lut ranges`() {
        val extremes = listOf(-100, -50, 0, 50, 100)
        for (brightness in extremes) {
            for (contrast in extremes) {
                for (saturation in extremes) {
                    for (shadows in extremes) {
                        for (highlights in extremes) {
                            for (temperature in extremes) {
                                val values =
                                    fromSliders(brightness, contrast, saturation, shadows, highlights, temperature)
                                val at = "b=$brightness c=$contrast s=$saturation sh=$shadows hl=$highlights " +
                                    "t=$temperature"
                                assertTrue(values.brightness in -1.0..1.0, "brightness out of range at $at")
                                assertTrue(values.contrast in 0.0..3.0, "contrast out of range at $at")
                                assertTrue(values.saturation in 0.0..3.0, "saturation out of range at $at")
                                assertTrue(values.hueDegrees in -360.0..360.0, "hue out of range at $at")
                                assertTrue(values.shadowsLift in -255.0..255.0, "shadows out of range at $at")
                                assertTrue(values.highlightsLift in -255.0..255.0, "highlights out of range at $at")
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `out of range model values are clamped instead of leaking into the command`() {
        val values = FfmpegColorAdjustmentStrategy.map(
            AdjustmentsV1(
                brightness = 9.0f,
                contrast = -4.0f,
                saturation = 12.0f,
                shadows = 6.0f,
                highlights = -6.0f,
                whiteBalance = WhiteBalanceV1(temperature = 7.0f),
            )
        )

        assertEquals(1.0, values.brightness, 1e-6)
        assertEquals(0.0, values.contrast, 1e-6)
        assertEquals(3.0, values.saturation, 1e-6)
        assertEquals(63.75, values.shadowsLift, 1e-6)
        assertEquals(-63.75, values.highlightsLift, 1e-6)
        assertEquals(20.0, values.hueDegrees, 1e-6)
    }

    /** The luma curve that the export `lutyuv` expression and the preview shader both evaluate. */
    private fun toneCurve(values: FfmpegColorAdjustments, code: Int): Int {
        val dark = (255.0 - code) / 255.0
        val light = code / 255.0
        val v = code + (values.shadowsLift * dark * dark * dark) + (values.highlightsLift * light * light * light)
        return v.toInt().coerceIn(0, 255)
    }
}
