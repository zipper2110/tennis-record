package org.litvin.ui.tabs.adjustments

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.WhiteBalanceV1
import kotlin.math.roundToInt

/**
 * Centralized conversion between UI slider integer values and the adjustments data model.
 *
 * Every slider runs [-100..+100] with 0 as the identity:
 * - Brightness slider: [-100..+100] -> model.brightness [0.0..2.0] via ((v/100)+1)
 * - Contrast slider:   [-100..+100] -> model.contrast   [0.0..2.0] via ((v/100)+1)
 * - Saturation slider: [-100..+100] -> model.saturation [0.0..2.0] via ((v/100)+1)
 * - Shadows slider:    [-100..+100] -> model.shadows    [-1.0..+1.0]
 * - Highlights slider: [-100..+100] -> model.highlights [-1.0..+1.0]
 * - Temp slider:       [-100..+100] -> model.whiteBalance.temperature [-1.0..+1.0]
 */
object AdjustmentsUiConverter {

    data class SliderValues(
        val brightness: Int,
        val contrast: Int,
        val saturation: Int,
        val shadows: Int,
        val highlights: Int,
        val temperature: Int,
    )

    val DEFAULTS = AdjustmentsV1()

    fun slidersToModel(
        brightness: Int,
        contrast: Int,
        saturation: Int,
        shadows: Int,
        highlights: Int,
        temperature: Int,
    ): AdjustmentsV1 {
        val b = ((brightness / 100.0f) + 1.0f).coerceIn(0.0f, 2.0f)
        val c = ((contrast / 100.0f) + 1.0f).coerceIn(0.0f, 2.0f)
        val s = ((saturation / 100.0f) + 1.0f).coerceIn(0.0f, 2.0f)
        val sh = (shadows / 100.0f).coerceIn(-1.0f, 1.0f)
        val hl = (highlights / 100.0f).coerceIn(-1.0f, 1.0f)
        val t = (temperature / 100.0f).coerceIn(-1.0f, 1.0f)
        return AdjustmentsV1(
            brightness = b,
            contrast = c,
            saturation = s,
            shadows = sh,
            highlights = hl,
            whiteBalance = WhiteBalanceV1(temperature = t)
        )
    }

    fun modelToSliderValues(adj: AdjustmentsV1): SliderValues {
        val wb = adj.whiteBalance ?: WhiteBalanceV1()
        val b = ((adj.brightness.coerceIn(0.0f, 2.0f) - 1.0f) * 100.0f).roundToInt()
        val c = ((adj.contrast.coerceIn(0.0f, 2.0f) - 1.0f) * 100.0f).roundToInt()
        val s = ((adj.saturation.coerceIn(0.0f, 2.0f) - 1.0f) * 100.0f).roundToInt()
        val sh = (adj.shadows.coerceIn(-1.0f, 1.0f) * 100.0f).roundToInt()
        val hl = (adj.highlights.coerceIn(-1.0f, 1.0f) * 100.0f).roundToInt()
        val t = (wb.temperature.coerceIn(-1.0f, 1.0f) * 100.0f).roundToInt()
        return SliderValues(b, c, s, sh, hl, t)
    }
}
