package org.litvin.media

import org.litvin.adjustments.AdjustmentsV1
import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VlcCropGeometryCalculatorTest {
    @Test
    fun identityHasNoCropGeometry() {
        assertNull(VlcCropGeometryCalculator.fromAdjustments(Dimension(1920, 1080), AdjustmentsV1()))
    }

    @Test
    fun centeredZoomUsesSourceAspectCrop() {
        val crop = VlcCropGeometryCalculator.fromAdjustments(
            Dimension(1920, 1080),
            AdjustmentsV1(zoom = 1.5f)
        )

        assertEquals(VlcCropGeometry(1280, 720, 320, 180), crop)
        assertEquals("1280x720+320+180", crop?.cropGeometry)
    }

    @Test
    fun panMapsToCropTravelWithoutChangingSize() {
        val topLeft = VlcCropGeometryCalculator.fromAdjustments(
            Dimension(1920, 1080),
            AdjustmentsV1(zoom = 1.5f, panX = -1.0f, panY = 1.0f)
        )
        val right = VlcCropGeometryCalculator.fromAdjustments(
            Dimension(1920, 1080),
            AdjustmentsV1(zoom = 1.5f, panX = 1.0f, panY = 0.0f)
        )

        assertEquals(VlcCropGeometry(1280, 720, 0, 0), topLeft)
        assertEquals(VlcCropGeometry(1280, 720, 640, 180), right)
    }

    @Test
    fun sourceAspectRatioMatchesNaturalVideoSize() {
        assertEquals("1920:1080", VlcCropGeometryCalculator.sourceAspectRatio(Dimension(1920, 1080)))
    }
}
