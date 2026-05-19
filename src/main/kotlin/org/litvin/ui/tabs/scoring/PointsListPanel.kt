package org.litvin.ui.tabs.scoring

import org.litvin.markup.PointV1
import org.litvin.shared.util.Timecode
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import kotlin.math.max

/**
 * A focused UI component for the Scoring tab that renders the left points list:
 * - Header with Total/Scored badges
 * - Scrollable list of point rows with selection
 *
 * Exposes a minimal API to set data and selection and a callback for user-initiated selection changes.
 */
class PointsListPanel : JPanel(BorderLayout()) {

    private val rowComponents = mutableListOf<JComponent>()
    private lateinit var listContainer: JPanel
    private lateinit var listScroll: JScrollPane
    private lateinit var headerTotalBadge: JLabel
    private lateinit var headerScoredBadge: JLabel

    private var points: List<PointV1> = emptyList()
    private var scoredIds: Set<String> = emptySet()
    private var selectedIndex: Int = -1

    /** Called when user clicks a row inside the list. Arguments: index, userInitiated=true */
    var onSelect: ((Int, Boolean) -> Unit)? = null

    init {
        background = Color(0x15, 0x15, 0x15)
        border = BorderFactory.createMatteBorder(0, 0, 0, 1, Color(0x48, 0x48, 0x47, 0x33))
        preferredSize = Dimension(240, 0)

        val header = JPanel(BorderLayout())
        header.background = Color(0x20, 0x20, 0x1f)
        header.border = EmptyBorder(8, 8, 8, 8)

        val title = JLabel("Point Markers")
        title.foreground = Color(0xAD, 0xAA, 0xAA)
        title.font = title.font.deriveFont(Font.BOLD, 12f)
        header.add(title, BorderLayout.WEST)

        val counts = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))
        counts.isOpaque = false
        headerTotalBadge = smallBadge("0 Total", Color(0x26, 0x26, 0x26), Color(0xA1, 0xFE, 0x00))
        headerScoredBadge = smallBadge("0 Scored", Color(0x26, 0x26, 0x26), Color(0xAD, 0xAA, 0xAA))
        counts.add(headerTotalBadge)
        counts.add(headerScoredBadge)
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
        add(listScroll, BorderLayout.CENTER)
    }

    fun setData(points: List<PointV1>, scoredIds: Set<String>) {
        this.points = points
        this.scoredIds = scoredIds
        rebuild()
    }

    fun getSelectedIndex(): Int = selectedIndex

    fun setSelectedIndex(index: Int, userInitiated: Boolean) {
        if (index == selectedIndex) {
            if (userInitiated) scrollIntoView(index)
            return
        }
        val prev = selectedIndex
        selectedIndex = index
        if (prev in rowComponents.indices) {
            val prevScored = if (prev in points.indices) scoredIds.contains(points[prev].id) else false
            decorateRowSelection(rowComponents[prev], selected = false, scored = prevScored)
        }
        if (index in rowComponents.indices) {
            val nowScored = if (index in points.indices) scoredIds.contains(points[index].id) else false
            decorateRowSelection(rowComponents[index], selected = true, scored = nowScored)
            scrollIntoView(index)
        }
        if (userInitiated) onSelect?.invoke(index, true)
    }

    fun scrollIntoView(index: Int) {
        if (index !in rowComponents.indices) return
        val c = rowComponents[index]
        try {
            val rect = c.bounds
            val view = Rectangle(0, rect.y - 8, listScroll.viewport.width, rect.height + 16)
            listScroll.viewport.scrollRectToVisible(view)
        } catch (_: Throwable) { }
    }

    private fun rebuild() {
        try {
            listContainer.removeAll()
            rowComponents.clear()
            headerTotalBadge.text = "${points.size} Total"
            val scoredCount = points.count { scoredIds.contains(it.id) }
            headerScoredBadge.text = "$scoredCount Scored"
            if (points.isEmpty()) {
                val empty = JPanel(BorderLayout())
                empty.isOpaque = false
                empty.border = EmptyBorder(12, 8, 12, 8)
                val msg = JLabel("No points yet. Open the Markup tab and add point markers.")
                msg.foreground = Color(0xAD, 0xAA, 0xAA)
                msg.font = msg.font.deriveFont(Font.ITALIC, 12f)
                empty.add(msg, BorderLayout.NORTH)
                listContainer.add(empty)
            } else {
                points.forEachIndexed { i, p ->
                    val label = buildString {
                        append("#${i + 1}")
                        p.label?.let { if (it.isNotBlank()) append(" — ").append(it) }
                    }
                    val startTc = Timecode.format(p.startMs.toLong()).substring(0, 8)
                    val durSec = max(0, (p.endMs - p.startMs)) / 1000
                    val isScored = scoredIds.contains(p.id)
                    val row = pointRow(label, startTc, durSec, isScored)
                    decorateRowSelection(row, selected = (i == selectedIndex), scored = isScored)
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
            listContainer.revalidate()
            listContainer.repaint()
        } catch (_: Throwable) { }
    }

    private fun decorateRowSelection(row: JComponent, selected: Boolean, scored: Boolean) {
        row.background = if (selected) Color(0x2C, 0x2C, 0x2C) else if (scored) Color(0x24, 0x24, 0x24) else Color(0x1A, 0x1A, 0x1A)
        row.border = if (selected) {
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, Color(0xA1, 0xFE, 0x00)),
                (row.border ?: EmptyBorder(0, 0, 0, 0))
            )
        } else EmptyBorder(4, 8, 4, 8)
    }

    private fun pointRow(label: String, startTc: String, durationSec: Int, scored: Boolean): JComponent {
        val row = JPanel(BorderLayout(6, 0))
        row.border = EmptyBorder(4, 8, 4, 8)
        row.background = if (scored) Color(0x2C, 0x2C, 0x2C) else Color(0x1A, 0x1A, 0x1A)
        val left = JLabel("$startTc • ${durationSec}s")
        left.font = Font(Font.MONOSPACED, Font.PLAIN, 10)
        left.foreground = if (scored) Color(0xA1, 0xFE, 0x00) else Color(0xAD, 0xAA, 0xAA)
        row.add(left, BorderLayout.WEST)
        val center = JLabel(label)
        center.foreground = if (scored) Color(0xFF, 0xFF, 0xFF) else Color(0xAD, 0xAA, 0xAA)
        center.font = center.font.deriveFont(Font.PLAIN, 12f)
        row.add(center, BorderLayout.CENTER)
        if (scored) {
            val check = JLabel()
            check.icon = scoredIcon(18)
            row.add(check, BorderLayout.EAST)
        }
        val fixedH = 40
        row.minimumSize = Dimension(0, fixedH)
        row.preferredSize = Dimension(0, fixedH)
        row.maximumSize = Dimension(Int.MAX_VALUE, fixedH)
        row.alignmentX = 0f
        return row
    }

    private fun smallBadge(text: String, bg: Color, fg: Color): JLabel {
        val l = JLabel(text)
        l.isOpaque = true
        l.background = bg
        l.foreground = fg
        l.border = EmptyBorder(2, 6, 2, 6)
        l.font = l.font.deriveFont(10f)
        return l
    }

    // Green circle with white checkmark icon for scored points
    private fun scoredIcon(size: Int = 18): Icon {
        return object : Icon {
            override fun getIconWidth(): Int = size
            override fun getIconHeight(): Int = size
            override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
                if (g == null) return
                val g2 = (g.create() as Graphics2D)
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    val d = size
                    val green = Color(0x71, 0xB4, 0x00)
                    // Draw filled green circle
                    g2.color = green
                    g2.fillOval(x, y, d, d)
                    // Draw white checkmark
                    val s = d.toDouble()
                    val p = java.awt.geom.Path2D.Double()
                    p.moveTo(x + 0.28 * s, y + 0.55 * s)
                    p.lineTo(x + 0.45 * s, y + 0.72 * s)
                    p.lineTo(x + 0.75 * s, y + 0.35 * s)
                    g2.color = Color.WHITE
                    g2.stroke = BasicStroke((d * 0.12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g2.draw(p)
                } finally {
                    g2.dispose()
                }
            }
        }
    }
}
