package org.litvin.ui.tabs.scoring.ui

import org.litvin.ui.UiStyles
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * PlayerPanel — reusable scoring side panel (left/right)
 */
class PlayerPanel(
    private val isPrimary: Boolean,
    private val onPointClicked: () -> Unit,
) : JPanel() {

    private val pointsVal = JLabel("0")
    private var pointBtn: JToggleButton

    private val gp: SmallStatPanel
    private val sp: SmallStatPanel

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        preferredSize = Dimension(360, 0)
        isOpaque = false

        // POINTS value row
        val pointsRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        pointsRow.isOpaque = false
        val pointsLbl = JLabel("POINTS")
        pointsLbl.foreground = Color(0xAD, 0xAA, 0xAA)
        pointsLbl.font = pointsLbl.font.deriveFont(Font.BOLD, 11f)
        pointsVal.background = Color(0x26, 0x26, 0x26)
        pointsVal.foreground = if (isPrimary) Color(0xA1, 0xFE, 0x00) else Color.WHITE
        pointsVal.isOpaque = true
        pointsVal.border = EmptyBorder(6, 10, 6, 10)
        pointsRow.add(pointsLbl)
        pointsRow.add(pointsVal)

        // Point button
        pointBtn = JToggleButton()
        pointBtn.isFocusPainted = false
        pointBtn.foreground = if (isPrimary) Color(0x42, 0xA5, 0xF5) else Color(0xEF, 0x53, 0x50)
        pointBtn.background = Color(0x26, 0x26, 0x26)
        pointBtn.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x33), 1),
            EmptyBorder(6, 10, 6, 10)
        )
        pointBtn.name = if (isPrimary) "p1-point" else "p2-point"
        pointBtn.addActionListener { onPointClicked.invoke() }

        val topRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        topRow.isOpaque = false
        topRow.add(pointsRow)
        topRow.add(pointBtn)

        // GAMES & SETS cards
        val grids = JPanel(GridLayout(1, 2, 8, 0))
        grids.isOpaque = false
        grids.border = EmptyBorder(8, 0, 0, 0)


        gp = SmallStatPanel("GAMES", "Game Won")
        sp = SmallStatPanel("SETS", "Set Won")
        grids.add(gp)
        grids.add(sp)

        add(topRow)
        add(grids)
    }

    fun setPlayerName(name: String) {
        pointBtn.text = "Point for $name   " + if (isPrimary) "[Q]" else "[E]"
        pointBtn.toolTipText = (if (isPrimary) "Q" else "E") + " — Point for $name"
    }

    fun render(pointsDisplay: String, games: Int, sets: Int, gameWon: Boolean, setWon: Boolean) {
        pointsVal.text = pointsDisplay

        gp.setState(games.toString(), gameWon)
        sp.setState(sets.toString(), setWon)
    }

    fun setPointButtonEnabled(enabled: Boolean) {
        pointBtn.isEnabled = enabled
    }

    fun setPointSelected(selected: Boolean) {
        pointBtn.model.isSelected = selected
    }

}

private class SmallStatPanel(
    title: String,
    buttonText: String,
) : JPanel() {

    private val valueLabel: JLabel
    private val toggleButton: JToggleButton

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = Color(0x10, 0x10, 0x10)
        border = EmptyBorder(8, 8, 8, 8)

        val titleLabel = JLabel(title)
        titleLabel.foreground = Color(0xAD, 0xAA, 0xAA)
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 10f)
        titleLabel.alignmentX = 0.5f

        valueLabel = JLabel("0")
        valueLabel.foreground = Color.WHITE
        valueLabel.font = valueLabel.font.deriveFont(Font.BOLD, 24f)
        valueLabel.alignmentX = 0.5f

        toggleButton = readOnlyBtn(buttonText)

        add(titleLabel)
        add(Box.createVerticalStrut(4))
        add(valueLabel)
        add(Box.createVerticalStrut(6))
        add(toggleButton)
    }

    fun setState(value: String, toggled: Boolean) {
        valueLabel.text = value
        toggleButton.isSelected = toggled
//        toggleButton.model.isSelected = toggled
    }

    private fun readOnlyBtn(buttonText: String): JToggleButton {
        val btn = object : JToggleButton(buttonText) {
            override fun processMouseEvent(e: java.awt.event.MouseEvent) { /* read-only */ }
            override fun processKeyEvent(e: java.awt.event.KeyEvent) { /* read-only */ }
        }

        UiStyles.styleSecondary(btn)
        btn.isFocusable = false
        btn.isRequestFocusEnabled = false
        btn.isRolloverEnabled = false
        btn.cursor = Cursor.getDefaultCursor()
        btn.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x33), 1),
            EmptyBorder(6, 8, 6, 8)
        )
        btn.toolTipText = "Computed automatically"

        btn.alignmentX = 0.5f
        return btn
    }
}