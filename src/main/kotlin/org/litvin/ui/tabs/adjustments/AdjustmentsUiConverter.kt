package org.litvin.ui.tabs.adjustments

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.WhiteBalanceV1

/**
 * Centralized conversion between UI slider integer values and the adjustments data model.
 *
 * This mirrors the existing mapping used in SwingAdjustmentsPanel:
 * - Brightness slider: [-100..+100] -> model.brightness [0.0..2.0] via ((v/100)+1)
 * - Contrast slider:   [-100..+100] -> model.contrast   [0.0..2.0] via ((v/100)+1)
 * - Saturation slider: [-100..+100] -> model.saturation [0.0..2.0] via ((v/100)+1)
 * - Temp slider:       [-100..+100] -> model.whiteBalance.temperature [-1.0..+1.0]
 * - Tint slider:       [-100..+100] -> model.whiteBalance.tint        [-1.0..+1.0]
 */
object AdjustmentsUiConverter {

    data class SliderValues(
        val brightness: Int,
        val contrast: Int,
        val saturation: Int,
        val temperature: Int,
        val tint: Int,
    )

    val DEFAULTS = AdjustmentsV1()

    fun slidersToModel(
        brightness: Int,
        contrast: Int,
        saturation: Int,
        temperature: Int,
        tint: Int,
    ): AdjustmentsV1 {
        val b = ((brightness / 100.0f) + 1.0f).coerceIn(0.0f, 2.0f)
        val c = ((contrast / 100.0f) + 1.0f).coerceIn(0.0f, 2.0f)
        val s = ((saturation / 100.0f) + 1.0f).coerceIn(0.0f, 2.0f)
        val t = (temperature / 100.0f).coerceIn(-1.0f, 1.0f)
        val ti = (tint / 100.0f).coerceIn(-1.0f, 1.0f)
        return AdjustmentsV1(
            brightness = b,
            contrast = c,
            saturation = s,
            whiteBalance = WhiteBalanceV1(temperature = t, tint = ti)
        )
    }

    fun modelToSliderValues(adj: AdjustmentsV1): SliderValues {
        val wb = adj.whiteBalance ?: WhiteBalanceV1()
        val b = ((adj.brightness.coerceIn(0.0f, 2.0f) - 1.0f) * 100.0f).toInt()
        val c = ((adj.contrast.coerceIn(0.0f, 2.0f) - 1.0f) * 100.0f).toInt()
        val s = ((adj.saturation.coerceIn(0.0f, 2.0f) - 1.0f) * 100.0f).toInt()
        val t = (wb.temperature.coerceIn(-1.0f, 1.0f) * 100.0f).toInt()
        val ti = (wb.tint.coerceIn(-1.0f, 1.0f) * 100.0f).toInt()
        return SliderValues(b, c, s, t, ti)
    }
}
