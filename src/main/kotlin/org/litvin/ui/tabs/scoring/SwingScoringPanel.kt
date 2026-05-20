package org.litvin.ui.tabs.scoring
import org.litvin.*
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.ui.commons.Dialogs
import org.litvin.ui.UiStyles

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
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.BadLocationException
import javax.swing.text.DocumentFilter
import kotlin.math.max
import javax.swing.SwingUtilities
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.markup.EdlIO
import org.litvin.markup.PointV1
import org.litvin.shared.util.Timecode
import org.litvin.ui.tabs.scoring.ui.ControlsToolbar
import org.litvin.ui.tabs.scoring.ui.ScoreEntryPanel
import org.litvin.ui.tabs.scoring.ui.TimelineSection
import org.litvin.ui.tabs.scoring.ui.HotkeysHelpPanel
import org.litvin.ui.tabs.scoring.ui.MatchHeaderPanel
import org.litvin.ui.tabs.scoring.ui.VideoSyncPanel

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
 *
 * v0.3.0 — E-SC-001 (T1): Component map and contracts draft
 * Componentization target (leaves composed by this container):
 * - header/MatchHeaderPanel — match title/players, current set/game, serve indicator
 * - entry/ScoreEntryPanel — primary scoring inputs (points, undo/redo)
 * - toolbar/ControlsToolbar — top-level actions (reset/save/settings)
 * - timeline/TimelineSection — wraps existing PointsListPanel
 * - help/HotkeysHelpPanel — compact legend of scoring hotkeys
 * - video/VideoSyncPanel — basic video/timecode sync controls for scoring
 *
 * Shared contracts for wiring (defined in `org.litvin.ui.tabs.scoring`):
 * - `ScoringActions` — view-to-domain/container commands (pointWon, undo, redo, toggleServe, navigate, play/seek, save)
 * - `ScoringViewState` — immutable snapshot consumed by leaves (names, serving, points/games/sets, selection, player status)
 *
 * SwingScoringPanel remains the container that composes leaves and binds them to domain services,
 * while leaves depend only on the above contracts, per architecture rules.
 */
class SwingScoringPanel : JPanel(BorderLayout()) {

    // Actions adapter for leaf components (E-SC-001 contracts)
    private val actions: ScoringActions = object : ScoringActions {
        override fun pointWon(side: Side) {
            try {
                val outcome = if (side == Side.P1) Outcome.P1 else Outcome.P2
                setOutcomeForSelected(outcome)
            } catch (_: Throwable) { }
        }
        override fun undo() { /* TODO(E-SC-001/T4): hook up undo when available */ }
        override fun redo() { /* TODO(E-SC-001/T4): hook up redo when available */ }
        override fun finalizeGame(winner: Side) { /* TODO(E-SC-001/T4): implement when domain is ready */ }
        override fun finalizeSet(winner: Side) { /* TODO(E-SC-001/T4): implement when domain is ready */ }
        override fun toggleServe() { /* TODO(E-SC-001/T4): implement when domain is ready */ }

        override fun navigateToPoint(index: Int) {
            if (index in points.indices) setSelectedIndex(index, userInitiated = true)
        }
        override fun advanceToNextPoint() { this@SwingScoringPanel.advanceToNextPoint() }

        override fun playPause() { togglePlayPause() }
        override fun seekBy(milliseconds: Long) { this@SwingScoringPanel.seekBy(milliseconds) }
        override fun setSpeedMultiplier(multiplier: Float) {
            try {
                // Choose closest preset
                val presets = SessionSettings.speedPresets
                var bestIdx = 0
                var bestDiff = Float.MAX_VALUE
                for (i in presets.indices) {
                    val d = kotlin.math.abs(presets[i] - multiplier)
                    if (d < bestDiff) { bestDiff = d; bestIdx = i }
                }
                SessionSettings.playbackSpeedIndex = SessionSettings.clampIndex(bestIdx)
                if (::speedCombo.isInitialized) speedCombo.selectedIndex = SessionSettings.playbackSpeedIndex
                else player.setRate(SessionSettings.toRate(SessionSettings.playbackSpeedIndex))
            } catch (_: Throwable) { }
        }

        override fun saveScore() { try { saveNow() } catch (_: Throwable) { } }
        override fun autosave() { try { autosaveNow() } catch (_: Throwable) { } }
    }

