package org.litvin.adjustments

import kotlin.math.abs

/**
 * The output geometry for a source frame: rotate the frame clockwise about its center (the frame size
 * does not change), then crop [crop]. The preview shader and the FFmpeg export both use this plan.
 *
 * [crop] is a fraction of the frame size, in the space of the rotated frame.
 */
data class GeometryPlan(
    val rotationDeg: Double,
    val crop: CropRect,
) {
    val hasRotation: Boolean get() = abs(rotationDeg) >= ROTATION_EPSILON_DEG
    val hasCrop: Boolean get() = crop != FULL_FRAME
    val isIdentity: Boolean get() = !hasRotation && !hasCrop

    /** The crop in whole pixels of a [width] x [height] frame. Exact for the frame size that built this plan. */
    fun cropPixels(width: Int, height: Int): IntArray = intArrayOf(
        Math.round(crop.width * width).toInt(),
        Math.round(crop.height * height).toInt(),
        Math.round(crop.x * width).toInt(),
        Math.round(crop.y * height).toInt(),
    )

    companion object {
        private const val ROTATION_EPSILON_DEG = 0.001
        private const val CROP_EPSILON = 1e-4

        val FULL_FRAME = CropRect(0.0, 0.0, 1.0, 1.0)
        val IDENTITY = GeometryPlan(0.0, FULL_FRAME)

        /**
         * Builds the plan for a [width] x [height] frame. The crop rectangle has the frame aspect and stays
         * inside the rotated frame, with the same math as the Crop & Rotate editor.
         */
        fun of(adjustments: AdjustmentsV1, width: Int, height: Int): GeometryPlan {
            val rotation = CropGeometryMath.normalizeRotation(adjustments.rotationDeg.coerceIn(-180.0f, 180.0f)).toDouble()
            if (width <= 0 || height <= 0) return GeometryPlan(rotation, FULL_FRAME)
            val w = width.toDouble()
            val h = height.toDouble()
            val aspect = w / h
            val model = CropGeometryMath.overlayFromModel(w, h, adjustments)
            val allowed = CropGeometryMath.largestCenteredInscribedRect(w, h, rotation, aspect)
            val clamped = CropGeometryMath.clampInside(model, allowed, aspect)
            val normalized = CropRect(clamped.x / w, clamped.y / h, clamped.width / w, clamped.height / h)
            if (isFullFrame(normalized)) return GeometryPlan(rotation, FULL_FRAME)
            // Snap to even source pixels: FFmpeg crops 4:2:0 video at even whole pixels.
            // The preview uses the same snapped rectangle, so both crop the same pixels.
            val pixels = snapToEvenPixels(clamped, width, height)
            return GeometryPlan(
                rotation,
                CropRect(pixels.x / w, pixels.y / h, pixels.width / w, pixels.height / h),
            )
        }

        /** Width and height round down, so the crop stays inside the rotated frame. x and y round to nearest. */
        private fun snapToEvenPixels(rect: CropRect, width: Int, height: Int): CropRect {
            fun even(value: Double) = Math.round(value / 2.0) * 2.0
            fun evenDown(value: Double) = Math.floor(value / 2.0 + 1e-9) * 2.0
            val snappedWidth = evenDown(rect.width).coerceIn(2.0, width.toDouble())
            val snappedHeight = evenDown(rect.height).coerceIn(2.0, height.toDouble())
            val x = even(rect.x).coerceIn(0.0, width - snappedWidth)
            val y = even(rect.y).coerceIn(0.0, height - snappedHeight)
            return CropRect(x, y, snappedWidth, snappedHeight)
        }

        private fun isFullFrame(rect: CropRect): Boolean =
            abs(rect.x) < CROP_EPSILON && abs(rect.y) < CROP_EPSILON &&
                abs(rect.width - 1.0) < CROP_EPSILON && abs(rect.height - 1.0) < CROP_EPSILON
    }
}
