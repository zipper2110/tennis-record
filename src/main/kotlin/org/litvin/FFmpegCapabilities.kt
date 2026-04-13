package org.litvin

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
    private var cached: Set<String>? = null

    /** Clear cached detection result to force a re-probe. */
    @Synchronized
    fun refresh() { cached = null }

    private fun ffmpegCmd(): List<String> {
        val sysProp = System.getProperty("tr.ffmpeg.path")?.trim().orEmpty()
        val envPath = System.getenv("FFMPEG_PATH")?.trim().orEmpty()
        return when {
            sysProp.isNotEmpty() -> listOf(sysProp)
            envPath.isNotEmpty() -> listOf(envPath)
            else -> listOf("ffmpeg")
        }
    }

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
            System.err.println("[FFMPEG][Probe] TIMEOUT after ${dur}ms while running: $cmdShown  (captured $lines lines, ${bytes} bytes)")
        } else {
            println("[FFMPEG][Probe] Exit=${exitCode ?: "?"} after ${dur}ms: $cmdShown  (captured $lines lines, ${bytes} bytes)")
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
            println("[FFMPEG] Probing encoders using: $exe -hide_banner -encoders (timeout=${timeoutMs}ms)")
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
            System.err.println("[FFMPEG] Encoder probe failed: ${t.javaClass.simpleName}: ${t.message}")
            t.printStackTrace()
        }
        cached = set
        if (set.isEmpty()) {
            println("[FFMPEG] No hardware H.264 encoders detected (probe parsed none). Software (libx264) only. If unexpected, ensure ffmpeg with NVENC/QSV/AMF is on PATH or set FFMPEG_PATH / -Dtr.ffmpeg.path.")
        } else {
            println("[FFMPEG] Detected H.264 encoders: ${set.joinToString()}")
        }
        return set
    }
}
