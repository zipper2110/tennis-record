package org.litvin

import org.litvin.adjustments.AdjustmentsV1

data class FfmpegColorAdjustments(
    val brightness: Double,
    val contrast: Double,
    val saturation: Double,
    val gamma: Double,
    val hueDegrees: Double,
) {
    val hasEqualizerAdjustments: Boolean
        get() =
            kotlin.math.abs(brightness) >= EPSILON ||
                kotlin.math.abs(contrast - 1.0) >= EPSILON ||
                kotlin.math.abs(saturation - 1.0) >= EPSILON ||
                kotlin.math.abs(gamma - 1.0) >= EPSILON

    val hasHueAdjustments: Boolean
        get() = kotlin.math.abs(hueDegrees) >= EPSILON

    private companion object {
        const val EPSILON = 1e-6
    }
}

object FfmpegColorAdjustmentStrategy {
    fun map(adjustments: AdjustmentsV1): FfmpegColorAdjustments {
        val whiteBalance = adjustments.whiteBalance
        val temperature = (whiteBalance?.temperature ?: 0.0f).coerceIn(-1.0f, 1.0f)
        val tint = (whiteBalance?.tint ?: 0.0f).coerceIn(-1.0f, 1.0f)
        val saturation = (adjustments.saturation + (temperature * 0.05f)).coerceIn(0.0f, 3.0f).toDouble()

        return FfmpegColorAdjustments(
            brightness = ((adjustments.brightness - 1.0f) * 0.39f).coerceIn(-1.0f, 1.0f).toDouble(),
            contrast = mapContrast(adjustments.contrast),
            saturation = if (saturation <= 1.0) saturation else 1.0 + ((saturation - 1.0) * 0.5),
            gamma = (1.0 + (tint.toDouble() * 0.2)).coerceIn(0.1, 10.0),
            hueDegrees = (temperature.toDouble() * 180.0) + (tint.toDouble() * 12.0),
        )
    }

    private fun mapContrast(modelContrast: Float): Double {
        val contrastSlider = ((modelContrast.coerceIn(0.0f, 2.0f) - 1.0f) * 100.0f).toDouble()
        val mappedSlider = when {
            contrastSlider >= 0.0 -> contrastSlider
            contrastSlider >= -50.0 -> contrastSlider
            else -> -50.0 + ((contrastSlider + 50.0) * 0.6)
        }
        return (1.0 + (mappedSlider / 100.0)).coerceIn(0.0, 3.0)
    }
}
