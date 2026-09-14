package org.litvin.ui.tabs.markup.ui

import org.litvin.ui.tabs.markup.AutosaveState
import org.litvin.ui.tabs.markup.CommentDto
import org.litvin.ui.tabs.markup.MarkupActions
import org.litvin.ui.tabs.markup.MarkupViewState
import org.litvin.ui.tabs.markup.PointDto
import org.litvin.ui.tabs.markup.PointPatch
import org.litvin.ui.tabs.markup.RallyEventDto
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventsCardsViewTest {
    @Test
    fun eventsAreOrderedBySourceTimeAndCommentCardShowsStableNumberAndPreview() {
        SwingUtilities.invokeAndWait {
            val view = PointsCardsView(NoOpActions)
            view.setState(
                MarkupViewState(
                    isPlaying = false,
                    currentTimeMs = 0,
                    selectedVisualIndex = null,
                    pendingDraftStartMs = null,
                    events = listOf(
                        RallyEventDto(PointDto("rally-a", 1_000, 2_000, null)),
                        CommentDto(4, 500, 2_000, "Call was in", "#FFFFFF"),
                    ),
                    autosave = AutosaveState(false, null),
                ),
            )

            assertEquals(listOf("Comment #4", "#1"), view.visibleTitles())
            assertTrue(view.visibleText().contains("Call was in"))
        }
    }

    @Test
    fun commentColorSwatchForwardsNormalizedColor() {
        SwingUtilities.invokeAndWait {
            val actions = RecordingActions()
            val view = PointsCardsView(actions) { _, _ -> "#12ab34" }
            view.setState(
                MarkupViewState(
                    isPlaying = false,
                    currentTimeMs = 0,
                    selectedVisualIndex = null,
                    pendingDraftStartMs = null,
                    events = listOf(CommentDto(4, 500, 2_000, "Call was in", "#FFFFFF")),
                    autosave = AutosaveState(false, null),
                ),
            )

            view.chooseCommentColorForTest(4)

            assertEquals(listOf(4 to "#12AB34"), actions.changedColors)
        }
    }

    private object NoOpActions : MarkupActions {
        override fun togglePlayPause() = Unit
        override fun seekTo(ms: Long) = Unit
        override fun jumpToSelected() = Unit
        override fun setStartAtPlayhead() = Unit
        override fun setEndAtPlayhead() = Unit
        override fun createPointAt(ms: Long) = Unit
        override fun editPoint(id: String, patch: PointPatch) = Unit
        override fun deletePoint(id: String) = Unit
        override fun toggleFavorite(id: String) = Unit
        override fun selectByVisualIndex(index: Int) = Unit
        override fun saveNow() = Unit
    }

    private class RecordingActions : MarkupActions by NoOpActions {
        val changedColors = mutableListOf<Pair<Int, String>>()

        override fun updateCommentColor(id: Int, colorHex: String) {
            changedColors += id to colorHex
        }
    }
}
