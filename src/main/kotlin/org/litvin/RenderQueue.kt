package org.litvin

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Task 3.8 — Render job model and global queue manager (MVP)
 *
 * Provides:
 * - RenderJob data model capturing configuration snapshot and runtime fields
 * - Global RenderQueueManager with a single-worker sequential processor
 * - Simple observer pattern to notify UI on state changes (active queue changes)
 *
 * Note: Actual ffmpeg execution and progress parsing are implemented in later tasks (3.9+).
 */

enum class RenderStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCELED }

data class RenderJob(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String? = null,
    val sourcePath: String,
    val edlSnapshot: List<PointV1> = emptyList(),
    val presetId: String,
    val outWidth: Int,
    val outHeight: Int,
    val encoderLabel: String,
    val idleTrim: Boolean,
    val outputPath: String,
    // Runtime fields
    var status: RenderStatus = RenderStatus.QUEUED,
    var progress: Double = 0.0,          // 0.0 .. 1.0
    var etaSeconds: Long? = null,
    var bytesWritten: Long = 0,
    // Error reporting (negative cases)
    var failureReason: String? = null,
    var stderrTail: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    var updatedAtEpochMs: Long = System.currentTimeMillis(),
)

/** Simple snapshot for observers */
data class ActiveQueueSnapshot(
    val current: RenderJob?,
    val queued: List<RenderJob>,
)

/**
 * Global queue manager: single worker thread consumes jobs sequentially.
 * Exposes observer callbacks for UI to refresh immediately on state changes.
 */
object RenderQueueManager {
    // public read-only view
    @Volatile private var currentJob: RenderJob? = null
    private val queue = LinkedBlockingQueue<RenderJob>()

    private val observers = CopyOnWriteArrayList<(ActiveQueueSnapshot) -> Unit>()

    private val started = AtomicBoolean(false)
    private val stopSignal = AtomicBoolean(false)

    // Track current ffmpeg process and .part file for cancel/cleanup
    @Volatile private var currentProc: Process? = null
    @Volatile private var currentPartFile: java.io.File? = null
    @Volatile private var currentCanceled: Boolean = false

    /** Cancel the currently running job, if any. */
    fun cancelCurrent() {
        val proc = currentProc
        val job = currentJob
        if (proc != null && job != null && job.status == RenderStatus.RUNNING) {
            println("[QUEUE] Cancel requested for id=${job.id}")
            currentCanceled = true
            try { proc.destroy() } catch (_: Throwable) { }
            try { proc.destroyForcibly() } catch (_: Throwable) { }
        }
    }

    fun addObserver(cb: (ActiveQueueSnapshot) -> Unit) {
        observers.add(cb)
        // Push initial snapshot to new observer
        cb(snapshot())
    }

    fun removeObserver(cb: (ActiveQueueSnapshot) -> Unit) {
        observers.remove(cb)
    }

    private fun notifyObservers() {
        val snap = snapshot()
        observers.forEach { o ->
            try { o(snap) } catch (_: Throwable) {}
        }
    }

    private fun snapshot(): ActiveQueueSnapshot {
        val list = queue.toList()
        return ActiveQueueSnapshot(current = currentJob, queued = list)
    }

    fun enqueue(job: RenderJob) {
        println("[QUEUE] Enqueue job id=${job.id} → ${job.outputPath}")
        job.status = RenderStatus.QUEUED
        job.updatedAtEpochMs = System.currentTimeMillis()
        queue.put(job)
        notifyObservers()
        ensureWorker()
    }

    private fun ensureWorker() {
        if (started.compareAndSet(false, true)) {
            Thread({ workerLoop() }, "RenderQueue-Worker").apply { isDaemon = true }.start()
            println("[QUEUE] Worker started")
        }
    }

