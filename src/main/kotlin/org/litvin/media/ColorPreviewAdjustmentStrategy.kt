package org.litvin.media

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.WhiteBalanceV1
import org.litvin.ui.tabs.adjustments.AdjustmentsUiConverter

data class VlcPreviewColorAdjustments(
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val hue: Float,
    val gamma: Float,
)

interface ColorPreviewAdjustmentStrategy {
    fun map(adjustments: AdjustmentsV1): VlcPreviewColorAdjustments
}

object CalibratedVlcColorPreviewAdjustmentStrategy : ColorPreviewAdjustmentStrategy {
    override fun map(adjustments: AdjustmentsV1): VlcPreviewColorAdjustments {
        val sliders = AdjustmentsUiConverter.modelToSliderValues(adjustments)
        val contrast = sliders.contrast.toFloat()

        val wb = adjustments.whiteBalance ?: WhiteBalanceV1()
        val temperature = wb.temperature.coerceIn(-1.0f, 1.0f)
        val tint = wb.tint.coerceIn(-1.0f, 1.0f)
        return VlcPreviewColorAdjustments(
            brightness = sliderToVlcMultiplier(sliders.brightness + contrastBrightnessOffset(contrast), max = 2.0f),
            contrast = sliderToVlcMultiplier(contrastContrastOffset(contrast), max = 2.0f),
            saturation = sliderToVlcMultiplier(
                sliders.saturation + contrastSaturationOffset(contrast) + (temperature * 5.0f),
                max = 3.0f
            ),
            hue = (temperature * 180.0f) + (tint * 12.0f),
            gamma = (1.0f + (tint * 0.2f)).coerceIn(0.2f, 3.0f),
        )
    }

    private fun contrastBrightnessOffset(contrast: Float): Float {
        return if (contrast >= 0f) {
            -0.40f * contrast
        } else {
            -0.52f * contrast
        }
    }

    private fun contrastContrastOffset(contrast: Float): Float {
        return if (contrast >= 0f) {
            0.50f * contrast
        } else {
            0.7f * contrast
        }
    }

    private fun contrastSaturationOffset(contrast: Float): Float {
        return if (contrast >= 0f) {
            -0.30f * contrast
        } else {
            -1.62f * contrast
        }
    }

    private fun sliderToVlcMultiplier(value: Float, max: Float): Float {
        val minSlider = -100.0f
        val maxSlider = (max - 1.0f) * 100.0f
        return ((value.coerceIn(minSlider, maxSlider) / 100.0f) + 1.0f).coerceIn(0.0f, max)
    }

}
