package org.litvin.media

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.State
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.awt.Component
import java.io.File

/**
 * Swing variant of VLCJ-backed media player using EmbeddedMediaPlayerComponent (AWT Canvas).
 *
 * Note: We intentionally do NOT implement AppMediaPlayer here because that interface is bound to JavaFX Node.
 * This class mirrors the API and can be adapted by a thin layer later in migration.
 */
class VlcjSwingMediaPlayerAdapter {
    // For Phase 0 we stick to defaults; options can be tuned later if needed.
    private val embeddedComponent = EmbeddedMediaPlayerComponent()
    private val mediaPlayer: EmbeddedMediaPlayer = embeddedComponent.mediaPlayer()

    // Expose AWT component for embedding into Swing containers
    val component: Component get() = embeddedComponent

    private var durationMs: Long = 0L

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
    fun applyGeometryAdjustments(adj: org.litvin.AdjustmentsV1): Boolean {
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
    fun applyColorAdjustments(adj: org.litvin.AdjustmentsV1) {
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

    fun dispose() {
        try { adjustTimer.stop() } catch (_: Throwable) {}
        try { mediaPlayer.release() } catch (_: Throwable) {}
        try { embeddedComponent.release() } catch (_: Throwable) {}
    }
}