    // Active state controlled by navigation
    private var isActive: Boolean = false

    fun onActivated() {
        isActive = true
        ensurePlayerLoaded()
        try { player.pause() } catch (_: Throwable) {}
        // Refresh points every time the tab is opened to reflect latest Markup changes
        try { refreshPointsFromProject() } catch (_: Throwable) { }
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
    // Deferred media loading
    private var pendingMediaFile: File? = null
    private var isMediaLoaded: Boolean = false
    private var loadErrorShown: Boolean = false

    override fun addNotify() {
        super.addNotify()
        try { SwingUtilities.invokeLater { ensurePlayerLoaded() } } catch (_: Throwable) { }
    }

    private fun ensurePlayerLoaded() {
        try {
            if (isMediaLoaded) return
            val f = pendingMediaFile ?: return
            val wnd = try { SwingUtilities.getWindowAncestor(player.component) } catch (_: Throwable) { null }
            if (!player.component.isDisplayable || wnd == null || !wnd.isShowing) return
            player.load(f)
            try { player.pause() } catch (_: Throwable) {}
            isMediaLoaded = true
        } catch (t: Throwable) {
            if (!loadErrorShown) {
                loadErrorShown = true
                try { Dialogs.showError(this, t, "Failed to load project") } catch (_: Throwable) { }
            }
        }
    }

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

    // Autosave timer for names typing debounce (~300 ms)
    private val namesSaveTimer = javax.swing.Timer(300) { _ -> autosaveNow() }.apply { isRepeats = false }

    // Left list UI refs
    private lateinit var headerTotalBadge: JLabel
    private lateinit var headerScoredBadge: JLabel
    private lateinit var listContainer: JPanel
    private lateinit var listScroll: JScrollPane
    private val rowComponents = mutableListOf<JComponent>()
    private var selectedIndex: Int = -1
    private lateinit var pointsList: TimelineSection
    private lateinit var nextPointBtn: JButton

    // Player names (Task 4.17)
    private var player1Name: String = "Player 1"
    private var player2Name: String = "Player 2"
    private lateinit var p1NameField: JTextField
    private lateinit var p2NameField: JTextField
    private var isUpdatingNameFields: Boolean = false

    // Scrub/UI refs in center
    private lateinit var segStartLabel: JLabel
    private lateinit var segNowLabel: JLabel
    private lateinit var scrubSlider: JSlider
    private lateinit var segErrorLabel: JLabel
    private lateinit var videoFrame: JComponent

    // Overlay UI (Task 4.12) — temporarily removed for v0.1.0; will be reimplemented in v0.2.0.
    // (Keeping method stubs like updateOverlay() as no-ops to simplify future reintegration.)

    // Speed control UI
    private lateinit var speedCombo: JComboBox<String>

    // Top action row buttons (Task 4.6)
    private lateinit var btnP1: JToggleButton
    private lateinit var btnNone: JToggleButton
    private lateinit var btnP2: JToggleButton

    // Extracted leaf: primary scoring inputs (E-SC-001 T4)
    private var scoreEntryPanel: ScoreEntryPanel? = null

    // Extracted leaves (E-SC-001): header, video sync, hotkeys help
    private lateinit var headerPanel: MatchHeaderPanel
    private lateinit var videoSyncPanel: VideoSyncPanel
    private lateinit var hotkeysHelpPanel: HotkeysHelpPanel

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
            try { namesSaveTimer.stop() } catch (_: Throwable) { }
            this.projectDir = File(path).parentFile.absolutePath
            // Load adjustments for this project into the central store
            try { AdjustmentsStore.load(projectDir!!) } catch (_: Throwable) { }
            // Load points from EDL (sorted by startMs)
            val edl = EdlIO.readForProjectDir(projectDir!!)
            points = edl.points.sortedBy { it.startMs }
            // Load outcomes and player names from score.json (ignore orphans)
            outcomesByPointId.clear()
            try {
                val score = ScoreIO.readForProjectDir(projectDir!!)
                // Names
                player1Name = score.player1Name
                player2Name = score.player2Name
                if (::p1NameField.isInitialized && ::p2NameField.isInitialized) {
                    isUpdatingNameFields = true
                    try {
                        p1NameField.text = player1Name
                        p2NameField.text = player2Name
                    } finally { isUpdatingNameFields = false }
                }
                refreshNameDependentUi()
                // Outcomes
                val validIds = points.map { it.id }.toSet()
                score.outcomes.forEach { (id, out) -> if (id in validIds) outcomesByPointId[id] = out }
            } catch (_: Throwable) { /* keep defaults and empty on error */ }
            rebuildPointsList()
            autoSelectInitial()
            // Load media from manifest (deferred until component is displayable)
            val manifest = ManifestIO.read(path)
            val src = manifest.sourceVideo
            if (!src.isNullOrBlank() && File(src).exists()) {
                pendingMediaFile = File(src)
                isMediaLoaded = false
                loadErrorShown = false
                ensurePlayerLoaded()
            }
        } catch (t: Throwable) {
            // Keep scaffold visible; in real app show dialog
            t.printStackTrace()
        }
    }

    // Reload points from the project's EDL and refresh UI; invoked on tab activation
    private fun refreshPointsFromProject() {
        try {
            val dir = projectDir ?: return
            // Remember currently selected point id (if any) to restore selection after reload
            val prevSelectedId = if (selectedIndex in points.indices) points[selectedIndex].id else null
            // Re-read EDL and sort points
            val edl = EdlIO.readForProjectDir(dir)
            val newPoints = edl.points.sortedBy { it.startMs }
            points = newPoints
            // Remove outcomes for orphaned point ids (keep existing outcomes for still-valid ids)
            val validIds = newPoints.map { it.id }.toSet()
            val itKeys = outcomesByPointId.keys.iterator()
            while (itKeys.hasNext()) {
                val k = itKeys.next()
                if (!validIds.contains(k)) itKeys.remove()
            }
            // Rebuild list UI and restore selection if possible
            rebuildPointsList()
            val newIndex = prevSelectedId?.let { id -> newPoints.indexOfFirst { it.id == id } } ?: -1
            when {
                newIndex >= 0 -> setSelectedIndex(newIndex, userInitiated = false)
                newPoints.isNotEmpty() && selectedIndex !in newPoints.indices -> autoSelectInitial()
                else -> { /* keep current selection (or none) */ }
            }
        } catch (_: Throwable) { /* ignore refresh errors to avoid breaking UI */ }
    }

    init {
        installKeyBindings()
        background = Color(0x1A, 0x1A, 0x1A)
        layout = BorderLayout()

        // Top toolbar with global actions (E-SC-001 T3)
        val toolbar = ControlsToolbar(actions)
        add(toolbar, BorderLayout.NORTH)

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
                    // Jump to start of current segment and ensure a preview frame is rendered immediately
                    // Some VLC builds keep the canvas black until the first decoded frame is shown.
                    // Nudge by seeking a millisecond forward and back while paused to force a frame render.
                    try {
                        player.pause()
                        player.seek(segStartMs)
                        val maxPlayable = (segEndMs - 1).coerceAtLeast(segStartMs)
                        val nudge = (segStartMs + 1).coerceAtMost(maxPlayable)
                        if (nudge != segStartMs) player.seek(nudge)
                        player.seek(segStartMs)
                    } catch (_: Throwable) { /* ignore preview priming errors */ }
                }
                updatePlayPauseButton()
            }
        }
    }

    // Rebuild left points list from current 'points' and 'scoredPointIds'
    private fun rebuildPointsList() {
        try {
            pointsList.setList(points, scoredPointIds)
            try { if (::nextPointBtn.isInitialized) nextPointBtn.isEnabled = points.isNotEmpty() } catch (_: Throwable) {}
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
        selectedIndex = index
        try { pointsList.setSelectedIndex(index, userInitiated) } catch (_: Throwable) { }
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
            // Hide inline error on no selection
            try { if (::segErrorLabel.isInitialized) segErrorLabel.isVisible = false } catch (_: Throwable) {}
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
        // Update scrub panel to show segment start and end of the point
        segStartLabel.text = Timecode.format(segStartMs).substring(0, 8)
        segNowLabel.text = Timecode.format(segEndMs).substring(0, 8) + "  "
        updateScrubUi(segStartMs)
        // Jump playback to start and pause; focus player when active
        player.pause()
        player.seek(segStartMs)
        // Prime a preview frame to avoid an initial black canvas on some systems
        try {
            val maxPlayable = (segEndMs - 1).coerceAtLeast(segStartMs)
            val nudge = (segStartMs + 1).coerceAtMost(maxPlayable)
            if (nudge != segStartMs) player.seek(nudge)
            player.seek(segStartMs)
        } catch (_: Throwable) { }
        if (isActive) player.component.requestFocusInWindow()
        // Update action buttons based on existing stored outcome and validity
        val valid = segEndMs > segStartMs
        val existing = outcomesByPointId[p.id]
        updateActionButtonsState(enable = valid, selectedOutcome = existing)
        // Inline error indicator for malformed/zero-duration segments (Task 4.16)
        try { if (::segErrorLabel.isInitialized) segErrorLabel.isVisible = !valid } catch (_: Throwable) {}
        // Enable/disable Next based on whether a subsequent point exists
        try { if (::nextPointBtn.isInitialized) nextPointBtn.isEnabled = (selectedIndex + 1) in points.indices } catch (_: Throwable) {}
        // Recompute panels for current selection
        recomputeFrom(selectedIndex)
        pushStateUpdate()
    }

    private fun scrollRowIntoView(index: Int) {
        try { pointsList.scrollIntoView(index) } catch (_: Throwable) { }
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
            // Show the ending time of the point on the right label (not the current playback position)
            segNowLabel.text = Timecode.format(segEndMs).substring(0, 8) + "  "
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
        } else EmptyBorder(4, 8, 4, 8)
    }

    private fun buildLeftListPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BorderLayout()
        panel.background = Color(0x15, 0x15, 0x15)
        panel.border = BorderFactory.createMatteBorder(0, 0, 0, 1, Color(0x48, 0x48, 0x47, 0x33))
        panel.preferredSize = Dimension(240, 0) // Fixed left list width per spec (was ~320 px)

        // Points list component
        pointsList = TimelineSection(actions)
        panel.add(pointsList, BorderLayout.CENTER)

        // Footer buttons and Player Names (Task 4.17)
        val footer = JPanel()
        footer.layout = BoxLayout(footer, BoxLayout.Y_AXIS)
        // Match points list side padding (6px) so buttons align to list width
        footer.border = EmptyBorder(6, 6, 6, 6)
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
            // Make the button stretch to full available width in the footer
            val prefH = b.preferredSize.height
            b.maximumSize = Dimension(Int.MAX_VALUE, prefH)
            b.minimumSize = Dimension(0, prefH)
            b.alignmentX = 0f
            return b
        }

        nextPointBtn = fullButton("Next Point  [R]")
        nextPointBtn.name = "next-point"
        nextPointBtn.accessibleContext.accessibleName = "Next Point"
        nextPointBtn.toolTipText = "R — Next Point"
        nextPointBtn.addActionListener { advanceToNextPoint() }
        nextPointBtn.isEnabled = false
        val manualBtn = fullButton("Manual Marker")
        manualBtn.name = "manual-marker"
        manualBtn.accessibleContext.accessibleName = "Manual Marker"
        manualBtn.foreground = Color(0xFF, 0xFF, 0xFF)
        manualBtn.isEnabled = false
        manualBtn.toolTipText = "Temporarily disabled"
        val settingsBtn = fullButton("Scoreboard Settings")
        settingsBtn.name = "scoreboard-settings"
        settingsBtn.accessibleContext.accessibleName = "Scoreboard Settings"
        settingsBtn.foreground = Color(0xFF, 0xFF, 0xFF)
        settingsBtn.isEnabled = false
        settingsBtn.toolTipText = "Temporarily disabled"

        // Player names panel
        fun nameField(labelText: String): JTextField {
            val tf = JTextField()
            tf.background = Color(0x26, 0x26, 0x26)
            tf.foreground = Color(0xFF, 0xFF, 0xFF)
            tf.caretColor = Color(0xFF, 0xFF, 0xFF)
            tf.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x55), 1),
                EmptyBorder(6, 6, 6, 6)
            )
            tf.maximumSize = Dimension(Int.MAX_VALUE, tf.preferredSize.height)
            tf.alignmentX = 0f
            try { tf.accessibleContext.accessibleName = labelText } catch (_: Throwable) { }
            // Limit to 24 chars
            val doc = tf.document
            if (doc is AbstractDocument) {
                doc.documentFilter = object : DocumentFilter() {
                    @Throws(BadLocationException::class)
                    override fun insertString(fb: FilterBypass, offs: Int, str: String, a: AttributeSet?) {
                        val newLen = fb.document.length + (str.length)
                        if (newLen <= 24) super.insertString(fb, offs, str, a)
                        else {
                            val allowed = 24 - fb.document.length
                            if (allowed > 0) super.insertString(fb, offs, str.substring(0, allowed), a)
                        }
                    }
                    @Throws(BadLocationException::class)
                    override fun replace(fb: FilterBypass, offs: Int, length: Int, str: String?, a: AttributeSet?) {
                        val currentLen = fb.document.length
                        val addLen = str?.length ?: 0
                        val newLen = currentLen - length + addLen
                        if (newLen <= 24) super.replace(fb, offs, length, str, a)
                        else {
                            val allowed = 24 - (currentLen - length)
                            if (allowed > 0 && str != null) super.replace(fb, offs, length, str.substring(0, allowed), a)
                        }
                    }
                }
            }
            return tf
        }
        val p1Label = JLabel("Player 1 name")
        p1Label.foreground = Color(0xAD, 0xAA, 0xAA)
        p1Label.font = p1Label.font.deriveFont(Font.BOLD, 10f)
        p1NameField = nameField("Player 1 name")
        val p2Label = JLabel("Player 2 name")
        p2Label.foreground = Color(0xAD, 0xAA, 0xAA)
        p2Label.font = p2Label.font.deriveFont(Font.BOLD, 10f)
        p2NameField = nameField("Player 2 name")

        fun onNameChanged(isP1: Boolean) {
            if (isUpdatingNameFields) return
            val raw = if (isP1) p1NameField.text else p2NameField.text
            val trimmed = raw.trim()
            if (isP1) player1Name = trimmed else player2Name = trimmed
            refreshNameDependentUi()
            try { namesSaveTimer.restart() } catch (_: Throwable) { autosaveNow() }
        }
        p1NameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onNameChanged(true)
            override fun removeUpdate(e: DocumentEvent) = onNameChanged(true)
            override fun changedUpdate(e: DocumentEvent) = onNameChanged(true)
        })
        p2NameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onNameChanged(false)
            override fun removeUpdate(e: DocumentEvent) = onNameChanged(false)
            override fun changedUpdate(e: DocumentEvent) = onNameChanged(false)
        })

        footer.add(nextPointBtn)
        footer.add(Box.createVerticalStrut(6))
        footer.add(manualBtn)
        footer.add(Box.createVerticalStrut(6))
        footer.add(settingsBtn)
        footer.add(Box.createVerticalStrut(10))
        footer.add(p1Label)
        footer.add(Box.createVerticalStrut(3))
        footer.add(p1NameField)
        footer.add(Box.createVerticalStrut(6))
        footer.add(p2Label)
        footer.add(Box.createVerticalStrut(3))
        footer.add(p2NameField)

        val footerWrap = JPanel(BorderLayout())
        footerWrap.isOpaque = false
        footerWrap.add(footer, BorderLayout.NORTH)
        panel.add(footerWrap, BorderLayout.SOUTH)

        return panel
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
            check.icon = org.litvin.ui.UiStyles.scoredIcon(18)
            row.add(check, BorderLayout.EAST)
        }
        val fixedH = 40
        row.minimumSize = Dimension(0, fixedH)
        row.preferredSize = Dimension(0, fixedH)
        row.maximumSize = Dimension(Int.MAX_VALUE, fixedH)
        row.alignmentX = 0f
        return row
    }


    private fun buildCenterPanel(): JComponent {
        val root = JPanel()
        root.layout = BorderLayout()
        root.isOpaque = true
        root.background = Color(0x0E, 0x0E, 0x0E)

        // Header (E-SC-001 T2)
        headerPanel = MatchHeaderPanel()
        root.add(headerPanel, BorderLayout.NORTH)

        // Video area with overlay
        val videoWrap = JPanel(BorderLayout())
        videoWrap.background = Color.BLACK
        videoWrap.border = EmptyBorder(8, 12, 8, 12)

        val vf = AspectPanel(16.0 / 9.0)
        vf.background = Color(0, 0, 0)
        vf.layout = null // absolute for overlay position managed by AspectPanel.doLayout
        videoFrame = vf

        // Add media player component wrapped into geometry viewport (stretched by AspectPanel)
        val videoComponent = player.component
        try { videoComponent.accessibleContext.accessibleName = "Video player" } catch (_: Throwable) {}
        val viewport = GeometryViewportPanel(videoComponent)
        viewport.name = "video"
        vf.add(viewport)
        // Subscribe to central adjustments store to live-apply color and geometry (Tasks 5.3/5.4)
        try {
            val unsub = AdjustmentsStore.subscribe { adj ->
                try { player.applyColorAdjustments(adj) } catch (_: Throwable) { }
                try { player.applyGeometryAdjustments(adj) } catch (_: Throwable) { }
            }
            // Apply current state immediately
            val currentAdj = try { AdjustmentsStore.get() } catch (_: Throwable) {
                AdjustmentsV1()
            }
            try { player.applyColorAdjustments(currentAdj) } catch (_: Throwable) { }
            try { player.applyGeometryAdjustments(currentAdj) } catch (_: Throwable) { }
            // Store unsubscribe handle on the frame for potential cleanup
            vf.putClientProperty("adj_unsub", unsub)
        } catch (_: Throwable) { }

        // Overlay temporarily removed in v0.1.0. A proper implementation will be added in v0.2.0.
        // No overlay panel/window is created here.

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
        // Inline error for zero-duration/malformed segment (Task 4.16)
        segErrorLabel = JLabel("Invalid point duration — fix on Markup tab")
        segErrorLabel.foreground = Color(0xFF, 0x66, 0x66)
        segErrorLabel.font = segLbl.font.deriveFont(Font.BOLD, 10f)
        segErrorLabel.isVisible = false
        rightBox.add(segErrorLabel)
        scrubSlider = JSlider(0, 100, 0)
        scrubSlider.name = "scrub"
        try { scrubSlider.accessibleContext.accessibleName = "Point segment scrub bar" } catch (_: Throwable) {}
        scrubSlider.toolTipText = "Scrub within the selected point segment"
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
        bottom.background = Color(0x1A, 0x1A, 0x1A)

        val leftPlayer = buildPlayerPanel("Player 1", primary = true)
        val rightPlayer = buildPlayerPanel("Player 2", primary = false)
        val centerControls = buildCenterControls()

        bottom.add(leftPlayer, BorderLayout.WEST)
        bottom.add(centerControls, BorderLayout.CENTER)
        bottom.add(rightPlayer, BorderLayout.EAST)

        // Apply former southWrap border directly to the bottom panel and drop the extra wrapper
        // Preserve previous 10px padding by composing MatteBorder (outer) + EmptyBorder (inner)
        bottom.border = EmptyBorder(10, 20, 20, 20)

        // Attach south after the scrub bar: create a stack (video -> scrub -> bottom)
        val centerStack = JPanel()
        centerStack.layout = BorderLayout()
        centerStack.add(videoWrap, BorderLayout.CENTER)
        centerStack.add(scrubPanel, BorderLayout.SOUTH)

        val centerWithBottom = JPanel(BorderLayout())
        centerWithBottom.add(centerStack, BorderLayout.CENTER)
        centerWithBottom.add(bottom, BorderLayout.SOUTH)

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
        val pointBtn = JToggleButton("Point for $name   " + if (primary) "[Q]" else "[E]")
        pointBtn.isFocusPainted = false
        pointBtn.foreground = if (primary) Color(0x42, 0xA5, 0xF5) else Color(0xEF, 0x53, 0x50)
        pointBtn.background = Color(0x26, 0x26, 0x26)
        pointBtn.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x33), 1),
            EmptyBorder(6, 10, 6, 10)
        )
        try {
            pointBtn.name = if (primary) "p1-point" else "p2-point"
            pointBtn.accessibleContext.accessibleName = if (primary) "Point for Player 1" else "Point for Player 2"
            pointBtn.toolTipText = if (primary) "Q — Point for Player 1" else "E — Point for Player 2"
        } catch (_: Throwable) {}
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
            val btn = object : JToggleButton(buttonText) {
                override fun processMouseEvent(e: java.awt.event.MouseEvent) { /* block mouse to keep read-only */ }
                override fun processKeyEvent(e: java.awt.event.KeyEvent) { /* block keyboard activation */ }
            }
            // Style to match mock: dark surface with thin border; keep enabled for proper colors
            UiStyles.styleSecondary(btn)
            // Keep as read-only indicator but preserve styling colors; leave enabled to avoid LAF greying
            btn.isFocusable = false
            btn.isRequestFocusEnabled = false
            btn.isRolloverEnabled = false
            btn.cursor = Cursor.getDefaultCursor()
            // Slightly smaller padding to fit the compact stat card
            btn.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x33), 1),
                EmptyBorder(6, 8, 6, 8)
            )
            try {
                btn.name = (if (buttonText.contains("Game")) "game-won" else "set-won") + (if (primary) "-p1" else "-p2")
                btn.accessibleContext.accessibleName = (if (buttonText.contains("Game")) "Game Won" else "Set Won") +
                        if (primary) " — Player 1 (computed)" else " — Player 2 (computed)"
                btn.toolTipText = "Computed automatically"
            } catch (_: Throwable) {}
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

    private fun buildCenterControls(): JComponent {
        val wrap = JPanel()
        wrap.layout = BoxLayout(wrap, BoxLayout.Y_AXIS)
        wrap.isOpaque = false

        // Center scoring entry (E-SC-001 T4)
        val entry = ScoreEntryPanel(actions)
        scoreEntryPanel = entry
        try { entry.setPlayerNames(displayNameP1(), displayNameP2()) } catch (_: Throwable) { }
        wrap.add(entry)
        wrap.add(Box.createVerticalStrut(6))

        // Video/timecode sync compact cluster (E-SC-001 T7)
        videoSyncPanel = VideoSyncPanel(actions)
        wrap.add(videoSyncPanel)
        wrap.add(Box.createVerticalStrut(6))

        // Hotkeys help (E-SC-001 T6)
        val hotkeysSupplier = java.util.function.Supplier {
            mapOf(
                "Q" to "Point for ${'$'}{displayNameP1()}",
                "W" to "No Point",
                "E" to "Point for ${'$'}{displayNameP2()}",
                "R" to "Next Point",
                "SPACE" to "Play/Pause",
                "← / →" to "Seek ±1s",
                "Shift+← / Shift+→" to "Seek ±10s",
                "↑ / ↓" to "Change speed"
            )
        }
        hotkeysHelpPanel = HotkeysHelpPanel(hotkeysSupplier)
        wrap.add(hotkeysHelpPanel)

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
        // Delegate to leaf via state propagation
        pushStateUpdate()
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
        // Scoring hotkeys: Q = P1, W = No Point, E = P2 (Task 4.6)
        bind("Q", "scoreP1") { setOutcomeForSelected(Outcome.P1) }
        bind("W", "scoreNone") { setOutcomeForSelected(Outcome.NONE) }
        bind("E", "scoreP2") { setOutcomeForSelected(Outcome.P2) }
        // Next Point navigation (Task 4.10): R advances to next index without auto-play
        bind("R", "nextPoint") { advanceToNextPoint() }
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

    // ===== E-SC-001 T8: State assembly and propagation to leaves =====
    private fun assembleScoringState(): ScoringViewState {
        val idx = if (selectedIndex in points.indices) selectedIndex else -1
        val st = if (idx >= 0 && idx < statesAfterPoint.size) statesAfterPoint[idx]
                 else MatchState(0, 0, 0, 0, 0, 0, null, null, false)
        val setsCompleted: List<SetScoreDto> = if (idx >= 0 && idx < setHistoryAfterPoint.size) {
            setHistoryAfterPoint[idx].map { SetScoreDto(it.p1, it.p2, it.tiebreak) }
        } else emptyList()
        val speed = SessionSettings.toRate(SessionSettings.playbackSpeedIndex)
        val isPlaying = try { player.status() == PlayerStatus.PLAYING } catch (_: Throwable) { false }
        return ScoringViewState(
            player1Name = displayNameP1(),
            player2Name = displayNameP2(),
            serving = null, // serving side TBD; not tracked yet in container
            p1Points = st.p1Pts,
            p2Points = st.p2Pts,
            isTiebreak = st.isTiebreak,
            sets = setsCompleted,
            gamesP1 = st.gamesP1,
            gamesP2 = st.gamesP2,
            selectedPointIndex = if (idx >= 0) idx else null,
            totalPoints = points.size,
            isPlaying = isPlaying,
            speedMultiplier = speed,
        )
    }
    private fun pushStateUpdate() {
        try {
            val state = assembleScoringState()
            try { headerPanel.render(state) } catch (_: Throwable) { }
            try { videoSyncPanel.render(state) } catch (_: Throwable) { }
            try { scoreEntryPanel?.render(state) } catch (_: Throwable) { }
        } catch (_: Throwable) { }
    }

    // ===== Task 4.6 helpers =====
    private fun updateActionButtonsState(enable: Boolean, selectedOutcome: Outcome?) {
        // Enable/disable all action controls
        if (::btnP1.isInitialized) btnP1.isEnabled = enable
        if (::btnNone.isInitialized) btnNone.isEnabled = enable
        if (::btnP2.isInitialized) btnP2.isEnabled = enable
        if (::p1PointBtn.isInitialized) p1PointBtn.isEnabled = enable
        if (::p2PointBtn.isInitialized) p2PointBtn.isEnabled = enable
        // Propagate to extracted ScoreEntryPanel
        scoreEntryPanel?.setActionsEnabled(enable)

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

    // ===== Task 4.13 — Persistence helpers (score.json, save immediately) =====
    private fun scheduleScoreAutosave() {
        // Save immediately on change (no debounce) per 4.13
        autosaveNow()
    }
    private fun autosaveNow() {
        try {
            val dir = projectDir ?: return
            val map = LinkedHashMap(outcomesByPointId) // snapshot
            val s1 = player1Name
            val s2 = player2Name
            ScoreIO.writeForProjectDir(dir, ScoreV1(outcomes = map, version = 1, player1Name = s1, player2Name = s2))
        } catch (t: Throwable) {
            // Non-fatal; show error similarly to Markup autosave
            try { Dialogs.showError(this, t, "Autosave failed") } catch (_: Throwable) { }
        }
    }
    fun saveNow() { // exposed for File -> Save All
        try { autosaveNow() } catch (_: Throwable) { }
    }

    // ===== Task 4.17 — Dynamic labels for scoring buttons based on player names =====
    private fun displayNameP1(): String = player1Name.ifBlank { "Player 1" }
    private fun displayNameP2(): String = player2Name.ifBlank { "Player 2" }

    private fun refreshNameDependentUi() {
        val name1 = displayNameP1()
        val name2 = displayNameP2()
        try {
            if (::p1PointBtn.isInitialized) {
                p1PointBtn.text = "Point for $name1   [Q]"
                p1PointBtn.toolTipText = "Q — Point for $name1"
                try { p1PointBtn.accessibleContext.accessibleName = "Point for $name1" } catch (_: Throwable) {}
            }
            if (::p2PointBtn.isInitialized) {
                p2PointBtn.text = "Point for $name2   [E]"
                p2PointBtn.toolTipText = "E — Point for $name2"
                try { p2PointBtn.accessibleContext.accessibleName = "Point for $name2" } catch (_: Throwable) {}
            }
            if (::btnP1.isInitialized) {
                btnP1.text = "Point for $name1   [Q]"
                btnP1.toolTipText = "Q — Point for $name1"
                try { btnP1.accessibleContext.accessibleName = "Point for $name1" } catch (_: Throwable) {}
            }
            if (::btnP2.isInitialized) {
                btnP2.text = "Point for $name2   [E]"
                btnP2.toolTipText = "E — Point for $name2"
                try { btnP2.accessibleContext.accessibleName = "Point for $name2" } catch (_: Throwable) {}
            }
            scoreEntryPanel?.setPlayerNames(name1, name2)
        } catch (_: Throwable) { }
        pushStateUpdate()
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
        // Overlay temporarily disabled in v0.1.0 pending proper implementation in v0.2.0.
        return
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

    // ===== Overlay window hosting (keep overlay above VLCJ Canvas) =====
    override fun removeNotify() {
        try {
            val unsub = try { videoFrame?.getClientProperty("adj_unsub") } catch (_: Throwable) { null } as? (() -> Unit)
            unsub?.invoke()
            try { videoFrame?.putClientProperty("adj_unsub", null) } catch (_: Throwable) { }
        } catch (_: Throwable) { }
        super.removeNotify()
    }
    private fun ensureOverlayWindow() { }
    private fun updateOverlayWindowBounds() { }

    private fun scheduleOverlayBoundsRetry() { }

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
