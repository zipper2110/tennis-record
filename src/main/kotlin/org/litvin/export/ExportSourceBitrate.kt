package org.litvin.export

import java.util.Locale
import kotlin.math.roundToLong

/** Bitrate of the source video, in bits per second. */
data class ExportSourceBitrate(val bitsPerSecond: Long) {
    /** Megabits per second, rounded for display: one decimal below 10 Mbit/s, whole numbers above. */
    val displayMegabits: String
        get() {
            val megabits = bitsPerSecond / 1_000_000.0
            return if (megabits < 10.0) {
                String.format(Locale.US, "%.1f", megabits)
            } else {
                megabits.roundToLong().toString()
            }
        }
}

object ExportSourceBitrates {
    fun parse(rawBitrate: String?): ExportSourceBitrate? {
        val value = rawBitrate?.trim()?.toLongOrNull() ?: return null
        return if (value > 0) ExportSourceBitrate(value) else null
    }
}
