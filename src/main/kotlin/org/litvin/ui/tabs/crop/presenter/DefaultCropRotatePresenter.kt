package org.litvin.ui.tabs.crop.presenter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.media.StillFrameCaptureService
import org.litvin.media.VlcjStillFrameCaptureService
import org.litvin.projects.ManifestIO
import java.awt.EventQueue
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DefaultCropRotatePresenter(
    private val frameService: StillFrameCaptureService = VlcjStillFrameCaptureService(),
) : CropRotatePresenter {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "crop-rotate-frame-capture").apply { isDaemon = true }
    }
    private val frameSequence = AtomicInteger(0)
    private val seekDurationsMs = ArrayDeque<Long>()

    private var view: CropRotateView? = null
    private var unsubscribeStore: (() -> Unit)? = null
    private var projectManifestPath: String? = null
    private var projectDir: String? = null
    private var pendingSeek: ScheduledFuture<*>? = null
    private var active = false
    private var consecutiveFrameFailures = 0

    private var state = CropRotateViewState()

    override fun attach(view: CropRotateView) {
        this.view = view
        render()
    }

    override fun detach() {
        unsubscribeStore?.invoke()
        unsubscribeStore = null
        view = null
    }

    override fun onActivated() {
        active = true
        unsubscribeStore?.invoke()
        unsubscribeStore = AdjustmentsStore.subscribe { adjustments ->
            updateState {
                it.copy(adjustments = adjustments, banner = failureBanner())
            }
        }
        updateState { it.copy(adjustments = AdjustmentsStore.get()) }
        requestFrame(state.seekMs, immediate = true)
    }

    override fun onDeactivated() {
        active = false
        pendingSeek?.cancel(false)
        pendingSeek = null
        AdjustmentsStore.save(projectDir)
    }

    override fun onIntent(intent: CropRotateIntent) {
        when (intent) {
            is CropRotateIntent.LoadProject -> loadProject(intent.manifestPath)
            is CropRotateIntent.SeekTo -> {
                val target = intent.ms.coerceIn(0L, playableDuration())
                updateState { it.copy(seekMs = target) }
                requestFrame(target, immediate = intent.immediate)
            }
            is CropRotateIntent.ChangeTransform -> {
                val incoming = intent.adjustments.copy(
                    zoom = intent.adjustments.zoom.coerceIn(0.1f, 4.0f),
                    panX = intent.adjustments.panX.coerceIn(-1.0f, 1.0f),
                    panY = intent.adjustments.panY.coerceIn(-1.0f, 1.0f),
                    rotationDeg = intent.adjustments.rotationDeg.coerceIn(-180.0f, 180.0f),
                )
                val next = state.adjustments.copy(
                    zoom = incoming.zoom,
                    panX = incoming.panX,
                    panY = incoming.panY,
                    rotationDeg = incoming.rotationDeg,
                )
                updateState { it.copy(adjustments = next) }
                AdjustmentsStore.set { previous ->
                    previous.copy(
                        zoom = incoming.zoom,
                        panX = incoming.panX,
                        panY = incoming.panY,
                        rotationDeg = incoming.rotationDeg,
                    )
                }
            }
            CropRotateIntent.ResetTransform -> {
                AdjustmentsStore.set { previous ->
                    previous.copy(zoom = 1.0f, panX = 0.0f, panY = 0.0f, rotationDeg = 0.0f)
                }
            }
            CropRotateIntent.ResetAll -> AdjustmentsStore.reset()
        }
    }

    fun dispose() {
        onDeactivated()
        detach()
        executor.shutdownNow()
        frameService.close()
    }

    private fun loadProject(manifestPath: String) {
        projectManifestPath = manifestPath
        projectDir = File(manifestPath).parentFile?.absolutePath
        projectDir?.let { AdjustmentsStore.load(it) }
        updateState { it.copy(adjustments = AdjustmentsStore.get(), frame = null, seekMs = 0L, durationMs = 0L) }

        executor.execute {
            try {
                val manifest = ManifestIO.read(manifestPath)
                val source = manifest.sourceVideo?.let { File(it) }
                if (source == null || !source.exists()) {
                    postEffect(CropRotateViewEffect.ShowError("Project source video is missing."))
                    return@execute
                }
                val info = frameService.load(source)
                val aspect = info.frameSize?.let { dim ->
                    if (dim.width > 0 && dim.height > 0) dim.width.toDouble() / dim.height.toDouble() else 16.0 / 9.0
                } ?: 16.0 / 9.0
                updateState {
                    it.copy(
                        durationMs = info.durationMs.coerceAtLeast(0L),
                        outputAspect = aspect,
                        banner = null,
                    )
                }
                requestFrame(0L, immediate = true)
            } catch (t: Throwable) {
                postEffect(CropRotateViewEffect.ShowError(t.message ?: "Could not load source video."))
            }
        }
    }

    private fun requestFrame(ms: Long, immediate: Boolean) {
        if (!active && state.frame != null) return
        pendingSeek?.cancel(false)
        val sequence = frameSequence.incrementAndGet()
        val task = Runnable { captureFrame(sequence, ms) }
        updateState { it.copy(frameLoading = true) }
        pendingSeek = if (immediate) {
            executor.schedule(task, 0L, TimeUnit.MILLISECONDS)
        } else {
            executor.schedule(task, 120L, TimeUnit.MILLISECONDS)
        }
    }

    private fun captureFrame(sequence: Int, ms: Long) {
        val start = System.nanoTime()
        val frame = try {
            frameService.captureAt(ms)
        } catch (_: Throwable) {
            null
        }
        if (sequence != frameSequence.get()) return

        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        synchronized(seekDurationsMs) {
            seekDurationsMs.addLast(elapsedMs)
            while (seekDurationsMs.size > 40) seekDurationsMs.removeFirst()
        }
        if (frame == null) consecutiveFrameFailures++ else consecutiveFrameFailures = 0

        updateState {
            it.copy(
                frame = frame ?: it.frame,
                frameLoading = false,
                banner = failureBanner(),
            )
        }
        logPerformanceIfEnabled()
    }

    private fun failureBanner(): String? {
        return if (consecutiveFrameFailures >= 3) {
            "Frame preview is having trouble updating; keeping the last good frame."
        } else {
            null
        }
    }

    private fun playableDuration(): Long = state.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE

    private fun updateState(reducer: (CropRotateViewState) -> CropRotateViewState) {
        state = reducer(state)
        render()
    }

    private fun render() {
        val snapshot = state
        val target = view ?: return
        if (EventQueue.isDispatchThread()) {
            target.render(snapshot)
        } else {
            EventQueue.invokeLater { view?.render(snapshot) }
        }
    }

    private fun postEffect(effect: CropRotateViewEffect) {
        if (EventQueue.isDispatchThread()) {
            view?.renderEffect(effect)
        } else {
            EventQueue.invokeLater { view?.renderEffect(effect) }
        }
    }

    private fun logPerformanceIfEnabled() {
        if (System.getProperty("tennisrecord.crop.debug") != "true") return
        val samples = synchronized(seekDurationsMs) { seekDurationsMs.sorted() }
        if (samples.isEmpty()) return
        val p50 = samples[(samples.size * 0.50).toInt().coerceIn(0, samples.lastIndex)]
        val p95 = samples[(samples.size * 0.95).toInt().coerceIn(0, samples.lastIndex)]
        logger.debug { "Crop/rotate seek->frame p50=${p50}ms p95=${p95}ms samples=${samples.size}" }
    }
}
