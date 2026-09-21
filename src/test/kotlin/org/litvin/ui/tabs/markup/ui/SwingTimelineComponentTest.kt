package org.litvin.ui.tabs.markup.ui

import org.litvin.markup.CommentV1
import org.litvin.markup.PointV1
import java.awt.Cursor
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwingTimelineComponentTest {
    @Test
    fun clickingRightAttachedCommentBoxSelectsCommentAndSeeksToItsStart() {
        SwingUtilities.invokeAndWait {
            val selected = mutableListOf<Int>()
            val seeks = mutableListOf<Long>()
            val timeline = timelineWithComments(
                comments = listOf(CommentV1(12, 5_000, 1_000, "In", "#FFFFFF")),
                onCommentSelected = { selected += it },
                onSeek = { seeks += it },
            )
            timeline.setSize(200, timeline.preferredSize.height)
            val box = timeline.commentMarkerLayoutsForTest().single().boxBounds

            timeline.dispatchEvent(
                MouseEvent(timeline, MouseEvent.MOUSE_PRESSED, 0, 0, box.centerX.toInt(), box.centerY.toInt(), 1, false),
            )

            assertEquals(listOf(12), selected)
            assertEquals(listOf(5_000L), seeks)
        }
    }

    @Test
    fun collidingCommentLabelsUseLanesAndMarkerBarsCoverAllTracks() {
        SwingUtilities.invokeAndWait {
            val timeline = timelineWithComments(
                comments = listOf(
                    CommentV1(1, 5_000, 1_000, "In", "#FFFFFF"),
                    CommentV1(2, 5_100, 1_000, "Out", "#FFFFFF"),
                ),
            )
            timeline.setSize(200, timeline.preferredSize.height)
            val layouts = timeline.commentMarkerLayoutsForTest()

            assertEquals(2, timeline.commentLaneCountForTest())
            assertEquals(2, layouts.size)
            assertFalse(layouts[0].boxBounds.intersects(layouts[1].boxBounds))
            assertTrue(layouts.all { it.boxBounds.x == it.lineX + 3 })
            assertTrue(layouts.all { it.lineTop == timeline.videoTrackTopForTest() })
            assertTrue(layouts.all { it.lineBottom == timeline.commentTrackBottomForTest() })
        }
    }

    @Test
    fun cursorSignalsHandOverCommentsAndMarksAndCrosshairOverTheScrubbableRest() {
        SwingUtilities.invokeAndWait {
            val timeline = timelineWithComments(
                comments = listOf(CommentV1(12, 5_000, 1_000, "In", "#FFFFFF")),
                points = listOf(PointV1("p1", 1_000, 2_000)),
            )
            timeline.setSize(200, timeline.preferredSize.height)
            val box = timeline.commentMarkerLayoutsForTest().single().boxBounds

            assertEquals(
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                timeline.cursorAtForTest(box.centerX.toInt(), box.centerY.toInt()),
            )
            assertEquals(
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                timeline.cursorAtForTest(30, timeline.marksTrackCenterYForTest()),
            )
            assertEquals(
                Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR),
                timeline.cursorAtForTest(150, timeline.marksTrackCenterYForTest()),
            )
            assertEquals(
                Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR),
                timeline.cursorAtForTest(150, timeline.videoTrackTopForTest() + 2),
            )
        }
    }

    private fun timelineWithComments(
        comments: List<CommentV1>,
        points: List<PointV1> = emptyList(),
        onCommentSelected: (Int) -> Unit = {},
        onSeek: (Long) -> Unit = {},
    ) = SwingTimelineComponent(
        timeProvider = { 0L },
        durationProvider = { 10_000L },
        pointsProvider = { points },
        onSeekRequested = onSeek,
        commentsProvider = { comments },
        onCommentSelected = onCommentSelected,
    )
}
