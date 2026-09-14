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
    private val tiers = listOf(
        ExportQualityTier("data-saver", "Data Saver", 24, "veryfast", listOf(3_000, 6_000, 6_000, 12_000)),
        ExportQualityTier("balanced", "Balanced", 21, "medium", listOf(5_000, 10_000, 10_000, 20_000)),
        ExportQualityTier("high", "High", 20, "medium", listOf(10_000, 20_000, 20_000, 40_000)),
        ExportQualityTier("very-high", "Very High", 18, "slow", listOf(15_000, 30_000, 30_000, 60_000)),
        ExportQualityTier("maximum", "Maximum", 17, "slow", listOf(20_000, 40_000, 40_000, 80_000)),
    )

    fun all(): List<ExportQualityTier> = tiers

    fun normalizedId(id: String?): String? = when (id?.lowercase()) {
        "fast" -> "data-saver"
        "quality" -> "maximum"
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

    fun description(preset: ExportPreset, outWidth: Int, outHeight: Int, outputFrameRate: String?): String? {
        val tier = tiers.firstOrNull { it.id == normalizedId(preset.id) } ?: return preset.description
        return "${tier.label} — up to ${bitrateK(tier, outWidth, outHeight, outputFrameRate) / 1_000} Mb/s"
    }
}
