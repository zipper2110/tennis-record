package org.litvin.ui.tabs.scoring.ui

import org.litvin.ui.UiStyles
import org.litvin.ui.tabs.scoring.ScoringActions
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/**
 * E-SC-001 (T3): ControlsToolbar
 *
 * A thin leaf Swing component that hosts top-level Scoring tab actions such as
 * Save and Settings. It emits callbacks strictly via `ScoringActions`.
 *
 * Notes
 * - Icons/styles: uses existing `UiStyles` helpers where applicable.
 * - No direct dependency on domain services; all side effects are delegated
 *   through `ScoringActions` provided by the container.
 */
class ControlsToolbar(
    private val centerContent: JComponent? = null,
    private val centerContentOffsetPx: Int = 0,
    private val onHelp: (() -> Unit)? = null,
) : JPanel(BorderLayout()) {

    init {
        isOpaque = true
        background = Color(0x11, 0x11, 0x11)
        border = EmptyBorder(6, 10, 6, 10)

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        left.isOpaque = false
        val center = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        center.isOpaque = false
        centerContent?.let {
            if (centerContentOffsetPx > 0) center.add(Box.createHorizontalStrut(centerContentOffsetPx))
            center.add(it)
        }
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        right.isOpaque = false

        // Settings placeholder (disabled for now, parity with current state)
        val btnSettings = JButton("Scoreboard Settings")
        UiStyles.styleSecondary(btnSettings)
        btnSettings.isEnabled = false

        btnSettings.name = "toolbar-scoreboard-settings"
        btnSettings.toolTipText = "Temporarily disabled"

        // Help button (right-aligned)
        val btnHelp = JButton("Help [F1]")
        UiStyles.styleSecondary(btnHelp)

        btnHelp.name = "toolbar-help"
        btnHelp.toolTipText = "F1 — Help"

        btnHelp.addActionListener { onHelp?.invoke() }

        right.add(btnSettings)
        right.add(btnHelp)

        add(left, BorderLayout.WEST)
        add(center, BorderLayout.CENTER)
        add(right, BorderLayout.EAST)
    }
}
