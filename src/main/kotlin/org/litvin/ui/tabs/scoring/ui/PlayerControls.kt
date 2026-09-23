package org.litvin.ui.tabs.scoring.ui

import com.formdev.flatlaf.FlatClientProperties
import org.litvin.ui.UiStyles
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Scoring controls for one player. [ScoringControlsPanel] puts the two parts in different grid rows:
 * - [header]: the POINTS value and the "Point for <name>" button
 * - [stats]: the GAMES and SETS cards
 */
class PlayerControls(
    private val isPrimary: Boolean,
    private val onPointClicked: () -> Unit,
) {

    private val pointsVal = JLabel("0")
    private val pointBtn = JToggleButton()
    private var accentColor: Color = if (isPrimary) Color(0x4D, 0xA3, 0xFF) else Color(0xFF, 0x6B, 0x6B)

    private val pointFill = PlayerColorFill(pointBtn)

    private val gp = SmallStatPanel("GAMES", "Game Won") { color -> UiStyles.flagIcon(color = color) }
    private val sp = SmallStatPanel("SETS", "Set Won") { color -> UiStyles.trophyIcon(color = color) }

    /** One row, left-aligned: POINTS label, points value, point button. */
    val header: JPanel = JPanel()

    /** Two cards of equal width: GAMES and SETS. */
    val stats: JPanel = JPanel(GridLayout(1, 2, 8, 0))

    init {
        val pointsLbl = JLabel("POINTS")
        pointsLbl.foreground = Color(0xAD, 0xAA, 0xAA)
        pointsLbl.font = pointsLbl.font.deriveFont(Font.BOLD, 11f)
        pointsVal.background = Color(0x26, 0x26, 0x26)
        if (isPrimary) pointsVal.name = "scoring-score-summary"
        pointsVal.foreground = if (isPrimary) Color(0xA1, 0xFE, 0x00) else Color.WHITE
        pointsVal.isOpaque = true
        pointsVal.border = EmptyBorder(6, 10, 6, 10)
        // Fixed width for the widest value ("40"), so the point button does not move when the score changes
        pointsVal.horizontalAlignment = SwingConstants.CENTER
        pointsVal.text = "40"
        pointsVal.preferredSize = pointsVal.preferredSize
        pointsVal.maximumSize = pointsVal.preferredSize
        pointsVal.text = "0"

        pointBtn.isFocusPainted = false
        pointBtn.background = Color(0x26, 0x26, 0x26)
        pointBtn.name = if (isPrimary) "scoring-player-1-point" else "scoring-player-2-point"
        // A long player name shortens the label ("...") instead of pushing the row out of its column
        pointBtn.minimumSize = Dimension(0, 0)
        pointBtn.addActionListener { onPointClicked.invoke() }
        // Border strip, selected fill, and stat icons in the player color
        applyAccentColor()

        header.isOpaque = false
        header.layout = BoxLayout(header, BoxLayout.X_AXIS)
        header.add(pointsLbl)
        header.add(Box.createHorizontalStrut(8))
        header.add(pointsVal)
        header.add(Box.createHorizontalStrut(12))
        header.add(pointBtn)
        header.add(Box.createHorizontalGlue())

        stats.isOpaque = false
        stats.add(gp)
        stats.add(sp)
    }

    fun setPlayerName(name: String) {
        pointBtn.text = "Point for $name   " + if (isPrimary) "[Q]" else "[E]"
        pointBtn.toolTipText = (if (isPrimary) "Q" else "E") + " — Point for $name"
    }

    fun setAccentColorHex(hex: String?) {
        val c = parseHexOrNull(hex) ?: return
        accentColor = c
        applyAccentColor()
    }

    private fun applyAccentColor() {
        pointBtn.border = buildPointButtonBorder()
        pointFill.accent = accentColor
        gp.setAccentColor(accentColor)
        sp.setAccentColor(accentColor)
    }

    private fun buildPointButtonBorder(): javax.swing.border.Border {
        val outer = BorderFactory.createCompoundBorder(
            // Left colored strip 6px, other sides 1px neutral border
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 6, 0, 0, accentColor),
                BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x33), 1)
            ),
            EmptyBorder(6, 10, 6, 10)
        )
        return outer
    }

    private fun parseHexOrNull(s: String?): Color? {
        if (s == null) return null
        val t = s.trim().removePrefix("#")
        if (t.length != 6) return null
        return try {
            val r = t.substring(0, 2).toInt(16)
            val g = t.substring(2, 4).toInt(16)
            val b = t.substring(4, 6).toInt(16)
            Color(r, g, b)
        } catch (_: Throwable) { null }
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

/**
 * Fills a selected toggle button with the player color: the point button (the recorded outcome)
 * and the Game Won / Set Won markers.
 *
 * FlatLaf paints a selected toggle with its own selectedBackground and ignores `background`,
 * so the fill goes in the FlatLaf style of the button. FlatLaf also ignores selectedForeground
 * when the button has its own foreground, so this class sets the text color and the icon color.
 */
private class PlayerColorFill(
    private val button: JToggleButton,
    /** Creates the button icon in the given color; null for a button without an icon. */
    private val icon: ((Color) -> Icon)? = null,
) {
    var accent: Color = UiStyles.FG_SECONDARY
        set(value) {
            field = value
            button.putClientProperty(FlatClientProperties.STYLE, "selectedBackground: ${hex(value)}")
            update()
        }

    init {
        button.addItemListener { update() }
    }

    /** Selected: black or white text and icon on the player color. Not selected: default text, icon in the player color. */
    private fun update() {
        val selected = button.isSelected
        val text = if (selected) UiStyles.contrastingTextColor(accent) else UiStyles.FG_PRIMARY
        button.foreground = text
        icon?.let { button.icon = it(if (selected) text else accent) }
    }

    private fun hex(c: Color): String = "#%02X%02X%02X".format(c.red, c.green, c.blue)
}

private class SmallStatPanel(
    title: String,
    buttonText: String,
    icon: (Color) -> Icon,
) : JPanel() {

    private val valueLabel: JLabel
    private val toggleButton: JToggleButton
    private val fill: PlayerColorFill

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
        fill = PlayerColorFill(toggleButton, icon)

        add(titleLabel)
        add(Box.createVerticalStrut(4))
        add(valueLabel)
        add(Box.createVerticalStrut(6))
        add(toggleButton)
    }

    fun setState(value: String, toggled: Boolean) {
        valueLabel.text = value
        toggleButton.isSelected = toggled
    }

    fun setAccentColor(color: Color) {
        fill.accent = color
    }

    private fun readOnlyBtn(buttonText: String): JToggleButton {
        val btn = object : JToggleButton(buttonText) {
            override fun processMouseEvent(e: java.awt.event.MouseEvent) { /* read-only */ }
            override fun processKeyEvent(e: java.awt.event.KeyEvent) { /* read-only */ }
        }

        UiStyles.styleSecondary(btn)
        btn.iconTextGap = 6
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
