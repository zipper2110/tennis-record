package org.litvin.media

import org.litvin.adjustments.AdjustmentsV1
import java.awt.Dimension
import kotlin.math.abs
import kotlin.math.roundToInt

data class VlcCropGeometry(
    val cropWidth: Int,
    val cropHeight: Int,
    val x: Int,
    val y: Int,
) {
    val cropGeometry: String = "${cropWidth}x${cropHeight}+${x}+${y}"
}

object VlcCropGeometryCalculator {
    fun fromAdjustments(sourceSize: Dimension, adjustments: AdjustmentsV1): VlcCropGeometry? {
        val sourceWidth = sourceSize.width
        val sourceHeight = sourceSize.height
        if (sourceWidth <= 0 || sourceHeight <= 0) return null

        val zoom = adjustments.zoom.coerceIn(0.1f, 4.0f)
        val panX = adjustments.panX.coerceIn(-1.0f, 1.0f)
        val panY = (-1.0f * adjustments.panY).coerceIn(-1.0f, 1.0f)
        if (abs(zoom - 1.0f) < 0.001f && abs(panX) < 0.001f && abs(panY) < 0.001f) {
            return null
        }

        val width = (sourceWidth / zoom).roundToInt().coerceIn(1, sourceWidth)
        val centerX = (sourceWidth / 2.0).roundToInt()
        val minX = centerX - (width / 2.0).roundToInt()
        val maxX = centerX + (width / 2.0).roundToInt()

        val height = (sourceHeight / zoom).roundToInt().coerceIn(1, sourceHeight)
        val centerY = (sourceHeight / 2.0).roundToInt()
        val minY = centerY - (height / 2.0).roundToInt()
        val maxY = centerY + (height / 2.0).roundToInt()

        val panPixelsX = (panX * (sourceWidth / 2.0f - width / 2.0f)).roundToInt()
        val panPixelsY = (panY * (sourceHeight / 2.0f - height / 2.0f)).roundToInt()

        val pannedMinX = minX + panPixelsX
        val pannedMaxX = maxX + panPixelsX

//        val pannedMinY = sourceWidth - (minY + panPixelsY)
        val pannedMinY = minY + panPixelsY
//        val pannedMaxY = sourceWidth - (maxY + panPixelsY)
        val pannedMaxY = maxY + panPixelsY

        return VlcCropGeometry(pannedMaxX, pannedMaxY, pannedMinX, pannedMinY)
    }

    fun sourceAspectRatio(size: Dimension): String? {
        return if (size.width > 0 && size.height > 0) "${size.width}:${size.height}" else null
    }
}
