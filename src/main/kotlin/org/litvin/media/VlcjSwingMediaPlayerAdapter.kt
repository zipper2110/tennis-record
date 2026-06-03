package org.litvin.media

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.ui.tabs.adjustments.AdjustmentsUiConverter
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.State
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Swing VLCJ-backed media player using VLCJ's embedded native output.
 *
 * The component remains laid out at normal preview size; zoom/pan preview is
 * delegated to VLC crop geometry rather than Swing component transforms.
 */
class VlcjSwingMediaPlayerAdapter(
    private val colorPreviewStrategy: ColorPreviewAdjustmentStrategy = CalibratedVlcColorPreviewAdjustmentStrategy,
) {
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

    val component: Component get() = componentHost

    private data class PlaybackRestore(
        val timeMs: Long,
        val playing: Boolean,
        val rate: Float,
    )

    private var durationMs: Long = 0L
    private var mediaFile: File? = null
    @Volatile private var playbackRate: Float = 1.0f
    @Volatile private var lastKnownTimeMs: Long = 0L
    @Volatile private var pendingPlaybackRestore: PlaybackRestore? = null

    @Volatile private var adjustSupportChecked: Boolean = false
    @Volatile private var adjustSupported: Boolean = false

    var onReady: (() -> Unit)? = null
    var onStatusChanged: ((PlayerStatus) -> Unit)? = null
    var onTimeChanged: ((Long) -> Unit)? = null

    fun setRate(rate: Float) {
        playbackRate = rate
        mediaPlayer?.controls()?.setRate(rate)
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
        val args = VlcPreviewMediaOptions.factoryArguments(rotationDeg)
        logger.info {
            "Creating VLCJ preview component #$playerId: rotationDeg=$rotationDeg, " +
                "factoryArgs=${formatOptions(args)}"
        }
        return EmbeddedMediaPlayerComponent(*args)
    }

    private fun formatOptions(options: Array<String>): String {
        return if (options.isEmpty()) "<none>" else options.joinToString(" ")
    }

    fun activatePreview(reason: String = "activate") {
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

    fun deactivatePreview(reason: String = "deactivate") {
        releaseNativePlayer(reason)
    }

    fun applyPreviewRotation(rotationDeg: Float, reason: String = "apply rotation") {
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

    fun isAdjustSupported(): Boolean {
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

    fun applyColorAdjustments(adj: AdjustmentsV1) {
        val preview = colorPreviewStrategy.map(adj)
        pendingBrightness = preview.brightness
        pendingContrast = preview.contrast
        pendingSaturation = preview.saturation
        pendingHue = preview.hue
        pendingGamma = preview.gamma
        dirtyAdjust = true
    }

    fun applyGeometryAdjustments(adj: AdjustmentsV1): Boolean {
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

    fun applyPreviewAdjustments(adj: AdjustmentsV1) {
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
            }

            override fun videoOutput(mediaPlayer: MediaPlayer, newCount: Int) {
                logger.info {
                    "VLC preview component #$playerId videoOutput: count=$newCount, " +
                        "loadedRotationDeg=$loadedRotationDeg, pendingRotationDeg=$pendingRotationDeg"
                }
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

    fun load(file: File) {
        mediaFile = file
        pendingPlaybackRestore = null
        durationMs = 0L
        logger.info {
            "Loading media into VLC preview: component=#$mediaPlayerInstanceId, " +
                "file=${file.absolutePath}, pendingRotationDeg=$pendingRotationDeg, " +
                "loadedRotationDeg=$loadedRotationDeg"
        }
        playMedia(file, pendingRotationDeg, PlaybackRestore(0L, playing = false, rate = playbackRate), "load")
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

    fun play() {
        mediaFile?.let { activatePreview("play") }
        mediaPlayer?.controls()?.play()
    }

    fun pause() {
        mediaPlayer?.controls()?.setPause(true)
    }

    fun seek(ms: Long) {
        val target = ms.coerceAtLeast(0L)
        lastKnownTimeMs = target
        mediaPlayer?.controls()?.setTime(target)
    }

    fun stepFrameForward() {
        try {
            mediaPlayer?.controls()?.nextFrame()
        } catch (_: Throwable) {
        }
    }

    fun stepFrameBackward() {
        try {
            val target = (currentTimeMs() - 40L).coerceAtLeast(0L)
            mediaPlayer?.controls()?.setTime(target)
            mediaPlayer?.controls()?.nextFrame()
        } catch (_: Throwable) {
        }
    }

    fun nextFrame() = try {
        mediaPlayer?.controls()?.nextFrame()
    } catch (_: Throwable) {
    }

    fun currentTimeMs(): Long = mediaPlayer?.status()?.time()?.also { lastKnownTimeMs = it } ?: lastKnownTimeMs
    fun totalDurationMs(): Long = if (durationMs > 0) durationMs else mediaPlayer?.status()?.length() ?: 0L

    fun status(): PlayerStatus {
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

    fun dispose() {
        adjustTimer.stop()
        rotationReloadTimer.stop()
        releaseNativePlayer("dispose")
    }
}
