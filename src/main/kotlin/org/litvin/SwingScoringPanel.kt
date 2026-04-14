package org.litvin

import org.litvin.media.VlcjSwingMediaPlayerAdapter
import org.litvin.media.PlayerStatus
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.ChangeListener
import kotlin.math.max
import org.litvin.SessionSettings
import javax.swing.SwingUtilities
import org.litvin.Outcome
import org.litvin.ScoreIO
import org.litvin.ScoreV1

/**
 * v0.1.0 — Scoring tab shell (Task 4.1)
 *
 * UI scaffolding that mirrors the mock in design/scoring.html:
 * - Left points list with header badges and footer buttons
 * - Center video area with a scoreboard overlay placeholder
 * - Per-point scrub bar under the video
 * - Bottom area with: top action row (centered No Point), transport, speed control,
 *   and side player panels (P1 / P2)
 *
 * No data wiring or persistence yet. All actions are no-ops for v0.1.0 task 4.1.
 */
class SwingScoringPanel : JPanel(BorderLayout()) {

    // Active state controlled by navigation
    private var isActive: Boolean = false

    fun onActivated() {
        isActive = true
        try { player.pause() } catch (_: Throwable) {}
        // Do not auto-play; optionally restore focus
        EventQueue.invokeLater { try { player.component.requestFocusInWindow() } catch (_: Throwable) {} }
    }

    fun onDeactivated() {
        isActive = false
        try { player.pause() } catch (_: Throwable) {}
        // Flush pending autosave when leaving the tab
        try { autosaveNow() } catch (_: Throwable) {}
    }

    // Play/Pause button ref for icon updates
    private lateinit var btnPlayPause: JButton

    // Media player (reuse Markup adapter)
    private val player = VlcjSwingMediaPlayerAdapter()

    // Current selected segment bounds [startMs, endMs)
    private var segStartMs: Long = 0L
    private var segEndMs: Long = 0L

    // Scrub interaction guard
    private var isScrubUpdatingFromPlayer = false

    private var currentManifestPath: String? = null
    private var projectDir: String? = null

    // Data
    private var points: List<PointV1> = emptyList()
    // Outcomes persistence (ScoreV1). "Scored" includes NONE.
    private val outcomesByPointId: MutableMap<String, Outcome> = LinkedHashMap()
    private val scoredPointIds: Set<String>
        get() = outcomesByPointId.keys

    // Autosave debounce timer (~300 ms) for score.json (Task 4.9)
    private val autosaveTimer = javax.swing.Timer(300) { _ -> autosaveNow() }.apply { isRepeats = false }

    // Left list UI refs
    private lateinit var headerTotalBadge: JLabel
    private lateinit var headerScoredBadge: JLabel
    private lateinit var listContainer: JPanel
    private lateinit var listScroll: JScrollPane
    private val rowComponents = mutableListOf<JComponent>()
    private var selectedIndex: Int = -1
    private lateinit var nextPointBtn: JButton

    // Scrub/UI refs in center
    private lateinit var segStartLabel: JLabel
    private lateinit var segNowLabel: JLabel
    private lateinit var scrubSlider: JSlider
    private lateinit var videoFrame: JComponent

    // Overlay UI (Task 4.12)
    private lateinit var overlayPanel: JPanel
    private lateinit var overlayTitle: JLabel

    // Speed control UI
    private lateinit var speedCombo: JComboBox<String>

    // Top action row buttons (Task 4.6)
    private lateinit var btnP1: JToggleButton
    private lateinit var btnNone: JToggleButton
    private lateinit var btnP2: JToggleButton

    // Bottom panels — dynamic refs for Task 4.7
    private lateinit var p1PointsVal: JLabel
    private lateinit var p2PointsVal: JLabel
    private lateinit var p1GamesVal: JLabel
    private lateinit var p2GamesVal: JLabel
    private lateinit var p1SetsVal: JLabel
    private lateinit var p2SetsVal: JLabel
    private lateinit var p1PointBtn: JToggleButton
    private lateinit var p2PointBtn: JToggleButton
    private lateinit var p1GameWonToggle: JToggleButton
    private lateinit var p2GameWonToggle: JToggleButton
    private lateinit var p1SetWonToggle: JToggleButton
    private lateinit var p2SetWonToggle: JToggleButton

    // Computed match state snapshot for current selection
    private data class MatchState(
        val p1Pts: Int, // internal tennis points counter (0..N) or tiebreak points when isTiebreak = true
        val p2Pts: Int,
        val gamesP1: Int,
        val gamesP2: Int,
        val setsP1: Int,
        val setsP2: Int,
        val lastGameWonBy: Int?, // 1,2 or null
        val lastSetWonBy: Int?,  // 1,2 or null
        val isTiebreak: Boolean
    )

    private data class SetScore(val p1: Int, val p2: Int, val tiebreak: Boolean)

    // Wire project and media
    fun setProjectManifest(path: String) {
        this.currentManifestPath = path
        try {
            this.projectDir = File(path).parentFile.absolutePath
            // Load points from EDL (sorted by startMs)
            val edl = EdlIO.readForProjectDir(projectDir!!)
            points = edl.points.sortedBy { it.startMs }
            // Load outcomes from score.json (ignore orphans)
            outcomesByPointId.clear()
            try {
                val score = ScoreIO.readForProjectDir(projectDir!!)
                val validIds = points.map { it.id }.toSet()
                score.outcomes.forEach { (id, out) -> if (id in validIds) outcomesByPointId[id] = out }
            } catch (_: Throwable) { /* keep empty on error */ }
            rebuildPointsList()
            autoSelectInitial()
            // Load media from manifest
            val manifest = ManifestIO.read(path)
            val src = manifest.sourceVideo
            if (!src.isNullOrBlank() && File(src).exists()) {
                player.load(File(src))
            }
        } catch (t: Throwable) {
            // Keep scaffold visible; in real app show dialog
            t.printStackTrace()
        }
    }

    init {
        installKeyBindings()
        background = Color(0x1A, 0x1A, 0x1A)
        layout = BorderLayout()

        // Root content: left list (fixed width) + center content
        val leftListPanel = buildLeftListPanel()
        val centerPanel = buildCenterPanel()

        add(leftListPanel, BorderLayout.WEST)
        add(centerPanel, BorderLayout.CENTER)

        // Wire media callbacks for segment clamping and UI updates
        player.onTimeChanged = { t ->
            EventQueue.invokeLater { onPlayerTimeChanged(t) }
        }
        player.onStatusChanged = {
            EventQueue.invokeLater { updatePlayPauseButton() }
        }
        player.onReady = {
            EventQueue.invokeLater {
                // After media length known, ensure UI labels reflect current selection
                // Apply current session speed to player
                try { player.setRate(SessionSettings.toRate(SessionSettings.playbackSpeedIndex)) } catch (_: Throwable) {}
                if (selectedIndex in points.indices) {
                    // Jump to start of current segment
                    player.seek(segStartMs)
                    player.pause()
                }
                updatePlayPauseButton()
            }
        }
    }

