package org.litvin.ui.tabs.scoring.ui

import org.litvin.ui.UiStyles
import org.litvin.ui.tabs.scoring.ScoringActions
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.FlowLayout
import javax.swing.JButton
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
    private val actions: ScoringActions,
) : JPanel(BorderLayout()) {

    init {
        isOpaque = true
        background = Color(0x11, 0x11, 0x11)
        border = EmptyBorder(6, 10, 6, 10)

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        left.isOpaque = false
        val center = JPanel(FlowLayout(FlowLayout.CENTER, 8, 0))
        center.isOpaque = false
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        right.isOpaque = false

        // Save button (top-level persistence)
        val btnSave = JButton("Save")
        UiStyles.styleSecondary(btnSave)
        try {
            btnSave.name = "save-score"
            btnSave.toolTipText = "Save scoring data now"
            btnSave.accessibleContext.accessibleName = "Save"
        } catch (_: Throwable) {}
        btnSave.addActionListener { actions.saveScore() }

        // Next Point shortcut (mirrors left footer but exposed on toolbar as a top action)
        val btnNext = JButton("Next Point  [R]")
        UiStyles.styleSecondary(btnNext)
        try {
            btnNext.name = "toolbar-next-point"
            btnNext.toolTipText = "R — Next Point"
            btnNext.accessibleContext.accessibleName = "Next Point"
        } catch (_: Throwable) {}
        btnNext.addActionListener { actions.advanceToNextPoint() }

        // Settings placeholder (disabled for now, parity with current state)
        val btnSettings = JButton("Scoreboard Settings")
        UiStyles.styleSecondary(btnSettings)
        btnSettings.isEnabled = false
        try {
            btnSettings.name = "toolbar-scoreboard-settings"
            btnSettings.toolTipText = "Temporarily disabled"
            btnSettings.accessibleContext.accessibleName = "Scoreboard Settings"
        } catch (_: Throwable) {}

        left.add(btnSave)
        center.add(btnNext)
        right.add(btnSettings)

        add(left, BorderLayout.WEST)
        add(center, BorderLayout.CENTER)
        add(right, BorderLayout.EAST)
    }
}