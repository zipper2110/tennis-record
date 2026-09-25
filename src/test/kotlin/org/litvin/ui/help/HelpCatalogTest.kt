package org.litvin.ui.help

import org.litvin.ui.commons.AppShortcuts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HelpCatalogTest {
    @Test
    fun pagesFollowApplicationWorkflowOrder() {
        assertEquals(
            listOf(
                HelpPage.OVERVIEW,
                HelpPage.PROJECTS,
                HelpPage.COLORS,
                HelpPage.CROP,
                HelpPage.POINTS,
                HelpPage.SCORING,
                HelpPage.STATISTICS,
                HelpPage.EXPORT,
            ),
            HelpCatalog.pages.map { it.page },
        )
    }

    @Test
    fun everyPageHasWorkflowActionsGoodToKnowAndContextHelpShortcut() {
        HelpCatalog.pages.forEach { page ->
            assertTrue(page.summary.isNotBlank(), "${page.page} needs a summary")
            assertTrue(page.workflow.isNotEmpty(), "${page.page} needs workflow steps")
            assertTrue(page.actions.isNotEmpty(), "${page.page} needs actions")
            assertTrue(page.goodToKnow.isNotEmpty(), "${page.page} needs good-to-know items")
            assertTrue(
                page.shortcuts.any { it.shortcut == AppShortcuts.HELP },
                "${page.page} needs the shared F1 shortcut",
            )
        }
    }

    @Test
    fun editingPagesUseSharedShortcutDefinitions() {
        val points = HelpCatalog.content(HelpPage.POINTS).shortcuts.map { it.shortcut }
        val scoring = HelpCatalog.content(HelpPage.SCORING).shortcuts.map { it.shortcut }
        val crop = HelpCatalog.content(HelpPage.CROP).shortcuts.map { it.shortcut }

        assertTrue(AppShortcuts.POINT_START in points)
        assertTrue(AppShortcuts.SHIFT_LEFT in points)
        assertTrue(AppShortcuts.SCORE_PLAYER_1 in scoring)
        assertTrue(AppShortcuts.TOGGLE_FRAME_STEP in scoring)
        assertTrue(AppShortcuts.SHIFT_UP in crop)
    }
}
