package org.litvin.media

import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.File

data class StillFrameMediaInfo(
    val durationMs: Long,
    val frameSize: Dimension?,
)

interface StillFrameCaptureService : AutoCloseable {
    fun load(file: File): StillFrameMediaInfo
    fun captureAt(ms: Long): BufferedImage?
    fun durationMs(): Long
    override fun close()
}
