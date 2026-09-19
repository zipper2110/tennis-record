package org.litvin.ui.flow.fakes

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.media.MediaPlayerFactory
import org.litvin.media.MediaScreen
import org.litvin.media.PlayerStatus
import org.litvin.media.SwingMediaPlayer
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.image.BufferedImage
import java.awt.image.RenderedImage
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel

data class MediaPlayerCall(
    val screen: MediaScreen,
    val action: String,
    val detail: String? = null,
)

class FakeMediaPlayer(
    private val screen: MediaScreen,
    private val recordedCalls: MutableList<MediaPlayerCall> = CopyOnWriteArrayList(),
) : SwingMediaPlayer {
    override val component: Component = JPanel().apply {
        name = "fake-media-${screen.name.lowercase()}"
        background = Color.BLACK
        isOpaque = true
    }
    override var onReady: (() -> Unit)? = null
    override var onStatusChanged: ((PlayerStatus) -> Unit)? = null
    override var onTimeChanged: ((Long) -> Unit)? = null

    @Volatile private var playerStatus = PlayerStatus.UNKNOWN
    @Volatile private var positionMs = 0L
    private val closed = AtomicBoolean(false)

    val calls: List<MediaPlayerCall> get() = recordedCalls.toList()

    override fun load(file: File) {
        record("load", file.absolutePath)
        positionMs = 0L
        playerStatus = PlayerStatus.READY
        val readyPosition = positionMs
        val readyStatus = playerStatus
        onEdt {
            onReady?.invoke()
            onStatusChanged?.invoke(readyStatus)
            onTimeChanged?.invoke(readyPosition)
        }
    }

    override fun play() {
        record("play")
        playerStatus = PlayerStatus.PLAYING
        val status = playerStatus
        onEdt { onStatusChanged?.invoke(status) }
    }

    override fun pause() {
        record("pause")
        playerStatus = PlayerStatus.PAUSED
        val status = playerStatus
        onEdt { onStatusChanged?.invoke(status) }
    }

    override fun seek(ms: Long) {
        positionMs = ms.coerceIn(0L, DURATION_MS)
        record("seek", positionMs.toString())
        val position = positionMs
        onEdt { onTimeChanged?.invoke(position) }
    }

    override fun setRate(rate: Float) = record("set-rate", rate.toString())
    override fun status(): PlayerStatus = playerStatus
    override fun currentTimeMs(): Long = positionMs
    override fun totalDurationMs(): Long = DURATION_MS
    override fun isAdjustSupported(): Boolean = true
    override fun applyColorAdjustments(adj: AdjustmentsV1) = record("color-adjustments", adj.toString())
    override fun applyGeometryAdjustments(adj: AdjustmentsV1): Boolean {
        record("geometry-adjustments", adj.toString())
        return true
    }
    override fun applyPreviewAdjustments(adj: AdjustmentsV1) = record("preview-adjustments", adj.toString())
    override fun applyPreviewRotation(rotationDeg: Float, reason: String) =
        record("preview-rotation", "$rotationDeg ($reason)")
    override fun setPreviewOverlayImage(image: RenderedImage?) =
        record("preview-overlay", if (image == null) "clear" else "${image.width}x${image.height}")

    override fun stepFrameForward(maximumTimeMs: Long): Long {
        seek((positionMs + FRAME_MS).coerceAtMost(maximumTimeMs))
        record("step-forward", positionMs.toString())
        return positionMs
    }

    override fun stepFrameBackward(minimumTimeMs: Long): Long {
        seek((positionMs - FRAME_MS).coerceAtLeast(minimumTimeMs))
        record("step-backward", positionMs.toString())
        return positionMs
    }

    override fun nextFrame() {
        stepFrameForward()
        record("next-frame", positionMs.toString())
    }

    override fun setSubtitleFile(file: File): Boolean {
        record("subtitle", file.absolutePath)
        return true
    }

    override fun activatePreview(reason: String) = record("activate-preview", reason)
    override fun deactivatePreview(reason: String) = record("deactivate-preview", reason)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            playerStatus = PlayerStatus.STOPPED
            record("close")
            val status = playerStatus
            onEdt { onStatusChanged?.invoke(status) }
        }
    }

    private fun record(action: String, detail: String? = null) {
        recordedCalls += MediaPlayerCall(screen, action, detail)
    }

    private fun onEdt(callback: () -> Unit) {
        if (EventQueue.isDispatchThread()) callback() else EventQueue.invokeLater(callback)
    }

    private companion object {
        const val DURATION_MS = 10_000L
        const val FRAME_MS = 40L
    }
}

class FakeMediaPlayerFactory : MediaPlayerFactory {
    private val recordedCalls = CopyOnWriteArrayList<MediaPlayerCall>()
    private val createdPlayers = CopyOnWriteArrayList<FakeMediaPlayer>()
    private val closed = AtomicBoolean(false)

    val players: List<FakeMediaPlayer> get() = createdPlayers.toList()
    val calls: List<MediaPlayerCall> get() = recordedCalls.toList()

    override fun create(screen: MediaScreen): SwingMediaPlayer {
        check(!closed.get()) { "Fake media-player factory is closed" }
        return FakeMediaPlayer(screen, recordedCalls).also(createdPlayers::add)
    }

    fun assertEditingFlowEvents() {
        val snapshot = calls
        val requiredScreens = setOf(MediaScreen.MARKUP, MediaScreen.COLORS, MediaScreen.SCORING)
        val loadedScreens = snapshot.filter { it.action == "load" }.mapTo(linkedSetOf()) { it.screen }
        check(loadedScreens.containsAll(requiredScreens)) {
            "Expected media loads for $requiredScreens, recorded $snapshot"
        }
        check(snapshot.any { it.screen == MediaScreen.COLORS && it.action == "play" }) {
            "Expected Colors playback to start, recorded $snapshot"
        }
        check(snapshot.any { it.screen == MediaScreen.COLORS && it.action == "pause" }) {
            "Expected Colors playback to pause, recorded $snapshot"
        }
        check(snapshot.any { it.action == "seek" }) {
            "Expected at least one fake-media seek, recorded $snapshot"
        }
        check(createdPlayers.all { it.javaClass == FakeMediaPlayer::class.java }) {
            "Unexpected native media-player implementation: ${createdPlayers.map { it.javaClass.name }}"
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        createdPlayers.forEach(FakeMediaPlayer::close)
    }
}

