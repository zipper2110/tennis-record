package org.litvin.media

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.State
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.awt.Component
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * Swing variant of VLCJ-backed media player using EmbeddedMediaPlayerComponent (AWT Canvas).
 *
 * Note: We intentionally do NOT implement AppMediaPlayer here because that interface is bound to JavaFX Node.
 * This class mirrors the API and can be adapted by a thin layer later in migration.
 */
import org.litvin.adjustments.AdjustmentsV1
class VlcjSwingMediaPlayerAdapter {
    // For Phase 0 we stick to defaults; options can be tuned later if needed.
    private val embeddedComponent = EmbeddedMediaPlayerComponent()
    private val mediaPlayer: EmbeddedMediaPlayer = embeddedComponent.mediaPlayer()

    // Expose AWT component for embedding into Swing containers
    val component: Component get() = embeddedComponent

    private var durationMs: Long = 0L

    // Some VLC snapshot implementations return an upside-down image (origin at bottom-left).
    // Flip snapshots vertically by default; can be disabled via -Dtennis.vlc.snap.flipY=false
    private val flipSnapshots: Boolean = try {
        java.lang.Boolean.parseBoolean(System.getProperty("tennis.vlc.snap.flipY", "true"))
    } catch (_: Throwable) { true }

    var onReady: (() -> Unit)? = null
    var onStatusChanged: ((PlayerStatus) -> Unit)? = null
    var onTimeChanged: ((Long) -> Unit)? = null

    fun setRate(rate: Float) {
        try { mediaPlayer.controls().setRate(rate) } catch (_: Throwable) { }
    }

    // ===== Live color adjustments (Task 5.3) =====
    @Volatile private var pendingBrightness = 0.0f
    @Volatile private var pendingContrast = 1.0f
    @Volatile private var pendingSaturation = 1.0f
    @Volatile private var pendingHue: Float = 0.0f // hue in degrees for VLC (float)
    @Volatile private var pendingGamma = 1.0f
    @Volatile private var dirtyAdjust = false

    // ===== Live geometry (Task 5.4) — VLC crop-based primary path =====
    @Volatile private var pendingZoom = 1.0f
    @Volatile private var pendingPanX = 0.0f
    @Volatile private var pendingPanY = 0.0f
    @Volatile private var pendingRotation = 0.0f
    @Volatile private var dirtyGeometry = false

    // Throttle/coalesce updates to ~120 Hz
    private val adjustTimer = javax.swing.Timer(8) { _ ->
        if (dirtyAdjust) {
            dirtyAdjust = false
            applyAdjustNow()
        }
        if (dirtyGeometry) {
            dirtyGeometry = false
            applyGeometryNow()
        }
    }.apply { isRepeats = true; start() }

    private fun enableAdjustIfNeeded(enable: Boolean) {
        try {
            mediaPlayer.video().setAdjustVideo(enable)
        } catch (_: Throwable) { /* ignore if unsupported */ }
    }

    private fun applyAdjustNow() {
        try {
            val anyNonIdentity = (pendingBrightness != 0.0f) || (pendingContrast != 1.0f) || (pendingSaturation != 1.0f) || (pendingHue != 0.0f) || (pendingGamma != 1.0f)
            enableAdjustIfNeeded(anyNonIdentity)
            if (anyNonIdentity) {
                try { mediaPlayer.video().setBrightness(pendingBrightness.coerceIn(-1.0f, 1.0f)) } catch (_: Throwable) { }
                try { mediaPlayer.video().setContrast(pendingContrast.coerceIn(0.0f, 3.0f)) } catch (_: Throwable) { }
                try { mediaPlayer.video().setSaturation(pendingSaturation.coerceIn(0.0f, 3.0f)) } catch (_: Throwable) { }
                try { mediaPlayer.video().setHue(pendingHue) } catch (_: Throwable) { }
                try { mediaPlayer.video().setGamma(pendingGamma.coerceIn(0.2f, 3.0f)) } catch (_: Throwable) { }
            }
        } catch (_: Throwable) { }
    }

    /** Try to apply geometry via VLC crop; returns true if applied/scheduled, false if not supported. */
    fun applyGeometryAdjustments(adj: AdjustmentsV1): Boolean {
        // Stash and schedule apply; we always schedule and return true — if video dims are unknown, we'll retry next tick
        pendingZoom = adj.zoom.coerceIn(0.1f, 4.0f)
        pendingPanX = adj.panX.coerceIn(-1.0f, 1.0f)
        pendingPanY = adj.panY.coerceIn(-1.0f, 1.0f)
        pendingRotation = adj.rotationDeg.coerceIn(-360f, 360f)
        dirtyGeometry = true
        return true
    }

