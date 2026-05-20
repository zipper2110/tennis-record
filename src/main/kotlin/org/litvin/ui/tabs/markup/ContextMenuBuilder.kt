package org.litvin.ui.tabs.markup

import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/**
 * Builds context/pop-up menus for Markup tab views (table, cards, etc.).
 *
 * Leaf menu builders depend only on Markup contracts and UI widgets, not on domain services.
 */
class ContextMenuBuilder {

    /**
     * Table context menu with actions related to the currently selected row.
     *
     * @param actions callbacks into the container
     * @param canActOnSelection whether menu items should be enabled based on current selection rules
     * @param onDeleteSelected callback to perform delete for the currently selected item
     */
    fun buildTableMenu(
        actions: MarkupActions,
        canActOnSelection: () -> Boolean,
        onDeleteSelected: () -> Unit,
    ): JPopupMenu {
        val menu = JPopupMenu()
        val jump = JMenuItem("Go to start").apply {
            addActionListener { actions.jumpToSelected() }
        }
        val del = JMenuItem("Delete").apply {
            addActionListener { onDeleteSelected() }
        }
        // Initial enable state reflecting current selection rules
        val enabled = safeEval(canActOnSelection)
        jump.isEnabled = enabled
        del.isEnabled = enabled

        // Re-evaluate enablement each time the menu becomes visible
        menu.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
                val en = safeEval(canActOnSelection)
                jump.isEnabled = en
                del.isEnabled = en
            }
            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {}
            override fun popupMenuCanceled(e: PopupMenuEvent?) {}
        })

        menu.add(jump)
        menu.add(del)
        return menu
    }

    private inline fun safeEval(block: () -> Boolean): Boolean = try { block() } catch (_: Throwable) { false }
}