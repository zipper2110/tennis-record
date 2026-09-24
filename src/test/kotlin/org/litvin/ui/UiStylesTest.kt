package org.litvin.ui

import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiStylesTest {
    @Test
    fun contrastingTextColorPicksWhiteOnDarkAndBlackOnLight() {
        assertEquals(Color.WHITE, UiStyles.contrastingTextColor(Color(0x1A, 0x1A, 0x1A)))
        assertEquals(Color.WHITE, UiStyles.contrastingTextColor(Color(0x2B, 0x49, 0x00)))
        assertEquals(Color.BLACK, UiStyles.contrastingTextColor(Color(0xFF, 0xD5, 0x4A)))
        assertEquals(Color.BLACK, UiStyles.contrastingTextColor(Color(0xA1, 0xFE, 0x00)))
        // Mid-tone blue: 5.7:1 against black beats 3.7:1 against white, so black wins.
        assertEquals(Color.BLACK, UiStyles.contrastingTextColor(Color(0x3B, 0x82, 0xF6)))
    }

    @Test
    fun contrastingTextColorUsesLuminanceNotChannelSum() {
        // Pure green is bright to the eye despite only one channel being lit.
        assertEquals(Color.BLACK, UiStyles.contrastingTextColor(Color(0x00, 0xFF, 0x00)))
        // Pure blue is dark to the eye even at full intensity.
        assertEquals(Color.WHITE, UiStyles.contrastingTextColor(Color(0x00, 0x00, 0xFF)))
    }

    @Test
    fun serveRacketIconStaysInsideItsBox() {
        for (size in listOf(12, 14, 16, 24)) {
            for (active in listOf(true, false)) {
                val icon = UiStyles.serveRacketIcon(size, active = active, color = Color.WHITE)
                val margin = 8
                val image = BufferedImage(size + margin * 2, size + margin * 2, BufferedImage.TYPE_INT_ARGB)
                val g = image.createGraphics()
                icon.paintIcon(null, g, margin, margin)
                g.dispose()
                val outside = (0 until image.width).flatMap { px -> (0 until image.height).map { py -> px to py } }
                    .filter { (px, py) -> px !in margin until margin + size || py !in margin until margin + size }
                    .count { (px, py) -> (image.getRGB(px, py) ushr 24) > 0 }
                assertEquals(0, outside, "size $size, active $active: pixels outside the icon box")
                val inside = (margin until margin + size).sumOf { px ->
                    (margin until margin + size).count { py -> (image.getRGB(px, py) ushr 24) > 0 }
                }
                assertTrue(inside > 0, "size $size, active $active: the icon paints nothing")
            }
        }
    }
}
