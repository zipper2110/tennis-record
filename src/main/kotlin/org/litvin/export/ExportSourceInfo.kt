package org.litvin.export

import java.util.concurrent.TimeUnit

/** Video properties of the source file. A null field means that ffprobe did not report it. */
data class ExportSourceInfo(
    val resolution: ExportResolution? = null,
    val frameRate: ExportFrameRate? = null,
    val bitrate: ExportSourceBitrate? = null,
    val durationMs: Long? = null,
    /** Frames divided by duration. It differs from [frameRate] when the frame rate of the source changes. */
    val averageFrameRate: ExportFrameRate? = null,
) {
    /**
     * True when the source has a variable frame rate. Phones do this: for example, they record
     * at 60 fps and change to 30 fps when the light gets low or the phone gets hot.
     */
    val variableFrameRate: Boolean
        get() {
            val nominal = frameRate?.framesPerSecond ?: return false
            val average = averageFrameRate?.framesPerSecond ?: return false
            return kotlin.math.abs(nominal - average) / nominal > 0.01
        }

    companion object {
        val UNKNOWN = ExportSourceInfo()
    }
}

object ExportSourceProbe {
    /** Reads the size, frame rate, bitrate and duration of the source with one ffprobe call. */
    fun probe(sourcePath: String, ffprobeExecutable: String): ExportSourceInfo {
        return try {
            val process = ProcessBuilder(
                ffprobeExecutable,
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height,avg_frame_rate,r_frame_rate,bit_rate:format=duration,bit_rate",
                "-of", "flat",
                sourcePath,
            ).redirectErrorStream(true).start()
            // The output has only a few short lines, so it cannot fill the pipe before the process ends.
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return ExportSourceInfo.UNKNOWN
            }
            if (process.exitValue() != 0) return ExportSourceInfo.UNKNOWN
            parseFlat(process.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Throwable) {
            ExportSourceInfo.UNKNOWN
        }
    }

    /** Parses the `-of flat` output of ffprobe, for example `streams.stream.0.width=1920`. */
    fun parseFlat(output: String): ExportSourceInfo {
        val values = output.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                line.substring(0, separator).trim() to line.substring(separator + 1).trim().removeSurrounding("\"")
            }
            .toMap()
        fun stream(key: String) = values["streams.stream.0.$key"]

        val width = stream("width")?.toIntOrNull()?.takeIf { it > 0 }
        val height = stream("height")?.toIntOrNull()?.takeIf { it > 0 }
        val resolution = if (width != null && height != null) {
            ExportResolution(ExportVideoOptions.resolutionLabel(width, height), width, height)
        } else {
            null
        }
        // r_frame_rate is the nominal rate, for example 60 for a phone video that changes between 60 and 30 fps.
        // The average of such a video (for example 36.9) is not a real rate, and an export at that
        // constant rate would drop and repeat frames. Some files report a time base such as 90000
        // as r_frame_rate, so the average replaces values that are too high to be a real rate.
        val averageFrameRate = stream("avg_frame_rate")?.let(ExportFrameRates::parse)
        val frameRate = stream("r_frame_rate")?.let(ExportFrameRates::parse)
            ?.takeIf { it.framesPerSecond <= MAX_NOMINAL_FPS }
            ?: averageFrameRate
        // Matroska and some other containers record only the container bitrate.
        val bitrate = ExportSourceBitrates.parse(stream("bit_rate"))
            ?: ExportSourceBitrates.parse(values["format.bit_rate"])
        val durationMs = values["format.duration"]?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0 }
            ?.let { (it * 1000).toLong() }
        return ExportSourceInfo(resolution, frameRate, bitrate, durationMs, averageFrameRate)
    }

    private const val MAX_NOMINAL_FPS = 240.0
}
