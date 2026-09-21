package org.litvin.export

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

    /** How the source video was cut for a render: full video, all cut points, or favorites only. */
    fun formatCutMode(idleTrim: Boolean, favoriteOnly: Boolean): String = when {
        !idleTrim -> "Full video"
        favoriteOnly -> "Favorite points"
        else -> "Cut points"
    }

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
}
