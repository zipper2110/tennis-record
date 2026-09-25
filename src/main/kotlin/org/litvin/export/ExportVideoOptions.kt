package org.litvin.export

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Rough quality grade of a resolution or frame rate choice. */
enum class ExportQualityLevel(val label: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
}

/** One resolution card on the Advanced tab. */
data class ExportResolutionChoice(
    val title: String,
    val resolution: ExportResolution,
    val level: ExportQualityLevel?,
    val isSource: Boolean,
    val available: Boolean,
)

/** One frame rate card on the Advanced tab. */
data class ExportFrameRateChoice(
    val title: String,
    val frameRate: ExportFrameRate,
    val level: ExportQualityLevel?,
    val isSource: Boolean,
    val available: Boolean,
)

/** Limits of the bitrate slider, in kilobits per second. */
data class ExportBitrateRange(
    val maxK: Int,
    val minK: Int,
    /** True when ffprobe did not report the source bitrate and [maxK] is an estimate. */
    val estimated: Boolean,
)

/** The three quality choices on the Simple tab. */
enum class ExportSimplePreset(
    val id: String,
    val title: String,
    val summary: String,
    val exportTime: String,
    val bitrateFactor: Double,
) {
    BEST("best", "Original quality", "Largest file, slowest export", "slow", 1.0),
    BALANCED("balanced", "Balanced", "Good quality, smaller file", "medium", 0.75),
    FAST("fast", "Fast export", "Lower quality, smallest file", "fast", 0.5),
    ;

    companion object {
        fun fromId(id: String?): ExportSimplePreset? = entries.firstOrNull { it.id == id }
    }
}

/** The video settings that an export uses. A null [frameRate] keeps the frame rate of the source. */
data class ExportVideoTarget(
    val presetId: String,
    val resolution: ExportResolution,
    val frameRate: ExportFrameRate?,
    val bitrateK: Int,
)

object ExportVideoOptions {
    const val CUSTOM_PRESET_ID = "custom"
    const val AUDIO_BITRATE_K = 192

    private data class Standard(val title: String, val width: Int, val height: Int, val level: ExportQualityLevel)

    private val standardResolutions = listOf(
        Standard("720p", 1280, 720, ExportQualityLevel.LOW),
        Standard("1080p", 1920, 1080, ExportQualityLevel.MEDIUM),
        Standard("4K", 3840, 2160, ExportQualityLevel.HIGH),
    )

    private val standardFrameRates = listOf(
        24 to ExportQualityLevel.LOW,
        30 to ExportQualityLevel.MEDIUM,
        60 to ExportQualityLevel.HIGH,
    )

    private val fallbackResolution = ExportResolution("1080p", 1920, 1080)

    /** Common sizes with a short name. A camera or a crop can make a size some pixels different. */
    private val namedResolutions = listOf(
        Triple("720p", 1280, 720),
        Triple("1080p", 1920, 1080),
        Triple("2K", 2560, 1440),
        Triple("4K", 3840, 2160),
    )
    private const val NAME_TOLERANCE_PX = 8

    private fun resolutionName(width: Int, height: Int): String? = namedResolutions.firstOrNull { (_, w, h) ->
        abs(width - w) <= NAME_TOLERANCE_PX && abs(height - h) <= NAME_TOLERANCE_PX
    }?.first

    /** Short name for file names and job labels, for example "4K" or "2704x1520". */
    fun resolutionLabel(width: Int, height: Int): String = resolutionName(width, height) ?: "${width}x$height"

    /** True when the size is exactly 720p, 1080p, 2K or 4K. */
    fun isStandardResolution(width: Int, height: Int): Boolean =
        namedResolutions.any { (_, w, h) -> w == width && h == height }

    /** Name to show in the UI, for example "1080p", "2K", "4K" or "2704×1520". */
    fun displayResolution(width: Int, height: Int): String = resolutionName(width, height) ?: "${width}×$height"

    /**
     * Returns the 720p, 1080p and 4K cards. A card is available when the source is at least that tall.
     * The card with the height of the source keeps the source size and is marked as the source.
     * A source with a different height gets its own card.
     */
    fun resolutionChoices(source: ExportResolution?): List<ExportResolutionChoice> {
        if (source == null) {
            return standardResolutions.map {
                ExportResolutionChoice(it.title, ExportResolution(it.title, it.width, it.height), it.level, isSource = false, available = true)
            }
        }
        val choices = standardResolutions.map { standard ->
            val isSource = standard.height == source.height
            ExportResolutionChoice(
                title = standard.title,
                resolution = if (isSource) source.copy(label = standard.title) else scaledTo(source, standard),
                level = standard.level,
                isSource = isSource,
                available = standard.height <= source.height,
            )
        }
        if (choices.any { it.isSource }) return choices
        val sourceChoice = ExportResolutionChoice(
            title = displayResolution(source.width, source.height),
            resolution = source,
            level = null,
            isSource = true,
            available = true,
        )
        return (choices + sourceChoice).sortedBy { it.resolution.height }
    }

