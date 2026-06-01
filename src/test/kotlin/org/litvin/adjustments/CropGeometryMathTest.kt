package org.litvin.adjustments

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CropGeometryMathTest {
    @Test
    fun overlayModelRoundTripPreservesTransform() {
        val original = AdjustmentsV1(zoom = 2.0f, panX = 0.5f, panY = -0.25f, rotationDeg = 12.5f)
        val overlay = CropGeometryMath.overlayFromModel(1920.0, 1080.0, original)
        val roundTrip = CropGeometryMath.modelFromOverlay(1920.0, 1080.0, overlay, original)

        assertNear(original.zoom, roundTrip.zoom, 0.01f)
        assertNear(original.panX, roundTrip.panX, 0.01f)
        assertNear(original.panY, roundTrip.panY, 0.01f)
        assertEquals(original.rotationDeg, roundTrip.rotationDeg)
    }

    @Test
    fun clampInsideKeepsOverlayWithinBoundsAndAspect() {
        val bounds = CropRect(10.0, 20.0, 640.0, 360.0)
        val outside = CropRect(-100.0, -100.0, 900.0, 100.0)
        val clamped = CropGeometryMath.clampInside(outside, bounds, 16.0 / 9.0)

        assertTrue(clamped.x >= bounds.x)
        assertTrue(clamped.y >= bounds.y)
        assertTrue(clamped.x + clamped.width <= bounds.x + bounds.width + 0.001)
        assertTrue(clamped.y + clamped.height <= bounds.y + bounds.height + 0.001)
        assertTrue(abs((clamped.width / clamped.height) - (16.0 / 9.0)) < 0.001)
    }

    @Test
    fun rotatedInscribedRectShrinksAndStaysCentered() {
        val rect = CropGeometryMath.largestCenteredInscribedRect(1920.0, 1080.0, 45.0, 16.0 / 9.0)

        assertTrue(rect.width < 1920.0)
        assertTrue(rect.height < 1080.0)
        assertNear(1920.0 / 2.0, rect.x + rect.width / 2.0, 0.5)
        assertNear(1080.0 / 2.0, rect.y + rect.height / 2.0, 0.5)
    }

    @Test
    fun clampInsideToleratesNearEqualBoundsAfterRotation() {
        val bounds = CropRect(
            x = 231.283734500025,
            y = 231.283734500025,
            width = 420.0,
            height = 231.28373450002493
        )
        val rect = CropRect(
            x = 231.283734500025,
            y = 231.283734500025,
            width = 411.1719724444888,
            height = 231.283734500025
        )

        val clamped = CropGeometryMath.clampInside(rect, bounds, 16.0 / 9.0)

        assertNear(bounds.y, clamped.y, 0.000001)
        assertTrue(clamped.height <= bounds.height + 0.000001)
    }

    @Test
    fun rotationSnapsNearCardinalAngles() {
        assertEquals(90.0f, CropGeometryMath.snapRotation(88.5f, forceSnap = false))
        assertEquals(90.0f, CropGeometryMath.snapRotation(74.0f, forceSnap = true))
        assertEquals(180.0f, CropGeometryMath.normalizeRotation(180.0f))
    }

    private fun assertNear(expected: Float, actual: Float, tolerance: Float) {
        assertTrue(abs(expected - actual) <= tolerance, "Expected $actual to be within $tolerance of $expected")
    }

    private fun assertNear(expected: Double, actual: Double, tolerance: Double) {
        assertTrue(abs(expected - actual) <= tolerance, "Expected $actual to be within $tolerance of $expected")
    }
}
