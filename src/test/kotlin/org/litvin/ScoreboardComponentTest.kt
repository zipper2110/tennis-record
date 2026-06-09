package org.litvin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScoreboardComponentTest {
    @Test
    fun classicDisplayNormalizesNamesColorsPointsAndSets() {
        val display = ScoreboardComponent.display(
            OverlaySpan(
                startMs = 0L,
                endMs = 1_000L,
                text = "",
                p1Name = "Alexandria Very Longname",
                p2Name = "Bob",
                p1ColorHex = "#112233",
                p2ColorHex = "#445566",
                p1Pts = 4,
                p2Pts = 3,
                gamesP1 = 5,
                gamesP2 = 4,
                completedSets = listOf(6 to 2, 3 to 6, 7 to 5),
            )
        )

        assertEquals("ALEXANDRIA VERY LON...", display.player1Name)
        assertEquals("ALEXANDRIA ...", display.player1PreviewName)
        assertEquals("BOB", display.player2Name)
        assertEquals(0x112233, display.player1Rgb)
        assertEquals(0x445566, display.player2Rgb)
        assertEquals("Ad", display.player1PointText)
        assertEquals("40", display.player2PointText)
        assertEquals(listOf(3 to 6, 7 to 5), display.completedSets)
        assertTrue(display.player1Leading)
    }

    @Test
    fun tiedFortyIsNotLeading() {
        val display = ScoreboardComponent.display(
            OverlaySpan(
                startMs = 0L,
                endMs = 1_000L,
                text = "",
                p1Pts = 3,
                p2Pts = 3,
            )
        )

        assertEquals("40", display.player1PointText)
        assertEquals("40", display.player2PointText)
        assertFalse(display.player1Leading)
    }
}