    /**
     * Returns the 24, 30 and 60 FPS cards. A card is available when the source rate is at least that high.
     * A source rate near a standard rate, such as 29.97, marks that card. Other source rates get their own card.
     */
    fun frameRateChoices(source: ExportFrameRate?): List<ExportFrameRateChoice> {
        val choices = standardFrameRates.map { (fps, level) ->
            val standard = ExportFrameRate(fps.toString(), fps.toDouble())
            val isSource = source != null && isNear(source.framesPerSecond, fps.toDouble())
            ExportFrameRateChoice(
                title = "${(if (isSource) source!! else standard).displayFps} fps",
                frameRate = if (isSource) source!! else standard,
                level = level,
                isSource = isSource,
                available = source == null || isSource || fps <= source.framesPerSecond,
            )
        }
        if (source == null || choices.any { it.isSource }) return choices
        val sourceChoice = ExportFrameRateChoice("${source.displayFps} fps", source, null, isSource = true, available = true)
        return (choices + sourceChoice).sortedBy { it.frameRate.framesPerSecond }
    }

    /** The card that is selected by default: the source, or 1080p when the source is unknown. */
    fun defaultResolution(choices: List<ExportResolutionChoice>): ExportResolutionChoice =
        choices.firstOrNull { it.isSource }
            ?: choices.firstOrNull { it.title == "1080p" && it.available }
            ?: choices.first { it.available }

    /** The card that is selected by default: the source, or 30 fps when the source is unknown. */
    fun defaultFrameRate(choices: List<ExportFrameRateChoice>): ExportFrameRateChoice =
        choices.firstOrNull { it.isSource }
            ?: choices.firstOrNull { it.frameRate.framesPerSecond == 30.0 && it.available }
            ?: choices.first { it.available }

    /** The slider goes from one fifth of the source bitrate to the source bitrate. */
    fun bitrateRange(source: ExportSourceInfo): ExportBitrateRange {
        val sourceK = source.bitrate?.let { (it.bitsPerSecond / 1000).toInt() }?.takeIf { it > 0 }
        val maxK = sourceK ?: nominalBitrateK(source.resolution ?: fallbackResolution, source.frameRate?.framesPerSecond)
        return ExportBitrateRange(maxK = maxK, minK = (maxK / 5).coerceAtLeast(1), estimated = sourceK == null)
    }

    /** A typical camera bitrate. It replaces the source bitrate when ffprobe does not report it. */
    fun nominalBitrateK(resolution: ExportResolution, framesPerSecond: Double?): Int {
        val base = when {
            resolution.height <= 720 -> 8_000
            resolution.height <= 1080 -> 16_000
            resolution.height <= 1440 -> 24_000
            else -> 45_000
        }
        return if ((framesPerSecond ?: 30.0) > 31.0) base * 3 / 2 else base
    }

    /** Converts a Simple tab choice into the video settings for the export. */
    fun simpleTarget(preset: ExportSimplePreset, source: ExportSourceInfo): ExportVideoTarget {
        val sourceResolution = source.resolution ?: fallbackResolution
        val resolution = if (preset == ExportSimplePreset.FAST && sourceResolution.height > 1080) {
            scaledTo(sourceResolution, standardResolutions.first { it.height == 1080 })
        } else {
            sourceResolution
        }
        val maxK = bitrateRange(source).maxK
        return ExportVideoTarget(
            presetId = preset.id,
            resolution = resolution,
            frameRate = source.frameRate,
            bitrateK = (maxK * preset.bitrateFactor).roundToInt().coerceAtLeast(1),
        )
    }

    /** Estimated size of the output file: video and audio bitrate multiplied by the duration. */
    fun estimatedBytes(videoBitrateK: Int, durationMs: Long): Long =
        (videoBitrateK + AUDIO_BITRATE_K).toLong() * 1000L / 8L * durationMs / 1000L

    fun formatBitrate(bitrateK: Int): String {
        val megabits = bitrateK / 1000.0
        return if (megabits < 10.0) String.format(Locale.US, "%.1f Mbit/s", megabits) else "${megabits.roundToInt()} Mbit/s"
    }

    /** Keeps the aspect ratio of the source. The width is even because H.264 needs even dimensions. */
    private fun scaledTo(source: ExportResolution, standard: Standard): ExportResolution {
        val width = (source.width.toDouble() * standard.height / source.height / 2).roundToInt() * 2
        return ExportResolution(standard.title, width, standard.height)
    }

    private fun isNear(actual: Double, standard: Double) = abs(actual - standard) / standard < 0.01
}
