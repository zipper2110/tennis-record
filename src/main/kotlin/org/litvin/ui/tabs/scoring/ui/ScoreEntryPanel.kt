package org.litvin.ui.tabs.scoring.ui

import org.litvin.ui.UiStyles
import org.litvin.ui.tabs.scoring.ScoringActions
import org.litvin.ui.tabs.scoring.ScoringViewState
import org.litvin.ui.tabs.scoring.Side
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.border.EmptyBorder

/**
 * ScoreEntryPanel (E-SC-001 T4)
 *
 * Purpose
 * - Leaf Swing component hosting the primary scoring inputs for the Scoring tab:
 *   point won (left/right) and common edit controls (undo/redo). Optionally exposes
 *   buttons to finalize a game or a set for either side.
 *
 * Contracts
 * - Emits callbacks strictly via [org.litvin.ui.tabs.scoring.ScoringActions].
 * - Consumes immutable snapshots via [render] when provided by the container.
 * - No references to domain/services beyond the scoring contracts.
 *
 * Hotkeys
 * - Q — Point for Player 1 (bound by the container)
 * - E — Point for Player 2 (bound by the container)
 * - Other hotkeys (e.g., Next Point, transport) remain owned by the container.
 *
 * Notes
 * - Layout is intentionally minimalist to keep iteration easy.
 */
class ScoreEntryPanel(
    private val actions: ScoringActions,
) : JPanel(BorderLayout()) {

    // Optional finalize cluster (disabled styling by default; container may enable when applicable)
    private val finalizeGameP1 = JButton("Game P1")
    private val finalizeGameP2 = JButton("Game P2")
    private val finalizeSetP1 = JButton("Set P1")
    private val finalizeSetP2 = JButton("Set P2")

    init {
        isOpaque = true
        background = Color(0x12, 0x12, 0x12)
        border = EmptyBorder(6, 10, 6, 10)

        // Optional finalize controls (compact, right-aligned)
        val finalize = JPanel(FlowLayout(FlowLayout.CENTER, 6, 0))
        finalize.isOpaque = false
        listOf(finalizeGameP1, finalizeGameP2, finalizeSetP1, finalizeSetP2).forEach { UiStyles.styleSecondary(it) }
        try {
            finalizeGameP1.name = "finalize-game-p1"; finalizeGameP2.name = "finalize-game-p2"
            finalizeSetP1.name = "finalize-set-p1"; finalizeSetP2.name = "finalize-set-p2"
        } catch (_: Throwable) {}
        finalizeGameP1.addActionListener { actions.finalizeGame(Side.P1) }
        finalizeGameP2.addActionListener { actions.finalizeGame(Side.P2) }
        finalizeSetP1.addActionListener { actions.finalizeSet(Side.P1) }
        finalizeSetP2.addActionListener { actions.finalizeSet(Side.P2) }

        // Keep finalize cluster visually secondary
        val south = JPanel(BorderLayout())
        south.isOpaque = false
        south.add(finalize, BorderLayout.SOUTH)

        add(south, BorderLayout.CENTER)
    }
}