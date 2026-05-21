package org.litvin.ui.tabs.scoring

import org.litvin.*
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.ui.commons.Dialogs
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.AspectPanel
import org.litvin.ui.commons.uiSafe

import org.litvin.media.VlcjSwingMediaPlayerAdapter
import org.litvin.media.PlayerStatus
import java.awt.*
import java.awt.event.ActionEvent
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
import javax.swing.SwingUtilities
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.markup.EdlIO
import org.litvin.markup.PointV1
import org.litvin.shared.util.Timecode
import org.litvin.ui.tabs.scoring.ui.ControlsToolbar
import org.litvin.ui.tabs.scoring.ui.TimelineSection
import org.litvin.ui.tabs.scoring.ui.VideoSyncPanel
import org.litvin.ui.tabs.scoring.ui.ScoringHelpDialog

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
        override fun pointWon(side: Side) = uiSafe {
            val outcome = if (side == Side.P1) Outcome.P1 else Outcome.P2
            setOutcomeForSelected(outcome)
        }

        override fun undo() { /* TODO(E-SC-001/T4): hook up undo when available */
        }

        override fun redo() { /* TODO(E-SC-001/T4): hook up redo when available */
        }

        override fun finalizeGame(winner: Side) { /* TODO(E-SC-001/T4): implement when domain is ready */
        }

        override fun finalizeSet(winner: Side) { /* TODO(E-SC-001/T4): implement when domain is ready */
        }

        override fun toggleServe() { /* TODO(E-SC-001/T4): implement when domain is ready */
        }

        override fun navigateToPoint(index: Int) {
            if (index in points.indices) setSelectedIndex(index, userInitiated = true)
        }

        override fun advanceToNextPoint() {
            this@SwingScoringPanel.advanceToNextPoint()
        }

        override fun playPause() {
            togglePlayPause()
        }

        override fun seekBy(milliseconds: Long) {
            this@SwingScoringPanel.seekBy(milliseconds)
        }

        override fun setSpeedMultiplier(multiplier: Float) = uiSafe {
            // Choose closest preset
            val presets = SessionSettings.speedPresets
            var bestIdx = 0
            var bestDiff = Float.MAX_VALUE
            for (i in presets.indices) {
                val d = kotlin.math.abs(presets[i] - multiplier)
                if (d < bestDiff) {
                    bestDiff = d; bestIdx = i
                }
            }
            SessionSettings.playbackSpeedIndex = SessionSettings.clampIndex(bestIdx)
            if (::speedCombo.isInitialized) speedCombo.selectedIndex = SessionSettings.playbackSpeedIndex
            else player.setRate(SessionSettings.toRate(SessionSettings.playbackSpeedIndex))
        }

        override fun saveScore() = uiSafe { saveNow() }
        override fun autosave() = uiSafe { autosaveNow() }
    }

    // Active state controlled by navigation
    private var isActive: Boolean = false

    fun onActivated() = uiSafe {
        isActive = true
        ensurePlayerLoaded()
        player.pause()
        // Refresh points every time the tab is opened to reflect latest Markup changes
        refreshPointsFromProject()
        // Do not auto-play; optionally restore focus
        EventQueue.invokeLater { player.component.requestFocusInWindow() }
    }

    fun onDeactivated() = uiSafe {
        isActive = false
        player.pause()
        // Flush pending autosave when leaving the tab
        autosaveNow()
    }

    // Media player (reuse Markup adapter)
    private val player = VlcjSwingMediaPlayerAdapter()

    // Deferred media loading
    private var pendingMediaFile: File? = null
    private var isMediaLoaded: Boolean = false

    override fun addNotify() = uiSafe {
        super.addNotify()
        SwingUtilities.invokeLater { ensurePlayerLoaded() }
    }

    private fun ensurePlayerLoaded() = uiSafe {
        try {
            if (isMediaLoaded) return@uiSafe
            val videoFile = pendingMediaFile ?: return@uiSafe
            val wnd = SwingUtilities.getWindowAncestor(player.component)
            if (!player.component.isDisplayable || wnd == null || !wnd.isShowing) return@uiSafe
            player.load(videoFile)
            player.pause()
            isMediaLoaded = true
        } catch (t: Throwable) {
            Dialogs.showError(this, t, "Failed to load project")
        }
    }


    // Scrub interaction guard
    private var isScrubUpdatingFromPlayer = false

    private var projectDir: String? = null

    // Data
    private var points: List<PointV1> = emptyList()

    // Outcomes persistence (ScoreV1). "Scored" includes NONE.
    private val outcomesByPointId: MutableMap<String, Outcome> = LinkedHashMap()
    private val scoredPointIds: Set<String>
        get() = outcomesByPointId.keys

    // Autosave timer for names typing debounce (~300 ms)
    private val namesSaveTimer = Timer(300) { _ -> autosaveNow() }.apply { isRepeats = false }

    // Left list UI refs
    private var selectedPointIndex: Int = -1
    private lateinit var pointsListPanel: TimelineSection
    private lateinit var nextPointBtn: JButton

    private var player1Name: String = "Player 1"
    private var player2Name: String = "Player 2"
    private fun displayNameP1(): String = player1Name.ifBlank { "Player 1" }
    private fun displayNameP2(): String = player2Name.ifBlank { "Player 2" }
    private lateinit var p1NameField: JTextField
    private lateinit var p2NameField: JTextField
    private var isUpdatingNameFields: Boolean = false

    // Current selected segment bounds [startMs, endMs)
    private var segmentStartMs: Long = 0L
    private var segmentEndMs: Long = 0L

    // Scrub/UI refs in center
    private lateinit var segmentStartLabel: JLabel
    private lateinit var segmentNowLabel: JLabel
    private lateinit var scrubSlider: JSlider
    private lateinit var segmentErrorLabel: JLabel
    private lateinit var videoFrame: JComponent

    private lateinit var speedCombo: JComboBox<String>

    private lateinit var videoSyncPanel: VideoSyncPanel

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
    fun setProjectManifest(path: String) = uiSafe {
        namesSaveTimer.stop()
        this.projectDir = File(path).parentFile.absolutePath
        // Load adjustments for this project into the central store
        AdjustmentsStore.load(projectDir!!)
        // Load points from EDL (sorted by startMs)
        val edl = EdlIO.readForProjectDir(projectDir!!)
        points = edl.points.sortedBy { it.startMs }
        // Load outcomes and player names from score.json (ignore orphans)
        outcomesByPointId.clear()

        val score = ScoreIO.readForProjectDir(projectDir!!)
        // Names
        player1Name = score.player1Name
        player2Name = score.player2Name
        if (::p1NameField.isInitialized && ::p2NameField.isInitialized) {
            p1NameField.text = player1Name
            p2NameField.text = player2Name
        }
        refreshNameDependentUi()
        // Outcomes
        val validIds = points.map { it.id }.toSet()
        score.outcomes.forEach { (id, out) -> if (id in validIds) outcomesByPointId[id] = out }

        rebuildPointsList()
        autoSelectInitial()
        // Load media from manifest (deferred until component is displayable)
        val manifest = ManifestIO.read(path)
        val src = manifest.sourceVideo
        if (!src.isNullOrBlank() && File(src).exists()) {
            pendingMediaFile = File(src)
            isMediaLoaded = false
            ensurePlayerLoaded()
        }
    }

    // Reload points from the project's EDL and refresh UI; invoked on tab activation
    private fun refreshPointsFromProject() = uiSafe {
        val dir = projectDir ?: return@uiSafe
        // Remember currently selected point id (if any) to restore selection after reload
        val prevSelectedId = if (selectedPointIndex in points.indices) points[selectedPointIndex].id else null
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
            newPoints.isNotEmpty() && selectedPointIndex !in newPoints.indices -> autoSelectInitial()
            else -> { /* keep current selection (or none) */
            }
        }
    }

    init {
        installKeyBindings()
        background = Color(0x1A, 0x1A, 0x1A)
        layout = BorderLayout()

        // Top toolbar with global actions (E-SC-001 T3)
        val toolbar = ControlsToolbar(actions) { showHelpDialog() }
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
                player.setRate(SessionSettings.toRate(SessionSettings.playbackSpeedIndex))
                if (selectedPointIndex in points.indices) {
                    // Jump to start of current segment and ensure a preview frame is rendered immediately
                    // Some VLC builds keep the canvas black until the first decoded frame is shown.
                    // Nudge by seeking a millisecond forward and back while paused to force a frame render.
                    uiSafe {
                        player.pause()
                        player.seek(segmentStartMs)
                        val maxPlayable = (segmentEndMs - 1).coerceAtLeast(segmentStartMs)
                        val nudge = (segmentStartMs + 1).coerceAtMost(maxPlayable)
                        if (nudge != segmentStartMs) player.seek(nudge)
                        player.seek(segmentStartMs)
                    }
                }
                updatePlayPauseButton()
            }
        }
    }

    // Rebuild left points list from current 'points' and 'scoredPointIds'
    private fun rebuildPointsList() = uiSafe {
        pointsListPanel.setList(points, scoredPointIds)
        if (::nextPointBtn.isInitialized) nextPointBtn.isEnabled = points.isNotEmpty()
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
        if (index == selectedPointIndex) {
            if (userInitiated) scrollRowIntoView(index)
            return
        }
        selectedPointIndex = index
        pointsListPanel.setSelectedIndex(index, userInitiated)
        onSelectionChanged()
    }

    private fun onSelectionChanged() = uiSafe {
        if (selectedPointIndex !in points.indices) {
            // Reset scrub labels
            segmentStartMs = 0L
            segmentEndMs = 0L
            segmentStartLabel.text = "00:00:00"
            segmentNowLabel.text = "00:00:00  "
            scrubSlider.value = 0
            // Hide inline error on no selection
            if (::segmentErrorLabel.isInitialized) segmentErrorLabel.isVisible = false
            updateActionButtonsState(enable = false, selectedOutcome = null)
            // Disable Next on no selection or empty list
            if (::nextPointBtn.isInitialized) nextPointBtn.isEnabled = false
            // Clear bottom panels and overlay
            val zero = MatchState(0, 0, 0, 0, 0, 0, null, null, false)
            updateBottomPanels(zero)
            return@uiSafe
        }
        val p = points[selectedPointIndex]
        // Update segment bounds
        segmentStartMs = p.startMs.toLong()
        segmentEndMs = p.endMs.toLong()
        // Update scrub panel to show segment start and end of the point
        segmentStartLabel.text = Timecode.format(segmentStartMs).substring(0, 8)
        segmentNowLabel.text = Timecode.format(segmentEndMs).substring(0, 8) + "  "
        updateScrubUi(segmentStartMs)
        // Jump playback to start and pause; focus player when active
        player.pause()
        player.seek(segmentStartMs)
        // Prime a preview frame to avoid an initial black canvas on some systems

        val maxPlayable = (segmentEndMs - 1).coerceAtLeast(segmentStartMs)
        val nudge = (segmentStartMs + 1).coerceAtMost(maxPlayable)
        if (nudge != segmentStartMs) player.seek(nudge)
        player.seek(segmentStartMs)

        if (isActive) player.component.requestFocusInWindow()
        // Update action buttons based on existing stored outcome and validity
        val valid = segmentEndMs > segmentStartMs
        val existing = outcomesByPointId[p.id]
        updateActionButtonsState(enable = valid, selectedOutcome = existing)
        // Inline error indicator for malformed/zero-duration segments (Task 4.16)
        if (::segmentErrorLabel.isInitialized) segmentErrorLabel.isVisible = !valid
        // Enable/disable Next based on whether a subsequent point exists
        if (::nextPointBtn.isInitialized) nextPointBtn.isEnabled = (selectedPointIndex + 1) in points.indices
        // Recompute panels for current selection
        recomputeFrom(selectedPointIndex)
        pushStateUpdate()
    }

    private fun scrollRowIntoView(index: Int) {
        pointsListPanel.scrollIntoView(index)
    }

    // Task 4.10 — Next Point navigation
    private fun advanceToNextPoint() = uiSafe {
        if (selectedPointIndex !in points.indices) return@uiSafe
        val next = selectedPointIndex + 1
        if (next !in points.indices) {
            if (::nextPointBtn.isInitialized) nextPointBtn.isEnabled = false
            return@uiSafe
        }
        // Keep playback paused and just move selection
        player.pause()
        setSelectedIndex(next, userInitiated = false)
        // Focus should remain in the player area for Space/arrows to work
        EventQueue.invokeLater { uiSafe { player.component.requestFocusInWindow() } }
    }

    private fun onPlayerTimeChanged(absMs: Long) {
        // If we have a valid segment, clamp playback to [start, end)
        if (segmentEndMs > segmentStartMs) {
            val maxPlayable = (segmentEndMs - 1).coerceAtLeast(segmentStartMs)
            when {
                absMs >= segmentEndMs -> {
                    // Pause at end and clamp to last playable millisecond
                    player.pause()
                    player.seek(maxPlayable)
                    updateScrubUi(maxPlayable)
                    return
                }

                absMs < segmentStartMs -> {
                    // Clamp to start if an external seek went before the segment
                    player.seek(segmentStartMs)
                    updateScrubUi(segmentStartMs)
                    return
                }
            }
        }
        updateScrubUi(absMs)
    }

    private fun updateScrubUi(absMs: Long) {
        if (segmentEndMs > segmentStartMs) {
            val rel = (absMs - segmentStartMs).coerceAtLeast(0L)
            val dur = (segmentEndMs - segmentStartMs).coerceAtLeast(1L)
            val frac = (rel.toDouble() / dur.toDouble()).coerceIn(0.0, 0.999)
            val sliderVal = (frac * 100).toInt().coerceIn(0, 100)
            isScrubUpdatingFromPlayer = true
            try {
                scrubSlider.value = sliderVal
            } finally {
                isScrubUpdatingFromPlayer = false
            }
            // Show the ending time of the point on the right label (not the current playback position)
            segmentNowLabel.text = Timecode.format(segmentEndMs).substring(0, 8) + "  "
        } else {
            isScrubUpdatingFromPlayer = true
            try {
                scrubSlider.value = 0
            } finally {
                isScrubUpdatingFromPlayer = false
            }
            segmentNowLabel.text = "00:00:00  "
        }
    }

    private fun buildLeftListPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BorderLayout()
        panel.background = Color(0x15, 0x15, 0x15)
        panel.border = BorderFactory.createMatteBorder(0, 0, 0, 1, Color(0x48, 0x48, 0x47, 0x33))
        panel.preferredSize = Dimension(240, 0) // Fixed left list width per spec (was ~320 px)

        // Points list component
        pointsListPanel = TimelineSection(actions)
        panel.add(pointsListPanel, BorderLayout.CENTER)

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
                            if (allowed > 0 && str != null) super.replace(
                                fb,
                                offs,
                                length,
                                str.substring(0, allowed),
                                a
                            )
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
            try {
                namesSaveTimer.restart()
            } catch (_: Throwable) {
                autosaveNow()
            }
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

    fun videoPanel(): JPanel {
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
        val viewport = GeometryViewportPanel(videoComponent)
        viewport.name = "video"
        vf.add(viewport)

        videoWrap.add(vf, BorderLayout.CENTER)

        return videoWrap
    }

    private fun scrubPanel() : JPanel {

        val scrubPanel = JPanel(BorderLayout(8, 0))
        scrubPanel.border = EmptyBorder(8, 16, 8, 16)
        scrubPanel.background = Color(0x11, 0x11, 0x11)

        segmentStartLabel = JLabel("00:00:00")
        segmentStartLabel.font = Font(Font.MONOSPACED, Font.PLAIN, 10)
        segmentStartLabel.foreground = Color(0xAD, 0xAA, 0xAA)

        scrubSlider = JSlider(0, 100, 0)
        scrubSlider.name = "scrub"
        scrubSlider.toolTipText = "Scrub within the selected point segment"
        scrubSlider.background = scrubPanel.background
        scrubSlider.addChangeListener(ChangeListener {
            if (isScrubUpdatingFromPlayer) return@ChangeListener
            if (segmentEndMs <= segmentStartMs) return@ChangeListener
            val frac = scrubSlider.value / 100.0
            var target = segmentStartMs + ((segmentEndMs - segmentStartMs) * frac).toLong()
            val maxPlayable = (segmentEndMs - 1).coerceAtLeast(segmentStartMs)
            if (target > maxPlayable) target = maxPlayable
            player.pause() // seeking pauses per Scoring 4.3 (no auto-advance)
            player.seek(target)
            updateScrubUi(target)
        })

        val segLbl = JLabel("Point segment")
        segLbl.font = segLbl.font.deriveFont(9f)
        segLbl.foreground = Color(150, 150, 150)

        segmentNowLabel = JLabel("00:00:00  ")
        segmentNowLabel.font = Font(Font.MONOSPACED, Font.PLAIN, 10)
        segmentNowLabel.foreground = Color(0xAD, 0xAA, 0xAA)

        val rightBox = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        rightBox.isOpaque = false
        rightBox.add(segmentNowLabel)
        rightBox.add(segLbl)
        // Inline error for zero-duration/malformed segment (Task 4.16)
        segmentErrorLabel = JLabel("Invalid point duration — fix on Markup tab")
        segmentErrorLabel.foreground = Color(0xFF, 0x66, 0x66)
        segmentErrorLabel.font = segLbl.font.deriveFont(Font.BOLD, 10f)
        segmentErrorLabel.isVisible = false
        rightBox.add(segmentErrorLabel)

        scrubPanel.add(segmentStartLabel, BorderLayout.WEST)
        scrubPanel.add(scrubSlider, BorderLayout.CENTER)
        scrubPanel.add(rightBox, BorderLayout.EAST)
        return scrubPanel
    }

    private fun buildCenterPanel(): JComponent {
        val centerStack = JPanel()
        centerStack.layout = BorderLayout()
        centerStack.add(videoPanel(), BorderLayout.CENTER)
        centerStack.add(scrubPanel(), BorderLayout.SOUTH)

        val centerWithBottom = JPanel(BorderLayout())
        centerWithBottom.add(centerStack, BorderLayout.CENTER)
        centerWithBottom.add(bottomPanel(), BorderLayout.SOUTH)

        return centerWithBottom
    }

    fun bottomPanel(): JPanel {
        // Bottom controls & player panels
        val bottom = JPanel(BorderLayout())
        bottom.background = Color(0x1A, 0x1A, 0x1A)

        val leftPlayer = buildPlayerPanel(displayNameP1(), primary = true)
        val rightPlayer = buildPlayerPanel(displayNameP2(), primary = false)
        val centerControls = buildCenterControls()

        bottom.add(leftPlayer, BorderLayout.WEST)
        bottom.add(centerControls, BorderLayout.CENTER)
        bottom.add(rightPlayer, BorderLayout.EAST)

        // Apply former southWrap border directly to the bottom panel and drop the extra wrapper
        // Preserve previous 10px padding by composing MatteBorder (outer) + EmptyBorder (inner)
        bottom.border = EmptyBorder(10, 20, 20, 20)
        return bottom
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
        pointBtn.name = if (primary) "p1-point" else "p2-point"
        pointBtn.toolTipText = if (primary) "Q — Point for Player 1" else "E — Point for Player 2"

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

        fun smallStatPanelDyn(
            title: String,
            buttonText: String,
            assign: (JLabel, JToggleButton) -> Unit
        ): JComponent {
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
                override fun processMouseEvent(e: java.awt.event.MouseEvent) { /* block mouse to keep read-only */
                }

                override fun processKeyEvent(e: java.awt.event.KeyEvent) { /* block keyboard activation */
                }
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
            btn.name = (if (buttonText.contains("Game")) "game-won" else "set-won") + (if (primary) "-p1" else "-p2")
            btn.toolTipText = "Computed automatically"

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
            "Game Won"
        ) { label, toggle ->
            if (primary) {
                p1GamesVal = label; p1GameWonToggle = toggle
            } else {
                p2GamesVal = label; p2GameWonToggle = toggle
            }
        }
        val setsPanel = smallStatPanelDyn(
            "SETS",
            "Set Won"
        ) { label, toggle ->
            if (primary) {
                p1SetsVal = label; p1SetWonToggle = toggle
            } else {
                p2SetsVal = label; p2SetWonToggle = toggle
            }
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

        wrap.add(Box.createVerticalStrut(46))

        // Video/timecode sync compact cluster (E-SC-001 T7)
        videoSyncPanel = VideoSyncPanel(actions)
        wrap.add(videoSyncPanel)
        wrap.add(Box.createVerticalStrut(6))

        return wrap
    }

    private fun showHelpDialog() {
        val dlg = ScoringHelpDialog(this, { displayNameP1() }, { displayNameP2() })
        dlg.isVisible = true
    }

    private fun isTextEditingFocus(): Boolean {
        return try {
            val fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            (fo is javax.swing.text.JTextComponent) && fo.isEnabled && fo.isVisible && fo.isEditable
        } catch (_: Throwable) {
            false
        }
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
        if (segmentEndMs > segmentStartMs) {
            val maxPlayable = (segmentEndMs - 1).coerceAtLeast(segmentStartMs)
            if (target < segmentStartMs) target = segmentStartMs
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
        bind("shift LEFT", "seekLeft10s") { seekBy(-5_000) }
        bind("shift RIGHT", "seekRight10s") { seekBy(5_000) }
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
        // Help dialog (v0.3.1): F1 opens contextual help
        bind("F1", "openHelp") { showHelpDialog() }
    }

    private fun isSpeedComboFocus(): Boolean {
        return try {
            val fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            fo != null && (fo === speedCombo || SwingUtilities.isDescendingFrom(fo, speedCombo))
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPlayerAreaFocus(): Boolean {
        return try {
            val fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            val comp = player.component
            fo != null && (fo === comp || SwingUtilities.isDescendingFrom(fo, comp))
        } catch (_: Throwable) {
            false
        }
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
        val idx = if (selectedPointIndex in points.indices) selectedPointIndex else -1
        val st = if (idx >= 0 && idx < statesAfterPoint.size) statesAfterPoint[idx]
        else MatchState(0, 0, 0, 0, 0, 0, null, null, false)
        val setsCompleted: List<SetScoreDto> = if (idx >= 0 && idx < setHistoryAfterPoint.size) {
            setHistoryAfterPoint[idx].map { SetScoreDto(it.p1, it.p2, it.tiebreak) }
        } else emptyList()
        val speed = SessionSettings.toRate(SessionSettings.playbackSpeedIndex)
        val isPlaying = player.status() == PlayerStatus.PLAYING
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
        val state = assembleScoringState()
        videoSyncPanel.render(state)
    }

    private fun updateActionButtonsState(enable: Boolean, selectedOutcome: Outcome?) {
        p1PointBtn.isEnabled = enable
        p2PointBtn.isEnabled = enable

        // Reflect stored outcome as pressed/selected state (Task 4.11)
        val sel = selectedOutcome
        val p1 = sel == Outcome.P1
        val none = sel == Outcome.NONE
        val p2 = sel == Outcome.P2

        p1PointBtn.model.isSelected = p1
        p2PointBtn.model.isSelected = p2
    }

    private fun setOutcomeForSelected(outcome: Outcome) {
        if (selectedPointIndex !in points.indices) return
        // Validate segment duration per 4.6 disabled state rule
        if (segmentEndMs <= segmentStartMs) return
        val p = points[selectedPointIndex]
        outcomesByPointId[p.id] = outcome
        updateActionButtonsState(enable = true, selectedOutcome = outcome)
        // Refresh left list counts and checkmarks
        val keepIndex = selectedPointIndex
        rebuildPointsList()
        setSelectedIndex(keepIndex, userInitiated = false)
        scheduleScoreAutosave()
        recomputeFrom(keepIndex)

        EventQueue.invokeLater {
            player.component.requestFocusInWindow()
        }
    }

    private var statesAfterPoint: MutableList<MatchState> = mutableListOf()
    private var setHistoryAfterPoint: MutableList<List<SetScore>> = mutableListOf()

    private fun scheduleScoreAutosave() {
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
            Dialogs.showError(this, t, "Autosave failed")
        }
    }

    fun saveNow() { // exposed for File -> Save All
        autosaveNow()
    }

    private fun refreshNameDependentUi() {
        val name1 = displayNameP1()
        val name2 = displayNameP2()

        if (::p1PointBtn.isInitialized) {
            p1PointBtn.text = "Point for $name1   [Q]"
            p1PointBtn.toolTipText = "Q — Point for $name1"
        }
        if (::p2PointBtn.isInitialized) {
            p2PointBtn.text = "Point for $name2   [E]"
            p2PointBtn.toolTipText = "E — Point for $name2"
        }

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
                    if (p1Pts > p2Pts) completedSets.add(SetScore(7, 6, true)) else completedSets.add(
                        SetScore(
                            6,
                            7,
                            true
                        )
                    )
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
                    result[i] =
                        MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, null, lastSetWonBy, isTiebreak)
                    setsPerPoint[i] = completedSets.toList()
                    // After set win via tiebreak we reset pts/isTiebreak above; snapshot next loop will reflect reset
                } else {
                    if (outcome == Outcome.P1) p1Pts++ else p2Pts++
                    val gw = checkRegularGameWin()
                    if (gw != null) {
                        addRegularGame(gw)
                        result[i] =
                            MatchState(p1Pts, p2Pts, gamesP1, gamesP2, setsP1, setsP2, gw, lastSetWonBy, isTiebreak)
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
        } else {
            val zero = MatchState(0, 0, 0, 0, 0, 0, null, null, false)
            updateBottomPanels(zero)
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
        if (::p1PointsVal.isInitialized) p1PointsVal.text =
            displayPoints(true, state.p1Pts, state.p2Pts, state.isTiebreak)
        if (::p2PointsVal.isInitialized) p2PointsVal.text =
            displayPoints(false, state.p1Pts, state.p2Pts, state.isTiebreak)
        if (::p1GamesVal.isInitialized) p1GamesVal.text = state.gamesP1.toString()
        if (::p2GamesVal.isInitialized) p2GamesVal.text = state.gamesP2.toString()
        if (::p1SetsVal.isInitialized) p1SetsVal.text = state.setsP1.toString()
        if (::p2SetsVal.isInitialized) p2SetsVal.text = state.setsP2.toString()
        if (::p1GameWonToggle.isInitialized) p1GameWonToggle.model.isSelected = state.lastGameWonBy == 1
        if (::p2GameWonToggle.isInitialized) p2GameWonToggle.model.isSelected = state.lastGameWonBy == 2
        if (::p1SetWonToggle.isInitialized) p1SetWonToggle.model.isSelected = state.lastSetWonBy == 1
        if (::p2SetWonToggle.isInitialized) p2SetWonToggle.model.isSelected = state.lastSetWonBy == 2
    }

    // ===== Overlay window hosting (keep overlay above VLCJ Canvas) =====
    override fun removeNotify() {
        videoFrame.putClientProperty("adj_unsub", null)
        super.removeNotify()
    }
}
