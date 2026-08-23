package org.litvin.media

import org.litvin.adjustments.AdjustmentsV1
import java.awt.Component
import java.awt.image.RenderedImage
import java.io.File

interface SwingMediaPlayer : AutoCloseable {
    val component: Component
    var onReady: (() -> Unit)?
    var onStatusChanged: ((PlayerStatus) -> Unit)?
    var onTimeChanged: ((Long) -> Unit)?

    fun load(file: File)
    fun play()
    fun pause()
    fun seek(ms: Long)
    fun setRate(rate: Float)
    fun status(): PlayerStatus
    fun currentTimeMs(): Long
    fun totalDurationMs(): Long
    fun isAdjustSupported(): Boolean
    fun applyColorAdjustments(adj: AdjustmentsV1)
    fun applyGeometryAdjustments(adj: AdjustmentsV1): Boolean
    fun applyPreviewAdjustments(adj: AdjustmentsV1)
    fun applyPreviewRotation(rotationDeg: Float, reason: String = "apply rotation")
    fun setPreviewOverlayImage(image: RenderedImage?)
    fun stepFrameForward(maximumTimeMs: Long = Long.MAX_VALUE): Long
    fun stepFrameBackward(minimumTimeMs: Long = 0L): Long
    fun nextFrame()
    fun setSubtitleFile(file: File): Boolean
    fun activatePreview(reason: String = "activate")
    fun deactivatePreview(reason: String = "deactivate")
    override fun close()
}
