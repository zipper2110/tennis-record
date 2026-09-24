package org.litvin.ui.tabs.scoring.ui

import com.formdev.flatlaf.FlatClientProperties
import org.litvin.ui.UiStyles
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/** The racket color of the serve button: a little less bright than [UiStyles.BALL]. */
private val SERVE_BUTTON_COLOR = Color(0xC2, 0xDB, 0x43)

/** How the serve button of a player shows the server of the selected point. */
enum class ServeState {
    /** The player does not serve, or the server is not known. */
    NOT_SERVING,

    /** The player serves. The app computed it from the marks on other points. */
    SERVING,

    /** The player serves. The user marked it on this point. */
    SERVING_MARKED,
}

/**
 * Scoring controls for one player. [ScoringControlsPanel] puts the two parts in different grid rows:
 * - [header]: the POINTS value, the "Point for <name>" button and the serve button
 * - [stats]: the GAMES and SETS cards
 *
 * The Game Won and Set Won markers show the computed score. In manual scoring they are buttons:
 * a click marks (or clears) the game or set win of this player on the current point.
 *
 * The serve button shows if this player serves the selected point. A click marks this player as the server.
 */
class PlayerControls(
    private val isPrimary: Boolean,
    private val onPointClicked: () -> Unit,
    onGameWonClicked: () -> Unit = {},
    onSetWonClicked: () -> Unit = {},
    onServeClicked: () -> Unit = {},
) {

    private val pointsVal = JLabel("0")
    private val pointBtn = JToggleButton()
    private var accentColor: Color = if (isPrimary) Color(0x4D, 0xA3, 0xFF) else Color(0xFF, 0x6B, 0x6B)

    private val pointFill = PlayerColorFill(pointBtn)

    private val serveBtn = JToggleButton()
    private var playerName: String = if (isPrimary) "Player 1" else "Player 2"
    private var serveState = ServeState.NOT_SERVING

    private val playerPrefix = if (isPrimary) "scoring-player-1" else "scoring-player-2"
    private val gp = SmallStatPanel("GAMES", "Game Won", "$playerPrefix-game-won", onGameWonClicked) { color ->
        UiStyles.flagIcon(color = color)
    }
    private val sp = SmallStatPanel("SETS", "Set Won", "$playerPrefix-set-won", onSetWonClicked) { color ->
        UiStyles.trophyIcon(color = color)
    }

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

        serveBtn.name = if (isPrimary) "scoring-player-1-serve" else "scoring-player-2-serve"
        serveBtn.isFocusPainted = false
        serveBtn.isFocusable = false
        serveBtn.background = Color(0x26, 0x26, 0x26)
        serveBtn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        serveBtn.putClientProperty(FlatClientProperties.STYLE, "selectedBackground: #3A3F24")
        // Only setServeState changes the selected state. A click does not toggle it.
        serveBtn.addActionListener {
            serveBtn.isSelected = serveState != ServeState.NOT_SERVING
            onServeClicked()
        }
        applyServeState()

        header.isOpaque = false
        header.layout = BoxLayout(header, BoxLayout.X_AXIS)
        header.add(pointsLbl)
        header.add(Box.createHorizontalStrut(8))
        header.add(pointsVal)
        header.add(Box.createHorizontalStrut(12))
        header.add(pointBtn)
        header.add(Box.createHorizontalStrut(6))
        header.add(serveBtn)
        header.add(Box.createHorizontalGlue())

        stats.isOpaque = false
        stats.add(gp)
        stats.add(sp)
    }

    fun setPlayerName(name: String) {
        playerName = name
        pointBtn.text = "Point for $name   " + if (isPrimary) "[Q]" else "[E]"
        pointBtn.toolTipText = (if (isPrimary) "Q" else "E") + " — Point for $name"
        applyServeState()
    }

    /** Shows if this player serves the selected point. */
    fun setServeState(state: ServeState) {
        serveState = state
        applyServeState()
    }

    fun setServeButtonEnabled(enabled: Boolean) {
        serveBtn.isEnabled = enabled
    }

    /**
     * Not serving: a gray racket. Serving: a colored racket.
     * Marked on this point: a colored racket with a lime border, so that the user can find the marks.
     */
    private fun applyServeState() {
        val serving = serveState != ServeState.NOT_SERVING
        serveBtn.isSelected = serving
        serveBtn.icon = UiStyles.serveRacketIcon(16, active = serving, color = SERVE_BUTTON_COLOR)
        val line = if (serveState == ServeState.SERVING_MARKED) {
            BorderFactory.createLineBorder(UiStyles.LIME, 2)
        } else {
            BorderFactory.createCompoundBorder(
                EmptyBorder(1, 1, 1, 1),
                BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x33), 1),
            )
        }
        serveBtn.border = BorderFactory.createCompoundBorder(line, EmptyBorder(5, 7, 5, 7))
        serveBtn.toolTipText = when (serveState) {
            ServeState.NOT_SERVING -> "Click to mark $playerName as the server of this point. S — switch the server"
            ServeState.SERVING -> "$playerName serves (computed from your serve marks). S — switch the server"
            ServeState.SERVING_MARKED -> "$playerName serves (marked on this point). Click to clear the mark. S — switch the server"
        }
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

    /** In manual scoring, the Game Won and Set Won markers accept clicks. [enabled] is false when no point is selected. */
    fun setManualScoring(manual: Boolean, enabled: Boolean) {
        gp.setInteractive(manual, enabled)
        sp.setInteractive(manual, enabled)
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
    buttonName: String,
    private val onClick: () -> Unit,
    icon: (Color) -> Icon,
) : JPanel() {

    private val valueLabel: JLabel
    private val toggleButton: MarkerButton
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

        toggleButton = markerBtn(buttonText)
        toggleButton.name = buttonName
        toggleButton.addActionListener { if (toggleButton.interactive) onClick() }
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

    /** Manual scoring: the button accepts clicks. Automatic scoring: the button only shows the state. */
    fun setInteractive(manual: Boolean, enabled: Boolean) {
        toggleButton.interactive = manual && enabled
        toggleButton.isRolloverEnabled = manual
        toggleButton.cursor = if (toggleButton.interactive) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
        toggleButton.toolTipText = if (manual) "Click to mark or clear the win on this point" else "Computed automatically"
    }

    /** A toggle button that ignores the mouse and the keyboard while it is not [interactive]. */
    private class MarkerButton(text: String) : JToggleButton(text) {
        var interactive = false

        override fun processMouseEvent(e: java.awt.event.MouseEvent) {
            if (interactive) super.processMouseEvent(e)
        }

        override fun processKeyEvent(e: java.awt.event.KeyEvent) {
            if (interactive) super.processKeyEvent(e)
        }
    }

    private fun markerBtn(buttonText: String): MarkerButton {
        val btn = MarkerButton(buttonText)

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
