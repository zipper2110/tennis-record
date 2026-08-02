package org.litvin.ui.tabs.scoring.ui

import org.junit.jupiter.api.Test
import org.litvin.OverlaySpan
import org.litvin.ScoreboardComponent
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewScoreboardRendererTest {
    @Test
    fun rendersVisibleArgbScoreboard() {
        val display = ScoreboardComponent.display(
            OverlaySpan(
                startMs = 0,
                endMs = 1_000,
                text = "",
                p1Name = "Player 1",
                p2Name = "Player 2",
                gamesP1 = 3,
                gamesP2 = 2,
                p1Pts = 2,
                p2Pts = 1,
            )
        )

        val image = PreviewScoreboardRenderer.render(display)

        assertEquals(440, image.width)
        assertEquals(166, image.height)
        assertTrue((image.getRGB(10, 10) ushr 24) > 0)
    }
}
