package org.litvin

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Lightweight runtime capability probe for ffmpeg encoders.
 *
 * We only care about H.264 paths for v0.1.0: libx264 (software), NVENC, QSV, AMF.
 * Detection is best-effort; failures simply return an empty set (software still available).
 */
object FFmpegCapabilities {
    private val logger = KotlinLogging.logger {}

    private var cached: Set<String>? = null

    /** Clear cached detection result to force a re-probe. */
    @Synchronized
    fun refresh() { cached = null }

    private fun ffmpegCmd(): List<String> = listOf(ApplicationLayout.current().ffmpegExecutable)

    private fun runAndCapture(cmd: List<String>, timeoutMs: Long): Triple<String, Int?, Boolean> {
        val start = System.currentTimeMillis()
        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val out = StringBuilder(8 * 1024)
        val reader = Thread({
            try {
                proc.inputStream.bufferedReader().useLines { seq ->
                    seq.forEach { line ->
                        out.append(line).append('\n')
                    }
                }
            } catch (_: Throwable) {}
        }, "ffmpeg-cap-probe-reader")
        reader.isDaemon = true
        reader.start()
        var timedOut = false
        val finished = try { proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS) } catch (t: Throwable) { false }
        val exitCode: Int?
        if (!finished) {
            timedOut = true
            try { proc.destroy() } catch (_: Throwable) {}
            try { proc.destroyForcibly() } catch (_: Throwable) {}
            exitCode = null
        } else {
            exitCode = try { proc.exitValue() } catch (_: Throwable) { null }
        }
        try { reader.join(200) } catch (_: Throwable) {}
        val dur = System.currentTimeMillis() - start
        val text = out.toString()
        val bytes = text.toByteArray().size
        val lines = text.lineSequence().count()
        val cmdShown = cmd.joinToString(" ")
        if (timedOut) {
            logger.warn { "FFmpeg probe timed out after ${dur}ms while running: $cmdShown (captured $lines lines, $bytes bytes)" }
        } else {
            logger.debug { "FFmpeg probe exit=${exitCode ?: "?"} after ${dur}ms: $cmdShown (captured $lines lines, $bytes bytes)" }
        }
        return Triple(text, exitCode, timedOut)
    }

    private fun parseEncodersFrom(reader: BufferedReader, out: MutableSet<String>) {
        reader.lineSequence().forEach { line ->
            val l = line.lowercase()
            if (l.contains("libx264")) out += "libx264"
            if (l.contains("h264_nvenc")) out += "h264_nvenc"
            if (l.contains("h264_qsv")) out += "h264_qsv"
            if (l.contains("h264_amf")) out += "h264_amf"
        }
    }

    /** Returns lower-case encoder ids, e.g., ["libx264", "h264_nvenc"]. */
    @Synchronized
    fun h264Encoders(): Set<String> {
        cached?.let { return it }
        val set = mutableSetOf<String>()
        val timeoutMs = System.getProperty("tr.ffmpeg.probe.timeout.ms")?.toLongOrNull() ?: 10_000L
        try {
            val exe = ffmpegCmd().joinToString(" ")
            logger.info { "Probing FFmpeg encoders using: $exe -hide_banner -encoders (timeout=${timeoutMs}ms)" }
            // Attempt 1 — consume output concurrently to avoid pipe deadlock
            val (text1, exit1, to1) = runAndCapture(ffmpegCmd() + listOf("-hide_banner", "-encoders"), timeoutMs)
            if (text1.isNotBlank()) {
                BufferedReader(InputStreamReader(text1.byteInputStream())).use { br -> parseEncodersFrom(br, set) }
            }
            // Attempt 2 with quieter loglevel if nothing parsed
            if (set.isEmpty()) {
                val (text2, exit2, to2) = runAndCapture(ffmpegCmd() + listOf("-v", "quiet", "-hide_banner", "-encoders"), timeoutMs)
                if (text2.isNotBlank()) {
                    BufferedReader(InputStreamReader(text2.byteInputStream())).use { br -> parseEncodersFrom(br, set) }
                }
                // Preflight to help diagnostics if still nothing
                if (set.isEmpty()) {
                    runAndCapture(ffmpegCmd() + listOf("-version"), 5_000L)
                }
            }
        } catch (t: Throwable) {
            logger.warn(t) { "FFmpeg encoder probe failed." }
        }
        cached = set
        if (set.isEmpty()) {
            logger.info { "No H.264 encoders were detected. Run the distribution diagnostics or verify the configured FFmpeg executable." }
        } else {
            logger.info { "Detected H.264 encoders: ${set.joinToString()}" }
        }
        return set
    }

    /**
     * Encodes a few frames of a generated black picture with [encoderId].
     * An ffmpeg build can list a hardware encoder that does not work on this computer, for example
     * AMF on a PC without an AMD GPU. Only a real encode shows that the encoder works.
     */
    fun canEncode(encoderId: String): Boolean {
        val timeoutMs = System.getProperty("tr.ffmpeg.probe.timeout.ms")?.toLongOrNull() ?: 15_000L
        return try {
            val (_, exitCode, timedOut) = runAndCapture(
                ffmpegCmd() + listOf(
                    "-hide_banner", "-v", "error",
                    "-f", "lavfi", "-i", "color=c=black:s=640x360:r=30",
                    "-frames:v", "3", "-c:v", encoderId, "-f", "null", "-",
                ),
                timeoutMs,
            )
            (!timedOut && exitCode == 0).also { works ->
                logger.info { "Test encode with $encoderId ${if (works) "succeeded" else "failed (exit=$exitCode)"}" }
            }
        } catch (t: Throwable) {
            logger.warn(t) { "Test encode with $encoderId failed." }
            false
        }
    }

    /**
     * Returns the names of the graphics adapters, with the manufacturer, for example
     * "NVIDIA GeForce RTX 3060 Laptop GPU". Returns an empty list if the names are not available.
     */
    fun videoAdapters(): List<String> {
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        if (!os.contains("windows")) return emptyList()
        return try {
            val (output, exitCode, timedOut) = runAndCapture(
                listOf(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-Command",
                    // Java does not escape inner double quotes on Windows, so the command uses only single quotes.
                    "Get-CimInstance Win32_VideoController | ForEach-Object { \$_.AdapterCompatibility + '|' + \$_.Name }",
                ),
                5_000L,
            )
            if (!timedOut && exitCode == 0) parseVideoAdapters(output) else emptyList()
        } catch (t: Throwable) {
            logger.debug(t) { "Could not detect Windows video adapters." }
            emptyList()
        }
    }

    /** Parses "manufacturer|name" lines. Removes virtual and basic display adapters. */
    internal fun parseVideoAdapters(output: String): List<String> = output.lineSequence()
        .mapNotNull { line ->
            val vendor = line.substringBefore('|', "").trim()
            val name = line.substringAfter('|').trim()
                .replace("(R)", "", ignoreCase = true)
                .replace("(TM)", "", ignoreCase = true)
                .replace(Regex("\\s+"), " ")
                .trim()
            if (name.isEmpty()) return@mapNotNull null
            val lower = "$vendor $name".lowercase()
            if ("microsoft" in lower || "virtual" in lower || "remote" in lower) return@mapNotNull null
            // The manufacturer field is often "Intel Corporation" or "Advanced Micro Devices, Inc.".
            // Add a short manufacturer name only when the model name does not already have it.
            val shortVendor = when {
                "nvidia" in vendor.lowercase() -> "NVIDIA"
                "intel" in vendor.lowercase() -> "Intel"
                "advanced micro devices" in vendor.lowercase() || "amd" in vendor.lowercase() -> "AMD"
                else -> vendor.substringBefore(',').trim()
            }
            if (shortVendor.isEmpty() || name.lowercase().contains(shortVendor.lowercase())) name else "$shortVendor $name"
        }
        .distinct()
        .toList()

    internal fun preferredH264Encoder(encoders: Set<String>, videoAdapters: String): String? {
        val adapters = videoAdapters.lowercase()
        return when {
            ("nvidia" in adapters || "geforce" in adapters) && "h264_nvenc" in encoders -> "h264_nvenc"
            ("amd" in adapters || "radeon" in adapters) && "h264_amf" in encoders -> "h264_amf"
            "intel" in adapters && "h264_qsv" in encoders -> "h264_qsv"
            "h264_nvenc" in encoders -> "h264_nvenc"
            "h264_amf" in encoders -> "h264_amf"
            "h264_qsv" in encoders -> "h264_qsv"
            else -> null
        }
    }
}
