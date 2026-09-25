package org.litvin.ui.commons

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class AnimatedAppMarkTest {
    private var nanos = 0L
    private val mark = AnimatedAppMark(84) { nanos }.apply { setSize(84, 84) }

    // The mark shows the 42-unit view box that starts at 11, so at 84 px one unit is 2 px.
    private fun pixel(x: Double, y: Double) = ((x - 11) * 2).toInt() to ((y - 11) * 2).toInt()

    // At rest, the left seam has its inner point at x = 32 - 21 * 0.6.
    private val leftSeam = pixel(19.4, 32.0)

    private fun renderAt(seconds: Double): BufferedImage {
        nanos = (seconds * 1_000_000_000).toLong()
        val image = BufferedImage(84, 84, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        mark.paint(g)
        g.dispose()
        return image
    }

    private fun BufferedImage.rgbAt(point: Pair<Int, Int>) = Color(getRGB(point.first, point.second), true)

    private fun Color.isDark() = red < 60 && green < 60 && blue < 60

    @Test
    fun ballColorShiftsOverTime() {
        val ballPoint = pixel(22.0, 32.0)
        val first = renderAt(2.0).rgbAt(ballPoint)
        val later = renderAt(4.0).rgbAt(ballPoint)
        assertEquals(255, first.alpha)
        assertNotEquals(first, later)
    }

    @Test
    fun playTriangleTurnsWithTheBallAndCornersStayTransparent() {
        val center = pixel(32.0, 32.0)
        for (seconds in listOf(0.0, 0.3, 5.0)) {
            assertEquals(0, renderAt(seconds).rgbAt(0 to 0).alpha, "corner at $seconds s")
        }
        assertEquals(DETAIL, renderAt(0.0).rgbAt(center), "triangle at rest")
        assertEquals(false, renderAt(0.5).rgbAt(center).isDark(), "triangle on the back at half a turn")
        assertEquals(DETAIL, renderAt(5.0).rgbAt(center), "triangle back after the spin")
    }

    @Test
    fun seamSpinsInTheFirstSecondOfEachCycleAndThenRests() {
        assertEquals(true, renderAt(0.0).rgbAt(leftSeam).isDark(), "rest pose at start")
        assertEquals(false, renderAt(0.35).rgbAt(leftSeam).isDark(), "seam moved during the spin")
        assertEquals(true, renderAt(5.0).rgbAt(leftSeam).isDark(), "rest pose after the spin")
        assertEquals(false, renderAt(16.35).rgbAt(leftSeam).isDark(), "seam moved during the next spin")
    }

    @Test
    fun doesNotAnimateWhileNotShowing() {
        assertFalse(AnimatedAppMark(44).isAnimating)
    }

    private companion object {
        val DETAIL = Color(0x0E, 0x0E, 0x0E)
    }
}
