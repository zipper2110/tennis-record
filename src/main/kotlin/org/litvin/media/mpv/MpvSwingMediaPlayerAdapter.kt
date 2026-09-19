package org.litvin.media.mpv

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.ApplicationLayout
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.media.OverlayShape
import org.litvin.media.PlayerStatus
import org.litvin.media.SwingMediaPlayer
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.image.RenderedImage
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Swing media player backed by libmpv.
 *
 * mpv renders into a child window of a heavyweight [Canvas] (the `wid` option). mpv decodes on the GPU
 * (d3d11va) and renders with libplacebo (vo=gpu-next). The `tr-adjust.hook` shader applies the color and
 * geometry adjustments with the same math as the FFmpeg export. Parameter changes do not reload the video.
 *
 * Deactivation unloads the file (this releases the decoder) but keeps the mpv instance.
 * Activation loads the file again at the last position.
 */
class MpvSwingMediaPlayerAdapter : SwingMediaPlayer {
    private companion object {
        private val logger = KotlinLogging.logger {}
        private val playerIds = AtomicInteger(0)

        /** Only one player keeps a file loaded (one hardware decoder), as with the VLC engine. */
        private val activeLock = Any()
        private var activePlayer: MpvSwingMediaPlayerAdapter? = null
        private const val OVERLAY_ID = "0"
        private const val OVERLAY_MARGIN_PX = 32.0
        private const val OVERLAY_REFERENCE_HEIGHT = 1080.0
        private const val EDITOR_OVERLAY_ID = "1"
        private const val EDITOR_MARGIN_VERTICAL = 0.07
        private const val EDITOR_MARGIN_HORIZONTAL = 0.04
    }

