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
import org.litvin.ui.tabs.adjustments.AdjustmentsUiConverter

class VlcjSwingMediaPlayerAdapter(
    private val colorPreviewStrategy: ColorPreviewAdjustmentStrategy = CalibratedVlcColorPreviewAdjustmentStrategy,
) {
    // For Phase 0 we stick to defaults; options can be tuned later if needed.
    private val embeddedComponent = EmbeddedMediaPlayerComponent()
    private val mediaPlayer: EmbeddedMediaPlayer = embeddedComponent.mediaPlayer()

    // Expose AWT component for embedding into Swing containers
    val component: Component get() = embeddedComponent

    private var durationMs: Long = 0L

    // Capability probing cache
    @Volatile
    private var adjustSupportChecked: Boolean = false

    @Volatile
    private var adjustSupported: Boolean = false

    var onReady: (() -> Unit)? = null
    var onStatusChanged: ((PlayerStatus) -> Unit)? = null
    var onTimeChanged: ((Long) -> Unit)? = null

    fun setRate(rate: Float) {
        mediaPlayer.controls().setRate(rate)
    }

    // ===== Live color adjustments (Task 5.3) =====
    @Volatile
    private var pendingBrightness = AdjustmentsUiConverter.DEFAULTS.brightness

    @Volatile
    private var pendingContrast = AdjustmentsUiConverter.DEFAULTS.contrast

    @Volatile
    private var pendingSaturation = AdjustmentsUiConverter.DEFAULTS.saturation

    @Volatile
    private var pendingHue: Float = 0.0f // hue in degrees for VLC (float)

    @Volatile
    private var pendingGamma = 1.0f

    @Volatile
    private var dirtyAdjust = false

    // ===== Live geometry (Task 5.4) — VLC crop-based primary path =====
    @Volatile
    private var pendingZoom = 1.0f

    @Volatile
    private var pendingPanX = 0.0f

    @Volatile
    private var pendingPanY = 0.0f

    @Volatile
    private var pendingRotation = 0.0f

    @Volatile
    private var dirtyGeometry = false

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

    /** Probe once whether VLC adjust filter APIs are available; caches the result. */
    fun isAdjustSupported(): Boolean {
        if (!adjustSupportChecked) {
            adjustSupportChecked = true
            adjustSupported = try {
                // Try toggling adjust mode; if it throws, not supported
                mediaPlayer.video().setAdjustVideo(true)
                // Try a harmless no-op set to ensure setter exists
                mediaPlayer.video().setBrightness(AdjustmentsUiConverter.DEFAULTS.brightness)
                true
            } catch (_: Throwable) {
                false
            }
            // Return to default state
            mediaPlayer.video().setAdjustVideo(false)
        }
        return adjustSupported
    }

    private val anyColorAdjustmentsMade: Boolean
        get() = pendingBrightness != AdjustmentsUiConverter.DEFAULTS.brightness ||
                pendingContrast != AdjustmentsUiConverter.DEFAULTS.contrast ||
                pendingSaturation != AdjustmentsUiConverter.DEFAULTS.saturation ||
                pendingHue != 0.0f ||
                pendingGamma != 1.0f

    private fun applyAdjustNow() {
        mediaPlayer.video().isAdjustVideo = anyColorAdjustmentsMade

        if (anyColorAdjustmentsMade) {
            mediaPlayer.video().setBrightness(pendingBrightness)
            mediaPlayer.video().setContrast(pendingContrast)
            mediaPlayer.video().setSaturation(pendingSaturation)
            mediaPlayer.video().setHue(pendingHue)
            mediaPlayer.video().setGamma(pendingGamma.coerceIn(0.2f, 3.0f))
        }
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
        // Query current video dimensions; may be null before playback is ready
        val dim: java.awt.Dimension? = try {
            mediaPlayer.video().videoDimension()
        } catch (_: Throwable) {
            null
        }
        val W = dim?.width ?: 0
        val H = dim?.height ?: 0
        if (W <= 0 || H <= 0) return // will retry on next tick when dims are known

        val z = if (pendingZoom <= 0f) 0.0001f else pendingZoom
        // Identity → clear crop
        if (kotlin.math.abs(z - 1.0f) < 1e-3 && kotlin.math.abs(pendingPanX) < 1e-3 && kotlin.math.abs(pendingPanY) < 1e-3) {
            mediaPlayer.video().setCropGeometry(null)
            return
        }

        val cropW = kotlin.math.max(1, java.lang.Math.round(W / z))
        val cropH = kotlin.math.max(1, java.lang.Math.round(H / z))
        val x = (((W - cropW) / 2.0) + pendingPanX * (W - cropW) / 2.0).toInt()
            .coerceIn(0, kotlin.math.max(0, W - cropW))
        val y = (((H - cropH) / 2.0) - pendingPanY * (H - cropH) / 2.0).toInt()
            .coerceIn(0, kotlin.math.max(0, H - cropH))
        val geo = "${cropW}x${cropH}+${x}+${y}"
        mediaPlayer.video().setCropGeometry(geo)
    }

    /**
     * Apply color adjustments to VLC preview without pausing (coalesced, ≤120 Hz).
     * Maps model values through a preview strategy so VLC's live filter can approximate export color semantics.
     */
    fun applyColorAdjustments(adj: AdjustmentsV1) {
        val preview = colorPreviewStrategy.map(adj)

        pendingBrightness = preview.brightness
        pendingContrast = preview.contrast
        pendingSaturation = preview.saturation
        pendingHue = preview.hue
        pendingGamma = preview.gamma
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
                // Media just became ready: re-apply any pending color/geometry adjustments.
                // Some VLC builds reset adjust/crop state on load, so enforce last known values now.
                try {
                    applyAdjustNow()
                } catch (_: Throwable) { /* ignore */ }
                try {
                    // Schedule geometry application; dimensions may not be known yet, so mark dirty to retry.
                    dirtyGeometry = true
                    applyGeometryNow()
                } catch (_: Throwable) { /* ignore */ }
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
    fun seek(ms: Long) = mediaPlayer.controls().setTime(ms.coerceAtLeast(0L))

    /** Step forward by exactly one frame (requires paused state for deterministic stepping). */
    fun stepFrameForward() {
        try {
            mediaPlayer.controls().nextFrame()
        } catch (_: Throwable) {
            // ignore; not all media support precise frame advancing
        }
    }

    /** Approximate step backward by one frame. VLCJ does not provide a previousFrame, so we seek a bit back and then advance one frame. */
    fun stepFrameBackward() {
        try {
            val backMs = 40L // ~1 frame at 25 fps; good compromise for sports footage
            val target = (currentTimeMs() - backMs).coerceAtLeast(0L)
            mediaPlayer.controls().setTime(target)
            mediaPlayer.controls().nextFrame()
        } catch (_: Throwable) {
            // ignore
        }
    }
    fun nextFrame() = try { mediaPlayer.controls().nextFrame() } catch (_: Throwable) { /* ignore */ }
 
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
        adjustTimer.stop()
        mediaPlayer.release()
        embeddedComponent.release()
    }
}
