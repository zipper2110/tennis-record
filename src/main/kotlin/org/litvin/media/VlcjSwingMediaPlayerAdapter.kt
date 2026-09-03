package org.litvin.media

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.VlcBootstrap
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.ui.tabs.adjustments.AdjustmentsUiConverter
import uk.co.caprica.vlcj.player.base.LogoPosition
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.State
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.RenderedImage
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.ceil

/**
 * Swing VLCJ-backed media player using VLCJ's embedded native output.
 *
 * The component remains laid out at normal preview size; zoom/pan preview is
 * delegated to VLC crop geometry rather than Swing component transforms.
 */
class VlcjSwingMediaPlayerAdapter(
    private val colorPreviewStrategy: ColorPreviewAdjustmentStrategy = CalibratedVlcColorPreviewAdjustmentStrategy,
) : SwingMediaPlayer {
    private companion object {
        private val logger = KotlinLogging.logger {}
        private val playerIds = AtomicInteger(0)
        private val activeLock = Any()
        private var activeAdapter: VlcjSwingMediaPlayerAdapter? = null
    }

    private val componentHost = JPanel(BorderLayout()).apply {
        isOpaque = false
        isFocusable = true
    }
    private var mediaPlayerInstanceId = 0
    private var embeddedComponent: EmbeddedMediaPlayerComponent? = null
    private var mediaPlayer: EmbeddedMediaPlayer? = null

    override val component: Component get() = componentHost

    private data class PlaybackRestore(
        val timeMs: Long,
        val playing: Boolean,
        val rate: Float,
    )

    private var durationMs: Long = 0L
    @Volatile private var frameDurationMs: Long = 40L
    @Volatile private var frameStepCursorMs: Long? = null
    @Volatile private var previewOverlayImage: RenderedImage? = null
    private var mediaFile: File? = null
    @Volatile private var playbackRate: Float = 1.0f
    @Volatile private var lastKnownTimeMs: Long = 0L
    @Volatile private var pendingPlaybackRestore: PlaybackRestore? = null

    @Volatile private var adjustSupportChecked: Boolean = false
    @Volatile private var adjustSupported: Boolean = false

    override var onReady: (() -> Unit)? = null
    override var onStatusChanged: ((PlayerStatus) -> Unit)? = null
    override var onTimeChanged: ((Long) -> Unit)? = null

    override fun setRate(rate: Float) {
        val nextRate = rate.takeIf { it.isFinite() && it > 0.0f } ?: return
        val previousRate = playbackRate
        playbackRate = nextRate
        val player = mediaPlayer ?: return
        val wasPlaying = status() == PlayerStatus.PLAYING
        val needsSlowRateRestart = wasPlaying && (previousRate < 0.5f || nextRate < 0.5f)
        val resumeTimeMs = if (needsSlowRateRestart) currentTimeMs() else 0L

        if (needsSlowRateRestart) {
            player.controls().setPause(true)
        }
        player.controls().setRate(nextRate)
        if (needsSlowRateRestart) {
            player.controls().setTime(resumeTimeMs)
            player.controls().play()
        }
    }

    @Volatile private var pendingBrightness = AdjustmentsUiConverter.DEFAULTS.brightness
    @Volatile private var pendingContrast = AdjustmentsUiConverter.DEFAULTS.contrast
    @Volatile private var pendingSaturation = AdjustmentsUiConverter.DEFAULTS.saturation
    @Volatile private var pendingHue: Float = 0.0f
    @Volatile private var pendingGamma = 1.0f
    @Volatile private var dirtyAdjust = false

    @Volatile private var pendingZoom = 1.0f
    @Volatile private var pendingPanX = 0.0f
    @Volatile private var pendingPanY = 0.0f
    @Volatile private var pendingRotationDeg = 0.0f
    @Volatile private var loadedRotationDeg = 0.0f
    @Volatile private var dirtyGeometry = false
    @Volatile private var sourceVideoDimension: Dimension? = null
    @Volatile private var missingGeometrySizeLogged = false
    @Volatile private var lastAppliedCropGeometry: String? = null
    @Volatile private var lastAppliedAspectRatio: String? = null

    private val adjustTimer = javax.swing.Timer(8) {
        if (dirtyAdjust) {
            dirtyAdjust = false
            applyAdjustNow()
        }
        if (dirtyGeometry) {
            dirtyGeometry = false
            applyGeometryNow()
        }
    }.apply { isRepeats = true; start() }

    private val rotationReloadTimer = javax.swing.Timer(120) {
        applyRotationFilterNow()
    }.apply { isRepeats = false }

    private fun createEmbeddedComponent(rotationDeg: Float, playerId: Int): EmbeddedMediaPlayerComponent {
        val args = VlcBootstrap.factoryArguments(VlcPreviewMediaOptions.factoryArguments(rotationDeg))
        logger.info {
            "Creating VLCJ preview component #$playerId: rotationDeg=$rotationDeg, " +
                "factoryArgs=${formatOptions(args)}"
        }
        return EmbeddedMediaPlayerComponent(*args)
    }

    private fun formatOptions(options: Array<String>): String {
        return if (options.isEmpty()) "<none>" else options.joinToString(" ")
    }

    override fun activatePreview(reason: String) {
        val file = mediaFile
        if (file != null && mediaPlayer == null) {
            playMedia(
                file,
                pendingRotationDeg,
                PlaybackRestore(lastKnownTimeMs, playing = false, rate = playbackRate),
                reason
            )
        } else {
            ensureNativePlayer(reason)
        }
    }

    override fun deactivatePreview(reason: String) {
        releaseNativePlayer(reason)
    }

    override fun applyPreviewRotation(rotationDeg: Float, reason: String) {
        pendingRotationDeg = rotationDeg.coerceIn(-180.0f, 180.0f)
        val file = mediaFile ?: return
        val restore = PlaybackRestore(
            timeMs = currentTimeMs().coerceAtLeast(0L),
            playing = status() == PlayerStatus.PLAYING,
            rate = playbackRate,
        )
        logger.info {
            "Applying explicit VLC preview rotation: rotationDeg=$pendingRotationDeg, reason=$reason, " +
                "restore=$restore, factoryArgs=${formatOptions(VlcPreviewMediaOptions.factoryArguments(pendingRotationDeg))}"
        }
        playMedia(file, pendingRotationDeg, restore, reason)
    }

    private fun ensureNativePlayer(reason: String): EmbeddedMediaPlayer {
        claimActive(reason)
        mediaPlayer?.let { return it }

        val nextPlayerId = playerIds.incrementAndGet()
        mediaPlayerInstanceId = nextPlayerId
        val nextComponent = createEmbeddedComponent(pendingRotationDeg, nextPlayerId)
        embeddedComponent = nextComponent
        mediaPlayer = nextComponent.mediaPlayer()
        loadedRotationDeg = pendingRotationDeg
        attachMediaPlayerEvents(nextPlayerId, mediaPlayer ?: nextComponent.mediaPlayer())
        addEmbeddedComponent(nextComponent)
        return mediaPlayer ?: nextComponent.mediaPlayer()
    }

    private fun claimActive(reason: String) {
        val previous = synchronized(activeLock) {
            val current = activeAdapter
            if (current !== this) {
                activeAdapter = this
                current
            } else {
                null
            }
        }
        previous?.releaseNativePlayer("superseded by component #$mediaPlayerInstanceId ($reason)")
    }

    private fun addEmbeddedComponent(nextComponent: EmbeddedMediaPlayerComponent) {
        fun addNow() {
            componentHost.removeAll()
            componentHost.add(nextComponent, BorderLayout.CENTER)
            componentHost.revalidate()
            componentHost.repaint()
        }
        if (SwingUtilities.isEventDispatchThread()) {
            addNow()
        } else {
            SwingUtilities.invokeLater { addNow() }
        }
    }

    private fun releaseNativePlayer(reason: String) {
        val oldComponent = embeddedComponent ?: return
        val oldPlayer = mediaPlayer ?: return
        val oldPlayerId = mediaPlayerInstanceId
        val releaseTimeMs = try {
            oldPlayer.status().time().coerceAtLeast(0L)
        } catch (_: Throwable) {
            lastKnownTimeMs
        }
        lastKnownTimeMs = releaseTimeMs
        durationMs = 0L
        frameDurationMs = 40L
        frameStepCursorMs = null
        sourceVideoDimension = null
        missingGeometrySizeLogged = false
        lastAppliedCropGeometry = null
        lastAppliedAspectRatio = null
        embeddedComponent = null
        mediaPlayer = null
        pendingPlaybackRestore = null
        if (activeAdapter === this) {
            synchronized(activeLock) {
                if (activeAdapter === this) activeAdapter = null
            }
        }

        logger.info {
            "Releasing VLC preview component #$oldPlayerId: reason=$reason, lastKnownTimeMs=$lastKnownTimeMs"
        }
        fun removeNow() {
            componentHost.remove(oldComponent)
            componentHost.revalidate()
            componentHost.repaint()
        }
        if (SwingUtilities.isEventDispatchThread()) {
            removeNow()
        } else {
            SwingUtilities.invokeLater { removeNow() }
        }

        Thread({
            try {
                oldPlayer.controls().stop()
            } catch (_: Throwable) {
            }
            try {
                oldPlayer.release()
            } catch (_: Throwable) {
            }
            try {
                oldComponent.release()
            } catch (_: Throwable) {
            }
        }, "vlcj-preview-release").apply { isDaemon = true }.start()
    }

    override fun isAdjustSupported(): Boolean {
        val player = mediaPlayer ?: return true
        if (!adjustSupportChecked) {
            adjustSupportChecked = true
            adjustSupported = try {
                player.video().setAdjustVideo(true)
                player.video().setBrightness(AdjustmentsUiConverter.DEFAULTS.brightness)
                true
            } catch (_: Throwable) {
                false
            }
            try {
                player.video().setAdjustVideo(false)
            } catch (_: Throwable) {
            }
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
        val player = mediaPlayer ?: return
        player.video().isAdjustVideo = anyColorAdjustmentsMade
        if (anyColorAdjustmentsMade) {
            player.video().setBrightness(pendingBrightness)
            player.video().setContrast(pendingContrast)
            player.video().setSaturation(pendingSaturation)
            player.video().setHue(pendingHue)
            player.video().setGamma(pendingGamma.coerceIn(0.2f, 3.0f))
        }
    }

    override fun applyColorAdjustments(adj: AdjustmentsV1) {
        val preview = colorPreviewStrategy.map(adj)
        pendingBrightness = preview.brightness
        pendingContrast = preview.contrast
        pendingSaturation = preview.saturation
        pendingHue = preview.hue
        pendingGamma = preview.gamma
        dirtyAdjust = true
    }

    override fun applyGeometryAdjustments(adj: AdjustmentsV1): Boolean {
        pendingZoom = adj.zoom.coerceIn(0.1f, 4.0f)
        pendingPanX = adj.panX.coerceIn(-1.0f, 1.0f)
        pendingPanY = adj.panY.coerceIn(-1.0f, 1.0f)
        val nextRotation = adj.rotationDeg.coerceIn(-180.0f, 180.0f)
        if (!VlcPreviewMediaOptions.isSameRotation(pendingRotationDeg, nextRotation)) {
            logger.info {
                "VLC preview rotation requested: component=#$mediaPlayerInstanceId, " +
                    "loadedRotationDeg=$loadedRotationDeg, pendingRotationDeg=$pendingRotationDeg, " +
                    "nextRotationDeg=$nextRotation, " +
                    "nextFactoryArgs=${formatOptions(VlcPreviewMediaOptions.factoryArguments(nextRotation))}"
            }
            pendingRotationDeg = nextRotation
            rotationReloadTimer.restart()
        }
        logger.debug {
            "Queued VLC geometry preview: zoom=$pendingZoom, panX=$pendingPanX, " +
                "panY=$pendingPanY, rotationDeg=$pendingRotationDeg"
        }
        dirtyGeometry = true
        return true
    }

    override fun applyPreviewAdjustments(adj: AdjustmentsV1) {
        applyColorAdjustments(adj)
        applyGeometryAdjustments(adj)
    }

    private fun applyGeometryNow() {
        val player = mediaPlayer ?: return
        if (!VlcPreviewMediaOptions.isSameRotation(loadedRotationDeg, pendingRotationDeg)) {
            rotationReloadTimer.restart()
            return
        }

        val sourceSize = sourceVideoDimension ?: captureSourceVideoDimension()
        if (sourceSize == null) {
            if (!missingGeometrySizeLogged) {
                logger.info {
                    "VLC geometry preview pending; source video dimensions are not available yet. " +
                        "zoom=$pendingZoom, panX=$pendingPanX, panY=$pendingPanY"
                }
                missingGeometrySizeLogged = true
            }
            return
        }
        val crop = VlcCropGeometryCalculator.fromAdjustments(
            sourceSize,
            AdjustmentsV1(zoom = pendingZoom, panX = pendingPanX, panY = pendingPanY)
        )
        if (crop == null) {
            clearGeometryPreview()
            return
        }

        val aspectRatio = VlcCropGeometryCalculator.sourceAspectRatio(sourceSize)
        if (lastAppliedCropGeometry != crop.cropGeometry || lastAppliedAspectRatio != aspectRatio) {
            logger.info {
                "Applying VLC geometry preview: source=${sourceSize.width}x${sourceSize.height}, " +
                    "zoom=$pendingZoom, panX=$pendingPanX, panY=$pendingPanY, " +
                    "cropGeometry=${crop.cropGeometry}, aspectRatio=$aspectRatio"
            }
        }
        try {
            player.video().setCropGeometry(crop.cropGeometry)
            restoreSourceAspectRatio(sourceSize)
            lastAppliedCropGeometry = crop.cropGeometry
            lastAppliedAspectRatio = aspectRatio
        } catch (e: Throwable) {
            logger.warn(e) {
                "Failed to apply VLC geometry preview: source=${sourceSize.width}x${sourceSize.height}, " +
                    "zoom=$pendingZoom, panX=$pendingPanX, panY=$pendingPanY, " +
                    "cropGeometry=${crop.cropGeometry}, aspectRatio=$aspectRatio"
            }
        }
    }

    private fun applyRotationFilterNow() {
        val file = mediaFile ?: return
        if (mediaPlayer == null) return
        if (VlcPreviewMediaOptions.isSameRotation(loadedRotationDeg, pendingRotationDeg)) return

        val restore = PlaybackRestore(
            timeMs = currentTimeMs().coerceAtLeast(0L),
            playing = status() == PlayerStatus.PLAYING,
            rate = playbackRate,
        )
        logger.info {
            "Recreating VLC preview with rotation filter: rotationDeg=$pendingRotationDeg, " +
                "timeMs=${restore.timeMs}, playing=${restore.playing}, " +
                "currentComponent=#$mediaPlayerInstanceId"
        }
        replaceMediaPlayer(pendingRotationDeg, restore)
        playMedia(file, pendingRotationDeg, restore, "rotation")
    }

    private fun captureSourceVideoDimension(): Dimension? {
        val player = mediaPlayer ?: return null
        sourceVideoDimension?.let { return it }
        val dim = try {
            player.video().setCropGeometry(null)
            player.video().videoDimension()
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to read VLC source video dimensions for geometry preview." }
            null
        }
        return dim
            ?.takeIf { it.width > 0 && it.height > 0 }
            ?.let {
                Dimension(it).also { captured ->
                    sourceVideoDimension = captured
                    missingGeometrySizeLogged = false
                    logger.info { "Captured VLC source video dimensions: ${captured.width}x${captured.height}" }
                }
            }
    }

    private fun clearGeometryPreview() {
        val player = mediaPlayer ?: return
        try {
            if (lastAppliedCropGeometry != null || lastAppliedAspectRatio != null) {
                logger.info {
                    "Clearing VLC geometry preview: previousCropGeometry=$lastAppliedCropGeometry, " +
                        "previousAspectRatio=$lastAppliedAspectRatio"
                }
            }
            player.video().setCropGeometry(null)
            player.video().setAspectRatio(null)
            lastAppliedCropGeometry = null
            lastAppliedAspectRatio = null
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to clear VLC geometry preview." }
        }
    }

    private fun restoreSourceAspectRatio(size: Dimension) {
        val player = mediaPlayer ?: return
        try {
            VlcCropGeometryCalculator.sourceAspectRatio(size)?.let { player.video().setAspectRatio(it) }
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to restore VLC source aspect ratio for ${size.width}x${size.height}." }
        }
    }

    private fun attachMediaPlayerEvents(playerId: Int, player: EmbeddedMediaPlayer) {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun mediaPlayerReady(mediaPlayer: MediaPlayer) {
                logger.info { "VLC preview component #$playerId mediaPlayerReady." }
                updateFrameDuration(mediaPlayer)
                applyPreviewOverlay(mediaPlayer)
            }

            override fun videoOutput(mediaPlayer: MediaPlayer, newCount: Int) {
                logger.info {
                    "VLC preview component #$playerId videoOutput: count=$newCount, " +
                        "loadedRotationDeg=$loadedRotationDeg, pendingRotationDeg=$pendingRotationDeg"
                }
                updateFrameDuration(mediaPlayer)
                applyPreviewOverlay(mediaPlayer)
            }

            override fun playing(mediaPlayer: MediaPlayer) {
                logger.debug { "VLC preview component #$playerId playing." }
                onStatusChanged?.invoke(PlayerStatus.PLAYING)
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                logger.debug { "VLC preview component #$playerId paused." }
                onStatusChanged?.invoke(PlayerStatus.PAUSED)
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                logger.debug { "VLC preview component #$playerId stopped." }
                onStatusChanged?.invoke(PlayerStatus.STOPPED)
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                lastKnownTimeMs = newTime.coerceAtLeast(0L)
                onTimeChanged?.invoke(newTime)
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                logger.info {
                    "VLC preview component #$playerId lengthChanged: lengthMs=$newLength, " +
                        "loadedRotationDeg=$loadedRotationDeg, pendingRotationDeg=$pendingRotationDeg, " +
                        "restorePending=${pendingPlaybackRestore != null}"
                }
                durationMs = newLength
                dirtyGeometry = true
                SwingUtilities.invokeLater {
                    restorePlaybackAfterMediaLoad()
                    try {
                        applyAdjustNow()
                    } catch (_: Throwable) {
                    }
                    try {
                        applyGeometryNow()
                    } catch (_: Throwable) {
                    }
                }
                onReady?.invoke()
                onStatusChanged?.invoke(PlayerStatus.READY)
            }

            override fun error(mediaPlayer: MediaPlayer) {
                logger.warn {
                    "VLC preview component #$playerId error: " +
                        "loadedRotationDeg=$loadedRotationDeg, pendingRotationDeg=$pendingRotationDeg"
                }
                onStatusChanged?.invoke(PlayerStatus.ERROR)
            }
        })
    }

    init {
        logger.info { "Created VLC preview host without native player." }
    }

    override fun load(file: File) {
        mediaFile = file
        pendingPlaybackRestore = null
        durationMs = 0L
        frameDurationMs = 40L
        frameStepCursorMs = null
        logger.info {
            "Loading media into VLC preview: component=#$mediaPlayerInstanceId, " +
                "file=${file.absolutePath}, pendingRotationDeg=$pendingRotationDeg, " +
                "loadedRotationDeg=$loadedRotationDeg"
        }
        playMedia(file, pendingRotationDeg, PlaybackRestore(0L, playing = false, rate = playbackRate), "load")
    }

    override fun setSubtitleFile(file: File): Boolean {
        val player = mediaPlayer ?: return false
        return try {
            player.subpictures().setSubTitleFile(file)
        } catch (t: Throwable) {
            logger.warn(t) { "Failed to attach VLC subtitle file: ${file.absolutePath}" }
            false
        }
    }

    private fun playMedia(file: File, rotationDeg: Float, restore: PlaybackRestore?, reason: String) {
        val player = ensureNativePlayer(reason)
        val previousLoadedRotationDeg = loadedRotationDeg
        durationMs = 0L
        sourceVideoDimension = null
        missingGeometrySizeLogged = false
        lastAppliedCropGeometry = null
        lastAppliedAspectRatio = null
        pendingPlaybackRestore = restore

        if (!VlcPreviewMediaOptions.isSameRotation(previousLoadedRotationDeg, rotationDeg)) {
            replaceMediaPlayer(rotationDeg, restore)
            playMedia(file, rotationDeg, restore, "$reason-after-replace")
            return
        } else {
            loadedRotationDeg = rotationDeg
        }

        val options = VlcPreviewMediaOptions.startPaused()
        logger.info {
            "Starting VLC preview media: component=#$mediaPlayerInstanceId, " +
                "file=${file.absolutePath}, rotationDeg=$rotationDeg, " +
                "mediaOptions=${formatOptions(options)}, " +
                "factoryArgs=${formatOptions(VlcPreviewMediaOptions.factoryArguments(rotationDeg))}, " +
                "restore=$restore"
        }
        player.media().play(file.absolutePath, *options)
    }

    private fun restorePlaybackAfterMediaLoad() {
        val player = mediaPlayer ?: return
        val restore = pendingPlaybackRestore ?: return
        try {
            player.controls().setRate(restore.rate)
            player.controls().setTime(restore.timeMs.coerceAtLeast(0L))
            if (restore.playing) {
                player.controls().play()
            } else {
                player.controls().setPause(true)
            }
            pendingPlaybackRestore = null
        } catch (_: Throwable) {
        }
    }

    private fun replaceMediaPlayer(rotationDeg: Float, restore: PlaybackRestore?) {
        val oldComponent = embeddedComponent
        val oldPlayer = mediaPlayer
        val oldPlayerId = mediaPlayerInstanceId
        val nextPlayerId = playerIds.incrementAndGet()
        logger.info {
            "Replacing VLC preview component: old=#$oldPlayerId, new=#$nextPlayerId, " +
                "rotationDeg=$rotationDeg, restore=$restore"
        }
        val nextComponent = createEmbeddedComponent(rotationDeg, nextPlayerId)
        embeddedComponent = nextComponent
        mediaPlayer = nextComponent.mediaPlayer()
        mediaPlayerInstanceId = nextPlayerId
        loadedRotationDeg = rotationDeg
        pendingPlaybackRestore = restore
        attachMediaPlayerEvents(nextPlayerId, mediaPlayer ?: nextComponent.mediaPlayer())

        fun swapComponent() {
            if (oldComponent != null) componentHost.remove(oldComponent)
            componentHost.add(nextComponent, BorderLayout.CENTER)
            componentHost.revalidate()
            componentHost.repaint()
        }
        if (SwingUtilities.isEventDispatchThread()) {
            swapComponent()
        } else {
            SwingUtilities.invokeLater { swapComponent() }
        }

        Thread({
            logger.info { "Releasing old VLC preview component #$oldPlayerId." }
            try {
                oldPlayer?.controls()?.stop()
            } catch (_: Throwable) {
            }
            try {
                oldPlayer?.release()
            } catch (_: Throwable) {
            }
            try {
                oldComponent?.release()
            } catch (_: Throwable) {
            }
        }, "vlcj-preview-release").apply { isDaemon = true }.start()
    }

    override fun play() {
        frameStepCursorMs = null
        mediaFile?.let { activatePreview("play") }
        mediaPlayer?.controls()?.let { controls ->
            controls.setRate(playbackRate)
            controls.play()
        }
    }

    override fun pause() {
        mediaPlayer?.controls()?.setPause(true)
    }

    override fun seek(ms: Long) {
        val target = ms.coerceAtLeast(0L)
        frameStepCursorMs = null
        seekForFrameStep(target)
    }

    private fun seekForFrameStep(target: Long) {
        lastKnownTimeMs = target
        mediaPlayer?.controls()?.setTime(target)
    }

    override fun stepFrameForward(maximumTimeMs: Long): Long {
        val base = frameStepCursorMs ?: currentTimeMs()
        val target = (base + frameDurationMs).coerceAtMost(maximumTimeMs.coerceAtLeast(0L))
        frameStepCursorMs = target
        try {
            mediaPlayer?.controls()?.nextFrame()
        } catch (_: Throwable) {
        }
        return target
    }

    override fun stepFrameBackward(minimumTimeMs: Long): Long {
        val base = frameStepCursorMs ?: currentTimeMs()
        val target = (base - frameDurationMs).coerceAtLeast(minimumTimeMs.coerceAtLeast(0L))
        frameStepCursorMs = target
        seekForFrameStep(target)
        return target
    }

    override fun nextFrame() {
        try {
            mediaPlayer?.controls()?.nextFrame()
        } catch (_: Throwable) {
        }
    }

    override fun currentTimeMs(): Long = mediaPlayer?.status()?.time()?.also { lastKnownTimeMs = it } ?: lastKnownTimeMs
    override fun totalDurationMs(): Long = if (durationMs > 0) durationMs else mediaPlayer?.status()?.length() ?: 0L

    private fun updateFrameDuration(player: MediaPlayer) {
        try {
            val videoTrack = player.media().info().videoTracks().firstOrNull() ?: return
            val frameRate = videoTrack.frameRate()
            val frameRateBase = videoTrack.frameRateBase()
            if (frameRate > 0 && frameRateBase > 0) {
                frameDurationMs = ceil(1_000.0 * frameRateBase / frameRate)
                    .toLong()
                    .coerceAtLeast(1L)
            }
        } catch (_: Throwable) {
            // Keep the 25 fps fallback when VLC has not exposed track metadata yet.
        }
    }

    override fun setPreviewOverlayImage(image: RenderedImage?) {
        previewOverlayImage = image
        mediaPlayer?.let { applyPreviewOverlay(it) }
    }

    private fun applyPreviewOverlay(player: MediaPlayer) {
        try {
            val logo = player.logo()
            val image = previewOverlayImage
            if (image == null) {
                logo.enable(false)
            } else {
                val videoSize = sourceVideoDimension ?: player.video().videoDimension()
                val scale = videoSize
                    ?.takeIf { it.height > 0 }
                    ?.let { it.height / 1080.0 }
                    ?: 1.0
                val scaledImage = scalePreviewOverlay(image, scale)
                val margin = (32 * scale).toInt().coerceAtLeast(8)
                logo.setImage(scaledImage)
                logo.setPosition(LogoPosition.TOP_LEFT)
                logo.setLocation(margin, margin)
                logo.setOpacity(1.0f)
                logo.enable(true)
            }
        } catch (_: Throwable) {
        }
    }

    private fun scalePreviewOverlay(image: RenderedImage, scale: Double): RenderedImage {
        if (scale in 0.99..1.01) return image
        val width = (image.width * scale).toInt().coerceAtLeast(1)
        val height = (image.height * scale).toInt().coerceAtLeast(1)
        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = scaled.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawRenderedImage(
                image,
                java.awt.geom.AffineTransform.getScaleInstance(
                    width.toDouble() / image.width,
                    height.toDouble() / image.height,
                )
            )
        } finally {
            g.dispose()
        }
        return scaled
    }

    override fun status(): PlayerStatus {
        val player = mediaPlayer ?: return PlayerStatus.STOPPED
        return when (player.status().state()) {
            State.PLAYING -> PlayerStatus.PLAYING
            State.PAUSED -> PlayerStatus.PAUSED
            State.STOPPED, State.ENDED -> PlayerStatus.STOPPED
            State.OPENING, State.BUFFERING -> PlayerStatus.READY
            State.ERROR -> PlayerStatus.ERROR
            else -> PlayerStatus.UNKNOWN
        }
    }

    override fun close() {
        adjustTimer.stop()
        rotationReloadTimer.stop()
        releaseNativePlayer("dispose")
    }

    fun dispose() = close()
}
