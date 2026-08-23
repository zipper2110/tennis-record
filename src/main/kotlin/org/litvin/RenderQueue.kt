package org.litvin

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.export.RenderRequestCoordinator
import org.litvin.export.RenderQueueRequest
import org.litvin.markup.PointV1
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
    val projectName: String? = null,
    val sourcePath: String,
    val edlSnapshot: List<PointV1> = emptyList(),
    val presetId: String,
    val outWidth: Int,
    val outHeight: Int,
    val outputFrameRate: String? = null,
    val encoderLabel: String,
    val idleTrim: Boolean,
    val favoriteOnly: Boolean = false,
    val includeScoreboard: Boolean = false,
    val overlayTimeline: List<OverlaySpan> = emptyList(),
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
    private val logger = KotlinLogging.logger {}

    // public read-only view
    @Volatile private var currentJob: RenderJob? = null
    @Volatile private var currentOwnerId: String? = null
    private val queue = LinkedBlockingQueue<RenderQueueRequest>()

    private data class ObserverRegistration(
        val ownerId: String,
        val callback: (ActiveQueueSnapshot) -> Unit,
    )
    private val observers = CopyOnWriteArrayList<ObserverRegistration>()
    private val requestCoordinator = RenderRequestCoordinator()

    private val started = AtomicBoolean(false)
    private val stopSignal = AtomicBoolean(false)

    /** Cancel the currently running job, if any. */
    fun cancelCurrent() = cancelCurrent(org.litvin.export.LEGACY_RENDER_OWNER_ID)

    internal fun cancelCurrent(ownerId: String) {
        currentJob?.takeIf { currentOwnerId == ownerId }?.let { job ->
            logger.info { "Cancel requested for render job id=${job.id}" }
        }
        requestCoordinator.cancelCurrent(ownerId)
    }

    internal fun closeOwner(ownerId: String) {
        requestCoordinator.closeOwner(ownerId)
    }

    /** Cancel a job that is still waiting in the queue. */
    fun cancelQueued(jobId: String): Boolean = cancelQueued(org.litvin.export.LEGACY_RENDER_OWNER_ID, jobId)

    internal fun cancelQueued(ownerId: String, jobId: String): Boolean {
        val request = queue.toList().firstOrNull {
            it.ownerId == ownerId && it.job.id == jobId && it.job.status == RenderStatus.QUEUED
        }
            ?: return false
        val removed = queue.remove(request)
        if (removed) {
            val job = request.job
            requestCoordinator.finish(request.ownerId, job.id)
            logger.info { "Canceled queued render job id=${job.id}" }
            job.status = RenderStatus.CANCELED
            job.updatedAtEpochMs = System.currentTimeMillis()
            notifyObservers()
        }
        return removed
    }

    fun addObserver(cb: (ActiveQueueSnapshot) -> Unit) =
        addObserver(org.litvin.export.LEGACY_RENDER_OWNER_ID, cb)

    internal fun addObserver(ownerId: String, cb: (ActiveQueueSnapshot) -> Unit) {
        observers.add(ObserverRegistration(ownerId, cb))
        // Push initial snapshot to new observer
        cb(snapshot(ownerId))
    }

    fun removeObserver(cb: (ActiveQueueSnapshot) -> Unit) =
        removeObserver(org.litvin.export.LEGACY_RENDER_OWNER_ID, cb)

    internal fun removeObserver(ownerId: String, cb: (ActiveQueueSnapshot) -> Unit) {
        observers.removeIf { it.ownerId == ownerId && it.callback === cb }
    }

    private fun notifyObservers() {
        observers.forEach { registration ->
            try { registration.callback(snapshot(registration.ownerId)) } catch (_: Throwable) {}
        }
    }

    private fun snapshot(ownerId: String): ActiveQueueSnapshot {
        val list = queue.toList().filter { it.ownerId == ownerId }.map { it.job }
        val current = currentJob.takeIf { currentOwnerId == ownerId }
        return ActiveQueueSnapshot(current = current, queued = list)
    }

    internal fun enqueue(request: RenderQueueRequest) {
        val job = request.job
        logger.info { "Enqueue render job id=${job.id} -> ${job.outputPath}" }
        job.status = RenderStatus.QUEUED
        job.updatedAtEpochMs = System.currentTimeMillis()
        requestCoordinator.register(request.ownerId, job.id)
        try {
            queue.put(request)
        } catch (failure: Throwable) {
            requestCoordinator.finish(request.ownerId, job.id)
            throw failure
        }
        notifyObservers()
        ensureWorker()
    }

    private fun ensureWorker() {
        if (started.compareAndSet(false, true)) {
            Thread({ workerLoop() }, "RenderQueue-Worker").apply { isDaemon = true }.start()
            logger.info { "Render queue worker started" }
        }
    }

    private fun workerLoop() {
        while (!stopSignal.get()) {
            var activeRequest: RenderQueueRequest? = null
            try {
                val request = queue.take() // blocks
                activeRequest = request
                val job = request.job
                val mayContinue = requestCoordinator.markCurrent(request.ownerId, job.id)
                currentJob = job
                currentOwnerId = request.ownerId
                if (!mayContinue) {
                    cancelBeforeProcessStart(request)
                    continue
                }
                job.status = RenderStatus.RUNNING
                job.updatedAtEpochMs = System.currentTimeMillis()
                logger.info { "Render job running id=${job.id} -> ${job.outputPath} (${job.encoderLabel} / ${job.outWidth}x${job.outHeight})" }
                notifyObservers()
                if (abortIfCanceled(request)) continue

                // Task 3.8.1 — Actual rendering engine (ffmpeg execution)
                val finalOut = java.io.File(job.outputPath)
                finalOut.parentFile?.let { if (!it.exists()) it.mkdirs() }
                val partOut = java.io.File(job.outputPath + ".part")
                if (partOut.exists()) partOut.delete()

                // Pre-run validations
                // 1) Source exists
                val srcFile = java.io.File(job.sourcePath)
                if (!srcFile.exists()) {
                    job.status = RenderStatus.FAILED
                    job.failureReason = "Source file missing: ${job.sourcePath}"
                    job.stderrTail = null
                    job.updatedAtEpochMs = System.currentTimeMillis()
                    logger.error { job.failureReason.orEmpty() }
                    notifyObservers()
                    clearCurrent(request)
                    notifyObservers()
                    continue
                }
                // 2) Output directory writable (attempt temp write)
                val outDir = finalOut.parentFile ?: java.io.File(".")
                try {
                    if (!outDir.exists()) outDir.mkdirs()
                    val probe = java.io.File(outDir, ".write_probe_${System.nanoTime()}.tmp")
                    probe.writeText("")
                    probe.delete()
                } catch (ex: Throwable) {
                    job.status = RenderStatus.FAILED
                    job.failureReason = "Cannot write to output directory: ${outDir.absolutePath} — ${ex.javaClass.simpleName}: ${ex.message}"
                    job.stderrTail = null
                    job.updatedAtEpochMs = System.currentTimeMillis()
                    logger.error(ex) { job.failureReason.orEmpty() }
                    notifyObservers()
                    clearCurrent(request)
                    notifyObservers()
                    continue
                }
                if (abortIfCanceled(request, partOut)) continue

                // Prepare scoreboard overlay ASS file if requested
                var assFile: java.io.File? = null
                if (job.includeScoreboard && job.overlayTimeline.isNotEmpty()) {
                    try {
                        assFile = java.io.File(partOut.absolutePath + ".ass")
                        AssOverlayWriter.write(assFile!!, job.overlayTimeline, job.outWidth, job.outHeight)
                    } catch (t: Throwable) {
                        logger.warn(t) { "Failed to prepare overlay ASS; proceeding without overlay" }
                        assFile = null
                    }
                }
                if (abortIfCanceled(request, partOut, assFile)) continue

                // Pre-probe total duration when not using EDL trimming (needed for percentage)
                var probedDurationMs: Long? = null
                if (!(job.idleTrim && job.edlSnapshot.isNotEmpty())) {
                    try {
                        val pbProbe = ProcessBuilder(
                            ApplicationLayout.current().ffprobeExecutable,
                            "-v", "error",
                            "-show_entries", "format=duration",
                            "-of", "default=nk=1:nw=1",
                            job.sourcePath
                        )
                        pbProbe.redirectErrorStream(true)
                        val pr = requestCoordinator.startProcess(request.ownerId, job.id) { pbProbe.start() }
                        if (pr == null) {
                            cancelBeforeProcessStart(request, partOut, assFile)
                            continue
                        }
                        val out = try {
                            pr.inputStream.bufferedReader().readText().trim().also { pr.waitFor() }
                        } finally {
                            requestCoordinator.clearProcess(request.ownerId, job.id, pr)
                        }
                        val seconds = out.toDoubleOrNull()
                        if (seconds != null && seconds.isFinite() && seconds > 0) {
                            probedDurationMs = (seconds * 1000).toLong()
                        }
                    } catch (_: Throwable) { /* ignore probe errors */ }
                }
                if (abortIfCanceled(request, partOut, assFile)) continue

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
                        outputFrameRate = job.outputFrameRate,
                        encoderLabel = job.encoderLabel,
                        idleTrim = job.idleTrim,
                        keeps = if (job.idleTrim) job.edlSnapshot else emptyList(),
                        subtitlesAssPath = assFile?.absolutePath,
                        adjustments = try { request.adjustments.get() } catch (_: Throwable) { null }
                    )
                )
                if (abortIfCanceled(request, partOut, assFile)) continue
                logger.debug { "ffmpeg command: ${build.preview}" }

                // Start process
                val cmd = mutableListOf<String>()
                cmd += ApplicationLayout.current().ffmpegExecutable
                cmd += build.args
                val pb = ProcessBuilder(cmd)
                pb.redirectErrorStream(false)
                pb.directory(finalOut.parentFile)
                val proc = try {
                    requestCoordinator.startProcess(request.ownerId, job.id) { pb.start() }
                } catch (ex: Throwable) {
                    logger.error(ex) { "Failed to start ffmpeg" }
                    job.status = RenderStatus.FAILED
                    job.failureReason = "Failed to start FFmpeg: ${ex.javaClass.simpleName}: ${ex.message}. Run Tennis Record distribution diagnostics for details."
                    job.stderrTail = null
                    job.updatedAtEpochMs = System.currentTimeMillis()
                    notifyObservers()
                    // Clear current and continue
                    clearCurrent(request)
                    notifyObservers()
                    continue
                }
                if (proc == null) {
                    cancelBeforeProcessStart(request, partOut, assFile)
                    continue
                }

                // Capture stdout/stderr asynchronously; keep last N stderr lines
                val tailSize = 200
                val errTail = java.util.ArrayDeque<String>(tailSize)

                // Progress parsing state
                var totalDurationMs: Long? = if (job.idleTrim && job.edlSnapshot.isNotEmpty()) {
                    job.edlSnapshot.fold(0L) { acc, p -> acc + (p.endMs - p.startMs).toLong() }
                } else probedDurationMs
                var lastUpdateMs = 0L
                var lastTimeMs = 0L
                var lastSpeed = 0.0
                
                val durRegex = Regex("""Duration:\s*(\d{2}):(\d{2}):(\d{2}(?:\.\d{1,6})?)(?:,|\s)""")
                val timeRegex = Regex("""time=\s*(\d{2}):(\d{2}):(\d{2}(?:\.\d{1,6})?)""")
                val sizeRegex = Regex("""size=\s*([0-9.]+)\s*([kKmMgG]i?[bB])""")
                val speedRegex = Regex("""speed=\s*([0-9.]+)x""")
                
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
                            val kvRegex = Regex("^([a-z_]+)=(.*)$", RegexOption.IGNORE_CASE)
                            while (br.readLine().also { line = it } != null) {
                                val ln = line!!.trimEnd('\r')

                                // keep tail for diagnostics
                                synchronized(errTail) {
                                    if (errTail.size == tailSize) errTail.removeFirst()
                                    errTail.addLast(ln)
                                }

                                // Parse -progress key=value lines first (robust across builds)
                                val kv = kvRegex.find(ln)?.destructured
                                if (kv != null) {
                                    val (kRaw, vRaw) = kv
                                    val k = kRaw.lowercase()
                                    val v = vRaw.trim()
                                    when (k) {
                                        "out_time_ms" -> {
                                            val ms = v.toLongOrNull()
                                            if (ms != null) lastTimeMs = ms
                                        }
                                        "out_time_us" -> {
                                            val us = v.toLongOrNull()
                                            if (us != null) lastTimeMs = us / 1000
                                        }
                                        "out_time" -> {
                                            // format HH:MM:SS.micro
                                            val parts = v.split(":")
                                            if (parts.size == 3) {
                                                lastTimeMs = hmsToMs(parts[0], parts[1], parts[2])
                                            }
                                        }
                                        "total_size" -> {
                                            val b = v.toLongOrNull()
                                            if (b != null) job.bytesWritten = b
                                        }
                                        "speed" -> {
                                            val s = v.removeSuffix("x").toDoubleOrNull()
                                            if (s != null) lastSpeed = s
                                        }
                                        "progress" -> {
                                            if (v.equals("end", true)) {
                                                job.progress = 1.0
                                                job.updatedAtEpochMs = System.currentTimeMillis()
                                                notifyObservers()
                                            }
                                        }
                                    }
                                    // After handling kv, continue to throttled UI update below
                                } else {
                                    // Attempt to extract duration from banner if not set yet
                                    if (totalDurationMs == null) {
                                        val m = durRegex.find(ln)
                                        if (m != null) {
                                            val (h, m2, s) = m.destructured
                                            totalDurationMs = hmsToMs(h, m2, s)
                                        }
                                    }

                                    // Fallback: classic stderr status parsing (time= size= speed=)
                                    val t = timeRegex.find(ln)?.destructured
                                    if (t != null) {
                                        val (h, m3, s) = t
                                        lastTimeMs = hmsToMs(h, m3, s)
                                    }
                                    val sz = sizeRegex.find(ln)?.destructured
                                    if (sz != null) {
                                        val (num, unit) = sz
                                        job.bytesWritten = parseSizeToBytes(num.toDoubleOrNull() ?: 0.0, unit)
                                    }
                                    val sp = speedRegex.find(ln)?.destructured
                                    if (sp != null) {
                                        lastSpeed = sp.component1().toDoubleOrNull() ?: lastSpeed
                                    }
                                }

                                // Fallback to filesystem check occasionally if size unknown
                                if (job.bytesWritten <= 0 && System.currentTimeMillis() - lastUpdateMs > 1000 && partOut.exists()) {
                                    job.bytesWritten = partOut.length()
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
                                        // Without total duration, show unknown ETA and keep 0%
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

                // Clear the process reference while retaining cancellation until request completion.
                requestCoordinator.clearProcess(request.ownerId, job.id, proc)

                if (requestCoordinator.isCanceled(request.ownerId, job.id)) {
                    // Treat as canceled
                    job.status = RenderStatus.CANCELED
                    // Cleanup partial
                    if (partOut.exists()) partOut.delete()
                    // Remove temp ASS if any
                    try { java.io.File(partOut.absolutePath + ".ass").delete() } catch (_: Throwable) {}
                    logger.info { "Render job canceled id=${job.id}" }
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
                        logger.error(mv) { "Failed to finalize output move for render job ${job.id}" }
                        job.status = RenderStatus.FAILED
                        val tailCopy = try { synchronized(errTail) { errTail.joinToString("\n") } } catch (_: Throwable) { null }
                        job.stderrTail = tailCopy
                        job.failureReason = "Failed to finalize output file move: ${mv.javaClass.simpleName}: ${mv.message}"
                        // Cleanup partial if any remains
                        if (partOut.exists()) partOut.delete()
                        // Remove temp ASS if any
                        try { java.io.File(partOut.absolutePath + ".ass").delete() } catch (_: Throwable) {}
                        // Notify UI about failure before clearing current
                        notifyObservers()
                        clearCurrent(request)
                        notifyObservers()
                        continue
                    }
                    job.bytesWritten = if (finalOut.exists()) finalOut.length() else 0
                    job.progress = 1.0
                    job.status = RenderStatus.COMPLETED
                    logger.info { "Render job completed id=${job.id}" }
                    // Remove temp ASS if any
                    try { java.io.File(partOut.absolutePath + ".ass").delete() } catch (_: Throwable) {}
                    // Persist to Completed store (Task 3.11)
                    try { request.completedRenders.append(job) } catch (_: Throwable) { }
                    // Notify UI about completion before clearing current
                    notifyObservers()
                } else {
                    job.status = RenderStatus.FAILED
                    // Cleanup partial
                    if (partOut.exists()) partOut.delete()
                    // Remove temp ASS if any
                    try { java.io.File(partOut.absolutePath + ".ass").delete() } catch (_: Throwable) {}
                    val tailCopy = synchronized(errTail) { errTail.joinToString("\n") }
                    job.stderrTail = tailCopy
                    // Derive a brief failure reason
                    val brief = tailCopy.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.lastOrNull()
                    job.failureReason = if (brief != null) {
                        "ffmpeg exited with code $exit — $brief"
                    } else {
                        "ffmpeg exited with code $exit (see logs)"
                    }
                    logger.error { "ffmpeg exited with code $exit for job ${job.id}. Stderr tail:\n$tailCopy" }
                }

                // Clear current and notify
                clearCurrent(request)
                notifyObservers()
            } catch (ie: InterruptedException) {
                // exiting
                break
            } catch (t: Throwable) {
                logger.error(t) { "Render queue worker failure" }
                activeRequest?.let { request ->
                    request.job.status = if (requestCoordinator.isCanceled(request.ownerId, request.job.id)) {
                        RenderStatus.CANCELED
                    } else {
                        RenderStatus.FAILED
                    }
                    clearCurrent(request)
                    notifyObservers()
                }
            }
        }
        logger.info { "Render queue worker stopped" }
    }

    private fun abortIfCanceled(
        request: RenderQueueRequest,
        partFile: java.io.File? = null,
        assFile: java.io.File? = null,
    ): Boolean {
        if (!requestCoordinator.isCanceled(request.ownerId, request.job.id)) return false
        cancelBeforeProcessStart(request, partFile, assFile)
        return true
    }

    private fun cancelBeforeProcessStart(
        request: RenderQueueRequest,
        partFile: java.io.File? = null,
        assFile: java.io.File? = null,
    ) {
        request.job.status = RenderStatus.CANCELED
        request.job.updatedAtEpochMs = System.currentTimeMillis()
        try { partFile?.delete() } catch (_: Throwable) { }
        try { assFile?.delete() } catch (_: Throwable) { }
        notifyObservers()
        clearCurrent(request)
        notifyObservers()
    }

    private fun clearCurrent(request: RenderQueueRequest) {
        currentJob = null
        currentOwnerId = null
        requestCoordinator.finish(request.ownerId, request.job.id)
    }
}
