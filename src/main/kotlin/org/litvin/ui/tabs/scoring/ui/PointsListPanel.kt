package org.litvin.ui.tabs.scoring.ui

import org.litvin.points.PointV1
import org.litvin.scoring.ManualScoreMarks
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoringEngine
import org.litvin.shared.util.Timecode
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.scrollIntoView
import org.litvin.ui.commons.uiSafe
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import kotlin.math.max
import org.litvin.ui.commons.applyDarkScrollbar

/**
 * A focused UI component for the Scoring tab that renders the left points list:
 * - Header with Total/Scored badges
 * - Scrollable list of point rows with selection
 *
 * Exposes a minimal API to set data and selection and a callback for user-initiated selection changes.
 */
class PointsListPanel : JPanel(BorderLayout()) {

    private companion object {
        const val BADGE_HEIGHT = 16
    }

    private val rowComponents = mutableListOf<JComponent>()
    private var listContainer: JPanel
    private var listScroll: JScrollPane
    private var headerTotalBadge: JLabel
    private var headerScoredBadge: JLabel
    private var headerFavoriteBadge: JLabel

    private var points: List<PointV1> = emptyList()
    private var outcomesByPointId: Map<String, Outcome> = emptyMap()
    private var rules: MatchRulesV1 = MatchRulesV1()
    private var manualMarks: ManualScoreMarks = ManualScoreMarks()
    private var serverMarks: Map<String, Outcome> = emptyMap()
    private var p1Color: Color = Color(0x4D, 0xA3, 0xFF)
    private var p2Color: Color = Color(0xFF, 0x6B, 0x6B)
    private var p1Name: String = "Player 1"
    private var p2Name: String = "Player 2"
    private var selectedIndex: Int = -1

    /** Milestone markers whose tooltips name a player, so a rename can refresh them in place. */
    private data class MilestoneBadge(val label: JLabel, val kind: String, val player: Int)

    private val milestoneBadges = mutableListOf<MilestoneBadge>()

    /** Serve mark icons with the marked player, so that a rename can refresh their tooltips in place. */
    private val serveMarkBadges = mutableListOf<Pair<JLabel, Int>>()

    /** Called when user clicks a row inside the list. Arguments: index, userInitiated=true */
    var onSelect: ((Int, Boolean) -> Unit)? = null
    var onToggleFavorite: ((Int) -> Unit)? = null