    private fun workerLoop() {
        while (!stopSignal.get()) {
            try {
                val job = queue.take() // blocks
                currentJob = job
                job.status = RenderStatus.RUNNING
                job.updatedAtEpochMs = System.currentTimeMillis()
                println("[QUEUE] RUNNING id=${job.id} → ${job.outputPath} (${job.encoderLabel} / ${job.outWidth}x${job.outHeight})")
                notifyObservers()

                // Task 3.8.1 — Actual rendering engine (ffmpeg execution)
                val finalOut = java.io.File(job.outputPath)
                finalOut.parentFile?.let { if (!it.exists()) it.mkdirs() }
                val partOut = java.io.File(job.outputPath + ".part")
                if (partOut.exists()) partOut.delete()

                // Build command
                val presets = ExportPresetsIO.load()
                val defIdx = ExportPresetsIO.defaultBalancedIndex(presets)
                val preset = presets.find { it.id.equals(job.presetId, ignoreCase = true) } ?: presets[defIdx]
                val build = FFmpegCommandBuilder.build(
                    FFmpegCommandBuilder.BuildParams(
                        sourcePath = job.sourcePath,
                        outputPath = partOut.absolutePath,
                        preset = preset,
                        outWidth = job.outWidth,
                        outHeight = job.outHeight,
                        encoderLabel = job.encoderLabel,
                        idleTrim = job.idleTrim,
                        keeps = if (job.idleTrim) job.edlSnapshot else emptyList(),
                    )
                )
                println("[DEBUG] ffmpeg command: ${build.preview}")

                // Resolve ffmpeg executable (FFMPEG_PATH env var overrides PATH)
                fun ffmpegExe(): String {
                    val envPath = System.getenv("FFMPEG_PATH")?.trim().orEmpty()
                    return if (envPath.isNotEmpty()) envPath else "ffmpeg"
                }

                // Start process
                val cmd = mutableListOf<String>()
                cmd += ffmpegExe()
                cmd += build.args
                val pb = ProcessBuilder(cmd)
                pb.redirectErrorStream(false)
                pb.directory(finalOut.parentFile)
                val proc = try { pb.start() } catch (ex: Throwable) {
                    System.err.println("[QUEUE][ERROR] Failed to start ffmpeg: ${ex.message}")
                    job.status = RenderStatus.FAILED
                    job.failureReason = "Failed to start FFmpeg: ${ex.javaClass.simpleName}: ${ex.message}. Ensure ffmpeg is installed and on PATH or set FFMPEG_PATH."
                    job.stderrTail = null
                    job.updatedAtEpochMs = System.currentTimeMillis()
                    notifyObservers()
                    // Clear current and continue
                    currentJob = null
                    notifyObservers()
                    continue
                }
                // Expose current process for cancel; reset cancel flag and remember .part
                currentProc = proc
                currentPartFile = partOut
                currentCanceled = false

                // Capture stdout/stderr asynchronously; keep last N stderr lines
                val tailSize = 200
                val errTail = java.util.ArrayDeque<String>(tailSize)

                // Progress parsing state
                var totalDurationMs: Long? = if (job.idleTrim && job.edlSnapshot.isNotEmpty()) {
                    job.edlSnapshot.fold(0L) { acc, p -> acc + (p.endMs - p.startMs).toLong() }
                } else null
                var lastUpdateMs = 0L
                var lastTimeMs = 0L
                var lastSpeed = 0.0

                val durRegex = Regex("Duration: (\\d{2}):(\\d{2}):(\\d{2}\\.\\d{2})")
                val timeRegex = Regex("time=\\s*(\\d{2}):(\\d{2}):(\\d{2}\\.\\d{2})")
                val sizeRegex = Regex("size=\\s*([0-9.]+)\\s*([kKmMgG]i?[bB])")
                val speedRegex = Regex("speed=\\s*([0-9.]+)x")

                fun hmsToMs(h: String, m: String, s: String): Long {
                    val hh = h.toLong()
                    val mm = m.toLong()
                    val ss = s.toDouble()
                    return ((hh * 3600 + mm * 60) * 1000L) + (ss * 1000).toLong()
                }
                fun parseSizeToBytes(num: Double, unit: String): Long {
                    val u = unit.lowercase()
                    return when {
                        u.startsWith("g") -> (num * 1_000_000_000L).toLong()
                        u.startsWith("m") -> (num * 1_000_000L).toLong()
                        u.startsWith("k") -> (num * 1_000L).toLong()
                        else -> num.toLong()
                    }
                }

                val errReader = Thread({
                    try {
                        proc.errorStream.bufferedReader().use { br ->
                            var line: String?
                            while (br.readLine().also { line = it } != null) {
                                val ln = line!!

                                // keep tail for diagnostics
                                synchronized(errTail) {
                                    if (errTail.size == tailSize) errTail.removeFirst()
                                    errTail.addLast(ln)
                                }

                                // Attempt to extract duration from probe section if not set
                                if (totalDurationMs == null) {
                                    val m = durRegex.find(ln)
                                    if (m != null) {
                                        val (h, m2, s) = m.destructured
                                        totalDurationMs = hmsToMs(h, m2, s)
                                    }
                                }

                                // Parse time/size/speed
                                val t = timeRegex.find(ln)?.destructured
                                if (t != null) {
                                    val (h, m3, s) = t
                                    lastTimeMs = hmsToMs(h, m3, s)
                                }
                                val sz = sizeRegex.find(ln)?.destructured
                                if (sz != null) {
                                    val (num, unit) = sz
                                    job.bytesWritten = parseSizeToBytes(num.toDoubleOrNull() ?: 0.0, unit)
                                } else {
                                    // fallback to filesystem check occasionally
                                    if (System.currentTimeMillis() - lastUpdateMs > 1000 && partOut.exists()) {
                                        job.bytesWritten = partOut.length()
                                    }
                                }
                                val sp = speedRegex.find(ln)?.destructured
                                if (sp != null) {
                                    lastSpeed = sp.component1().toDoubleOrNull() ?: lastSpeed
                                }

                                // Throttle UI updates to ~2.5Hz
                                val now = System.currentTimeMillis()
                                if (now - lastUpdateMs >= 400) {
                                    val total = totalDurationMs
                                    if (total != null && total > 0) {
                                        val prog = (lastTimeMs.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
                                        job.progress = prog
                                        // ETA estimation using progress if available
                                        val elapsed = (now - job.createdAtEpochMs) / 1000.0
                                        val eta = if (prog > 0.0001) ((1 - prog) / prog * elapsed).toLong() else null
                                        // If ffmpeg reports speed, refine ETA
                                        val eta2 = if (lastSpeed > 0.0) (((total - lastTimeMs) / 1000.0) / lastSpeed).toLong() else null
                                        job.etaSeconds = eta2 ?: eta
                                    } else {
                                        // Without total duration, we cannot compute percent reliably
                                        job.progress = 0.0
                                        job.etaSeconds = null
                                    }
                                    job.updatedAtEpochMs = now
                                    notifyObservers()
                                    lastUpdateMs = now
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }, "ffmpeg-stderr-${job.id}")
                errReader.isDaemon = true
                errReader.start()

                val outReader = Thread({
                    try {
                        proc.inputStream.bufferedReader().use { br ->
                            while (br.readLine() != null) { /* discard or log if needed */ }
                        }
                    } catch (_: Throwable) {}
                }, "ffmpeg-stdout-${job.id}")
                outReader.isDaemon = true
                outReader.start()

                val exit = proc.waitFor()
                errReader.join(200)
                outReader.join(200)
                job.updatedAtEpochMs = System.currentTimeMillis()

                // Clear process refs early
                currentProc = null
                currentPartFile = null

                if (currentCanceled) {
                    // Treat as canceled
                    job.status = RenderStatus.CANCELED
                    // Cleanup partial
                    if (partOut.exists()) partOut.delete()
                    println("[QUEUE] CANCELED id=${job.id}")
                    // Notify UI about final state before clearing current
                    notifyObservers()
                } else if (exit == 0) {
                    // Move .part → final (replace if exists)
                    try {
                        java.nio.file.Files.move(
                            partOut.toPath(),
                            finalOut.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        )
                    } catch (mv: Throwable) {
                        System.err.println("[QUEUE][ERROR] Failed to finalize output move: ${mv.message}")
                        job.status = RenderStatus.FAILED
                        // Cleanup partial if any remains
                        if (partOut.exists()) partOut.delete()
                        // Notify UI about failure before clearing current
                        notifyObservers()
                        currentJob = null
                        notifyObservers()
                        continue
                    }
                    job.bytesWritten = if (finalOut.exists()) finalOut.length() else 0
                    job.progress = 1.0
                    job.status = RenderStatus.COMPLETED
                    println("[QUEUE] COMPLETED id=${job.id}")
                    // Notify UI about completion before clearing current
                    notifyObservers()
                } else {
                    job.status = RenderStatus.FAILED
                    // Cleanup partial
                    if (partOut.exists()) partOut.delete()
                    val tailCopy = synchronized(errTail) { errTail.joinToString("\n") }
                    job.stderrTail = tailCopy
                    // Derive a brief failure reason
                    val brief = tailCopy.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.lastOrNull()
                    job.failureReason = if (brief != null) {
                        "ffmpeg exited with code $exit — $brief"
                    } else {
                        "ffmpeg exited with code $exit (see logs)"
                    }
                    System.err.println("[QUEUE][ERROR] ffmpeg exited with code $exit for job ${job.id}. Stderr tail:\n$tailCopy")
                }

                // Clear current and notify
                currentJob = null
                notifyObservers()
            } catch (ie: InterruptedException) {
                // exiting
                break
            } catch (t: Throwable) {
                System.err.println("[QUEUE][ERROR] Worker failure: ${t.message}")
                t.printStackTrace()
                // Attempt to continue loop
            }
        }
        println("[QUEUE] Worker stopped")
    }
}
