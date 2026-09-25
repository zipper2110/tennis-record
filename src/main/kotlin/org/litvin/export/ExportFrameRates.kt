package org.litvin.export

import java.util.Locale

data class ExportFrameRate(
    val ffmpegArgument: String,
    val framesPerSecond: Double,
) {
    val displayFps: String
        get() = if (kotlin.math.abs(framesPerSecond - framesPerSecond.toInt()) < 0.005) {
            framesPerSecond.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", framesPerSecond)
        }
}

object ExportFrameRates {
    fun parse(rawRate: String): ExportFrameRate? {
        val normalized = rawRate.trim()
        if (normalized.isEmpty()) return null
        val value = if ('/' in normalized) {
            val parts = normalized.split('/', limit = 2)
            if (parts.size != 2) return null
            val numerator = parts[0].toDoubleOrNull() ?: return null
            val denominator = parts[1].toDoubleOrNull() ?: return null
            if (denominator == 0.0) return null
            numerator / denominator
        } else {
            normalized.toDoubleOrNull() ?: return null
        }
        return ExportFrameRate(normalized, value.takeIf { it.isFinite() && it > 0 } ?: return null)
    }
}
