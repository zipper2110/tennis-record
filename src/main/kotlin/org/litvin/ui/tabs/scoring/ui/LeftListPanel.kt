package org.litvin.ui.tabs.scoring.ui

import org.litvin.points.PointV1
import org.litvin.scoring.ManualScoreMarks
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.Outcome
import org.litvin.ui.UiStyles
import org.litvin.ui.tabs.scoring.NavigationActions
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 *
 * Responsibilities:
 * - Hosts the timeline list of points (via TimelineSection)
 * - Shows the current point counter ("Point 3 / 8") with its favorite button
 * - Provides footer actions: Next/Previous Point, Scoring Settings and Scoreboard Style
 *
 */
class LeftListPanel(
    private val actions: NavigationActions,
    private val onScoreSettings: () -> Unit = {},
    private val onScoreboardStyle: () -> Unit = {},
) : JPanel(BorderLayout()) {

    private val timeline = TimelineSection(actions)
    private val nextPointBtn: JButton
    private val prevPointBtn: JButton
    private val currentPointLabel = JLabel("No point selected")
    private val currentPointFavoriteBtn: JButton
    private var currentPointIndex: Int = -1

    init {
        background = Color(0x15, 0x15, 0x15)
        border = BorderFactory.createMatteBorder(0, 0, 0, 1, Color(0x48, 0x48, 0x47, 0x33))
        preferredSize = Dimension(280, 0)

        add(timeline, BorderLayout.CENTER)

        val footer = JPanel()
        footer.layout = BoxLayout(footer, BoxLayout.Y_AXIS)
        footer.border = EmptyBorder(6, 6, 6, 6)
        footer.isOpaque = false

        fun fullButton(text: String, icon: Icon? = null): JButton {
            val b = JButton(text, icon)
            b.iconTextGap = 8
            b.isFocusPainted = false
            b.background = Color(0x26, 0x26, 0x26)
            b.foreground = Color(0xDD, 0xFF, 0xB0)
            b.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x55), 1),
                EmptyBorder(8, 8, 8, 8)
            )
            val prefH = b.preferredSize.height
            b.maximumSize = Dimension(Int.MAX_VALUE, prefH)
            b.minimumSize = Dimension(0, prefH)
            b.alignmentX = 0f
            return b
        }

        currentPointLabel.name = "current-point-label"
        currentPointLabel.foreground = Color.WHITE
        currentPointLabel.font = currentPointLabel.font.deriveFont(Font.BOLD, 13f)

        currentPointFavoriteBtn = UiStyles.smallIconButton(UiStyles.favoriteIcon(18, false), "Favorite [A]") {
            if (currentPointIndex >= 0) actions.toggleFavorite(currentPointIndex)
        }.apply {
            text = "[A]"
            font = font.deriveFont(Font.BOLD, 10f)
            name = "current-point-favorite"
        }

        val currentPointRow = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            alignmentX = 0f
            border = EmptyBorder(0, 4, 0, 0)
            add(currentPointLabel, BorderLayout.CENTER)
            add(currentPointFavoriteBtn, BorderLayout.EAST)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

        nextPointBtn = fullButton("Next Point  [R]")
        nextPointBtn.name = "next-point"
        nextPointBtn.toolTipText = "R — Next Point"
        nextPointBtn.addActionListener { actions.advanceToNextPoint() }
        nextPointBtn.isEnabled = false

        prevPointBtn = fullButton("Previous Point  [Shift+R]")
        prevPointBtn.name = "previous-point"
        prevPointBtn.toolTipText = "Shift+R — Previous Point"
        prevPointBtn.addActionListener { actions.goToPreviousPoint() }
        prevPointBtn.isEnabled = false

        val scoreSettingsBtn = fullButton("Scoring Settings", UiStyles.scoreSettingsIcon())
        scoreSettingsBtn.name = "score-settings"
        scoreSettingsBtn.foreground = Color(0xFF, 0xFF, 0xFF)
        scoreSettingsBtn.toolTipText = "Set the player names and colors, the match format, and manual scoring"
        scoreSettingsBtn.addActionListener { onScoreSettings() }

        val scoreboardStyleBtn = fullButton("Scoreboard Style", UiStyles.scoreboardStyleIcon())
        scoreboardStyleBtn.name = "scoreboard-style"
        scoreboardStyleBtn.foreground = Color(0xFF, 0xFF, 0xFF)
        scoreboardStyleBtn.toolTipText = "Set the scoreboard style, title, position, and size"
        scoreboardStyleBtn.addActionListener { onScoreboardStyle() }

        footer.add(currentPointRow)
        footer.add(Box.createVerticalStrut(6))
        footer.add(nextPointBtn)
        footer.add(Box.createVerticalStrut(6))
        footer.add(prevPointBtn)
        footer.add(Box.createVerticalStrut(10))
        footer.add(scoreSettingsBtn)
        footer.add(Box.createVerticalStrut(6))
        footer.add(scoreboardStyleBtn)

        val footerWrap = JPanel(BorderLayout())
        footerWrap.isOpaque = false
        footerWrap.add(footer, BorderLayout.NORTH)
        add(footerWrap, BorderLayout.SOUTH)

        setCurrentPoint(-1, 0, favorite = false)
    }

    // API
    fun setList(
        points: List<PointV1>,
        outcomesByPointId: Map<String, Outcome>,
        p1ColorHex: String,
        p2ColorHex: String,
        rules: MatchRulesV1 = MatchRulesV1(),
        manualMarks: ManualScoreMarks = ManualScoreMarks(),
        serverMarks: Map<String, Outcome> = emptyMap(),
    ) {
        timeline.setList(points, outcomesByPointId, p1ColorHex, p2ColorHex, rules, manualMarks, serverMarks)
    }

    fun setSelectedIndex(index: Int, userInitiated: Boolean) {
        timeline.setSelectedIndex(index, userInitiated)
    }

    fun scrollIntoView(index: Int) {
        timeline.scrollIntoView(index)
    }

    fun setNextEnabled(enabled: Boolean) {
        nextPointBtn.isEnabled = enabled
    }

    fun setPreviousEnabled(enabled: Boolean) {
        prevPointBtn.isEnabled = enabled
    }

    /** Player names used by the milestone tooltips of the list. */
    fun setPlayerNames(p1: String, p2: String) {
        timeline.setPlayerNames(p1, p2)
    }

    /** Shows "Point [index + 1] / [total]" and the favorite state. A negative [index] means no selection. */
    fun setCurrentPoint(index: Int, total: Int, favorite: Boolean) {
        currentPointIndex = index
        if (index < 0) {
            currentPointLabel.text = "No point selected"
            currentPointLabel.foreground = Color(0xAD, 0xAA, 0xAA)
            currentPointFavoriteBtn.isEnabled = false
            currentPointFavoriteBtn.icon = UiStyles.favoriteIcon(18, false)
            currentPointFavoriteBtn.foreground = UiStyles.FG_SECONDARY
            return
        }
        currentPointLabel.text = "Point ${index + 1} / $total"
        currentPointLabel.foreground = Color.WHITE
        currentPointFavoriteBtn.isEnabled = true
        currentPointFavoriteBtn.icon = UiStyles.favoriteIcon(18, favorite)
        currentPointFavoriteBtn.foreground = if (favorite) UiStyles.YELLOW else UiStyles.FG_SECONDARY
    }
}
