package org.litvin.ui.tabs.scoring.ui

import org.litvin.ui.tabs.scoring.ScoringViewState
import org.litvin.ui.tabs.scoring.Side
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder

/**
 * MatchHeaderPanel (E-SC-001 T2)
 *
 * Purpose
 * - Leaf Swing component that renders match header info for the Scoring tab:
 *   player names, completed sets, current set games, and serve indicator.
 *
 * Contracts
 * - Consumes immutable snapshots via [render].
 * - No direct references to domain/services; depends only on scoring contracts DTOs.
 *
 * Notes
 * - Layout is intentionally simple to ease further iterations.
 * - Long player names are truncated with an ellipsis, and the full value is available via tooltip.
 */
class MatchHeaderPanel : JPanel() {
    private val p1ServeDot = ServeDot()
    private val p2ServeDot = ServeDot()
    private val p1Name = JLabel("Player 1")
    private val p2Name = JLabel("Player 2")
    private val setsLabel = JLabel("")
    private val gamesLabel = JLabel("")

    init {
        layout = BorderLayout()
        isOpaque = true
        background = Color(0x12, 0x12, 0x12)
        border = EmptyBorder(6, 12, 6, 12)

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            add(p1ServeDot)
            add(p1Name)
        }
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
            add(p2Name)
            add(p2ServeDot)
        }
        val center = JPanel()
        center.layout = BoxLayout(center, BoxLayout.Y_AXIS)
        center.isOpaque = false
        setsLabel.horizontalAlignment = SwingConstants.CENTER
        gamesLabel.horizontalAlignment = SwingConstants.CENTER
        setsLabel.foreground = Color(0xDD, 0xDD, 0xDD)
        gamesLabel.foreground = Color(0xAA, 0xAA, 0xAA)
        center.add(setsLabel)
        center.add(Box.createVerticalStrut(2))
        center.add(gamesLabel)

        add(left, BorderLayout.WEST)
        add(center, BorderLayout.CENTER)
        add(right, BorderLayout.EAST)

        // Basic font tweaks
        p1Name.font = p1Name.font.deriveFont(Font.BOLD)
        p2Name.font = p2Name.font.deriveFont(Font.BOLD)
        setsLabel.font = setsLabel.font.deriveFont(Font.BOLD, (setsLabel.font.size + 1).toFloat())
        gamesLabel.font = gamesLabel.font.deriveFont((gamesLabel.font.size).toFloat())

        // Initial state hidden serve dots
        p1ServeDot.isServing = false
        p2ServeDot.isServing = false
    }

    /** Update header from a full [org.litvin.ui.tabs.scoring.ScoringViewState] snapshot. */
    fun render(state: ScoringViewState) {
        setPlayerName(p1Name, state.player1Name)
        setPlayerName(p2Name, state.player2Name)

        // Serve indicator
        p1ServeDot.isServing = state.serving == Side.P1
        p2ServeDot.isServing = state.serving == Side.P2
        p1ServeDot.repaint()
        p2ServeDot.repaint()

        // Completed sets string
        val setsStr = if (state.sets.isEmpty())
            "—"
        else
            state.sets.joinToString("  ") { s ->
                val base = "${'$'}{s.p1Games}-${'$'}{s.p2Games}"
                if (s.tiebreak) "${'$'}base·TB" else base
            }
        setsLabel.text = setsStr
        setsLabel.toolTipText = setsStr

        // Current games in-progress (current set)
        val gamesStr = "Games  ${'$'}{state.gamesP1} – ${'$'}{state.gamesP2}"
        gamesLabel.text = gamesStr
        gamesLabel.toolTipText = gamesStr
    }

    private fun setPlayerName(label: JLabel, full: String) {
        label.toolTipText = full
        label.text = ellipsize(full, 26)
    }

    private fun ellipsize(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        if (maxChars <= 1) return "…"
        return text.substring(0, maxChars - 1) + "…"
    }

    /** Small circular dot used to indicate serving side. */
    private class ServeDot : JComponent() {
        var isServing: Boolean = false
        override fun getPreferredSize(): Dimension = Dimension(10, 10)
        override fun getMinimumSize(): Dimension = preferredSize
        override fun getMaximumSize(): Dimension = preferredSize
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val r = height.coerceAtMost(width) - 1
            val x = (width - r) / 2
            val y = (height - r) / 2
            val fill = if (isServing) Color(0x3C, 0xD1, 0x6F) else Color(0x55, 0x55, 0x55)
            g2.color = fill
            g2.fillOval(x, y, r, r)
        }
    }
}