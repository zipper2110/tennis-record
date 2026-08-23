package org.litvin.export

import java.util.Locale
import java.util.concurrent.TimeUnit

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

data class ExportFrameRateOption(
    val frameRate: ExportFrameRate,
    val isSourceRate: Boolean,
) {
    val label: String
        get() = "${frameRate.displayFps} FPS" + if (isSourceRate) " (source)" else ""

    override fun toString(): String = label
}

object ExportFrameRates {
    private val standardRates = listOf("24", "25", "30", "50", "60")
        .map(::parse)
        .filterNotNull()

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

    fun availableFor(sourceRate: ExportFrameRate): List<ExportFrameRateOption> {
        val options = standardRates
            .filter { it.framesPerSecond <= sourceRate.framesPerSecond + EPSILON }
            .map { ExportFrameRateOption(it, isSourceRate = false) }
            .toMutableList()
        val matchingIndex = options.indexOfFirst {
            kotlin.math.abs(it.frameRate.framesPerSecond - sourceRate.framesPerSecond) < EPSILON
        }
        if (matchingIndex >= 0) {
            options[matchingIndex] = ExportFrameRateOption(sourceRate, isSourceRate = true)
        } else {
            options += ExportFrameRateOption(sourceRate, isSourceRate = true)
        }
        return options.sortedBy { it.frameRate.framesPerSecond }
    }

    private const val EPSILON = 0.0001
}

object ExportFrameRateProbe {
    fun probe(sourcePath: String, ffprobeExecutable: String): ExportFrameRate? {
        return try {
            val process = ProcessBuilder(
                ffprobeExecutable,
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=avg_frame_rate,r_frame_rate",
                "-of", "default=nokey=1:noprint_wrappers=1",
                sourcePath,
            ).redirectErrorStream(true).start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) return null
            process.inputStream.bufferedReader().use { reader ->
                reader.lineSequence().mapNotNull(ExportFrameRates::parse).firstOrNull()
            }
        } catch (_: Throwable) {
            null
        }
    }
}
