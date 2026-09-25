package org.litvin.export

import org.litvin.ExportQualityProfiles
import java.util.Locale

object RenderFormatting {
    fun formatSize(bytes: Long): String {
        val kb = 1000.0
        val mb = kb * 1000
        val gb = mb * 1000
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.2f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.2f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    /** How the source video was cut for an export. The names are the same as the Content options. */
    fun formatCutMode(idleTrim: Boolean, favoriteOnly: Boolean): String = when {
        !idleTrim -> "Full video"
        favoriteOnly -> "Only favorites"
        else -> "Only points"
    }

    /** The content settings of an export, for example "Only points (12 points)  ·  Scoreboard: on  ·  Comments: off". */
    fun formatContent(
        idleTrim: Boolean,
        favoriteOnly: Boolean,
        pointCount: Int?,
        includeScoreboard: Boolean,
        includeComments: Boolean,
    ): String {
        val points = pointCount?.takeIf { idleTrim }?.let { " (${formatCount(it, "point")})" }.orEmpty()
        return listOf(
            formatCutMode(idleTrim, favoriteOnly) + points,
            "Scoreboard: ${onOff(includeScoreboard)}",
            "Comments: ${onOff(includeComments)}",
        ).joinToString(SEPARATOR)
    }

    /**
     * The video settings of an export, for example
     * "Balanced  ·  1080p (1920×1080)  ·  60 FPS  ·  12 Mbit/s  ·  H.264 (NVENC)".
     * A job without a frame rate keeps the source rate. Old entries without a bitrate do not show one.
     */
    fun formatVideo(
        presetId: String?,
        width: Int,
        height: Int,
        frameRate: String?,
        videoBitrateK: Int?,
        encoderLabel: String,
    ): String {
        // A standard size shows only its name. A size near a standard size also shows the pixels.
        val name = ExportVideoOptions.displayResolution(width, height)
        val resolution = when {
            ExportVideoOptions.isStandardResolution(width, height) -> name
            name == "${width}×$height" -> name
            else -> "$name (${width}×$height)"
        }
        return listOfNotNull(
            formatPreset(presetId),
            resolution,
            formatFrameRate(frameRate) ?: "Original FPS",
            videoBitrateK?.let(ExportVideoOptions::formatBitrate),
            encoderLabel,
        ).joinToString(SEPARATOR)
    }

    /** Name of the quality preset, for example "Balanced". Null when the id is not known. */
    fun formatPreset(presetId: String?): String? {
        val id = ExportQualityProfiles.normalizedId(presetId) ?: return null
        return ExportQualityProfiles.all().firstOrNull { it.id == id }?.label
    }

    /** The written size and the expected size, for example "120.50 MB / ~480.00 MB". Without an expected size, only the written size. */
    fun formatSizeProgress(bytesWritten: Long, expectedBytes: Long?): String =
        formatSize(bytesWritten) + expectedBytes?.let { " / ~" + formatSize(it) }.orEmpty()

    private fun formatCount(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"

    private fun onOff(value: Boolean): String = if (value) "on" else "off"

    private const val SEPARATOR = "  ·  "

    /** Output frame rate for display, e.g. "60 FPS" or "29.97 FPS"; null when the job kept the source rate. */
    fun formatFrameRate(rawRate: String?): String? = rawRate
        ?.let(ExportFrameRates::parse)
        ?.let { "${it.displayFps} FPS" }

    fun formatDuration(ms: Long): String {
        var remaining = ms.coerceAtLeast(0L)
        val h = remaining / 3_600_000
        remaining %= 3_600_000
        val m = remaining / 60_000
        remaining %= 60_000
        val s = remaining / 1000
        return String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    }

    /** Duration in words for summaries, for example "2h 30min 10sec" or "45sec". */
    fun formatDurationWords(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return buildList {
            if (h > 0) add("${h}h")
            if (h > 0 || m > 0) add("${m}min")
            add("${s}sec")
        }.joinToString(" ")
    }
}