    // Rebuild left points list from current 'points' and 'scoredPointIds'
    private fun rebuildPointsList() {
        try {
            listContainer.removeAll()
            rowComponents.clear()
            headerTotalBadge.text = "${points.size} Total"
            val scoredCount = points.count { scoredPointIds.contains(it.id) }
            headerScoredBadge.text = "$scoredCount Scored"
            points.forEachIndexed { i, p ->
                val label = buildString {
                    append("#${i + 1}")
                    p.label?.let { if (it.isNotBlank()) append(" — ").append(it) }
                }
                val startTc = Timecode.format(p.startMs.toLong()).substring(0, 8)
                val durSec = max(0, (p.endMs - p.startMs)) / 1000
                val isScored = scoredPointIds.contains(p.id)
                val row = pointRow(label, startTc, durSec, isScored)
                // Selection visuals and click handler
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
            listContainer.revalidate()
            listContainer.repaint()
        } catch (_: Throwable) { }
    }

    private fun autoSelectInitial() {
        if (points.isEmpty()) {
            setSelectedIndex(-1, userInitiated = false)
            return
        }
        val firstUnscored = points.indexOfFirst { !scoredPointIds.contains(it.id) }
        if (firstUnscored >= 0) setSelectedIndex(firstUnscored, userInitiated = false)
        else setSelectedIndex(0, userInitiated = false)
    }

    private fun setSelectedIndex(index: Int, userInitiated: Boolean) {
        if (index == selectedIndex) {
            if (userInitiated) scrollRowIntoView(index)
            return
        }
        val prev = selectedIndex
        selectedIndex = index
        if (prev in rowComponents.indices) {
            val prevScored = if (prev in points.indices) scoredPointIds.contains(points[prev].id) else false
            decorateRowSelection(rowComponents[prev], selected = false, scored = prevScored)
        }
        if (index in rowComponents.indices) {
            val nowScored = if (index in points.indices) scoredPointIds.contains(points[index].id) else false
            decorateRowSelection(rowComponents[index], selected = true, scored = nowScored)
            scrollRowIntoView(index)
        }
        onSelectionChanged()
    }

    private fun onSelectionChanged() {
        if (selectedIndex !in points.indices) {
            // Reset scrub labels
            segStartMs = 0L
            segEndMs = 0L
            segStartLabel.text = "00:00:00"
            segNowLabel.text = "00:00:00  "
            scrubSlider.value = 0
            updateActionButtonsState(enable = false, selectedOutcome = null)
            // Disable Next on no selection or empty list
            try { if (::nextPointBtn.isInitialized) nextPointBtn.isEnabled = false } catch (_: Throwable) {}
            // Clear bottom panels and overlay
            val zero = MatchState(0, 0, 0, 0, 0, 0, null, null, false)
            updateBottomPanels(zero)
            try { updateOverlay(zero, emptyList()) } catch (_: Throwable) { }
            return
        }
        val p = points[selectedIndex]
        // Update segment bounds
        segStartMs = p.startMs.toLong()
        segEndMs = p.endMs.toLong()
        // Update scrub panel to show segment start and reset current within-segment time
        segStartLabel.text = Timecode.format(segStartMs).substring(0, 8)
        updateScrubUi(segStartMs)
        // Jump playback to start and pause; focus player when active
        player.pause()
        player.seek(segStartMs)
        if (isActive) player.component.requestFocusInWindow()
        // Update action buttons based on existing stored outcome and validity
        val valid = segEndMs > segStartMs
        val existing = outcomesByPointId[p.id]
        updateActionButtonsState(enable = valid, selectedOutcome = existing)
        // Enable/disable Next based on whether a subsequent point exists
        try { if (::nextPointBtn.isInitialized) nextPointBtn.isEnabled = (selectedIndex + 1) in points.indices } catch (_: Throwable) {}
        // Recompute panels for current selection
        recomputeFrom(selectedIndex)
    }

    private fun scrollRowIntoView(index: Int) {
        if (index !in rowComponents.indices) return
        val c = rowComponents[index]
        c.scrollRectToVisible(Rectangle(0, 0, c.width.coerceAtLeast(1), c.height.coerceAtLeast(1)))
    }

    // Task 4.10 — Next Point navigation
    private fun advanceToNextPoint() {
        if (selectedIndex !in points.indices) return
        val next = selectedIndex + 1
        if (next !in points.indices) {
            try { if (::nextPointBtn.isInitialized) nextPointBtn.isEnabled = false } catch (_: Throwable) {}
            return
        }
        // Keep playback paused and just move selection
        try { player.pause() } catch (_: Throwable) {}
        setSelectedIndex(next, userInitiated = false)
        // Focus should remain in the player area for Space/arrows to work
        EventQueue.invokeLater { try { player.component.requestFocusInWindow() } catch (_: Throwable) {} }
    }

    private fun onPlayerTimeChanged(absMs: Long) {
        // If we have a valid segment, clamp playback to [start, end)
        if (segEndMs > segStartMs) {
            val maxPlayable = (segEndMs - 1).coerceAtLeast(segStartMs)
            when {
                absMs >= segEndMs -> {
                    // Pause at end and clamp to last playable millisecond
                    player.pause()
                    player.seek(maxPlayable)
                    updateScrubUi(maxPlayable)
                    return
                }
                absMs < segStartMs -> {
                    // Clamp to start if an external seek went before the segment
                    player.seek(segStartMs)
                    updateScrubUi(segStartMs)
                    return
                }
            }
        }
        updateScrubUi(absMs)
    }

    private fun updateScrubUi(absMs: Long) {
        if (segEndMs > segStartMs) {
            val rel = (absMs - segStartMs).coerceAtLeast(0L)
            val dur = (segEndMs - segStartMs).coerceAtLeast(1L)
            val frac = (rel.toDouble() / dur.toDouble()).coerceIn(0.0, 0.999)
            val sliderVal = (frac * 100).toInt().coerceIn(0, 100)
            isScrubUpdatingFromPlayer = true
            try {
                scrubSlider.value = sliderVal
            } finally {
                isScrubUpdatingFromPlayer = false
            }
            segNowLabel.text = Timecode.format(rel).substring(0, 8) + "  "
        } else {
            isScrubUpdatingFromPlayer = true
            try { scrubSlider.value = 0 } finally { isScrubUpdatingFromPlayer = false }
            segNowLabel.text = "00:00:00  "
        }
    }

    private fun decorateRowSelection(row: JComponent, selected: Boolean, scored: Boolean) {
        // Base bg based on scored, then overlay selection stripe when selected
        row.background = if (selected) Color(0x2C, 0x2C, 0x2C) else if (scored) Color(0x24, 0x24, 0x24) else Color(0x1A, 0x1A, 0x1A)
        row.border = if (selected) {
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, Color(0xA1, 0xFE, 0x00)),
                (row.border ?: EmptyBorder(0, 0, 0, 0))
            )
        } else EmptyBorder(8, 8, 8, 8)
    }

    private fun buildLeftListPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BorderLayout()
        panel.background = Color(0x15, 0x15, 0x15)
        panel.border = BorderFactory.createMatteBorder(0, 0, 0, 1, Color(0x48, 0x48, 0x47, 0x33))
        panel.preferredSize = Dimension(320, 0) // Match mock left panel width (~320 px)

        // Header with badges: "Point Markers" + Total/Scored
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

        panel.add(header, BorderLayout.NORTH)

        // List body (dynamic items from EDL)
        listContainer = JPanel()
        listContainer.layout = BoxLayout(listContainer, BoxLayout.Y_AXIS)
        listContainer.isOpaque = false
        listContainer.border = EmptyBorder(6, 6, 6, 6)
        listScroll = JScrollPane(listContainer)
        listScroll.border = null
        listScroll.verticalScrollBar.unitIncrement = 16
        panel.add(listScroll, BorderLayout.CENTER)

        // Footer buttons: Next Point (W), Manual Marker, Scoreboard Settings — all no-ops
        val footer = JPanel()
        footer.layout = BoxLayout(footer, BoxLayout.Y_AXIS)
        footer.border = EmptyBorder(8, 8, 8, 8)
        footer.isOpaque = false

        fun fullButton(text: String): JButton {
            val b = JButton(text)
            b.isFocusPainted = false
            b.background = Color(0x26, 0x26, 0x26)
            b.foreground = Color(0xDD, 0xFF, 0xB0)
            b.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x55), 1),
                EmptyBorder(8, 8, 8, 8)
            )
            b.alignmentX = 0f
            return b
        }

        nextPointBtn = fullButton("Next Point    W")
        nextPointBtn.addActionListener { advanceToNextPoint() }
        nextPointBtn.isEnabled = false
        val manualBtn = fullButton("Manual Marker")
        manualBtn.foreground = Color(0xFF, 0xFF, 0xFF)
        val settingsBtn = fullButton("Scoreboard Settings")
        settingsBtn.foreground = Color(0xFF, 0xFF, 0xFF)

        footer.add(nextPointBtn)
        footer.add(Box.createVerticalStrut(6))
        footer.add(manualBtn)
        footer.add(Box.createVerticalStrut(6))
        footer.add(settingsBtn)

        val footerWrap = JPanel(BorderLayout())
        footerWrap.isOpaque = false
        footerWrap.add(footer, BorderLayout.NORTH)
        panel.add(footerWrap, BorderLayout.SOUTH)

        return panel
    }

    private fun pointRow(label: String, startTc: String, durationSec: Int, scored: Boolean): JComponent {
        val row = JPanel(BorderLayout(6, 0))
        row.border = EmptyBorder(8, 8, 8, 8)
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
            val check = JLabel("\u2714")
            check.foreground = Color(0xA1, 0xFE, 0x00)
            row.add(check, BorderLayout.EAST)
        }
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

    private fun buildCenterPanel(): JComponent {
        val root = JPanel()
        root.layout = BorderLayout()
        root.isOpaque = true
        root.background = Color(0x0E, 0x0E, 0x0E)

        // Video area with overlay
        val videoWrap = JPanel(BorderLayout())
        videoWrap.background = Color.BLACK
        videoWrap.border = EmptyBorder(8, 12, 8, 12)

        val vf = AspectPanel(16.0 / 9.0)
        vf.background = Color(0, 0, 0)
        vf.layout = null // absolute for overlay position managed by AspectPanel.doLayout
        videoFrame = vf

        // Add media player component (stretched to full area by AspectPanel.doLayout)
        val videoComponent = player.component
        videoComponent.name = "video"
        vf.add(videoComponent)

        // Overlay (top-left) — dynamic scoreboard (Task 4.12)
        overlayPanel = JPanel()
        overlayPanel.layout = BorderLayout()
        overlayPanel.background = Color(0, 0, 0, 160)
        overlayPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(255, 255, 255, 25)),
            EmptyBorder(6, 8, 6, 8)
        )
        val titleWrap = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        titleWrap.isOpaque = false
        overlayTitle = JLabel("Batumi Raketo league")
        overlayTitle.foreground = Color(0xA1, 0xFE, 0x00)
        overlayTitle.font = overlayTitle.font.deriveFont(Font.BOLD, 10f)
        titleWrap.add(overlayTitle)
        overlayPanel.add(titleWrap, BorderLayout.NORTH)
        // placeholder center; real content built by updateOverlay()
        overlayPanel.add(JPanel().apply { isOpaque = false }, BorderLayout.CENTER)

        // Place overlay
        vf.add(overlayPanel)
        overlayPanel.setBounds(24, 24, 420, 110)

        // Initial overlay state 0–0
        updateOverlay(MatchState(0,0,0,0,0,0,null,null,false), emptyList())

        videoWrap.add(vf, BorderLayout.CENTER)
        root.add(videoWrap, BorderLayout.CENTER)

        // Scrub bar — current point segment only
        val scrubPanel = JPanel(BorderLayout(8, 0))
        scrubPanel.border = EmptyBorder(8, 16, 8, 16)
        scrubPanel.background = Color(0x11, 0x11, 0x11)
        segStartLabel = JLabel("00:00:00")
        segStartLabel.font = Font(Font.MONOSPACED, Font.PLAIN, 10)
        segStartLabel.foreground = Color(0xAD, 0xAA, 0xAA)
        segNowLabel = JLabel("00:00:00  ")
        segNowLabel.font = Font(Font.MONOSPACED, Font.PLAIN, 10)
        segNowLabel.foreground = Color(0xAD, 0xAA, 0xAA)
        val segLbl = JLabel("Point segment")
        segLbl.font = segLbl.font.deriveFont(9f)
        segLbl.foreground = Color(150, 150, 150)
        val rightBox = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        rightBox.isOpaque = false
        rightBox.add(segNowLabel)
        rightBox.add(segLbl)
        scrubSlider = JSlider(0, 100, 0)
        scrubSlider.background = scrubPanel.background
        scrubSlider.addChangeListener(ChangeListener {
            if (isScrubUpdatingFromPlayer) return@ChangeListener
            if (segEndMs <= segStartMs) return@ChangeListener
            val frac = scrubSlider.value / 100.0
            var target = segStartMs + ((segEndMs - segStartMs) * frac).toLong()
            val maxPlayable = (segEndMs - 1).coerceAtLeast(segStartMs)
            if (target > maxPlayable) target = maxPlayable
            player.pause() // seeking pauses per Scoring 4.3 (no auto-advance)
            player.seek(target)
            updateScrubUi(target)
        })
        scrubPanel.add(segStartLabel, BorderLayout.WEST)
        scrubPanel.add(scrubSlider, BorderLayout.CENTER)
        scrubPanel.add(rightBox, BorderLayout.EAST)
        root.add(scrubPanel, BorderLayout.SOUTH)

        // Bottom controls & player panels
        val bottom = JPanel(BorderLayout())
        bottom.border = EmptyBorder(8, 12, 8, 12)
        bottom.background = Color(0x1A, 0x1A, 0x1A)

        val leftPlayer = buildPlayerPanel("Player 1", primary = true)
        val rightPlayer = buildPlayerPanel("Player 2", primary = false)
        val centerControls = buildCenterControls()

        bottom.add(leftPlayer, BorderLayout.WEST)
        bottom.add(centerControls, BorderLayout.CENTER)
        bottom.add(rightPlayer, BorderLayout.EAST)

        val southWrap = JPanel(BorderLayout())
        southWrap.add(bottom, BorderLayout.CENTER)
        southWrap.border = BorderFactory.createMatteBorder(1, 0, 0, 0, Color(0x48, 0x48, 0x47, 0x33))

        // Attach south after the scrub bar: create a stack (video -> scrub -> bottom)
        val centerStack = JPanel()
        centerStack.layout = BorderLayout()
        centerStack.add(videoWrap, BorderLayout.CENTER)
        centerStack.add(scrubPanel, BorderLayout.SOUTH)

        val centerWithBottom = JPanel(BorderLayout())
        centerWithBottom.add(centerStack, BorderLayout.CENTER)
        centerWithBottom.add(southWrap, BorderLayout.SOUTH)

        return centerWithBottom
    }

    private fun buildPlayerPanel(name: String, primary: Boolean): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.preferredSize = Dimension(360, 0)
        panel.isOpaque = false

        // POINTS value
        val pointsRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        pointsRow.isOpaque = false
        val pointsLbl = JLabel("POINTS")
        pointsLbl.foreground = Color(0xAD, 0xAA, 0xAA)
        pointsLbl.font = pointsLbl.font.deriveFont(Font.BOLD, 11f)
        val pointsVal = JLabel("0")
        pointsVal.background = Color(0x26, 0x26, 0x26)
        pointsVal.foreground = if (primary) Color(0xA1, 0xFE, 0x00) else Color.WHITE
        pointsVal.isOpaque = true
        pointsVal.border = EmptyBorder(6, 10, 6, 10)
        pointsRow.add(pointsLbl)
        pointsRow.add(pointsVal)
        if (primary) p1PointsVal = pointsVal else p2PointsVal = pointsVal

        // "Point for Player" button (duplicates top actions) — toggle to reflect pressed state (Task 4.11)
        val pointBtn = JToggleButton("Point for $name")
        pointBtn.isFocusPainted = false
        pointBtn.foreground = if (primary) Color(0x42, 0xA5, 0xF5) else Color(0xEF, 0x53, 0x50)
        pointBtn.background = Color(0x26, 0x26, 0x26)
        pointBtn.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x33), 1),
            EmptyBorder(6, 10, 6, 10)
        )
        pointBtn.addActionListener {
            setOutcomeForSelected(if (primary) Outcome.P1 else Outcome.P2)
        }
        if (primary) p1PointBtn = pointBtn else p2PointBtn = pointBtn

        val topRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        topRow.isOpaque = false
        topRow.add(pointsRow)
        topRow.add(pointBtn)

        // GAMES and SETS grids with read-only toggles reflecting completion
        val grids = JPanel(GridLayout(1, 2, 8, 0))
        grids.isOpaque = false
        grids.border = EmptyBorder(8, 0, 0, 0)

        fun smallStatPanelDyn(title: String, accent: Color, buttonText: String, assign: (JLabel, JToggleButton) -> Unit): JComponent {
            val p = JPanel()
            p.layout = BoxLayout(p, BoxLayout.Y_AXIS)
            p.background = Color(0x10, 0x10, 0x10)
            p.border = EmptyBorder(8, 8, 8, 8)
            val t = JLabel(title)
            t.foreground = Color(0xAD, 0xAA, 0xAA)
            t.font = t.font.deriveFont(Font.BOLD, 10f)
            val v = JLabel("0")
            v.foreground = Color.WHITE
            v.font = v.font.deriveFont(Font.BOLD, 24f)
            val btn = JToggleButton(buttonText)
            btn.isEnabled = false // read-only indicator per 4.7 decisions
            btn.foreground = accent
            btn.background = Color(0x26, 0x26, 0x26)
            btn.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x33), 1),
                EmptyBorder(6, 8, 6, 8)
            )
            t.alignmentX = 0.5f
            v.alignmentX = 0.5f
            btn.alignmentX = 0.5f
            p.add(t)
            p.add(Box.createVerticalStrut(4))
            p.add(v)
            p.add(Box.createVerticalStrut(6))
            p.add(btn)
            assign(v, btn)
            return p
        }
        val gamesPanel = smallStatPanelDyn(
            "GAMES",
            if (primary) Color(0x42, 0xA5, 0xF5) else Color(0xEF, 0x53, 0x50),
            "Game Won"
        ) { label, toggle ->
            if (primary) { p1GamesVal = label; p1GameWonToggle = toggle } else { p2GamesVal = label; p2GameWonToggle = toggle }
        }
        val setsPanel = smallStatPanelDyn(
            "SETS",
            if (primary) Color(0x42, 0xA5, 0xF5) else Color(0xEF, 0x53, 0x50),
            "Set Won"
        ) { label, toggle ->
            if (primary) { p1SetsVal = label; p1SetWonToggle = toggle } else { p2SetsVal = label; p2SetWonToggle = toggle }
        }
        grids.add(gamesPanel)
        grids.add(setsPanel)

        panel.add(topRow)
        panel.add(grids)
        return panel
    }

    private fun smallStatPanel(title: String, value: String, accent: Color, buttonText: String): JComponent {
        val p = JPanel()
        p.layout = BoxLayout(p, BoxLayout.Y_AXIS)
        p.background = Color(0x10, 0x10, 0x10)
        p.border = EmptyBorder(8, 8, 8, 8)

        val t = JLabel(title)
        t.foreground = Color(0xAD, 0xAA, 0xAA)
        t.font = t.font.deriveFont(Font.BOLD, 10f)
        val v = JLabel(value)
        v.foreground = Color.WHITE
        v.font = v.font.deriveFont(Font.BOLD, 24f)
        val btn = JButton(buttonText)
        btn.isEnabled = false // v0.1.0: read-only indicator
        btn.foreground = accent
        btn.background = Color(0x26, 0x26, 0x26)
        btn.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x33), 1),
            EmptyBorder(6, 8, 6, 8)
        )

        t.alignmentX = 0.5f
        v.alignmentX = 0.5f
        btn.alignmentX = 0.5f

        p.add(t)
        p.add(Box.createVerticalStrut(4))
        p.add(v)
        p.add(Box.createVerticalStrut(6))
        p.add(btn)
        return p
    }

    private fun buildCenterControls(): JComponent {
        val wrap = JPanel()
        wrap.layout = BoxLayout(wrap, BoxLayout.Y_AXIS)
        wrap.isOpaque = false

        // Top action row — left: P1, center: No Point, right: P2 (Task 4.6)
        val actionRow = JPanel(BorderLayout())
        actionRow.isOpaque = false
        val leftWrap = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }
        val rightWrap = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply { isOpaque = false }
        val centerWrap = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply { isOpaque = false }

        fun makeToggle(text: String): JToggleButton {
            val b = JToggleButton(text)
            b.isFocusPainted = false
            UiStyles.styleSecondary(b)
            b.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x33), 1),
                EmptyBorder(6, 12, 6, 12)
            )
            return b
        }
        btnP1 = makeToggle("Point for Player 1   [A]")
        btnNone = makeToggle("No Point   [N]")
        btnP2 = makeToggle("Point for Player 2   [L]")

        // Make them act like radio buttons
        val group = ButtonGroup()
        group.add(btnP1); group.add(btnNone); group.add(btnP2)

        btnP1.addActionListener { setOutcomeForSelected(Outcome.P1) }
        btnNone.addActionListener { setOutcomeForSelected(Outcome.NONE) }
        btnP2.addActionListener { setOutcomeForSelected(Outcome.P2) }

        leftWrap.add(btnP1)
        centerWrap.add(btnNone)
        rightWrap.add(btnP2)
        actionRow.add(leftWrap, BorderLayout.WEST)
        actionRow.add(centerWrap, BorderLayout.CENTER)
        actionRow.add(rightWrap, BorderLayout.EAST)
        // Initially disabled until a valid selection is made
        updateActionButtonsState(enable = false, selectedOutcome = null)

        // Transport controls — match Markup screen (−10s, −1s, Play, +1s, +10s)
        val transport = JPanel()
        transport.layout = BoxLayout(transport, BoxLayout.X_AXIS)
        transport.isOpaque = false

        fun styleSeek(b: JButton) {
            UiStyles.styleSecondary(b)
            b.iconTextGap = 6
            b.preferredSize = Dimension(100, 44)
            b.minimumSize = Dimension(100, 40)
        }
        fun styleSeekLarge(b: JButton) {
            UiStyles.styleSecondary(b)
            b.iconTextGap = 6
            b.preferredSize = Dimension(150, 44)
            b.minimumSize = Dimension(120, 40)
        }

        val btnSeekBack10 = JButton()
        val btnSeekBack1 = JButton()
        val btnSeekFwd1 = JButton()
        val btnSeekFwd10 = JButton()
        styleSeekLarge(btnSeekBack10); styleSeek(btnSeekBack1); styleSeek(btnSeekFwd1); styleSeekLarge(btnSeekFwd10)
        btnSeekBack10.icon = UiStyles.seekIcon(false, 18); btnSeekBack10.text = "-10s [shift+←]"
        btnSeekBack1.icon = UiStyles.seekIcon(false, 18); btnSeekBack1.text = "-1s [←]"
        btnSeekFwd1.icon = UiStyles.seekIcon(true, 18); btnSeekFwd1.text = "+1s [→]"
        btnSeekFwd10.icon = UiStyles.seekIcon(true, 18); btnSeekFwd10.text = "+10s [shift+→]"
        btnSeekBack10.toolTipText = "Shift+Left"
        btnSeekBack1.toolTipText = "Left"
        btnSeekFwd1.toolTipText = "Right"
        btnSeekFwd10.toolTipText = "Shift+Right"
        btnSeekBack10.horizontalTextPosition = SwingConstants.RIGHT
        btnSeekBack1.horizontalTextPosition = SwingConstants.RIGHT
        btnSeekFwd1.horizontalTextPosition = SwingConstants.LEFT
        btnSeekFwd10.horizontalTextPosition = SwingConstants.LEFT
        // Wire actions
        btnSeekBack10.addActionListener { seekBy(-10_000); player.component.requestFocusInWindow() }
        btnSeekBack1.addActionListener { seekBy(-1_000); player.component.requestFocusInWindow() }
        btnSeekFwd1.addActionListener { seekBy(1_000); player.component.requestFocusInWindow() }
        btnSeekFwd10.addActionListener { seekBy(10_000); player.component.requestFocusInWindow() }

        // Square Play/Pause button
        btnPlayPause = UiStyles.squarePrimaryButton(UiStyles.playIcon(28)) { togglePlayPause() }

        // Assemble row similar to Markup
        transport.add(btnSeekBack10); transport.add(Box.createHorizontalStrut(6))
        transport.add(btnSeekBack1); transport.add(Box.createHorizontalStrut(12))
        transport.add(btnPlayPause); transport.add(Box.createHorizontalStrut(12))
        transport.add(btnSeekFwd1); transport.add(Box.createHorizontalStrut(6))
        transport.add(btnSeekFwd10)

        // Speed control with hint (kept per Scoring spec 4.5)
        val speedRow = JPanel(FlowLayout(FlowLayout.CENTER, 8, 0))
        speedRow.isOpaque = false
        val speeds = arrayOf("2×", "1×", "0.5×", "0.25×", "0.1×")
        speedCombo = JComboBox(speeds)
        speedCombo.isFocusable = true
        speedCombo.selectedIndex = SessionSettings.clampIndex(SessionSettings.playbackSpeedIndex)
        speedCombo.toolTipText = "Use ↑/↓ to change speed"
        // Apply current speed to player immediately (affects playback instantly)
        player.setRate(SessionSettings.toRate(speedCombo.selectedIndex))
        speedCombo.addActionListener {
            val idx = speedCombo.selectedIndex
            SessionSettings.playbackSpeedIndex = SessionSettings.clampIndex(idx)
            player.setRate(SessionSettings.toRate(SessionSettings.playbackSpeedIndex))
            // Keep focus on player for continued space/seek keys
            EventQueue.invokeLater { player.component.requestFocusInWindow() }
        }
        speedRow.add(speedCombo)
        speedRow.add(JLabel("↑ / ↓ speed").apply {
            foreground = Color(0xAD, 0xAA, 0xAA)
            font = font.deriveFont(10f)
            toolTipText = "Use ↑/↓ to change speed"
        })

        wrap.add(actionRow)
        wrap.add(Box.createVerticalStrut(6))
        wrap.add(transport)
        wrap.add(Box.createVerticalStrut(2))
        wrap.add(speedRow)
        return wrap
    }

    // ===== Task 4.4: Transport controls and seeks (match Markup) =====
    private fun isTextEditingFocus(): Boolean {
        return try {
            val fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            (fo is javax.swing.text.JTextComponent) && fo.isEnabled && fo.isVisible && fo.isEditable
        } catch (_: Throwable) { false }
    }

    private fun updatePlayPauseButton() {
        try {
            val playing = player.status() == PlayerStatus.PLAYING
            btnPlayPause.icon = if (playing) UiStyles.pauseIcon(28) else UiStyles.playIcon(28)
            btnPlayPause.toolTipText = if (playing) "SPACE — Pause" else "SPACE — Play"
            btnPlayPause.repaint()
        } catch (_: Throwable) { }
    }

    private fun togglePlayPause() {
        val wasPlaying = player.status() == PlayerStatus.PLAYING
        if (wasPlaying) player.pause() else player.play()
        updatePlayPauseButton()
        player.component.requestFocusInWindow()
    }

    private fun seekBy(deltaMs: Long) {
        // Compute target and clamp to current segment when available
        var target = (player.currentTimeMs() + deltaMs)
        if (segEndMs > segStartMs) {
            val maxPlayable = (segEndMs - 1).coerceAtLeast(segStartMs)
            if (target < segStartMs) target = segStartMs
            if (target > maxPlayable) target = maxPlayable
        } else {
            if (target < 0L) target = 0L
        }
        player.seek(target)
        updateScrubUi(target)
    }

    private fun installKeyBindings() {
        fun bind(key: String, actionName: String, runnable: () -> Unit) {
            val am = this.actionMap
            val ims = arrayOf(
                JComponent.WHEN_IN_FOCUSED_WINDOW,
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
            )
            ims.forEach { cond ->
                val im = this.getInputMap(cond)
                im.put(KeyStroke.getKeyStroke(key), actionName)
            }
            am.put(actionName, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    if (isTextEditingFocus()) return
                    runnable()
                    EventQueue.invokeLater { player.component.requestFocusInWindow() }
                }
            })
        }
        // Space toggles play/pause
        bind("SPACE", "togglePlayPause") { togglePlayPause() }
        // Seeks: Left/Right = ±1s; Shift+arrows = ±10s
        bind("LEFT", "seekLeft1s") { seekBy(-1_000) }
        bind("RIGHT", "seekRight1s") { seekBy(1_000) }
        bind("shift LEFT", "seekLeft10s") { seekBy(-10_000) }
        bind("shift RIGHT", "seekRight10s") { seekBy(10_000) }
        // Speed control via keyboard: Up/Down when player area has focus
        bind("UP", "speedUp") {
            if (isSpeedComboFocus()) return@bind // let combo handle its own
            if (!isPlayerAreaFocus()) return@bind
            changeSpeedBy(-1) // presets ordered high→low; Up means go to higher preset → lower index
        }
        bind("DOWN", "speedDown") {
            if (isSpeedComboFocus()) return@bind
            if (!isPlayerAreaFocus()) return@bind
            changeSpeedBy(1)
        }
        // Scoring hotkeys: A = P1, N = No Point, L = P2 (Task 4.6)
        bind("A", "scoreP1") { setOutcomeForSelected(Outcome.P1) }
        bind("N", "scoreNone") { setOutcomeForSelected(Outcome.NONE) }
        bind("L", "scoreP2") { setOutcomeForSelected(Outcome.P2) }
        // Next Point navigation (Task 4.10): W advances to next index without auto-play
        bind("W", "nextPoint") { advanceToNextPoint() }
    }

    private fun isSpeedComboFocus(): Boolean {
        return try {
            val fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner as? Component
            fo != null && (fo === speedCombo || SwingUtilities.isDescendingFrom(fo, speedCombo))
        } catch (_: Throwable) { false }
    }

    private fun isPlayerAreaFocus(): Boolean {
        return try {
            val fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner as? Component
            val comp = player.component
            fo != null && (fo === comp || SwingUtilities.isDescendingFrom(fo, comp))
        } catch (_: Throwable) { false }
    }

    private fun changeSpeedBy(delta: Int) {
        if (!::speedCombo.isInitialized) return
        val current = speedCombo.selectedIndex
        val next = SessionSettings.clampIndex(current + delta)
        if (next != current) {
            speedCombo.selectedIndex = next // action listener will update player + session
        }
    }

    // ===== Task 4.6 helpers =====
    private fun updateActionButtonsState(enable: Boolean, selectedOutcome: Outcome?) {
        // Enable/disable all action controls
        if (::btnP1.isInitialized) btnP1.isEnabled = enable
        if (::btnNone.isInitialized) btnNone.isEnabled = enable
        if (::btnP2.isInitialized) btnP2.isEnabled = enable
        if (::p1PointBtn.isInitialized) p1PointBtn.isEnabled = enable
        if (::p2PointBtn.isInitialized) p2PointBtn.isEnabled = enable

        // Reflect stored outcome as pressed/selected state (Task 4.11)
        val sel = selectedOutcome
        val p1 = sel == Outcome.P1
        val none = sel == Outcome.NONE
        val p2 = sel == Outcome.P2
        // Top action row (radio-like)
        if (::btnP1.isInitialized) btnP1.model.isSelected = p1
        if (::btnNone.isInitialized) btnNone.model.isSelected = none
        if (::btnP2.isInitialized) btnP2.model.isSelected = p2
        // Bottom player quick actions (toggles to mirror top actions)
        if (::p1PointBtn.isInitialized) p1PointBtn.model.isSelected = p1
        if (::p2PointBtn.isInitialized) p2PointBtn.model.isSelected = p2
    }

    private fun setOutcomeForSelected(outcome: Outcome) {
        if (selectedIndex !in points.indices) return
        // Validate segment duration per 4.6 disabled state rule
        if (segEndMs <= segStartMs) return
        val p = points[selectedIndex]
        outcomesByPointId[p.id] = outcome
        updateActionButtonsState(enable = true, selectedOutcome = outcome)
        // Refresh left list counts and checkmarks
        val keepIndex = selectedIndex
        rebuildPointsList()
        setSelectedIndex(keepIndex, userInitiated = false)
        // Persistence: debounce autosave of score.json
        scheduleScoreAutosave()
        // Placeholder for future rules recompute (Task 4.8)
        recomputeFrom(keepIndex)
        // Keep focus on player area so Space/arrows continue to work
        EventQueue.invokeLater { try { player.component.requestFocusInWindow() } catch (_: Throwable) {} }
    }

    private var statesAfterPoint: MutableList<MatchState> = mutableListOf()
    private var setHistoryAfterPoint: MutableList<List<SetScore>> = mutableListOf()

    // ===== Task 4.9 — Persistence helpers (score.json with debounce) =====
    private fun scheduleScoreAutosave() {
        try { autosaveTimer.restart() } catch (_: Throwable) { }
    }
    private fun autosaveNow() {
        try {
            val dir = projectDir ?: return
            val map = LinkedHashMap(outcomesByPointId) // snapshot
            ScoreIO.writeForProjectDir(dir, ScoreV1(outcomes = map, version = 1))
        } catch (t: Throwable) {
            // Non-fatal; show error similarly to Markup autosave
            try { SwingDialogUtils.showError(this, t, "Autosave failed") } catch (_: Throwable) { }
        }
    }

    private fun recomputeFrom(index: Int) {
        // Task 4.8 — Full rules engine with deuce/advantage and 6–6 tiebreaks (best‑of‑3)
        fun computeTimeline(): Pair<MutableList<MatchState>, MutableList<List<SetScore>>> {
            val result = MutableList(points.size) { MatchState(0, 0, 0, 0, 0, 0, null, null, false) }
            val setsPerPoint = MutableList(points.size) { emptyList<SetScore>() }
            var p1Pts = 0
            var p2Pts = 0
            var gamesP1 = 0
            var gamesP2 = 0
            var setsP1 = 0
            var setsP2 = 0
            var lastGameWonBy: Int? = null
            var lastSetWonBy: Int? = null
            var isTiebreak = false
            val completedSets = mutableListOf<SetScore>()

            fun checkRegularGameWin(): Int? {
                if ((p1Pts >= 4 || p2Pts >= 4) && kotlin.math.abs(p1Pts - p2Pts) >= 2) {
                    return if (p1Pts > p2Pts) 1 else 2
                }
                return null
            }
            fun addRegularGame(winner: Int) {
                if (winner == 1) gamesP1++ else gamesP2++
                p1Pts = 0; p2Pts = 0
                lastGameWonBy = winner
                val lead = kotlin.math.abs(gamesP1 - gamesP2)
                val maxGames = kotlin.math.max(gamesP1, gamesP2)
                if (maxGames >= 6 && lead >= 2) {
                    // Record completed set score before reset
                    completedSets.add(SetScore(gamesP1, gamesP2, false))
                    if (gamesP1 > gamesP2) setsP1++ else setsP2++
                    gamesP1 = 0; gamesP2 = 0
                    lastSetWonBy = if (setsP1 > setsP2) 1 else 2
                    lastGameWonBy = null
                    isTiebreak = false
                } else if (gamesP1 == 6 && gamesP2 == 6) {
                    isTiebreak = true
                    lastGameWonBy = null
                    lastSetWonBy = null
                } else {
                    lastSetWonBy = null
                }
            }
            fun addTiebreakPoint(winner: Int) {
                if (winner == 1) p1Pts++ else p2Pts++
                val lead = kotlin.math.abs(p1Pts - p2Pts)
                val maxPts = kotlin.math.max(p1Pts, p2Pts)
                if (maxPts >= 7 && lead >= 2) {
                    // Record tiebreak set as 7–6 for the winner
                    if (p1Pts > p2Pts) completedSets.add(SetScore(7, 6, true)) else completedSets.add(SetScore(6, 7, true))
                    if (p1Pts > p2Pts) setsP1++ else setsP2++
                    gamesP1 = 0; gamesP2 = 0
                    lastSetWonBy = if (setsP1 > setsP2) 1 else 2
                    lastGameWonBy = null
                    p1Pts = 0; p2Pts = 0
                    isTiebreak = false
                } else {
                    lastSetWonBy = null
                }
            }

            for (i in points.indices) {
                // If match is already won (best‑of‑3), keep final state snapshots for remaining points
                if (setsP1 >= 2 || setsP2 >= 2) {
                    result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, null, null, false)
                    setsPerPoint[i] = completedSets.toList()
                    continue
                }
                val outcome = outcomesByPointId[points[i].id]
                if (outcome == null) {
                    // Unscored point: carry current state
                    result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, null, null, isTiebreak)
                    setsPerPoint[i] = completedSets.toList()
                    continue
                }
                if (outcome == Outcome.NONE) {
                    // Scored as NONE: state unchanged
                    result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, null, null, isTiebreak)
                    setsPerPoint[i] = completedSets.toList()
                    continue
                }
                if (isTiebreak) {
                    addTiebreakPoint(if (outcome == Outcome.P1) 1 else 2)
                    result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, null, lastSetWonBy, isTiebreak)
                    setsPerPoint[i] = completedSets.toList()
                    // After set win via tiebreak we reset pts/isTiebreak above; snapshot next loop will reflect reset
                } else {
                    if (outcome == Outcome.P1) p1Pts++ else p2Pts++
                    val gw = checkRegularGameWin()
                    if (gw != null) {
                        addRegularGame(gw)
                        result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, gw, lastSetWonBy, isTiebreak)
                        // Clear last markers if not a set win (so UI shows transient press)
                        if (lastSetWonBy == null) lastGameWonBy = null
                    } else {
                        lastGameWonBy = null
                        lastSetWonBy = null
                        result[i] = MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, null, null, isTiebreak)
                    }
                    setsPerPoint[i] = completedSets.toList()
                }
            }
            return Pair(result, setsPerPoint)
        }

        // Recompute the full timeline to satisfy 4.8 recomputation guarantees
        val (states, setsPerPoint) = computeTimeline()
        statesAfterPoint = states
        setHistoryAfterPoint = setsPerPoint

        // Update bottom panels for current selection
        if (index in points.indices) {
            val st = statesAfterPoint[index]
            updateBottomPanels(st)
            updateOverlay(st, setHistoryAfterPoint.getOrNull(index) ?: emptyList())
        } else {
            val zero = MatchState(0, 0, 0, 0, 0, 0, null, null, false)
            updateBottomPanels(zero)
            updateOverlay(zero, emptyList())
        }
    }

    private fun updateBottomPanels(state: MatchState) {
        // Map internal points to tennis display values; in tiebreak show numeric points
        fun displayPoints(forP1: Boolean, p1Pts: Int, p2Pts: Int, isTb: Boolean): String {
            val mine = if (forP1) p1Pts else p2Pts
            val other = if (forP1) p2Pts else p1Pts
            if (isTb) return mine.toString()
            val base = arrayOf("0", "15", "30", "40")
            if (mine < 4 && other < 4) return base[mine.coerceIn(0, 3)]
            // Deuce/Advantage area
            return if (mine == other) "40" else if (mine > other) "Ad" else "40"
        }
        if (::p1PointsVal.isInitialized) p1PointsVal.text = displayPoints(true, state.p1Pts, state.p2Pts, state.isTiebreak)
        if (::p2PointsVal.isInitialized) p2PointsVal.text = displayPoints(false, state.p1Pts, state.p2Pts, state.isTiebreak)
        if (::p1GamesVal.isInitialized) p1GamesVal.text = state.gamesP1.toString()
        if (::p2GamesVal.isInitialized) p2GamesVal.text = state.gamesP2.toString()
        if (::p1SetsVal.isInitialized) p1SetsVal.text = state.setsP1.toString()
        if (::p2SetsVal.isInitialized) p2SetsVal.text = state.setsP2.toString()
        if (::p1GameWonToggle.isInitialized) p1GameWonToggle.model.isSelected = state.lastGameWonBy == 1
        if (::p2GameWonToggle.isInitialized) p2GameWonToggle.model.isSelected = state.lastGameWonBy == 2
        if (::p1SetWonToggle.isInitialized) p1SetWonToggle.model.isSelected = state.lastSetWonBy == 1
        if (::p2SetWonToggle.isInitialized) p2SetWonToggle.model.isSelected = state.lastSetWonBy == 2
    }

    // ===== Task 4.12: Overlay scoreboard above the video =====
    private fun updateOverlay(state: MatchState, completedSets: List<SetScore>) {
        if (!::overlayPanel.isInitialized) return
        // Build dynamic center content: two rows (P1/P2), columns = completed sets + current set + points
        fun label(text: String, fg: Color, bold: Boolean = false, size: Float = 12f): JLabel {
            val l = JLabel(text)
            l.foreground = fg
            l.font = if (bold) l.font.deriveFont(Font.BOLD, size) else l.font.deriveFont(size)
            return l
        }
        fun cell(text: String, alignLeft: Boolean = false, accent: Color? = null): JComponent {
            val l = JLabel(text)
            l.foreground = Color.WHITE
            if (accent != null) l.foreground = accent
            l.border = EmptyBorder(0, 6, 0, 6)
            l.horizontalAlignment = if (alignLeft) SwingConstants.LEFT else SwingConstants.CENTER
            return l
        }
        fun displayPointVal(forP1: Boolean): String {
            val mine = if (forP1) state.p1Pts else state.p2Pts
            val other = if (forP1) state.p2Pts else state.p1Pts
            if (state.isTiebreak) return mine.toString()
            val base = arrayOf("0", "15", "30", "40")
            if (mine < 4 && other < 4) return base[mine.coerceIn(0, 3)]
            return if (mine == other) "40" else if (mine > other) "Ad" else "40"
        }

        val center = JPanel(GridBagLayout())
        center.isOpaque = false
        val gbc = GridBagConstraints().apply {
            insets = Insets(2, 4, 2, 4)
            anchor = GridBagConstraints.WEST
        }

        // Names column
        gbc.gridx = 0; gbc.gridy = 0
        center.add(label("Player 1", Color.WHITE, bold = true, size = 12f), gbc)
        gbc.gridy = 1
        center.add(label("Player 2", Color(0xDD, 0xDD, 0xDD), bold = true, size = 12f), gbc)

        // Add completed set columns
        var col = 1
        completedSets.forEach { s ->
            gbc.gridx = col; gbc.gridy = 0
            center.add(cell(s.p1.toString()), gbc)
            gbc.gridy = 1
            center.add(cell(s.p2.toString()), gbc)
            col++
        }
        // Current set column (games so far)
        gbc.gridx = col; gbc.gridy = 0
        center.add(cell(state.gamesP1.toString(), accent = Color(0xA1, 0xFE, 0x00)), gbc)
        gbc.gridy = 1
        center.add(cell(state.gamesP2.toString(), accent = Color(0xA1, 0xFE, 0x00)), gbc)
        col++
        // Current game points column
        gbc.gridx = col; gbc.gridy = 0
        center.add(cell(displayPointVal(true), alignLeft = false), gbc)
        gbc.gridy = 1
        center.add(cell(displayPointVal(false), alignLeft = false), gbc)

        // Replace center of overlayPanel
        val prev = if (overlayPanel.componentCount >= 2) overlayPanel.getComponent(1) else null
        if (prev != null) overlayPanel.remove(prev)
        overlayPanel.add(center, BorderLayout.CENTER)
        overlayPanel.revalidate()
        overlayPanel.repaint()
    }

    private fun circleButton(text: String): JComponent {
        val b = JButton(text)
        b.isFocusPainted = false
        b.preferredSize = Dimension(36, 36)
        b.background = Color(0x26, 0x26, 0x26)
        b.foreground = Color.WHITE
        b.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x33), 1),
            EmptyBorder(6, 6, 6, 6)
        )
        return b
    }

    private fun primaryCircleButton(text: String): JComponent {
        val b = JButton(text)
        b.isFocusPainted = false
        b.preferredSize = Dimension(44, 44)
        b.background = Color(0x26, 0x26, 0x26)
        b.foreground = Color(0xA1, 0xFE, 0x00)
        b.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0xA1, 0xFE, 0x00), 1),
            EmptyBorder(6, 6, 6, 6)
        )
        return b
    }

    /**
     * Simple panel that preserves the provided aspect ratio by letterboxing.
     */
    private class AspectPanel(private val aspect: Double) : JPanel() {
        override fun getPreferredSize(): Dimension {
            val w = super.getPreferredSize().width.takeIf { it > 0 } ?: 1280
            val h = (w / aspect).toInt()
            return Dimension(w, h)
        }
        override fun doLayout() {
            val w = width
            val h = height
            val targetW: Int
            val targetH: Int
            if (w / aspect < h) {
                targetW = w
                targetH = (w / aspect).toInt()
            } else {
                targetH = h
                targetW = (h * aspect).toInt()
            }
            val x = (w - targetW) / 2
            val y = (h - targetH) / 2
            for (i in 0 until componentCount) {
                val c = getComponent(i)
                if (!c.isVisible) continue
                if ("video" == c.name) {
                    c.setBounds(x, y, targetW, targetH)
                } else {
                    // Keep overlay absolute coordinates relative to this panel
                    c.revalidate()
                }
            }
        }
    }
}
