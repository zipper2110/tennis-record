package org.litvin.ui.tabs.points.ui

import org.litvin.ui.tabs.points.AutosaveState
import org.litvin.ui.tabs.points.CommentDto
import org.litvin.ui.tabs.points.PointsActions
import org.litvin.ui.tabs.points.PointsViewState
import org.litvin.ui.tabs.points.PointDto
import org.litvin.ui.tabs.points.PointPatch
import org.litvin.ui.tabs.points.PointEventDto
import java.awt.Component
import java.awt.Container
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventsCardsViewTest {
    @Test
    fun eventsAreOrderedBySourceTimeAndCommentCardShowsStableNumberAndPreview() {
        SwingUtilities.invokeAndWait {
            val view = PointsCardsView(NoOpActions)
            view.setState(
                PointsViewState(
                    isPlaying = false,
                    currentTimeMs = 0,
                    selectedVisualIndex = null,
                    pendingDraftStartMs = null,
                    events = listOf(
                        PointEventDto(PointDto("pt-a", 1_000, 2_000, null)),
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
    fun favoriteStarIsVisibleWithoutHoveringTheCard() {
        SwingUtilities.invokeAndWait {
            val view = PointsCardsView(NoOpActions)
            view.setState(
                PointsViewState(
                    isPlaying = false,
                    currentTimeMs = 0,
                    selectedVisualIndex = null,
                    pendingDraftStartMs = null,
                    events = listOf(
                        PointEventDto(PointDto("pt-fav", 1_000, 2_000, null, favorite = true)),
                        PointEventDto(PointDto("pt-plain", 3_000, 4_000, null)),
                    ),
                    autosave = AutosaveState(false, null),
                ),
            )

            assertTrue(findByName(view, "favorite-point-pt-fav")!!.isVisible, "favorite star should show at rest")
            assertFalse(findByName(view, "favorite-point-pt-plain")!!.isVisible, "plain point shows its star on hover only")
        }
    }

    private fun findByName(root: Component, name: String): Component? {
        if (root.name == name) return root
        if (root !is Container) return null
        return root.components.firstNotNullOfOrNull { findByName(it, name) }
    }

    private object NoOpActions : PointsActions {
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
}
