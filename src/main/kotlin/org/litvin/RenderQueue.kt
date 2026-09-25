package org.litvin

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.export.RenderRequestCoordinator
import org.litvin.export.RenderQueueRequest
import org.litvin.export.RenderTerminalOutcome
import org.litvin.points.PointV1
import org.litvin.scoring.ScoreboardSettingsV1
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
    // Target video bitrate in kilobits per second. Null uses the bitrate table of the preset.
    val videoBitrateK: Int? = null,
    // Estimated size of the output file in bytes. Null when the duration or the bitrate is not known.
    val expectedBytes: Long? = null,
    val encoderLabel: String,
    val idleTrim: Boolean,
    val favoriteOnly: Boolean = false,
    val includeScoreboard: Boolean = false,
    val overlayTimeline: List<OverlaySpan> = emptyList(),
    val scoreboardSettings: ScoreboardSettingsV1 = ScoreboardSettingsV1(),
    val outputPath: String,
    val includeComments: Boolean = false,
    val commentOverlayTimeline: List<CommentOverlaySpan> = emptyList(),
    // The statistics card after the last point, on a frozen last frame. Null when the export has no card.
    val statsCard: org.litvin.stats.StatsCardVideo? = null,
    // The statistics card of each completed set, after the last kept point of the set.
    val setSummaries: List<org.litvin.stats.SetSummaryCard> = emptyList(),
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
            logger.info { "Canceled queued render job id=${job.id}" }
            job.status = RenderStatus.CANCELED
            job.updatedAtEpochMs = System.currentTimeMillis()
            finishRequest(request)
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

                // Prepare the requested overlay ASS file.
                var assFile: java.io.File? = null
                try {
                    assFile = RenderOverlayScript.writeFor(job, partOut)
                } catch (t: Throwable) {
                    logger.warn(t) { "Failed to prepare overlay ASS; proceeding without overlay" }
                    assFile = null
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

                // The crop/rotate geometry needs the source aspect. Probe the size only when the geometry is used.
                val jobAdjustments = try { request.adjustments.get() } catch (_: Throwable) { null }
                val sourceSize = jobAdjustments
                    ?.takeIf { !org.litvin.adjustments.GeometryPlan.of(it, job.outWidth, job.outHeight).isIdentity }
                    ?.let { org.litvin.export.ExportResolutionProbe.probe(job.sourcePath, ApplicationLayout.current().ffprobeExecutable) }

                // Build command(s). Idle trim gives every kept segment its own seeked input, and
                // ffmpeg allocates a decoder for each input up front, so a long match is encoded in
                // bounded chunks that are joined afterwards without re-encoding.
                val presets = ExportPresetsIO.load()
                val defIdx = ExportPresetsIO.defaultBalancedIndex(presets)
                val preset = presets.find { it.id.equals(job.presetId, ignoreCase = true) } ?: presets[defIdx]
                val keeps = if (job.idleTrim) job.edlSnapshot else emptyList()
                // The video chunks and the statistics cards, in output order. A card is one more pass on a
                // frozen frame, so a job with a card always joins passes.
                val passes = org.litvin.export.ExportPassPlanner.plan(
                    keeps = keeps,
                    setSummaries = job.setSummaries,
                    endCard = job.statsCard,
                    fullVideoDurationMs = probedDurationMs,
                )

                // Keep the last N stderr lines for diagnostics, across every pass of this job.
                val tailSize = 200
                val errTail = java.util.ArrayDeque<String>(tailSize)

                val videoDurationMs: Long? = if (keeps.isNotEmpty()) {
                    org.litvin.export.ExportChunkPlanner.keptDurationMs(keeps)
                } else {
                    probedDurationMs
                }
                val totalDurationMs: Long? = videoDurationMs?.plus(org.litvin.export.ExportPassPlanner.cardDurationMs(passes))

                fun buildFor(
                    passKeeps: List<PointV1>,
                    target: java.io.File,
                    outputOffsetMs: Long,
                    chunkOutput: Boolean,
                    freezeFrame: FFmpegCommandBuilder.FreezeFrame? = null,
                    subtitlesPath: String? = assFile?.absolutePath,
                ) = FFmpegCommandBuilder.build(
                    FFmpegCommandBuilder.BuildParams(
                        sourcePath = job.sourcePath,
                        outputPath = target.absolutePath,
                        preset = preset,
                        outWidth = job.outWidth,
                        outHeight = job.outHeight,
                        outputFrameRate = job.outputFrameRate,
                        encoderLabel = job.encoderLabel,
                        idleTrim = job.idleTrim,
                        keeps = passKeeps,
                        subtitlesAssPath = subtitlesPath,
                        adjustments = jobAdjustments,
                        sourceWidth = sourceSize?.width,
                        sourceHeight = sourceSize?.height,
                        outputTimeOffsetMs = outputOffsetMs,
                        chunkOutput = chunkOutput,
                        videoBitrateK = job.videoBitrateK,
                        freezeFrame = freezeFrame,
                    )
                )

                val chunkFiles = mutableListOf<java.io.File>()
                val concatListFile = java.io.File(partOut.absolutePath + ".concat.txt")
                val cardAssFiles = mutableListOf<java.io.File>()
                fun discardIntermediates() {
                    chunkFiles.forEach { file -> try { file.delete() } catch (_: Throwable) { } }
                    try { concatListFile.delete() } catch (_: Throwable) { }
                    cardAssFiles.forEach { file -> try { file.delete() } catch (_: Throwable) { } }
                }

                var exit = 0
                var abandoned = false

                if (passes.size == 1) {
                    val build = buildFor(keeps, partOut, 0L, chunkOutput = false)
                    logger.debug { "ffmpeg command: ${build.preview}" }
                    val pass = runFfmpegPass(
                        request, build.args, finalOut.parentFile, partOut, errTail, tailSize,
                        totalDurationMs, 0L, 0L,
                    )
                    when (pass) {
                        is PassResult.Exited -> exit = pass.code
                        is PassResult.StartFailed -> {
                            reportStartFailure(request, pass.cause)
                            abandoned = true
                        }
                        PassResult.CanceledBeforeStart -> {
                            cancelBeforeProcessStart(request, partOut, assFile)
                            abandoned = true
                        }
                    }
                } else {
                    logger.info { "Render job ${job.id}: ${keeps.size} segments over ${passes.size} encode passes" }
                    var bytesBase = 0L
                    for ((index, pass) in passes.withIndex()) {
                        if (abortIfCanceled(request, partOut, assFile)) {
                            discardIntermediates()
                            abandoned = true
                            break
                        }
                        val passFile = java.io.File(partOut.absolutePath + ".chunk$index.ts")
                        val build = when (pass) {
                            is org.litvin.export.ExportPassPlanner.Video ->
                                buildFor(pass.chunk.keeps, passFile, pass.chunk.outputOffsetMs, chunkOutput = true)
                            is org.litvin.export.ExportPassPlanner.Card -> {
                                // The card pages are burned in over a frozen frame, without the scoreboard.
                                val cardAss = java.io.File(partOut.absolutePath + ".card$index.ass")
                                cardAssFiles += cardAss
                                try {
                                    AssOverlayWriter.writeStatsCard(cardAss, pass.card, job.outWidth, job.outHeight)
                                } catch (t: Throwable) {
                                    logger.warn(t) { "Failed to prepare a statistics card; the export does not show it" }
                                    continue
                                }
                                val freeze = FFmpegCommandBuilder.FreezeFrame(pass.freezeAtMs, pass.card.durationMs)
                                buildFor(emptyList(), passFile, 0L, chunkOutput = true, freeze, cardAss.absolutePath)
                            }
                        }
                        if (passFile.exists()) passFile.delete()
                        chunkFiles += passFile
                        logger.debug { "ffmpeg pass ${index + 1}/${passes.size}: ${build.preview}" }
                        val result = runFfmpegPass(
                            request, build.args, finalOut.parentFile, passFile, errTail, tailSize,
                            totalDurationMs, pass.progressBaseMs, bytesBase,
                        )
                        when (result) {
                            is PassResult.Exited -> exit = result.code
                            is PassResult.StartFailed -> {
                                discardIntermediates()
                                reportStartFailure(request, result.cause)
                                abandoned = true
                            }
                            PassResult.CanceledBeforeStart -> {
                                discardIntermediates()
                                cancelBeforeProcessStart(request, partOut, assFile)
                                abandoned = true
                            }
                        }
                        if (abandoned || exit != 0) break
                        bytesBase += if (passFile.exists()) passFile.length() else 0L
                    }

                    // Join the chunks into the real container. Copying the streams keeps the join a
                    // remux rather than a second encode.
                    if (!abandoned && exit == 0) {
                        if (abortIfCanceled(request, partOut, assFile)) {
                            discardIntermediates()
                            abandoned = true
                        } else {
                            concatListFile.writeText(
                                chunkFiles.joinToString("\n") { file -> concatEntry(file) } + "\n"
                            )
                            val joinArgs = mutableListOf(
                                "-y", "-v", "info", "-progress", "pipe:2", "-nostats",
                                "-f", "concat", "-safe", "0", "-i", concatListFile.absolutePath,
                                "-c", "copy",
                                // TS carries AAC as ADTS frames; MP4 wants raw AAC with the config in
                                // the sample entry, which is what this bitstream filter converts.
                                "-bsf:a", "aac_adtstoasc",
                            )
                            preset.container?.format?.let { fmt -> joinArgs += listOf("-f", fmt) }
                            if (preset.container?.fastStart == true) {
                                joinArgs += listOf("-movflags", "+faststart")
                            }
                            joinArgs += partOut.absolutePath
                            logger.debug { "ffmpeg join: ${joinArgs.joinToString(" ")}" }
                            // The join writes the whole output afresh, so its byte count starts at
                            // zero; the progress bar is already full and just waits out the remux.
                            val pass = runFfmpegPass(
                                request, joinArgs, finalOut.parentFile, partOut, errTail, tailSize,
                                totalDurationMs, totalDurationMs ?: 0L, 0L,
                            )
                            when (pass) {
                                is PassResult.Exited -> exit = pass.code
                                is PassResult.StartFailed -> {
                                    discardIntermediates()
                                    reportStartFailure(request, pass.cause)
                                    abandoned = true
                                }
                                PassResult.CanceledBeforeStart -> {
                                    discardIntermediates()
                                    cancelBeforeProcessStart(request, partOut, assFile)
                                    abandoned = true
                                }
                            }
                        }
                    }
                    discardIntermediates()
                }
                if (abandoned) continue
                job.updatedAtEpochMs = System.currentTimeMillis()

                val terminalized = when {
                    requestCoordinator.isCanceled(request.ownerId, job.id) -> false
                    exit == 0 -> requestCoordinator.terminalize(request.ownerId, job.id) {
                        try {
                            java.nio.file.Files.move(
                                partOut.toPath(),
                                finalOut.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            )
                            job.bytesWritten = if (finalOut.exists()) finalOut.length() else 0
                            job.progress = 1.0
                            job.status = RenderStatus.COMPLETED
                            logger.info { "Render job completed id=${job.id}" }
                            try { java.io.File(partOut.absolutePath + ".ass").delete() } catch (_: Throwable) { }
                            try { request.completedRenders.append(job) } catch (_: Throwable) { }
                        } catch (moveFailure: Throwable) {
                            logger.error(moveFailure) { "Failed to finalize output move for render job ${job.id}" }
                            job.status = RenderStatus.FAILED
                            job.stderrTail = try {
                                synchronized(errTail) { errTail.joinToString("\n") }
                            } catch (_: Throwable) {
                                null
                            }
                            job.failureReason = "Failed to finalize output file move: ${moveFailure.javaClass.simpleName}: ${moveFailure.message}"
                            if (partOut.exists()) partOut.delete()
                            try { java.io.File(partOut.absolutePath + ".ass").delete() } catch (_: Throwable) { }
                        }
                        notifyObservers()
                    }
                    else -> requestCoordinator.terminalize(request.ownerId, job.id) {
                        job.status = RenderStatus.FAILED
                        if (partOut.exists()) partOut.delete()
                        try { java.io.File(partOut.absolutePath + ".ass").delete() } catch (_: Throwable) { }
                        val tailCopy = synchronized(errTail) { errTail.joinToString("\n") }
                        job.stderrTail = tailCopy
                        val brief = tailCopy.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.lastOrNull()
                        job.failureReason = if (brief != null) {
                            "ffmpeg exited with code $exit — $brief"
                        } else {
                            "ffmpeg exited with code $exit (see logs)"
                        }
                        logger.error { "ffmpeg exited with code $exit for job ${job.id}. Stderr tail:\n$tailCopy" }
                        notifyObservers()
                    }
                }
                if (!terminalized) {
                    job.status = RenderStatus.CANCELED
                    if (partOut.exists()) partOut.delete()
                    try { java.io.File(partOut.absolutePath + ".ass").delete() } catch (_: Throwable) { }
                    logger.info { "Render job canceled id=${job.id}" }
                    notifyObservers()
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

    private sealed interface PassResult {
        data class Exited(val code: Int) : PassResult
        data class StartFailed(val cause: Throwable) : PassResult
        object CanceledBeforeStart : PassResult
    }

    /** One line of a concat demuxer list file; the path is single-quoted, so quotes are escaped. */
    private fun concatEntry(file: java.io.File): String =
        "file '" + file.absolutePath.replace("'", "'\\''") + "'"

    private fun reportStartFailure(request: RenderQueueRequest, cause: Throwable) {
        val job = request.job
        logger.error(cause) { "Failed to start ffmpeg" }
        job.status = RenderStatus.FAILED
        job.failureReason = "Failed to start FFmpeg: ${cause.javaClass.simpleName}: ${cause.message}. Run Tennis Record distribution diagnostics for details."
        job.stderrTail = null
        job.updatedAtEpochMs = System.currentTimeMillis()
        notifyObservers()
        clearCurrent(request)
        notifyObservers()
    }

    /**
     * Runs one ffmpeg pass to completion, streaming its `-progress` output into the job.
     *
     * A chunked export runs this several times. Each pass reports its own output time starting at
     * zero, so [progressBaseMs] carries the output time already produced and [bytesBase] the bytes
     * already written, which keeps the job showing one continuous progress bar and byte count.
     */
    private fun runFfmpegPass(
        request: RenderQueueRequest,
        args: List<String>,
        workingDir: java.io.File?,
        sizeProbeFile: java.io.File,
        errTail: java.util.ArrayDeque<String>,
        tailSize: Int,
        totalDurationMs: Long?,
        progressBaseMs: Long,
        bytesBase: Long,
    ): PassResult {
        val job = request.job
        val cmd = mutableListOf<String>()
        cmd += ApplicationLayout.current().ffmpegExecutable
        cmd += args
        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(false)
        pb.directory(workingDir)
        val proc = try {
            requestCoordinator.startProcess(request.ownerId, job.id) { pb.start() }
        } catch (ex: Throwable) {
            return PassResult.StartFailed(ex)
        } ?: return PassResult.CanceledBeforeStart

        // Progress parsing state
        var resolvedTotalMs: Long? = totalDurationMs
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
                                    if (b != null) job.bytesWritten = bytesBase + b
                                }
                                "speed" -> {
                                    val s = v.removeSuffix("x").toDoubleOrNull()
                                    if (s != null) lastSpeed = s
                                }
                                "progress" -> {
                                    if (v.equals("end", true)) {
                                        // Only the final pass completes the job; earlier chunks just
                                        // hand their output time over to the next one.
                                        val total = resolvedTotalMs
                                        val done = progressBaseMs + lastTimeMs
                                        job.progress = if (total != null && total > 0) {
                                            (done.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
                                        } else {
                                            1.0
                                        }
                                        job.updatedAtEpochMs = System.currentTimeMillis()
                                        notifyObservers()
                                    }
                                }
                            }
                            // After handling kv, continue to throttled UI update below
                        } else {
                            // Attempt to extract duration from banner if not set yet
                            if (resolvedTotalMs == null) {
                                val m = durRegex.find(ln)
                                if (m != null) {
                                    val (h, m2, s) = m.destructured
                                    resolvedTotalMs = hmsToMs(h, m2, s)
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
                                job.bytesWritten = bytesBase + parseSizeToBytes(num.toDoubleOrNull() ?: 0.0, unit)
                            }
                            val sp = speedRegex.find(ln)?.destructured
                            if (sp != null) {
                                lastSpeed = sp.component1().toDoubleOrNull() ?: lastSpeed
                            }
                        }

                        // Fallback to filesystem check occasionally if size unknown
                        if (job.bytesWritten <= bytesBase && System.currentTimeMillis() - lastUpdateMs > 1000 && sizeProbeFile.exists()) {
                            job.bytesWritten = bytesBase + sizeProbeFile.length()
                        }

                        // Throttle UI updates to ~2.5Hz
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateMs >= 400) {
                            val total = resolvedTotalMs
                            if (total != null && total > 0) {
                                val done = progressBaseMs + lastTimeMs
                                val prog = (done.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
                                job.progress = prog
                                // ETA estimation using progress if available
                                val elapsed = (now - job.createdAtEpochMs) / 1000.0
                                val eta = if (prog > 0.0001) ((1 - prog) / prog * elapsed).toLong() else null
                                // If ffmpeg reports speed, refine ETA
                                val eta2 = if (lastSpeed > 0.0) (((total - done) / 1000.0) / lastSpeed).toLong() else null
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
        return PassResult.Exited(exit)
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
        finishRequest(request)
    }

    private fun finishRequest(request: RenderQueueRequest) {
        requestCoordinator.finish(request.ownerId, request.job.id)
        val outcome = when (request.job.status) {
            RenderStatus.COMPLETED -> RenderTerminalOutcome.COMPLETED
            RenderStatus.CANCELED -> RenderTerminalOutcome.CANCELED
            else -> RenderTerminalOutcome.FAILED
        }
        try {
            request.signalTerminal(outcome)
        } catch (failure: Throwable) {
            logger.warn(failure) { "Render terminal callback failed for job ${request.job.id}" }
        }
    }
}