    private fun applyGeometryNow() {
        try {
            // Query current video dimensions; may be null before playback is ready
            val dim: java.awt.Dimension? = try { mediaPlayer.video().videoDimension() } catch (_: Throwable) { null }
            val W = dim?.width ?: 0
            val H = dim?.height ?: 0
            if (W <= 0 || H <= 0) return // will retry on next tick when dims are known

            val z = if (pendingZoom <= 0f) 0.0001f else pendingZoom
            // Identity → clear crop
            if (kotlin.math.abs(z - 1.0f) < 1e-3 && kotlin.math.abs(pendingPanX) < 1e-3 && kotlin.math.abs(pendingPanY) < 1e-3) {
                try { mediaPlayer.video().setCropGeometry(null) } catch (_: Throwable) { }
                return
            }

            val cropW = kotlin.math.max(1, java.lang.Math.round(W / z))
            val cropH = kotlin.math.max(1, java.lang.Math.round(H / z))
            val x = (((W - cropW) / 2.0) + pendingPanX * (W - cropW) / 2.0).toInt().coerceIn(0, kotlin.math.max(0, W - cropW))
            val y = (((H - cropH) / 2.0) - pendingPanY * (H - cropH) / 2.0).toInt().coerceIn(0, kotlin.math.max(0, H - cropH))
            val geo = "${cropW}x${cropH}+${x}+${y}"
            try { mediaPlayer.video().setCropGeometry(geo) } catch (_: Throwable) { }

        } catch (_: Throwable) { }
    }

    /**
     * Apply color adjustments to VLC preview without pausing (coalesced, ≤120 Hz).
     * Maps brightness/contrast/saturation directly; approximates white balance via hue/gamma and a slight sat shift.
     */
    fun applyColorAdjustments(adj: AdjustmentsV1) {
        // Map model → VLC values
        var b = adj.brightness.coerceIn(-1.0f, 1.0f)
        var c = adj.contrast.coerceIn(0.0f, 3.0f)
        var s = adj.saturation.coerceIn(0.0f, 3.0f)
        var hueDeg = 0.0f
        var gamma = 1.0f
        try {
            val wb = adj.whiteBalance
            if (wb != null) {
                hueDeg = wb.temperature.coerceIn(-1.0f, 1.0f) * 180.0f
                gamma = 1.0f + wb.tint.coerceIn(-1.0f, 1.0f) * 0.2f
                s = (s + wb.temperature * 0.05f).coerceIn(0.0f, 3.0f)
            }
        } catch (_: Throwable) { }

        pendingBrightness = b
        pendingContrast = c
        pendingSaturation = s
        pendingHue = hueDeg
        pendingGamma = gamma
        dirtyAdjust = true
    }

