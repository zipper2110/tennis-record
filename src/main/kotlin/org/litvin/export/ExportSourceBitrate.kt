package org.litvin.export

import java.util.Locale
import java.util.concurrent.TimeUnit
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

    fun label(bitrate: ExportSourceBitrate?): String =
        if (bitrate == null) "" else "Source: ${bitrate.displayMegabits} Megabits/s"
}

object ExportSourceBitrateProbe {
    /**
     * Reads the video stream bitrate, falling back to the container bitrate for formats
     * (Matroska among them) that do not record a per-stream value.
     */
    fun probe(sourcePath: String, ffprobeExecutable: String): ExportSourceBitrate? {
        return try {
            val process = ProcessBuilder(
                ffprobeExecutable,
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=bit_rate:format=bit_rate",
                "-of", "default=nokey=1:noprint_wrappers=1",
                sourcePath,
            ).redirectErrorStream(true).start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) return null
            process.inputStream.bufferedReader().use { reader ->
                reader.lineSequence().mapNotNull(ExportSourceBitrates::parse).firstOrNull()
            }
        } catch (_: Throwable) {
            null
        }
    }
}