    private val playerId = playerIds.incrementAndGet()
    private val canvas = VideoCanvas()
    private val host = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = Color.BLACK
        isFocusable = true
        add(canvas, BorderLayout.CENTER)
    }
    override val component: Component get() = host

    override var onReady: (() -> Unit)? = null
    override var onStatusChanged: ((PlayerStatus) -> Unit)? = null
    override var onTimeChanged: ((Long) -> Unit)? = null

    @Volatile private var core: MpvCore? = null
    @Volatile private var mediaFile: File? = null
    @Volatile private var fileLoaded = false
    @Volatile private var active = true
    @Volatile private var paused = true
    @Volatile private var durationMs = 0L
    @Volatile private var lastKnownTimeMs = 0L
    @Volatile private var frameDurationMs = 40L
    @Volatile private var frameStepCursorMs: Long? = null
    @Volatile private var playbackRate = 1.0f
    @Volatile private var lastStatus = PlayerStatus.STOPPED

    @Volatile private var videoWidth = 0
    @Volatile private var videoHeight = 0
    @Volatile private var colorMatrix: String? = null
    @Volatile private var colorLevels: String? = null
    @Volatile private var adjustments = AdjustmentsV1()
    @Volatile private var appliedShaderOpts: String? = null

    @Volatile private var overlayImage: RenderedImage? = null
    @Volatile private var osdWidth = 0
    @Volatile private var osdHeight = 0
    @Volatile private var osdMarginTop = 0
    @Volatile private var osdMarginBottom = 0
    @Volatile private var osdMarginLeft = 0
    @Volatile private var osdMarginRight = 0

    @Volatile private var cropEditing = false
    @Volatile private var editorShapes: List<OverlayShape>? = null
    @Volatile private var boundsChangePending = false
    override var onVideoBoundsChanged: (() -> Unit)? = null

    private inner class VideoCanvas : Canvas() {
        init {
            background = Color.BLACK
            isFocusable = false
            // The mpv window is disabled (see disableNativeInput), so Windows sends mouse input to this canvas.
            // Forward it to the host panel, where the application attaches its listeners.
            val forward = object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    host.requestFocusInWindow()
                    redispatch(e)
                }
                override fun mouseReleased(e: MouseEvent) = redispatch(e)
                override fun mouseClicked(e: MouseEvent) = redispatch(e)
                override fun mouseDragged(e: MouseEvent) = redispatch(e)
                override fun mouseMoved(e: MouseEvent) = redispatch(e)
                override fun mouseWheelMoved(e: MouseWheelEvent) = redispatch(e)
            }
            addMouseListener(forward)
            addMouseMotionListener(forward)
            addMouseWheelListener(forward)
        }

        private fun redispatch(e: MouseEvent) {
            host.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, host))
        }

        override fun update(g: Graphics) = Unit

        override fun paint(g: Graphics) {
            if (core == null) {
                g.color = Color.BLACK
                g.fillRect(0, 0, width, height)
            }
        }

        override fun addNotify() {
            super.addNotify()
            SwingUtilities.invokeLater { onCanvasDisplayable() }
        }

        override fun removeNotify() {
            destroyCore("canvas removed")
            super.removeNotify()
        }
    }

    // ---- Lifecycle -----------------------------------------------------------------------------------

    private fun onCanvasDisplayable() {
        if (active && mediaFile != null && !fileLoaded) loadCurrentFile("canvas displayable")
    }

    private fun ensureCore(): MpvCore? {
        core?.let { return it }
        if (!canvas.isDisplayable) return null
        val lib = LibMpv.instanceOrNull() ?: return null
        val wid = Native.getComponentID(canvas)
        if (wid == 0L) return null
        val shader = MpvShaderFile.path()
        val options = buildList {
            add("config" to "no")
            add("load-scripts" to "no")
            add("ytdl" to "no")
            add("osc" to "no")
            add("terminal" to "no")
            add("input-default-bindings" to "no")
            add("input-vo-keyboard" to "no")
            add("input-cursor" to "no")
            add("cursor-autohide" to "no")
            add("osd-level" to "0")
            add("idle" to "yes")
            add("keep-open" to "always")
            add("pause" to "yes")
            add("vo" to "gpu-next")
            add("gpu-api" to "d3d11")
            add("hwdec" to "d3d11va,auto-safe")
            add("hr-seek" to "yes")
            add("hr-seek-framedrop" to "no")
            add("background-color" to "#000000")
            add("wid" to wid.toString())
            if (shader != null) add("glsl-shaders" to shader.absolutePath)
        }
        return try {
            MpvCore(lib, options, "player-$playerId", ::handleEvent).also { created ->
                created.observe("time-pos", LibMpv.FORMAT_DOUBLE)
                created.observe("duration", LibMpv.FORMAT_DOUBLE)
                created.observe("pause", LibMpv.FORMAT_FLAG)
                created.observe("container-fps", LibMpv.FORMAT_DOUBLE)
                created.observe("video-params/w", LibMpv.FORMAT_INT64)
                created.observe("video-params/h", LibMpv.FORMAT_INT64)
                created.observe("video-params/colormatrix", LibMpv.FORMAT_STRING)
                created.observe("video-params/colorlevels", LibMpv.FORMAT_STRING)
                created.observe("osd-dimensions/w", LibMpv.FORMAT_INT64)
                created.observe("osd-dimensions/h", LibMpv.FORMAT_INT64)
                created.observe("osd-dimensions/mt", LibMpv.FORMAT_INT64)
                created.observe("osd-dimensions/mb", LibMpv.FORMAT_INT64)
                created.observe("osd-dimensions/ml", LibMpv.FORMAT_INT64)
                created.observe("osd-dimensions/mr", LibMpv.FORMAT_INT64)
                core = created
                logger.info { "Created mpv preview #$playerId (wid=$wid, shader=${shader?.absolutePath})" }
            }
        } catch (t: Throwable) {
            logger.warn(t) { "Failed to create mpv preview #$playerId" }
            null
        }
    }

    private fun destroyCore(reason: String) {
        releaseActive()
        val old = core ?: return
        core = null
        fileLoaded = false
        appliedShaderOpts = null
        logger.info { "Destroying mpv preview #$playerId: $reason" }
        old.close()
    }

    override fun activatePreview(reason: String) {
        active = true
        if (mediaFile != null && !fileLoaded) loadCurrentFile("activate: $reason")
    }

    override fun deactivatePreview(reason: String) {
        active = false
        releaseActive()
        val current = core ?: return
        if (!fileLoaded) return
        lastKnownTimeMs = currentTimeMs()
        fileLoaded = false
        logger.info { "Unloading mpv preview #$playerId at ${lastKnownTimeMs}ms: $reason" }
        current.commandSync("stop")
    }

    override fun close() {
        active = false
        destroyCore("close")
    }

    // ---- Media -----------------------------------------------------------------------------------------

    override fun load(file: File) {
        mediaFile = file
        durationMs = 0L
        lastKnownTimeMs = 0L
        frameStepCursorMs = null
        videoWidth = 0
        videoHeight = 0
        fileLoaded = false
        active = true
        loadCurrentFile("load")
    }

    private fun claimActive() {
        val previous = synchronized(activeLock) {
            activePlayer.also { activePlayer = this }
        }
        if (previous != null && previous !== this) previous.deactivatePreview("superseded by mpv preview #$playerId")
    }

    private fun releaseActive() {
        synchronized(activeLock) {
            if (activePlayer === this) activePlayer = null
        }
    }

    private fun loadCurrentFile(reason: String) {
        val file = mediaFile ?: return
        val mpv = ensureCore() ?: return
        claimActive()
        val startSeconds = String.format(Locale.US, "%.3f", lastKnownTimeMs / 1000.0)
        logger.info { "Loading into mpv preview #$playerId: ${file.absolutePath} at ${startSeconds}s ($reason)" }
        mpv.setProperty("pause", "yes")
        mpv.setProperty("speed", String.format(Locale.US, "%.3f", playbackRate))
        mpv.command("loadfile", file.absolutePath, "replace", "-1", "start=$startSeconds")
    }

    override fun setSubtitleFile(file: File): Boolean {
        val mpv = core ?: return false
        mpv.command("sub-add", file.absolutePath, "select")
        return true
    }

    // ---- Transport -------------------------------------------------------------------------------------

    override fun play() {
        frameStepCursorMs = null
        if (!fileLoaded) activatePreview("play")
        core?.setProperty("pause", "no")
    }

    override fun pause() {
        core?.setProperty("pause", "yes")
    }

    override fun seek(ms: Long) {
        val target = ms.coerceAtLeast(0L)
        frameStepCursorMs = null
        seekExact(target)
    }

    private fun seekExact(targetMs: Long) {
        lastKnownTimeMs = targetMs
        if (!fileLoaded) return
        core?.command("seek", String.format(Locale.US, "%.3f", targetMs / 1000.0), "absolute+exact")
    }

    override fun setRate(rate: Float) {
        val next = rate.takeIf { it.isFinite() && it > 0.0f } ?: return
        playbackRate = next
        core?.setProperty("speed", String.format(Locale.US, "%.3f", next))
    }

    override fun stepFrameForward(maximumTimeMs: Long): Long {
        val base = frameStepCursorMs ?: currentTimeMs()
        val limit = maximumTimeMs.coerceAtLeast(0L)
        val target = (base + frameDurationMs).coerceAtMost(limit)
        frameStepCursorMs = target
        if (target >= limit) seekExact(target) else core?.command("frame-step")
        return target
    }

    override fun stepFrameBackward(minimumTimeMs: Long): Long {
        val base = frameStepCursorMs ?: currentTimeMs()
        val target = (base - frameDurationMs).coerceAtLeast(minimumTimeMs.coerceAtLeast(0L))
        frameStepCursorMs = target
        seekExact(target)
        return target
    }

    override fun nextFrame() {
        core?.command("frame-step")
    }

    /** Reads an mpv property as a string, for diagnostics. */
    internal fun debugProperty(name: String): String? = core?.getProperty(name)

    internal val isFileLoaded: Boolean get() = fileLoaded

    override fun status(): PlayerStatus = lastStatus

    override fun currentTimeMs(): Long = lastKnownTimeMs

    override fun totalDurationMs(): Long = durationMs

    // ---- Adjustments ------------------------------------------------------------------------------------

    override fun isAdjustSupported(): Boolean = true

    override fun applyColorAdjustments(adj: AdjustmentsV1) {
        adjustments = adjustments.copy(
            brightness = adj.brightness,
            contrast = adj.contrast,
            saturation = adj.saturation,
            whiteBalance = adj.whiteBalance,
        )
        applyShaderOptions()
    }

    override fun applyGeometryAdjustments(adj: AdjustmentsV1): Boolean {
        adjustments = adjustments.copy(zoom = adj.zoom, panX = adj.panX, panY = adj.panY, rotationDeg = adj.rotationDeg)
        applyShaderOptions()
        return true
    }

    override fun applyPreviewAdjustments(adj: AdjustmentsV1) {
        adjustments = adj
        applyShaderOptions()
    }

    override fun applyPreviewRotation(rotationDeg: Float, reason: String) {
        adjustments = adjustments.copy(rotationDeg = rotationDeg)
        applyShaderOptions()
    }

    private fun applyShaderOptions() {
        val mpv = core ?: return
        val video = if (videoWidth > 0 && videoHeight > 0) {
            MpvVideoInfo(videoWidth, videoHeight, colorMatrix, colorLevels)
        } else {
            null
        }
        val opts = MpvShaderParams.build(adjustments, video, cropEditing)
        if (opts == appliedShaderOpts) return
        appliedShaderOpts = opts
        mpv.setProperty("glsl-shader-opts", opts)
    }

    // ---- Crop editor --------------------------------------------------------------------------------------

    override fun setCropEditing(enabled: Boolean) {
        cropEditing = enabled
        applyEditorMargins()
        applyShaderOptions()
    }

    /** Keeps a band around the video, so the crop handles stay visible at the frame edges. */
    private fun applyEditorMargins() {
        val mpv = core ?: return
        val vertical = if (cropEditing) EDITOR_MARGIN_VERTICAL else 0.0
        val horizontal = if (cropEditing) EDITOR_MARGIN_HORIZONTAL else 0.0
        mpv.setProperty("video-margin-ratio-top", vertical.toString())
        mpv.setProperty("video-margin-ratio-bottom", vertical.toString())
        mpv.setProperty("video-margin-ratio-left", horizontal.toString())
        mpv.setProperty("video-margin-ratio-right", horizontal.toString())
    }

    override fun videoBounds(): Rectangle2D.Double? {
        val width = osdWidth
        val height = osdHeight
        if (!fileLoaded || width <= 0 || height <= 0 || canvas.width <= 0) return null
        val scale = canvas.width.toDouble() / width
        val videoWidth = width - osdMarginLeft - osdMarginRight
        val videoHeight = height - osdMarginTop - osdMarginBottom
        if (videoWidth <= 0 || videoHeight <= 0) return null
        return Rectangle2D.Double(
            canvas.x + osdMarginLeft * scale,
            canvas.y + osdMarginTop * scale,
            videoWidth * scale,
            videoHeight * scale,
        )
    }

    override fun setEditorOverlay(shapes: List<OverlayShape>?) {
        editorShapes = shapes
        renderEditorOverlay()
    }

    private fun renderEditorOverlay() {
        val mpv = core ?: return
        val shapes = editorShapes
        if (shapes.isNullOrEmpty() || osdWidth <= 0 || osdHeight <= 0 || canvas.width <= 0) {
            mpv.command("osd-overlay", EDITOR_OVERLAY_ID, "none", "")
            return
        }
        val scale = osdWidth.toDouble() / canvas.width
        mpv.command(
            "osd-overlay", EDITOR_OVERLAY_ID, "ass-events", MpvAssOverlay.events(shapes, scale),
            osdWidth.toString(), osdHeight.toString(), "0",
        )
    }

    private fun scheduleBoundsChanged() {
        scheduleOverlay()
        if (boundsChangePending) return
        boundsChangePending = true
        SwingUtilities.invokeLater {
            boundsChangePending = false
            onVideoBoundsChanged?.invoke()
            renderEditorOverlay()
        }
    }

    // ---- Overlay -------------------------------------------------------------------------------------------

    override fun setPreviewOverlayImage(image: RenderedImage?) {
        overlayImage = image
        applyOverlay()
    }

    /**
     * Shows the overlay at the top-left of the video area. The size follows the video height
     * (reference 1080 px), as with the export scoreboard. The overlay-add command uses window pixels.
     */
    private fun applyOverlay() {
        val mpv = core ?: return
        val image = overlayImage
        if (image == null) {
            mpv.command("overlay-remove", OVERLAY_ID)
            return
        }
        val videoAreaHeight = osdHeight - osdMarginTop - osdMarginBottom
        if (osdWidth <= 0 || videoAreaHeight <= 0) return
        val scale = videoAreaHeight / OVERLAY_REFERENCE_HEIGHT
        val displayWidth = (image.width * scale).roundToInt().coerceAtLeast(1)
        val displayHeight = (image.height * scale).roundToInt().coerceAtLeast(1)
        val x = osdMarginLeft + (OVERLAY_MARGIN_PX * scale).roundToInt()
        val y = osdMarginTop + (OVERLAY_MARGIN_PX * scale).roundToInt()
        // mpv copies the bitmap before the command returns, so a synchronous call keeps the memory valid.
        val memory = premultipliedBgra(image)
        mpv.commandSync(
            "overlay-add", OVERLAY_ID, x.toString(), y.toString(), "&${Pointer.nativeValue(memory)}", "0", "bgra",
            image.width.toString(), image.height.toString(), (image.width * 4).toString(),
            displayWidth.toString(), displayHeight.toString(),
        )
        memory.close()
    }

    /** mpv "bgra" is premultiplied B-G-R-A bytes, which is a little-endian premultiplied ARGB int. */
    private fun premultipliedBgra(image: RenderedImage): Memory {
        val argb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        val g = argb.createGraphics()
        try {
            g.drawRenderedImage(image, null)
        } finally {
            g.dispose()
        }
        val pixels = IntArray(image.width * image.height)
        argb.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val alpha = pixel ushr 24
            val red = ((pixel shr 16) and 0xff) * alpha / 255
            val green = ((pixel shr 8) and 0xff) * alpha / 255
            val blue = (pixel and 0xff) * alpha / 255
            pixels[index] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
        return Memory(pixels.size * 4L).apply { write(0, pixels, 0, pixels.size) }
    }

    // ---- Events (mpv event thread) -----------------------------------------------------------------------

    private fun handleEvent(event: MpvEvent) {
        when (event.id) {
            LibMpv.EVENT_PROPERTY_CHANGE -> handlePropertyChange(event)
            LibMpv.EVENT_FILE_LOADED -> {
                fileLoaded = true
                // The duration property change can arrive after this event. Read it now for onReady.
                core?.getProperty("duration")?.toDoubleOrNull()?.let { durationMs = (it * 1000.0).roundToLong() }
                logger.info { "mpv preview #$playerId file loaded: durationMs=$durationMs" }
                SwingUtilities.invokeLater {
                    applyEditorMargins()
                    applyShaderOptions()
                    applyOverlay()
                    onVideoBoundsChanged?.invoke()
                    renderEditorOverlay()
                }
                onReady?.invoke()
                setStatus(PlayerStatus.READY)
                setStatus(if (paused) PlayerStatus.PAUSED else PlayerStatus.PLAYING)
            }
            LibMpv.EVENT_END_FILE -> {
                if (event.endFileReason() == LibMpv.END_FILE_REASON_ERROR) {
                    logger.warn { "mpv preview #$playerId end-file error ${event.endFileError()}" }
                    setStatus(PlayerStatus.ERROR)
                } else {
                    setStatus(PlayerStatus.STOPPED)
                }
            }
            LibMpv.EVENT_VIDEO_RECONFIG -> {
                logger.debug {
                    "mpv preview #$playerId video: hwdec=${core?.getProperty("hwdec-current")}, " +
                        "vo=${core?.getProperty("current-vo")}"
                }
                disableNativeInput()
                SwingUtilities.invokeLater { applyShaderOptions() }
            }
        }
    }

    private fun handlePropertyChange(event: MpvEvent) {
        val name = event.propertyName() ?: return
        val format = event.propertyFormat()
        when (name) {
            "time-pos" -> if (format == LibMpv.FORMAT_DOUBLE && fileLoaded) {
                val ms = ((event.propertyDouble() ?: return) * 1000.0).roundToLong().coerceAtLeast(0L)
                lastKnownTimeMs = ms
                onTimeChanged?.invoke(ms)
            }
            "duration" -> if (format == LibMpv.FORMAT_DOUBLE) {
                durationMs = ((event.propertyDouble() ?: 0.0) * 1000.0).roundToLong()
            }
            "pause" -> if (format == LibMpv.FORMAT_FLAG) {
                paused = event.propertyFlag() ?: true
                if (fileLoaded) setStatus(if (paused) PlayerStatus.PAUSED else PlayerStatus.PLAYING)
            }
            "container-fps" -> if (format == LibMpv.FORMAT_DOUBLE) {
                val fps = event.propertyDouble() ?: 0.0
                if (fps > 0.0) frameDurationMs = ceil(1000.0 / fps).toLong().coerceAtLeast(1L)
            }
            "video-params/w" -> videoWidth = event.int64OrZero(format)
            "video-params/h" -> videoHeight = event.int64OrZero(format)
            "video-params/colormatrix" -> colorMatrix = event.stringOrNull(format)
            "video-params/colorlevels" -> colorLevels = event.stringOrNull(format)
            "osd-dimensions/w" -> osdWidth = event.int64OrZero(format).also { scheduleBoundsChanged() }
            "osd-dimensions/h" -> osdHeight = event.int64OrZero(format).also { scheduleBoundsChanged() }
            "osd-dimensions/mt" -> osdMarginTop = event.int64OrZero(format).also { scheduleBoundsChanged() }
            "osd-dimensions/mb" -> osdMarginBottom = event.int64OrZero(format).also { scheduleBoundsChanged() }
            "osd-dimensions/ml" -> osdMarginLeft = event.int64OrZero(format).also { scheduleBoundsChanged() }
            "osd-dimensions/mr" -> osdMarginRight = event.int64OrZero(format).also { scheduleBoundsChanged() }
        }
    }

    /**
     * Disables the child windows that mpv creates in the canvas. A disabled child window gets no mouse
     * or keyboard input, so Windows sends the mouse input to the canvas and the keyboard focus stays in Java.
     * VLC uses the same method for its embedded video window.
     */
    private fun disableNativeInput() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        runCatching {
            val parent = WinDef.HWND(Native.getComponentPointer(canvas) ?: return)
            User32.INSTANCE.EnumChildWindows(parent, WinUser.WNDENUMPROC { child, _ ->
                WindowInput.INSTANCE.EnableWindow(child, false)
                true
            }, null)
        }.onFailure { logger.warn(it) { "Cannot disable input of the mpv video window." } }
    }

    private fun scheduleOverlay() {
        if (overlayImage != null) SwingUtilities.invokeLater { applyOverlay() }
    }

    private fun setStatus(status: PlayerStatus) {
        lastStatus = status
        onStatusChanged?.invoke(status)
    }

    private fun MpvEvent.int64OrZero(format: Int): Int =
        if (format == LibMpv.FORMAT_INT64) (propertyLong() ?: 0L).toInt() else 0

    private fun MpvEvent.stringOrNull(format: Int): String? =
        if (format == LibMpv.FORMAT_STRING) propertyString() else null
}

/** user32 functions that JNA's User32 mapping does not include. */
@Suppress("FunctionName")
private interface WindowInput : StdCallLibrary {
    fun EnableWindow(hWnd: WinDef.HWND, enable: Boolean): Boolean

    companion object {
        val INSTANCE: WindowInput = Native.load("user32", WindowInput::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

/** Copies the preview shader from the classpath to a file, because mpv loads shaders by path. */
internal object MpvShaderFile {
    private const val RESOURCE = "/shaders/tr-adjust.hook"
    private val logger = KotlinLogging.logger {}
    @Volatile private var cached: File? = null

    fun path(): File? {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: runCatching {
                val bytes = MpvShaderFile::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes() }
                    ?: error("Missing resource $RESOURCE")
                val target = File(ApplicationLayout.current().appDataDirectory, "shaders/tr-adjust.hook")
                if (!target.isFile || !target.readBytes().contentEquals(bytes)) {
                    target.parentFile.mkdirs()
                    target.writeBytes(bytes)
                }
                target
            }.onFailure { logger.warn(it) { "Cannot prepare the mpv preview shader." } }
                .getOrNull()
                ?.also { cached = it }
        }
    }
}
