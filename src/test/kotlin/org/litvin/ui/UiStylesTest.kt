package org.litvin.ui

import org.junit.jupiter.api.Test
import java.awt.Color
import kotlin.test.assertEquals

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
}
