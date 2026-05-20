package org.litvin.ui.tabs.markup.ui

import org.litvin.ui.UiStyles
import org.litvin.ui.tabs.markup.MarkupActions
import org.litvin.ui.tabs.markup.MarkupViewState
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/**
 * MarkupToolbar — top-level actions for the Markup tab.
 *
 * Responsibilities:
 * - Host a primary Save action (delegates to MarkupActions.saveNow())
 * - Surface autosave indicator ("Autosave…" while pending; "Saved" otherwise)
 * - No direct domain dependencies; thin leaf Swing component
 */
class MarkupToolbar(
    private val actions: MarkupActions,
) : JPanel(BorderLayout()) {

    private val autosaveLabel = JLabel("Saved").apply {
        foreground = UiStyles.FG_SECONDARY
    }

    init {
        isOpaque = true
        background = Color(0x11, 0x11, 0x11)
        border = EmptyBorder(6, 10, 6, 10)

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply { isOpaque = false }

        val btnSave = JButton("Save Now")
        UiStyles.styleSecondary(btnSave)
        try {
            btnSave.name = "markup-save-now"
            btnSave.toolTipText = "Save markup now"
            btnSave.accessibleContext.accessibleName = "Save Now"
        } catch (_: Throwable) { }
        btnSave.addActionListener { actions.saveNow() }

        left.add(btnSave)
        right.add(autosaveLabel)

        add(left, BorderLayout.WEST)
        add(right, BorderLayout.EAST)
    }

    fun setState(state: MarkupViewState) {
        val pending = state.autosave.pending
        autosaveLabel.text = if (pending) "Autosave…" else "Saved"
        autosaveLabel.foreground = if (pending) UiStyles.LIME else UiStyles.FG_SECONDARY
    }
}