package org.litvin.export

import java.util.concurrent.TimeUnit

object ExportResolutionProbe {
    fun probe(sourcePath: String, ffprobeExecutable: String): ExportResolution? {
        return try {
            val process = ProcessBuilder(
                ffprobeExecutable, "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=width,height", "-of", "csv=p=0", sourcePath,
            ).redirectErrorStream(true).start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) return null
            val dimensions = process.inputStream.bufferedReader().use { reader ->
                reader.lineSequence().firstOrNull()?.split(',', limit = 2)
                    ?.takeIf { it.size == 2 }?.map(String::toIntOrNull)
                    ?.takeIf { it.all { value -> value != null && value > 0 } }
                    ?.map { requireNotNull(it) }
            } ?: return null
            val (width, height) = dimensions
            ExportResolution(ExportVideoOptions.resolutionLabel(width, height), width, height)
        } catch (_: Throwable) {
            null
        }
    }
}