    init {
        background = Color(0x15, 0x15, 0x15)
        border = BorderFactory.createMatteBorder(0, 0, 0, 1, Color(0x48, 0x48, 0x47, 0x33))
        preferredSize = Dimension(240, 0)

        val header = JPanel(BorderLayout())
        header.background = Color(0x20, 0x20, 0x1f)
        header.border = EmptyBorder(8, 8, 8, 8)

        val title = JLabel("Played Points")
        title.foreground = Color(0xAD, 0xAA, 0xAA)
        title.font = title.font.deriveFont(Font.BOLD, 12f)
        header.add(title, BorderLayout.WEST)

        val counts = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))
        counts.isOpaque = false
        headerTotalBadge = UiStyles.smallBadge("0 Total", Color(0x26, 0x26, 0x26), Color(0xA1, 0xFE, 0x00))
        headerScoredBadge = UiStyles.smallBadge("0 Scored", Color(0x26, 0x26, 0x26), Color(0xAD, 0xAA, 0xAA))
        headerFavoriteBadge = UiStyles.smallBadge("0 Fav", Color(0x26, 0x26, 0x26), UiStyles.YELLOW)
        counts.add(headerTotalBadge)
        counts.add(headerScoredBadge)
        counts.add(headerFavoriteBadge)
        header.add(counts, BorderLayout.EAST)

        add(header, BorderLayout.NORTH)

        listContainer = JPanel()
        listContainer.layout = BoxLayout(listContainer, BoxLayout.Y_AXIS)
        listContainer.isOpaque = false
        listContainer.border = EmptyBorder(6, 6, 6, 6)

        listScroll = JScrollPane(listContainer)
        listScroll.border = null
        listScroll.verticalScrollBar.unitIncrement = 16
        listScroll.isOpaque = false
        listScroll.viewport.isOpaque = false
        listScroll.background = background
        listScroll.viewport.background = background

        // Apply unified dark scrollbar styling
        applyDarkScrollbar(listScroll, background)

        add(listScroll, BorderLayout.CENTER)
    }

    fun setData(
        points: List<PointV1>,
        outcomesByPointId: Map<String, Outcome>,
        p1ColorHex: String,
        p2ColorHex: String,
        rules: MatchRulesV1 = MatchRulesV1(),
        manualMarks: ManualScoreMarks = ManualScoreMarks(),
        serverMarks: Map<String, Outcome> = emptyMap(),
    ) {
        this.points = points
        this.outcomesByPointId = LinkedHashMap(outcomesByPointId)
        this.rules = rules
        this.manualMarks = manualMarks
        this.serverMarks = LinkedHashMap(serverMarks)
        this.p1Color = parseHexOrNull(p1ColorHex) ?: this.p1Color
        this.p2Color = parseHexOrNull(p2ColorHex) ?: this.p2Color
        rebuild()
    }

    /** Names used by milestone tooltips; applied in place, without rebuilding the rows. */
    fun setPlayerNames(p1: String, p2: String) {
        p1Name = p1.ifBlank { "Player 1" }
        p2Name = p2.ifBlank { "Player 2" }
        applyMilestoneTooltips()
    }

    fun getSelectedIndex(): Int = selectedIndex

    fun setSelectedIndex(index: Int, userInitiated: Boolean) {
        if (index == selectedIndex) {
            // Do not auto-scroll on user click of the already selected row; only programmatic.
            if (!userInitiated) scrollIntoView(index)
            return
        }
        val prev = selectedIndex
        selectedIndex = index
        if (prev in rowComponents.indices) {
            val prevOutcome = if (prev in points.indices) outcomesByPointId[points[prev].id] else null
            decorateRowSelection(rowComponents[prev], selected = false, outcome = prevOutcome)
        }
        if (index in rowComponents.indices) {
            val nowOutcome = if (index in points.indices) outcomesByPointId[points[index].id] else null
            decorateRowSelection(rowComponents[index], selected = true, outcome = nowOutcome)
            // Only auto-scroll for programmatic selection changes to avoid jumps on user clicks.
            if (!userInitiated) scrollIntoView(index)
        }
        if (userInitiated) onSelect?.invoke(index, true)
    }

    fun scrollIntoView(index: Int) = uiSafe {
        if (index !in rowComponents.indices) return@uiSafe
        val c = rowComponents[index]
        c.scrollIntoView()
    }

    private fun rebuild() {
        try {
            listContainer.removeAll()
            rowComponents.clear()
            milestoneBadges.clear()
            serveMarkBadges.clear()
            headerTotalBadge.text = "${points.size} Total"
            val scoredCount = points.count { outcomesByPointId.containsKey(it.id) }
            headerScoredBadge.text = "$scoredCount Scored"
            val favoriteCount = points.count { it.favorite }
            headerFavoriteBadge.text = "$favoriteCount Fav"
            if (points.isEmpty()) {
                val empty = JPanel(BorderLayout())
                empty.isOpaque = false
                empty.border = EmptyBorder(12, 8, 12, 8)
                val msg = JLabel("No points yet. Open the Points tab and add point markers.")
                msg.foreground = Color(0xAD, 0xAA, 0xAA)
                msg.font = msg.font.deriveFont(Font.ITALIC, 12f)
                empty.add(msg, BorderLayout.NORTH)
                listContainer.add(empty)
            } else {
                val states = ScoringEngine.computeTimeline(points, outcomesByPointId, rules, manualMarks).first
                points.forEachIndexed { i, p ->
                    val label = buildString {
                        append("#${i + 1}")
                        p.label?.let { if (it.isNotBlank()) append(" — ").append(it) }
                    }
                    val startTc = Timecode.format(p.startMs.toLong()).substring(0, 8)
                    val durSec = max(0, (p.endMs - p.startMs)) / 1000
                    val outcome = outcomesByPointId[p.id]
                    val state = states.getOrNull(i)
                    val row = pointRow(
                        pointName = label,
                        startTc = startTc,
                        durationSec = durSec,
                        outcome = outcome,
                        favorite = p.favorite,
                        gameWonBy = state?.lastGameWonBy,
                        setWonBy = state?.lastSetWonBy,
                        serverMark = when (serverMarks[p.id]) {
                            Outcome.P1 -> 1
                            Outcome.P2 -> 2
                            else -> null
                        },
                        onSelectRow = { setSelectedIndex(i, userInitiated = true) },
                    ) {
                        onToggleFavorite?.invoke(i)
                    }
                    decorateRowSelection(row, selected = (i == selectedIndex), outcome = outcome)
                    row.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    row.addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            setSelectedIndex(i, userInitiated = true)
                        }
                    })
                    rowComponents.add(row)
                    listContainer.add(row)
                    listContainer.add(Box.createVerticalStrut(4))
                }
            }
            applyMilestoneTooltips()
            listContainer.revalidate()
            listContainer.repaint()
        } catch (_: Throwable) { }
    }

    private fun decorateRowSelection(row: JComponent, selected: Boolean, outcome: Outcome?) {
        val scored = outcome != null
        val bg = when {
            selected -> Color(0x3A, 0x3A, 0x36)
            scored -> Color(0x24, 0x24, 0x24)
            else -> Color(0x1A, 0x1A, 0x1A)
        }
        row.background = bg
        row.border = buildRowBorder(selected, indicatorColor(outcome, bg))
    }

    private fun pointRow(
        pointName: String,
        startTc: String,
        durationSec: Int,
        outcome: Outcome?,
        favorite: Boolean,
        gameWonBy: Int?,
        setWonBy: Int?,
        serverMark: Int?,
        onSelectRow: () -> Unit,
        onFavorite: () -> Unit,
    ): JComponent {
        val scored = outcome != null
        val row = JPanel(BorderLayout(6, 0))
        row.background = if (scored) Color(0x2C, 0x2C, 0x2C) else Color(0x1A, 0x1A, 0x1A)
        row.border = buildRowBorder(selected = false, indicatorColor(outcome, row.background))

        val pointNameLabel = JLabel(pointName)
        pointNameLabel.foreground = if (scored) Color(0xFF, 0xFF, 0xFF) else Color(0xAD, 0xAA, 0xAA)
        pointNameLabel.font = pointNameLabel.font.deriveFont(Font.PLAIN, 12f)
        pointNameLabel.preferredSize = Dimension(35, 20)
        pointNameLabel.border = BorderFactory.createEmptyBorder(0, 0, 0, 8)
        row.add(pointNameLabel, BorderLayout.WEST)

        val pointTiming = JLabel("$startTc • ${durationSec}s")
        pointTiming.font = Font(Font.MONOSPACED, Font.PLAIN, 10)
        pointTiming.foreground = if (scored) Color(0xA1, 0xFE, 0x00) else Color(0xAD, 0xAA, 0xAA)
        row.add(pointTiming, BorderLayout.CENTER)

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
        }
        serverMark?.let { right.add(serveMarkBadge(it, onSelectRow)) }
        // A set-clinching point also wins a game; the set marker alone says the more interesting thing.
        if (setWonBy == null) {
            gameWonBy?.let { right.add(gameBadge(it, onSelectRow)) }
        }
        setWonBy?.let { right.add(setBadge(it, onSelectRow)) }
        right.add(scoredStatus(scored, onSelectRow))
        val favBtn = UiStyles.smallIconButton(UiStyles.favoriteIcon(16, favorite), "Favorite [A]") {
            onFavorite()
        }.apply {
            font = font.deriveFont(Font.BOLD, 9f)
            foreground = if (favorite) UiStyles.YELLOW else UiStyles.FG_SECONDARY
            name = "favorite-point"
            isFocusable = false
        }
        right.add(favBtn)
        row.add(right, BorderLayout.EAST)
        val fixedH = 40
        row.minimumSize = Dimension(0, fixedH)
        row.preferredSize = Dimension(0, fixedH)
        row.maximumSize = Dimension(Int.MAX_VALUE, fixedH)
        row.alignmentX = 0f
        return row
    }

    private fun playerColor(player: Int): Color = if (player == 1) p1Color else p2Color

    private fun playerName(player: Int): String = if (player == 1) p1Name else p2Name

    /** Solid marker in the winner's color: this point closed out a game. */
    private fun gameBadge(player: Int, onSelectRow: () -> Unit): JLabel = milestoneBadge(
        icon = UiStyles.textBadgeIcon("GAME", BADGE_HEIGHT, playerColor(player)),
        componentName = "game-won",
        kind = "Game",
        player = player,
        onSelectRow = onSelectRow,
    )

    /** Outlined marker: the winner's color moves to the text and a hairline border. */
    private fun setBadge(player: Int, onSelectRow: () -> Unit): JLabel {
        val color = playerColor(player)
        return milestoneBadge(
            icon = UiStyles.textBadgeIcon(
                text = "SET",
                height = BADGE_HEIGHT,
                bg = UiStyles.contrastingTextColor(color),
                fg = color,
                borderColor = color,
            ),
            componentName = "set-won",
            kind = "Set",
            player = player,
            onSelectRow = onSelectRow,
        )
    }

    /** A racket in the player color: the user marked this player as the server of this point. */
    private fun serveMarkBadge(player: Int, onSelectRow: () -> Unit): JLabel =
        JLabel(UiStyles.serveRacketIcon(14, active = true, color = playerColor(player))).apply {
            name = "serve-marked"
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = onSelectRow()
            })
            serveMarkBadges.add(this to player)
        }

    /** Check mark for a scored point, empty ring for an unscored one. Clicks select the row, as for the milestone badges. */
    private fun scoredStatus(scored: Boolean, onSelectRow: () -> Unit): JLabel =
        JLabel(if (scored) UiStyles.scoredIcon(18) else UiStyles.unscoredIcon(18)).apply {
            name = if (scored) "point-scored" else "point-unscored"
            toolTipText = if (scored) {
                "The point is scored"
            } else {
                "Not scored. Choose who won this point"
            }
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = onSelectRow()
            })
        }

    private fun milestoneBadge(
        icon: Icon,
        componentName: String,
        kind: String,
        player: Int,
        onSelectRow: () -> Unit,
    ): JLabel = JLabel(icon).apply {
        name = componentName
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = onSelectRow()
        })
        milestoneBadges.add(MilestoneBadge(this, kind, player))
    }

    /** Setting a tooltip also makes the label consume clicks, which its listener re-publishes as a selection. */
    private fun applyMilestoneTooltips() {
        milestoneBadges.forEach { badge ->
            badge.label.toolTipText = "${badge.kind} won by ${playerName(badge.player)}"
        }
        serveMarkBadges.forEach { (label, player) ->
            label.toolTipText = "Serve marked: ${playerName(player)} serves"
        }
    }

    private fun buildRowBorder(selected: Boolean, indicatorColor: Color): javax.swing.border.Border {
        val inner = if (selected) {
            BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color(0xA1, 0xFE, 0x00), 2),
                    BorderFactory.createMatteBorder(0, 4, 0, 0, Color(0xA1, 0xFE, 0x00, 0x66))
                ),
                EmptyBorder(2, 6, 2, 7)
            )
        } else {
            EmptyBorder(4, 8, 4, 8)
        }
        return BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 6, 0, 0, indicatorColor),
            inner
        )
    }

    private fun indicatorColor(outcome: Outcome?, fallback: Color): Color {
        return when (outcome) {
            Outcome.P1 -> p1Color
            Outcome.P2 -> p2Color
            Outcome.NONE -> Color(0xD6, 0xD6, 0xD6)
            null -> fallback
        }
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
}
