package org.litvin.export.scoreboard

import org.litvin.scoring.ScoreboardPosition
import org.litvin.scoring.ScoreboardSettingsV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreboardAssTest {
    private val scene = ScoreboardScene(
        width = 400.0,
        height = 100.0,
        items = listOf(
            SceneItem.Box(0.0, 0.0, 400.0, 100.0, 0x102030, 0.5, Corners.all(10.0)),
            SceneItem.Label(20.0, 50.0, "A{B}", "Segoe UI", 30.0, 0xFFFFFF),
        ),
    )

    @Test
    fun placesTheBoardInEachCornerWithTheFrameMargin() {
        fun place(position: ScoreboardPosition) =
            ScoreboardAss.place(scene, ScoreboardSettingsV1(position = position), 0.0, 0.0, 1920.0, 1080.0)

        assertEquals(BoardPlacement(48.0, 48.0, 1.0), place(ScoreboardPosition.TOP_LEFT))
        assertEquals(BoardPlacement(1920.0 - 48.0 - 400.0, 48.0, 1.0), place(ScoreboardPosition.TOP_RIGHT))
        assertEquals(BoardPlacement(48.0, 1080.0 - 48.0 - 100.0, 1.0), place(ScoreboardPosition.BOTTOM_LEFT))
        assertEquals(BoardPlacement(1920.0 - 448.0, 1080.0 - 148.0, 1.0), place(ScoreboardPosition.BOTTOM_RIGHT))
    }

    @Test
    fun scalesWithTheFrameHeightAndTheSizeSetting() {
        // The preview video area can start anywhere in the window.
        val placement = ScoreboardAss.place(scene, ScoreboardSettingsV1(sizePercent = 150), 100.0, 20.0, 1280.0, 720.0)

        assertEquals(100.0 + 32.0, placement.x, 1e-9)
        assertEquals(20.0 + 32.0, placement.y, 1e-9)
        assertEquals(720.0 / 1080.0 * 1.5, placement.scale, 1e-9)
    }

    @Test
    fun shrinksTheBoardWhenTheFrameIsTooNarrow() {
        val placement = ScoreboardAss.place(scene, ScoreboardSettingsV1(sizePercent = 200), 0.0, 0.0, 360.0, 1080.0)

        assertEquals((360.0 - 96.0) / 400.0, placement.scale, 1e-9)
    }

    @Test
    fun writesOneSelfContainedEventPerItem() {
        val events = ScoreboardAss.events(scene, BoardPlacement(10.0, 20.0, 0.5))

        assertEquals(2, events.size)
        val box = events[0]
        assertTrue(box.startsWith("{\\an7\\pos(10,20)"), box)
        assertTrue(box.contains("\\1c&H302010&"), box)
        assertTrue(box.contains("\\1a&H7F&"), box)
        assertTrue(box.contains("\\p1}m 5 0 l 195 0 b"), box)
        assertTrue(box.endsWith("{\\p0}"), box)

        val label = events[1]
        assertTrue(label.startsWith("{\\an4\\pos(20,45)"), label)
        assertTrue(label.contains("\\fnSegoe UI\\fs15\\b1"), label)
        assertTrue(label.contains("\\bord0"), label)
        assertTrue(label.endsWith("}A\\{B\\}"), label)
    }

    @Test
    fun aPolygonStartsAtTheTopLeftCornerOfItsBounds() {
        val polygon = SceneItem.Polygon(listOf(ScenePoint(10.0, 50.0), ScenePoint(30.0, 40.0), ScenePoint(30.0, 50.0)), 0xFF0000, 0.5)

        val event = ScoreboardAss.events(ScoreboardScene(100.0, 100.0, listOf(polygon)), BoardPlacement(100.0, 200.0, 2.0)).single()

        assertEquals("{\\an7\\pos(120,280)\\bord0\\shad0\\blur0\\fscx100\\fscy100\\frz0\\1c&H0000FF&\\1a&H7F&\\p1}m 0 20 l 40 0 40 20{\\p0}", event)
    }

    @Test
    fun aPolylineGoesBackOnItselfAndDrawsItsOutline() {
        val line = SceneItem.Polyline(listOf(ScenePoint(0.0, 10.0), ScenePoint(10.0, 0.0), ScenePoint(20.0, 10.0)), 4.0, 0xFFFFFF)

        val event = ScoreboardAss.events(ScoreboardScene(100.0, 100.0, listOf(line)), BoardPlacement(0.0, 0.0, 1.0)).single()

        // No fill, and an outline of half the width on each side.
        assertTrue("\\bord2\\" in event, event)
        assertTrue("\\1a&HFF&" in event, event)
        assertTrue(event.endsWith("\\p1}m 0 10 l 10 0 20 10 10 0 0 10{\\p0}"), event)
    }

    @Test
    fun roundedRectPathStartsAtTheTopEdgeAndSkipsZeroCorners() {
        assertEquals(
            "m 0 0 l 10 0 l 10 5 l 0 5 l 0 0",
            ScoreboardAss.roundedRectPath(10.0, 5.0, Corners.NONE),
        )
        val rounded = ScoreboardAss.roundedRectPath(10.0, 5.0, Corners(topRight = 2.0))
        assertTrue(rounded.startsWith("m 0 0 l 8 0 b "), rounded)
        assertEquals(1, Regex(" b ").findAll(rounded).count())
    }

    @Test
    fun skipsInvisibleItems() {
        val invisible = ScoreboardScene(
            10.0,
            10.0,
            listOf(
                SceneItem.Box(0.0, 0.0, 0.0, 10.0, 0, 1.0),
                SceneItem.Box(0.0, 0.0, 10.0, 10.0, 0, 0.0),
                SceneItem.Label(0.0, 0.0, "", "Arial", 10.0, 0),
            ),
        )

        assertEquals(emptyList(), ScoreboardAss.events(invisible, BoardPlacement(0.0, 0.0, 1.0)))
    }
}