    init {
        mediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                onStatusChanged?.invoke(PlayerStatus.PLAYING)
            }
            override fun paused(mediaPlayer: MediaPlayer) {
                onStatusChanged?.invoke(PlayerStatus.PAUSED)
            }
            override fun stopped(mediaPlayer: MediaPlayer) {
                onStatusChanged?.invoke(PlayerStatus.STOPPED)
            }
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                onTimeChanged?.invoke(newTime)
            }
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                durationMs = newLength
                onReady?.invoke()
                onStatusChanged?.invoke(PlayerStatus.READY)
            }
            override fun error(mediaPlayer: MediaPlayer) {
                onStatusChanged?.invoke(PlayerStatus.ERROR)
            }
        })
    }

    fun load(file: File) {
        durationMs = 0L
        mediaPlayer.media().play(file.absolutePath, ":start-paused")
        mediaPlayer.controls().setTime(0L)
    }

    fun play() = mediaPlayer.controls().play()
    fun pause() = mediaPlayer.controls().setPause(true)
    fun stop() = mediaPlayer.controls().stop()
    fun seek(ms: Long) = mediaPlayer.controls().setTime(ms.coerceAtLeast(0L))

    fun currentTimeMs(): Long = mediaPlayer.status().time()
    fun totalDurationMs(): Long = if (durationMs > 0) durationMs else mediaPlayer.status().length()

    fun status(): PlayerStatus = when (mediaPlayer.status().state()) {
        State.PLAYING -> PlayerStatus.PLAYING
        State.PAUSED -> PlayerStatus.PAUSED
        State.STOPPED, State.ENDED -> PlayerStatus.STOPPED
        State.OPENING, State.BUFFERING -> PlayerStatus.READY
        State.ERROR -> PlayerStatus.ERROR
        else -> PlayerStatus.UNKNOWN
    }

    fun captureFrame(): BufferedImage? {
        fun maybeFlip(img: BufferedImage?): BufferedImage? {
            if (img == null) return null
            if (!flipSnapshots) return img
            return try { flipVertically(img) } catch (_: Throwable) { img }
        }
        // Try direct BufferedImage snapshot first (fast path)
        try {
            val bi = mediaPlayer.snapshots().get()
            if (bi != null) return maybeFlip(bi)
        } catch (_: Throwable) { }
        // Fallback: save to a temp file via VLCJ and read it back
        try {
            val tmp = kotlin.io.path.createTempFile("vlc_snap_", ".png").toFile()
            tmp.deleteOnExit()
            val saved = try { mediaPlayer.snapshots().save(tmp) } catch (_: Throwable) { false }
            if (saved && tmp.exists()) {
                val img = try { ImageIO.read(tmp) } catch (_: Throwable) { null }
                try { tmp.delete() } catch (_: Throwable) { }
                val rs = maybeFlip(img)
                if (rs != null) return rs
            } else {
                try { tmp.delete() } catch (_: Throwable) { }
            }
        } catch (_: Throwable) { }
        // Last resort APIs (older vlcj): video().snapshot() that returns a BufferedImage
        try {
            val m = mediaPlayer.video()::class.java.methods.firstOrNull { it.name == "snapshot" && it.parameterCount == 0 }
            val r = m?.invoke(mediaPlayer.video()) as? BufferedImage
            if (r != null) return maybeFlip(r)
        } catch (_: Throwable) { }
        return null
    }

    private fun flipVertically(src: BufferedImage): BufferedImage {
        val w = src.width
        val h = src.height
        val dst = BufferedImage(w, h, src.type.takeIf { it != 0 } ?: BufferedImage.TYPE_INT_ARGB)
        val g = dst.createGraphics()
        try {
            val at = java.awt.geom.AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, h.toDouble())
            g.drawImage(src, at, null)
        } finally {
            try { g.dispose() } catch (_: Throwable) { }
        }
        return dst
    }

    fun captureFrameAt(targetMs: Long, timeoutMs: Long = 600, pollMs: Long = 25): BufferedImage? {
        val wallStart = System.currentTimeMillis()
        fun timedOut(): Boolean = (System.currentTimeMillis() - wallStart) > timeoutMs
        fun left(): Long = timeoutMs - (System.currentTimeMillis() - wallStart)
        println("[ADJ_SEEK] adapter.captureFrameAt start targetMs=$targetMs")
        try { pause() } catch (_: Throwable) { }
        try { seek(targetMs.coerceAtLeast(0L)) } catch (_: Throwable) { }
        // Avoid strict time-gating while paused; instead, nudge decode with a very short play/pause
        try {
            val playBudget = 120L
            val playStart = System.currentTimeMillis()
            mediaPlayer.controls().play()
            var lastT = -1L
            while (!timedOut() && System.currentTimeMillis() - playStart < playBudget) {
                try { lastT = currentTimeMs() } catch (_: Throwable) { }
                if (lastT >= targetMs) break
                try { Thread.sleep(pollMs.coerceAtLeast(10)) } catch (_: Throwable) { break }
            }
        } catch (_: Throwable) { }
        finally { try { mediaPlayer.controls().setPause(true) } catch (_: Throwable) { } }
        if (timedOut()) { println("[ADJ_SEEK] adapter.captureFrameAt timeout before snapshot"); return null }
        // Snapshot attempts: prefer save(tmp) first (often more reliable), then get(), then reflection
        fun trySnapshotOnce(): BufferedImage? {
            // save(tmp)
            try {
                val tmp = kotlin.io.path.createTempFile("vlc_snap_", ".png").toFile()
                tmp.deleteOnExit()
                val saved = try { mediaPlayer.snapshots().save(tmp) } catch (_: Throwable) { false }
                if (saved && tmp.exists()) {
                    val img = try { ImageIO.read(tmp) } catch (_: Throwable) { null }
                    try { tmp.delete() } catch (_: Throwable) { }
                    val rs = try { if (flipSnapshots) flipVertically(img!!) else img } catch (_: Throwable) { img }
                    if (rs != null) return rs
                } else {
                    try { tmp.delete() } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
            // get()
            try {
                val bi = mediaPlayer.snapshots().get()
                if (bi != null) return if (flipSnapshots) try { flipVertically(bi) } catch (_: Throwable) { bi } else bi
            } catch (_: Throwable) { }
            // reflection fallback
            try {
                val m = mediaPlayer.video()::class.java.methods.firstOrNull { it.name == "snapshot" && it.parameterCount == 0 }
                val r = m?.invoke(mediaPlayer.video()) as? BufferedImage
                if (r != null) return if (flipSnapshots) try { flipVertically(r) } catch (_: Throwable) { r } else r
            } catch (_: Throwable) { }
            return null
        }
        var img: BufferedImage? = trySnapshotOnce()
        if (img == null && !timedOut()) {
            try { Thread.sleep(60) } catch (_: Throwable) { }
            if (!timedOut()) img = trySnapshotOnce()
        }
        if (img == null && timedOut()) println("[ADJ_SEEK] adapter.captureFrameAt timeout (no image)")
        return img
    }

    fun dispose() {
        try { adjustTimer.stop() } catch (_: Throwable) {}
        try { mediaPlayer.release() } catch (_: Throwable) {}
        try { embeddedComponent.release() } catch (_: Throwable) {}
    }
}