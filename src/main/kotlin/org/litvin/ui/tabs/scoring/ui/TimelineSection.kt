package org.litvin.ui.tabs.scoring.ui

import org.litvin.markup.PointV1
import org.litvin.scoring.Outcome
import org.litvin.ui.tabs.scoring.NavigationActions
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * E-SC-001 (T5): TimelineSection
 *
 * A thin wrapper around the existing [PointsListPanel] that integrates it into the
 * componentized Scoring UI. It does not depend on domain services; all behavior
 * is delegated via [org.litvin.ui.tabs.scoring.ScoringActions].
 *
 * Notes
 * - This section purposefully keeps a minimal surface and adapts the legacy
 *   PointsListPanel without changing its internals.
 * - Selection changes initiated by the user are forwarded to [org.litvin.ui.tabs.scoring.ScoringActions.navigateToPoint].
 */
class TimelineSection(
    private val actions: NavigationActions,
) : JPanel(BorderLayout()) {

    private val list = PointsListPanel()

    init {
        isOpaque = false
        add(list, BorderLayout.CENTER)
        list.onSelect = { idx, _ ->
            actions.navigateToPoint(idx)
        }
        list.onToggleFavorite = { idx ->
            actions.toggleFavorite(idx)
        }
    }

    /** Provide list data to render (legacy DTOs used by the adapted list). */
    fun setList(
        points: List<PointV1>,
        outcomesByPointId: Map<String, Outcome>,
        p1ColorHex: String,
        p2ColorHex: String,
    ) {
        list.setData(points, outcomesByPointId, p1ColorHex, p2ColorHex)
    }

    /** Programmatically update selection (userInitiated controls behavior like scroll-focus). */
    fun setSelectedIndex(index: Int, userInitiated: Boolean) {
        list.setSelectedIndex(index, userInitiated)
    }

    fun scrollIntoView(index: Int) {
        list.scrollIntoView(index)
    }
}
