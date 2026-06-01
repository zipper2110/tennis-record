package org.litvin.adjustments

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class CropRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

object CropGeometryMath {
    private const val MIN_OVERLAY_SIZE = 24.0
    private const val EPSILON = 0.000001

    fun overlayFromModel(outputWidth: Double, outputHeight: Double, adjustments: AdjustmentsV1): CropRect {
        if (outputWidth <= 0.0 || outputHeight <= 0.0) return CropRect(0.0, 0.0, 0.0, 0.0)

        val zoom = adjustments.zoom.coerceIn(0.1f, 4.0f).toDouble().coerceAtLeast(0.0001)
        val cropWidth = (outputWidth / zoom).coerceIn(MIN_OVERLAY_SIZE.coerceAtMost(outputWidth), outputWidth)
        val cropHeight = (outputHeight / zoom).coerceIn(MIN_OVERLAY_SIZE.coerceAtMost(outputHeight), outputHeight)
        val travelX = (outputWidth - cropWidth).coerceAtLeast(0.0)
        val travelY = (outputHeight - cropHeight).coerceAtLeast(0.0)
        val centerX = outputWidth / 2.0 + adjustments.panX.coerceIn(-1.0f, 1.0f) * travelX / 2.0
        val centerY = outputHeight / 2.0 - adjustments.panY.coerceIn(-1.0f, 1.0f) * travelY / 2.0

        return CropRect(
            x = (centerX - cropWidth / 2.0).coerceIn(0.0, travelX),
            y = (centerY - cropHeight / 2.0).coerceIn(0.0, travelY),
            width = cropWidth,
            height = cropHeight,
        )
    }

    fun modelFromOverlay(
        outputWidth: Double,
        outputHeight: Double,
        overlay: CropRect,
        previous: AdjustmentsV1,
    ): AdjustmentsV1 {
        if (outputWidth <= 0.0 || outputHeight <= 0.0 || overlay.width <= 0.0 || overlay.height <= 0.0) {
            return previous
        }

        val clamped = clampInside(
            overlay,
            CropRect(0.0, 0.0, outputWidth, outputHeight),
            outputWidth / outputHeight,
        )
        val zoom = (outputWidth / clamped.width).coerceIn(0.1, 4.0).toFloat()
        val panX = if (clamped.width < outputWidth - 0.5) {
            ((2.0 * clamped.x + clamped.width - outputWidth) / (outputWidth - clamped.width))
                .coerceIn(-1.0, 1.0)
                .toFloat()
        } else {
            0.0f
        }
        val panY = if (clamped.height < outputHeight - 0.5) {
            ((outputHeight - (2.0 * clamped.y + clamped.height)) / (outputHeight - clamped.height))
                .coerceIn(-1.0, 1.0)
                .toFloat()
        } else {
            0.0f
        }

        return previous.copy(zoom = zoom, panX = panX, panY = panY)
    }

    fun clampInside(rect: CropRect, bounds: CropRect, aspect: Double): CropRect {
        if (bounds.width <= 0.0 || bounds.height <= 0.0) return CropRect(bounds.x, bounds.y, 0.0, 0.0)
        val safeAspect = aspect.takeIf { it > 0.0 } ?: (bounds.width / bounds.height)
        val maxWidthByHeight = bounds.height * safeAspect
        val maxWidth = min(bounds.width, maxWidthByHeight).coerceAtLeast(MIN_OVERLAY_SIZE.coerceAtMost(bounds.width))
        val minWidth = MIN_OVERLAY_SIZE.coerceAtMost(maxWidth)
        val width = rect.width.coerceIn(minWidth, maxWidth)
        val rawHeight = width / safeAspect
        val height = if (rawHeight > bounds.height && rawHeight - bounds.height <= EPSILON) {
            bounds.height
        } else {
            rawHeight.coerceAtMost(bounds.height)
        }
        val x = coerceCoordinate(rect.x, bounds.x, bounds.x + bounds.width - width)
        val y = coerceCoordinate(rect.y, bounds.y, bounds.y + bounds.height - height)
        return CropRect(x, y, width, height)
    }

    private fun coerceCoordinate(value: Double, minimum: Double, maximum: Double): Double {
        return if (maximum <= minimum) minimum else value.coerceIn(minimum, maximum)
    }

    fun centeredResize(start: CropRect, bounds: CropRect, aspect: Double, widthDelta: Double): CropRect {
        val centerX = start.x + start.width / 2.0
        val centerY = start.y + start.height / 2.0
        val targetWidth = start.width + widthDelta
        val resized = CropRect(
            x = centerX - targetWidth / 2.0,
            y = centerY - (targetWidth / aspect) / 2.0,
            width = targetWidth,
            height = targetWidth / aspect,
        )
        return clampInside(resized, bounds, aspect)
    }

    fun largestCenteredInscribedRect(
        width: Double,
        height: Double,
        rotationDeg: Double,
        aspect: Double,
    ): CropRect {
        if (width <= 0.0 || height <= 0.0) return CropRect(0.0, 0.0, 0.0, 0.0)
        val safeAspect = aspect.takeIf { it > 0.0 } ?: (width / height)
        val normalizedRotation = normalizeRotation(rotationDeg.toFloat()).toDouble()
        if (abs(normalizedRotation) < 0.001 || abs(abs(normalizedRotation) - 180.0) < 0.001) {
            return CropRect(0.0, 0.0, width, height)
        }

        val radians = Math.toRadians(normalizedRotation)
        val cos = cos(radians)
        val sin = sin(radians)
        val halfW = width / 2.0
        val halfH = height / 2.0

        fun fits(candidateWidth: Double): Boolean {
            val candidateHeight = candidateWidth / safeAspect
            val points = arrayOf(
                -candidateWidth / 2.0 to -candidateHeight / 2.0,
                candidateWidth / 2.0 to -candidateHeight / 2.0,
                candidateWidth / 2.0 to candidateHeight / 2.0,
                -candidateWidth / 2.0 to candidateHeight / 2.0,
            )
            return points.all { (x, y) ->
                val unrotatedX = x * cos + y * sin
                val unrotatedY = -x * sin + y * cos
                abs(unrotatedX) <= halfW + 0.001 && abs(unrotatedY) <= halfH + 0.001
            }
        }

        var low = 0.0
        var high = min(width, height * safeAspect)
        repeat(40) {
            val mid = (low + high) / 2.0
            if (fits(mid)) low = mid else high = mid
        }

        val resultWidth = max(MIN_OVERLAY_SIZE.coerceAtMost(width), low)
        val resultHeight = (resultWidth / safeAspect).coerceAtMost(height)
        return CropRect(
            x = (width - resultWidth) / 2.0,
            y = (height - resultHeight) / 2.0,
            width = resultWidth,
            height = resultHeight,
        )
    }

    fun normalizeRotation(degrees: Float): Float {
        var value = degrees
        while (value > 180.0f) value -= 360.0f
        while (value < -180.0f) value += 360.0f
        return value
    }

    fun snapRotation(degrees: Float, forceSnap: Boolean): Float {
        val normalized = normalizeRotation(degrees)
        val targets = floatArrayOf(-180.0f, -90.0f, 0.0f, 90.0f, 180.0f)
        val closest = targets.minBy { abs(it - normalized) }
        return if (forceSnap || abs(closest - normalized) <= 2.0f) {
            normalizeRotation(closest)
        } else {
            normalized
        }
    }
}
