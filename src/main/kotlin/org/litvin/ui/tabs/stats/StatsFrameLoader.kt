package org.litvin.ui.tabs.stats

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.ApplicationLayout
import org.litvin.FFmpegCommandBuilder
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.GeometryPlan
import org.litvin.export.ExportResolutionProbe
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/** One video frame under the statistics card: the source, the time, and the adjustments of the project. */
data class StatsFrameRequest(
    val sourcePath: String,
    /** The source time just after the frame, as the export uses it for the frozen frame. */
    val atMs: Long,
    val adjustments: AdjustmentsV1,
)

/**
 * Reads the video frame under the statistics card with ffmpeg, on a background thread.
 * The last frames stay in a cache, so a second request for the same frame does not run ffmpeg again.
 */
class StatsFrameLoader(
    private val extract: (StatsFrameRequest) -> BufferedImage? = ::extractWithFfmpeg,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "stats-frame-loader").apply { isDaemon = true }
    }
    private val cache = object : LinkedHashMap<StatsFrameRequest, BufferedImage>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<StatsFrameRequest, BufferedImage>) = size > CACHE_SIZE
    }

    /** The frame when it is in the cache, or null. */
    fun cached(request: StatsFrameRequest): BufferedImage? = synchronized(cache) { cache[request] }

    /** Reads the frame and calls [onLoaded] on the event thread. The image is null when ffmpeg cannot read the frame. */
    fun load(request: StatsFrameRequest, onLoaded: (BufferedImage?) -> Unit) {
        cached(request)?.let { image -> return onLoaded(image) }
        executor.execute {
            val image = cached(request) ?: try {
                extract(request)?.also { synchronized(cache) { cache[request] = it } }
            } catch (failure: Exception) {
                logger.warn(failure) { "Cannot read the video frame for the statistics preview" }
                null
            }
            SwingUtilities.invokeLater { onLoaded(image) }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val CACHE_SIZE = 8

        /** The width of the frame. The preview makes it smaller or larger to fit. */
        private const val FRAME_WIDTH = 1280

        private fun extractWithFfmpeg(request: StatsFrameRequest): BufferedImage? {
            if (!File(request.sourcePath).isFile) return null
            val layout = ApplicationLayout.current()
            // The crop needs the source size, as in the export. Probe it only when the project has a crop or a rotation.
            val sourceSize = request.adjustments
                .takeIf { !GeometryPlan.of(it, FRAME_WIDTH, FRAME_WIDTH * 9 / 16).isIdentity }
                ?.let { ExportResolutionProbe.probe(request.sourcePath, layout.ffprobeExecutable) }
            val output = File.createTempFile("stats-frame", ".png")
            try {
                val args = listOf(layout.ffmpegExecutable) + FFmpegCommandBuilder.stillFrameArgs(
                    sourcePath = request.sourcePath,
                    atMs = request.atMs,
                    outputPath = output.absolutePath,
                    width = FRAME_WIDTH,
                    adjustments = request.adjustments,
                    sourceWidth = sourceSize?.width,
                    sourceHeight = sourceSize?.height,
                )
                val process = ProcessBuilder(args)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    logger.warn { "ffmpeg did not read the statistics frame in 30 s" }
                    return null
                }
                if (process.exitValue() != 0 || output.length() == 0L) return null
                return ImageIO.read(output)
            } finally {
                output.delete()
            }
        }
    }
}
