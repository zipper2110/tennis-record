package org.litvin.ui.tabs.markup.components

import org.litvin.ui.commons.AppShortcuts
import java.awt.event.ActionEvent
import javax.swing.*

/**
 * Installs and uninstalls keyboard shortcuts for the Markup tab.
 *
 * Responsibilities:
 * - Bind Space/C/V/Delete and Left/Right (with Shift for big step) to provided actions
 * - Respect text-editing focus to avoid hijacking typing
 * - Cleanly uninstall all registered key strokes and actions
 */
class Keybindings(
    private val target: JComponent,
    private val isTextEditingFocus: () -> Boolean,
    private val actions: MarkupKeyActions
) {
    private val registered = mutableListOf<Pair<KeyStroke, String>>()
    private val conditions = intArrayOf(
        JComponent.WHEN_IN_FOCUSED_WINDOW,
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
    )

    init { install() }

    private fun bind(key: String, actionName: String, runnable: () -> Unit) {
        val ks = KeyStroke.getKeyStroke(key)
        // Register into both input map conditions for reliability (e.g., JTable focus)
        conditions.forEach { cond ->
            val im = target.getInputMap(cond)
            im.put(ks, actionName)
        }
        target.actionMap.put(actionName, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                if (isTextEditingFocus()) return
                runnable()
            }
        })
        registered += ks to actionName
    }

    private fun install() {
        // Primary controls
        bind(AppShortcuts.PLAY_PAUSE.keyStroke, "markup.toggle") { actions.toggle() }
        bind(AppShortcuts.POINT_START.keyStroke, "markup.pointStart") { actions.startAtPlayhead() }
        bind(AppShortcuts.POINT_END.keyStroke, "markup.pointEnd") { actions.endAtPlayhead() }
        bind(AppShortcuts.TOGGLE_FAVORITE.keyStroke, "markup.toggleFavorite") { actions.toggleFavoriteSelected() }
        bind(AppShortcuts.DELETE.keyStroke, "markup.delete") { actions.deleteSelected() }
        // Seeks (nudge)
        bind(AppShortcuts.LEFT.keyStroke, "markup.seekLeft") { actions.nudge(-1_000) }
        bind(AppShortcuts.RIGHT.keyStroke, "markup.seekRight") { actions.nudge(+1_000) }
        bind(AppShortcuts.SHIFT_LEFT.keyStroke, "markup.seekLeftBig") { actions.nudge(-5_000) }
        bind(AppShortcuts.SHIFT_RIGHT.keyStroke, "markup.seekRightBig") { actions.nudge(+5_000) }
    }

    fun uninstall() {
        try {
            val am = target.actionMap
            registered.forEach { (ks, name) ->
                conditions.forEach { cond ->
                    val im = target.getInputMap(cond)
                    try { im.remove(ks) } catch (_: Throwable) { }
                }
                try { am.remove(name) } catch (_: Throwable) { }
            }
        } finally {
            registered.clear()
        }
    }
}

interface MarkupKeyActions {
    fun toggle()
    fun startAtPlayhead()
    fun endAtPlayhead()
    fun toggleFavoriteSelected()
    fun deleteSelected()
    fun nudge(deltaMs: Long)
}
