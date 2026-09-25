package org.litvin

import org.litvin.export.ExportFrameRates

data class ExportQualityTier(
    val id: String,
    val label: String,
    val crf: Int,
    val x264Preset: String,
    val bitratesK: List<Int>,
)

object ExportQualityProfiles {
    // The export sets its own bitrate (RenderJob.videoBitrateK). The bitrate table and CRF only apply
    // to jobs without that value. The x264 preset sets the encoding speed in both cases.
    private val tiers = listOf(
        ExportQualityTier("best", "Original quality", 17, "slow", listOf(20_000, 40_000, 40_000, 80_000)),
        ExportQualityTier("balanced", "Balanced", 21, "medium", listOf(5_000, 10_000, 10_000, 20_000)),
        ExportQualityTier("fast", "Fast export", 24, "veryfast", listOf(3_000, 6_000, 6_000, 12_000)),
        ExportQualityTier("custom", "Custom", 20, "medium", listOf(10_000, 20_000, 20_000, 40_000)),
    )

    fun all(): List<ExportQualityTier> = tiers

    /** Maps the ids of the earlier five quality presets to the current tiers. */
    fun normalizedId(id: String?): String? = when (id?.lowercase()) {
        "quality", "maximum", "very-high" -> "best"
        "high" -> "balanced"
        "data-saver" -> "fast"
        else -> id?.lowercase()
    }

    fun resolve(preset: ExportPreset, outWidth: Int, outHeight: Int, outputFrameRate: String?): ExportPreset {
        val tier = tiers.firstOrNull { it.id == normalizedId(preset.id) } ?: return preset
        val bitrateK = bitrateK(tier, outWidth, outHeight, outputFrameRate)
        return preset.copy(
            video = preset.video.copy(
                x264Preset = tier.x264Preset,
                crf = tier.crf,
                vbvMaxrateK = bitrateK,
                vbvBufsizeK = bitrateK * 2,
            ),
        )
    }

    fun bitrateK(tier: ExportQualityTier, outWidth: Int, outHeight: Int, outputFrameRate: String?): Int {
        val is4k = outWidth >= 3840 || outHeight >= 2160
        val fps = outputFrameRate?.let(ExportFrameRates::parse)?.framesPerSecond ?: 30.0
        val highFrameRate = fps > 30.0
        return tier.bitratesK[(if (is4k) 2 else 0) + if (highFrameRate) 1 else 0]
    }

}
