package org.litvin

import org.litvin.adjustments.AdjustmentsV1

data class FfmpegColorAdjustments(
    val brightness: Double,
    val contrast: Double,
    val saturation: Double,
    val hueDegrees: Double,
    /** Tone curve lift at code value 0, in 8-bit code values. */
    val shadowsLift: Double,
    /** Tone curve lift at code value 255, in 8-bit code values. */
    val highlightsLift: Double,
) {
    val hasEqualizerAdjustments: Boolean
        get() =
            kotlin.math.abs(brightness) >= EPSILON ||
                kotlin.math.abs(contrast - 1.0) >= EPSILON ||
                kotlin.math.abs(saturation - 1.0) >= EPSILON

    val hasHueAdjustments: Boolean
        get() = kotlin.math.abs(hueDegrees) >= EPSILON

    val hasToneAdjustments: Boolean
        get() = kotlin.math.abs(shadowsLift) >= EPSILON || kotlin.math.abs(highlightsLift) >= EPSILON

    private companion object {
        const val EPSILON = 1e-6
    }
}

/**
 * Maps the stored color model to the FFmpeg `eq`, `hue` and `lutyuv` values. The export command
 * (FFmpegCommandBuilder) and the mpv preview shader (MpvShaderParams) both use this, so the
 * preview and the rendered file get the same numbers.
 *
 * Every Color tab slider runs [-100..+100] with 0 as the identity (AdjustmentsUiConverter), which
 * gives model values of [0.0..2.0] for brightness/contrast/saturation and [-1.0..+1.0] for
 * shadows/highlights/white balance. The mapping keeps that symmetry and stays inside the ranges the
 * eq/hue filters accept (and the shader parameters declare):
 *
 * - brightness [0..2] -> eq brightness [-1..+1], as `model - 1`; -100 is black, +100 is white.
 * - contrast   [0..2] -> eq contrast   [0..2],   as `model`;     -100 is flat grey, +100 is double.
 * - saturation [0..2] -> eq saturation [0..2],   as `model`;     -100 is greyscale, +100 is double.
 * - shadows    [-1..+1] -> a luma lift of up to [TONE_SPAN] of the code range at code 0, falling
 *   off as the cube of the distance from white.
 * - highlights [-1..+1] -> the same lift at code 255, falling off towards black.
 * - white balance temperature [-1..+1] -> hue h, up to [TEMPERATURE_HUE_DEGREES] degrees.
 *
 * The shadows/highlights curve is `val + shadowsLift*((255-val)/255)^3 + highlightsLift*(val/255)^3`
 * on the luma plane. Both lifts stay at or below a quarter of the code range, which keeps the curve
 * strictly increasing (the slope never drops below 0.25), so the tone controls cannot invert or
 * posterize the image.
 *
 * The white balance control is limited by what the preview shader can reproduce exactly: eq, hue and
 * a luma lookup table are the only filters it implements, so temperature is a hue rotation rather
 * than a true channel-gain white balance.
 */
object FfmpegColorAdjustmentStrategy {
    /** Hue rotation, in degrees, at white balance temperature -1.0 / +1.0. */
    private const val TEMPERATURE_HUE_DEGREES = 20.0

    /** Luma lift, as a fraction of the 8-bit code range, at shadows/highlights -1.0 / +1.0. */
    private const val TONE_SPAN = 0.25

    /** The 8-bit code range the tone curve is normalized over, in both the export and the shader. */
    const val TONE_CODE_RANGE = 255.0

    // Ranges accepted by ffmpeg's eq filter, mirrored by the tr-adjust.hook parameters.
    private const val EQ_BRIGHTNESS_MIN = -1.0
    private const val EQ_BRIGHTNESS_MAX = 1.0
    private const val EQ_CONTRAST_MIN = 0.0
    private const val EQ_CONTRAST_MAX = 3.0
    private const val EQ_SATURATION_MIN = 0.0
    private const val EQ_SATURATION_MAX = 3.0

    fun map(adjustments: AdjustmentsV1): FfmpegColorAdjustments {
        val whiteBalance = adjustments.whiteBalance
        val temperature = (whiteBalance?.temperature ?: 0.0f).coerceIn(-1.0f, 1.0f).toDouble()
        val shadows = adjustments.shadows.coerceIn(-1.0f, 1.0f).toDouble()
        val highlights = adjustments.highlights.coerceIn(-1.0f, 1.0f).toDouble()

        return FfmpegColorAdjustments(
            brightness = (adjustments.brightness.toDouble() - 1.0).coerceIn(EQ_BRIGHTNESS_MIN, EQ_BRIGHTNESS_MAX),
            contrast = adjustments.contrast.toDouble().coerceIn(EQ_CONTRAST_MIN, EQ_CONTRAST_MAX),
            saturation = adjustments.saturation.toDouble().coerceIn(EQ_SATURATION_MIN, EQ_SATURATION_MAX),
            hueDegrees = temperature * TEMPERATURE_HUE_DEGREES,
            shadowsLift = shadows * TONE_SPAN * TONE_CODE_RANGE,
            highlightsLift = highlights * TONE_SPAN * TONE_CODE_RANGE,
        )
    }
}
