package org.litvin.export

import java.util.concurrent.TimeUnit

data class ExportResolutionOption(
    val resolution: ExportResolution,
    val isSourceResolution: Boolean,
) {
    val label: String
        get() = resolution.label + if (isSourceResolution) " (source)" else ""

    override fun toString(): String = label
}

object ExportResolutions {
    private val standardResolutions = listOf(
        ExportResolution("1080p", 1920, 1080),
        ExportResolution("4K", 3840, 2160),
    )

    fun availableFor(sourceResolution: ExportResolution?): List<ExportResolutionOption> {
        if (sourceResolution == null) return standardResolutions.map { ExportResolutionOption(it, false) }
        val sourceOption = ExportResolutionOption(sourceResolution, true)
        val alternatives = standardResolutions
            .filterNot { it.width == sourceResolution.width && it.height == sourceResolution.height }
            .map { ExportResolutionOption(it, false) }
        return listOf(sourceOption) + alternatives
    }

    fun preferredOption(options: List<ExportResolutionOption>, savedResolution: String?): ExportResolutionOption? =
        options.firstOrNull { it.resolution.label == savedResolution }
            ?: options.firstOrNull { it.isSourceResolution }
            ?: options.firstOrNull()
}

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
            ExportResolution(
                label = when {
                    width == 3840 && height == 2160 -> "4K"
                    width == 1920 && height == 1080 -> "1080p"
                    else -> "${width}x${height}"
                },
                width = width,
                height = height,
            )
        } catch (_: Throwable) {
            null
        }
    }
}
