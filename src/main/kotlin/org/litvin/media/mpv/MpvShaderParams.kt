package org.litvin.media.mpv

import org.litvin.FfmpegColorAdjustmentStrategy
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.GeometryPlan
import org.litvin.adjustments.CropRect
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/** Source video facts that the preview shader needs. mpv reports them after the file loads. */
internal data class MpvVideoInfo(
    val width: Int,
    val height: Int,
    val colorMatrix: String? = null,
    val colorLevels: String? = null,
)

/**
 * Maps [AdjustmentsV1] to the `glsl-shader-opts` value of `tr-adjust.hook`.
 *
 * The color values come from [FfmpegColorAdjustmentStrategy], which also builds the export filters.
 * The values are rounded to 4 decimals, as FFmpegCommandBuilder writes them into the export command.
 * The geometry comes from [GeometryPlan], which the export also uses.
 */
internal object MpvShaderParams {
    private const val EPSILON = 1e-6

    /**
     * [cropEditing] true keeps the full rotated frame (no crop), for the Crop/Rotate editor.
     */
    fun build(adjustments: AdjustmentsV1, video: MpvVideoInfo?, cropEditing: Boolean = false): String {
        val color = FfmpegColorAdjustmentStrategy.map(adjustments)
        val brightness = if (color.hasEqualizerAdjustments) round4(color.brightness) else 0.0
        val contrast = if (color.hasEqualizerAdjustments) round4(color.contrast) else 1.0
        val saturation = if (color.hasEqualizerAdjustments) round4(color.saturation) else 1.0
        val hue = if (color.hasHueAdjustments) round4(color.hueDegrees) else 0.0
        val shadowsLift = if (color.hasToneAdjustments) round4(color.shadowsLift) else 0.0
        val highlightsLift = if (color.hasToneAdjustments) round4(color.highlightsLift) else 0.0

        val plan = GeometryPlan.of(adjustments, video?.width ?: 0, video?.height ?: 0)
        val rotation = plan.rotationDeg
        val crop = if (cropEditing) GeometryPlan.FULL_FRAME else plan.crop
        val geometryActive = plan.hasRotation || crop != GeometryPlan.FULL_FRAME
        val colorActive = color.hasEqualizerAdjustments || color.hasHueAdjustments || color.hasToneAdjustments
        val (kr, kb) = matrixCoefficients(video?.colorMatrix)
        val fullRange = video?.colorLevels.equals("full", ignoreCase = true)

        val values = linkedMapOf(
            "tr_active" to if (geometryActive || colorActive) "1" else "0",
            "tr_brightness" to fmt(brightness),
            "tr_contrast" to fmt(contrast),
            "tr_saturation" to fmt(saturation),
            "tr_shadows_lift" to fmt(shadowsLift),
            "tr_highlights_lift" to fmt(highlightsLift),
            "tr_hue_deg" to fmt(hue),
            "tr_rotation_deg" to fmt(rotation),
            "tr_crop_x" to fmt(crop.x),
            "tr_crop_y" to fmt(crop.y),
            "tr_crop_w" to fmt(crop.width),
            "tr_crop_h" to fmt(crop.height),
            "tr_kr" to fmt(kr),
            "tr_kb" to fmt(kb),
            "tr_full_range" to if (fullRange) "1" else "0",
        )
        return values.entries.joinToString(",") { "${it.key}=${it.value}" }
    }

    /** Returns the crop rectangle as fractions of the frame, in the space of the rotated frame. */
    internal fun cropRect(adjustments: AdjustmentsV1, rotationDeg: Double, video: MpvVideoInfo): CropRect =
        GeometryPlan.of(adjustments.copy(rotationDeg = rotationDeg.toFloat()), video.width, video.height).crop

    /** Kr and Kb of the Y'CbCr matrix, from the mpv `video-params/colormatrix` names. */
    internal fun matrixCoefficients(colorMatrix: String?): Pair<Double, Double> = when {
        colorMatrix == null -> 0.2126 to 0.0722
        colorMatrix.startsWith("bt.2020") -> 0.2627 to 0.0593
        colorMatrix == "bt.601" -> 0.299 to 0.114
        colorMatrix == "smpte-240m" -> 0.212 to 0.087
        colorMatrix == "fcc" -> 0.30 to 0.11
        else -> 0.2126 to 0.0722
    }

    private fun round4(value: Double): Double = round(value * 10_000.0) / 10_000.0

    private fun fmt(value: Double): String {
        val clean = if (abs(value) < EPSILON) 0.0 else value
        return String.format(Locale.US, "%.6f", clean)
    }
}
