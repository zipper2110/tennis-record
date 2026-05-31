package org.litvin.ui.tabs.scoring

import org.litvin.GeometryViewportPanel
import org.litvin.ManifestIO
import org.litvin.SessionSettings
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.markup.EdlIO
import org.litvin.markup.PointV1
import org.litvin.media.PlayerStatus
import org.litvin.media.VlcjSwingMediaPlayerAdapter
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoringEngine
import org.litvin.scoring.ScoringEngine.MatchState
import org.litvin.scoring.ScoringEngine.SetScore
import org.litvin.ui.commons.AspectPanel
import org.litvin.ui.commons.Dialogs
import org.litvin.ui.commons.uiSafe
import org.litvin.ui.tabs.scoring.ui.*
import java.awt.BorderLayout
import java.awt.Color
import java.awt.EventQueue
import java.awt.KeyboardFocusManager
import java.awt.event.ActionEvent
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder

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

    private val videoPlayerActions = object : VideoPlayerActions {
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
            player.setRate(SessionSettings.toRate(SessionSettings.playbackSpeedIndex))
            updateVideoControls()
        }

        override fun setFrameStepEnabled(enabled: Boolean) = uiSafe {
            SessionSettings.frameStepWhenPaused = enabled
            updateVideoControls()
            // After toggling via checkbox, return focus to player so hotkeys keep working
            EventQueue.invokeLater { player.component.requestFocusInWindow() }
        }
    }

    private val navigationActions = object : NavigationActions {
        override fun navigateToPoint(index: Int) {
            if (index in points.indices) setSelectedIndex(index, userInitiated = true)
        }

        override fun advanceToNextPoint() {
            this@SwingScoringPanel.advanceToNextPoint()
        }
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
        saveNow()
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
            // Re-apply current color adjustments after media is loaded to ensure VLC picks them up
            try {
                player.applyColorAdjustments(AdjustmentsStore.get())
            } catch (_: Throwable) { /* ignore */ }
        } catch (t: Throwable) {
            Dialogs.showError(this, t, "Failed to load project")
        }
    }



    private var projectDir: String? = null

    // Data
    private var points: List<PointV1> = emptyList()

    // Player colors (hex)
    private var player1ColorHex: String = "#4DA3FF"
    private var player2ColorHex: String = "#FF6B6B"

    // Outcomes persistence (ScoreV1). "Scored" includes NONE.
    private val outcomesByPointId: MutableMap<String, Outcome> = LinkedHashMap()
    private val scoredPointIds: Set<String>
        get() = outcomesByPointId.keys

    // Autosave timer for names typing debounce (~300 ms)
    private val namesSaveTimer = Timer(300) { _ -> saveNow() }.apply { isRepeats = false }

    // Left list UI refs
    private var selectedPointIndex: Int = -1
    private lateinit var leftListPanel: org.litvin.ui.tabs.scoring.ui.LeftListPanel

    private var player1Name: String = "Player 1"
    private var player2Name: String = "Player 2"
    private fun displayNameP1(): String = player1Name.ifBlank { "Player 1" }
    private fun displayNameP2(): String = player2Name.ifBlank { "Player 2" }

    // Current selected segment bounds [startMs, endMs)
    private var segmentStartMs: Long = 0L
    private var segmentEndMs: Long = 0L

    // Scrub/UI refs in center
    private lateinit var segmentScrub: ScrubPanel
    private lateinit var videoFrame: JComponent

    // Speed combo is now encapsulated within VideoSyncPanel; no direct reference here

    private lateinit var videoSyncPanel: VideoSyncPanel

    // Bottom panels — extracted into reusable PlayerPanel component
    private lateinit var leftPlayerPanel: PlayerPanel
    private lateinit var rightPlayerPanel: PlayerPanel

    // Computed match state snapshot provided by ScoringEngine
    // See ScoringEngine.MatchState and ScoringEngine.SetScore

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
        // Colors
        player1ColorHex = score.player1ColorHex
        player2ColorHex = score.player2ColorHex
        if (::leftListPanel.isInitialized) {
            leftListPanel.setPlayerNames(player1Name, player2Name)
            leftListPanel.setPlayerColors(player1ColorHex, player2ColorHex)
        }
        // Apply colors to point buttons if panels are already created
        if (::leftPlayerPanel.isInitialized) leftPlayerPanel.setAccentColorHex(player1ColorHex)
        if (::rightPlayerPanel.isInitialized) rightPlayerPanel.setAccentColorHex(player2ColorHex)
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
        val toolbar = ControlsToolbar { showHelpDialog() }
        add(toolbar, BorderLayout.NORTH)

        // Root content: left list (fixed width) + center content
        leftListPanel = org.litvin.ui.tabs.scoring.ui.LeftListPanel(navigationActions,
            onNamesChanged = { p1, p2 ->
                player1Name = p1
                player2Name = p2
                refreshNameDependentUi()
                try { namesSaveTimer.restart() } catch (_: Throwable) { saveNow() }
            },
            onColorsChanged = { c1, c2 ->
                player1ColorHex = c1
                player2ColorHex = c2
                // Apply immediately to point buttons
                if (::leftPlayerPanel.isInitialized) leftPlayerPanel.setAccentColorHex(player1ColorHex)
                if (::rightPlayerPanel.isInitialized) rightPlayerPanel.setAccentColorHex(player2ColorHex)
                try { namesSaveTimer.restart() } catch (_: Throwable) { saveNow() }
            }
        )
        val centerPanel = buildCenterPanel()

        add(leftListPanel, BorderLayout.WEST)
        add(centerPanel, BorderLayout.CENTER)

        // Wire media callbacks for segment clamping and UI updates
        player.onTimeChanged = { t ->
            EventQueue.invokeLater { onPlayerTimeChanged(t) }
        }
        player.onStatusChanged = {
            EventQueue.invokeLater { updateVideoControls() }
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
                updateVideoControls()
            }
        }
    }

    // Rebuild left points list from current 'points' and 'scoredPointIds'
    private fun rebuildPointsList() = uiSafe {
        leftListPanel.setList(points, scoredPointIds)
        leftListPanel.setNextEnabled(points.isNotEmpty())
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
        // Avoid undesired auto-scrolling on user click; only scroll programmatically.
        if (index == selectedPointIndex) {
            // No-op on reselect; do not trigger any scrolling.
            return
        }
        selectedPointIndex = index
        // Always pass userInitiated = false to LeftListPanel to prevent its internal auto-scroll.
        leftListPanel.setSelectedIndex(index, false)
        // When selection is changed programmatically (keyboard/auto-advance), ensure it's visible.
        if (!userInitiated) scrollRowIntoView(index)
        onSelectionChanged()
    }

    private fun onSelectionChanged() = uiSafe {
        if (selectedPointIndex !in points.indices) {
            // Reset scrub labels
            segmentStartMs = 0L
            segmentEndMs = 0L
            if (::segmentScrub.isInitialized) segmentScrub.reset()
            updateActionButtonsState(enable = false, selectedOutcome = null)
            // Disable Next on no selection or empty list
            if (::leftListPanel.isInitialized) leftListPanel.setNextEnabled(false)
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
        if (::segmentScrub.isInitialized) segmentScrub.setSegment(segmentStartMs, segmentEndMs)
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
        // Enable/disable Next based on whether a subsequent point exists
        if (::leftListPanel.isInitialized) leftListPanel.setNextEnabled((selectedPointIndex + 1) in points.indices)
        // Recompute panels for current selection
        updateScore(selectedPointIndex)
        updateVideoControls()
    }

    private fun scrollRowIntoView(index: Int) {
        leftListPanel.scrollIntoView(index)
    }

    // Task 4.10 — Next Point navigation
    private fun advanceToNextPoint() = uiSafe {
        if (selectedPointIndex !in points.indices) return@uiSafe
        val next = selectedPointIndex + 1
        if (next !in points.indices) {
            if (::leftListPanel.isInitialized) leftListPanel.setNextEnabled(false)
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
        segmentScrub.setPosition(absMs)
    }

    fun videoPanel(): JPanel {
        // Video area with overlay
        val videoWrapper = JPanel(BorderLayout())
        videoWrapper.background = Color.BLACK
        videoWrapper.border = EmptyBorder(8, 12, 8, 12)

        val videoPanel = AspectPanel(16.0 / 9.0)
        videoPanel.background = Color(0, 0, 0)
        videoPanel.layout = null // absolute for overlay position managed by AspectPanel.doLayout
        videoFrame = videoPanel
        videoWrapper.add(videoPanel, BorderLayout.CENTER)

        // Add media player component wrapped into geometry viewport (stretched by AspectPanel)
        val viewport = GeometryViewportPanel(player.component)
        viewport.name = "video"
        videoPanel.add(viewport)

        // Apply color adjustments from the central store (parity with Markup tab)
        val adjUnsub = AdjustmentsStore.subscribe { adj ->
            player.applyColorAdjustments(adj)
        }
        // Apply current adjustments immediately
        try {
            player.applyColorAdjustments(AdjustmentsStore.get())
        } catch (_: Throwable) { /* ignore */ }
        // Keep unsubscribe handle on the viewport for potential cleanup on removal
        viewport.putClientProperty("adj_unsub", adjUnsub)

        return videoWrapper
    }

    private fun buildCenterPanel(): JComponent {
        val centerStack = JPanel()
        centerStack.layout = BorderLayout()
        centerStack.add(videoPanel(), BorderLayout.CENTER)
        segmentScrub = ScrubPanel { target ->
            player.pause() // seeking pauses per Scoring 4.3 (no auto-advance)
            player.seek(target)
            updateScrubUi(target)
        }
        centerStack.add(segmentScrub, BorderLayout.SOUTH)

        val centerWithBottom = JPanel(BorderLayout())
        centerWithBottom.add(centerStack, BorderLayout.CENTER)
        centerWithBottom.add(bottomPanel(), BorderLayout.SOUTH)

        return centerWithBottom
    }

    fun bottomPanel(): JPanel {
        // Bottom controls & player panels
        val bottom = JPanel(BorderLayout())
        bottom.background = Color(0x1A, 0x1A, 0x1A)

        leftPlayerPanel = PlayerPanel(true) { setOutcomeForSelectedPoint(Outcome.P1) }
        leftPlayerPanel.setPlayerName(displayNameP1())
        leftPlayerPanel.setAccentColorHex(player1ColorHex)
        rightPlayerPanel = PlayerPanel(false) { setOutcomeForSelectedPoint(Outcome.P2) }
        rightPlayerPanel.setPlayerName(displayNameP2())
        rightPlayerPanel.setAccentColorHex(player2ColorHex)
        videoSyncPanel = VideoSyncPanel(videoPlayerActions) { setOutcomeForSelectedPoint(Outcome.NONE) }

        bottom.add(leftPlayerPanel, BorderLayout.WEST)
        bottom.add(videoSyncPanel, BorderLayout.CENTER)
        bottom.add(rightPlayerPanel, BorderLayout.EAST)

        // Apply former southWrap border directly to the bottom panel and drop the extra wrapper
        // Preserve previous 10px padding by composing MatteBorder (outer) + EmptyBorder (inner)
        bottom.border = EmptyBorder(10, 20, 20, 20)
        return bottom
    }

    // buildPlayerPanel extracted into org.litvin.ui.tabs.scoring.ui.PlayerPanel

    private fun showHelpDialog() {
        val dlg = ScoringHelpDialog(this, { displayNameP1() }, { displayNameP2() })
        dlg.isVisible = true
    }

    private fun togglePlayPause() {
        val wasPlaying = player.status() == PlayerStatus.PLAYING
        if (wasPlaying) player.pause() else player.play()
        updateVideoControls()
        player.component.requestFocusInWindow()
    }

    private fun updateVideoControls() {
        val state = getState()
        videoSyncPanel.render(state.isPlaying, state.speedMultiplier)
        // Sync frame-step checkbox from session setting
        videoSyncPanel.setFrameStepEnabled(SessionSettings.frameStepWhenPaused)
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
        // Arrow keys:
        // Default (frameStepWhenPaused == false): Left/Right = ±1s regardless of paused state; Shift = ±5s.
        // When frameStepWhenPaused == true: if paused → Left/Right step 1 frame; if playing → ±1s.
        bind("LEFT", "seekLeftOrPrevFrame") {
            val paused = player.status() == PlayerStatus.PAUSED
            if (SessionSettings.frameStepWhenPaused && paused) {
                // Approximate previous frame
                try { player.stepFrameBackward() } catch (_: Throwable) { /* ignore */ }
                updateScrubUi(player.currentTimeMs())
            } else {
                seekBy(-1_000)
            }
        }
        bind("RIGHT", "seekRightOrNextFrame") {
            val paused = player.status() == PlayerStatus.PAUSED
            if (SessionSettings.frameStepWhenPaused && paused) {
                try { player.stepFrameForward() } catch (_: Throwable) { /* ignore */ }
                updateScrubUi(player.currentTimeMs())
            } else {
                seekBy(1_000)
            }
        }
        bind("shift LEFT", "seekLeft5s") { seekBy(-5_000) }
        bind("shift RIGHT", "seekRight5s") { seekBy(5_000) }
        // Speed control via keyboard: Up/Down when player area has focus
        bind("UP", "speedUp") {
            if (!isPlayerAreaFocus()) return@bind
            changeSpeedBy(-1) // presets ordered high→low; Up means go to higher preset → lower index
        }
        bind("DOWN", "speedDown") {
            if (!isPlayerAreaFocus()) return@bind
            changeSpeedBy(1)
        }
        // Scoring hotkeys: Q = P1, W = No Point, E = P2 (Task 4.6)
        bind("Q", "scoreP1") { setOutcomeForSelectedPoint(Outcome.P1) }
        bind("W", "scoreNone") { setOutcomeForSelectedPoint(Outcome.NONE) }
        bind("E", "scoreP2") { setOutcomeForSelectedPoint(Outcome.P2) }
        // Next Point navigation (Task 4.10): R advances to next index without auto-play
        bind("R", "nextPoint") { advanceToNextPoint() }
        // Help dialog (v0.3.1): F1 opens contextual help
        bind("F1", "openHelp") { showHelpDialog() }
        // Frame-by-frame toggle: F
        bind("F", "toggleFrameStep") { videoPlayerActions.setFrameStepEnabled(!SessionSettings.frameStepWhenPaused) }
    }

    private fun isTextEditingFocus(): Boolean {
        return try {
            val fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            (fo is javax.swing.text.JTextComponent) && fo.isEnabled && fo.isVisible && fo.isEditable
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
        val current = SessionSettings.playbackSpeedIndex
        val next = SessionSettings.clampIndex(current + delta)
        if (next != current) {
            SessionSettings.playbackSpeedIndex = next
            player.setRate(SessionSettings.toRate(next))
            updateVideoControls()
        }
    }

    private var statesAfterPoint: MutableList<MatchState> = mutableListOf()
    private var setHistoryAfterPoint: MutableList<List<SetScore>> = mutableListOf()

    private fun getState(): ScoringViewState {
        val idx = if (selectedPointIndex in points.indices) selectedPointIndex else -1
        val score = if (idx >= 0 && idx < statesAfterPoint.size) statesAfterPoint[idx] else MatchState.INITIAL

        val setsCompleted: List<SetScoreDto> = if (idx >= 0 && idx < setHistoryAfterPoint.size) {
            setHistoryAfterPoint[idx].map { SetScoreDto(it.p1, it.p2, it.tiebreak) }
        } else emptyList()
        val speed = SessionSettings.toRate(SessionSettings.playbackSpeedIndex)
        val isPlaying = player.status() == PlayerStatus.PLAYING

        return ScoringViewState(
            player1Name = displayNameP1(),
            player2Name = displayNameP2(),
            serving = null, // serving side TBD; not tracked yet in container
            p1Points = score.p1Pts,
            p2Points = score.p2Pts,
            isTiebreak = score.isTiebreak,
            sets = setsCompleted,
            gamesP1 = score.gamesP1,
            gamesP2 = score.gamesP2,
            selectedPointIndex = if (idx >= 0) idx else null,
            totalPoints = points.size,
            isPlaying = isPlaying,
            speedMultiplier = speed,
        )
    }

    private fun updateActionButtonsState(enable: Boolean, selectedOutcome: Outcome?) {
        if (::leftPlayerPanel.isInitialized) leftPlayerPanel.setPointButtonEnabled(enable)
        if (::rightPlayerPanel.isInitialized) rightPlayerPanel.setPointButtonEnabled(enable)
        if (::videoSyncPanel.isInitialized) videoSyncPanel.setNoPointEnabled(enable)

        val sel = selectedOutcome
        val p1 = sel == Outcome.P1
        val p2 = sel == Outcome.P2
        val none = sel == Outcome.NONE
        if (::leftPlayerPanel.isInitialized) leftPlayerPanel.setPointSelected(p1)
        if (::rightPlayerPanel.isInitialized) rightPlayerPanel.setPointSelected(p2)
        if (::videoSyncPanel.isInitialized) videoSyncPanel.setNoPointSelected(none)
    }

    private fun setOutcomeForSelectedPoint(outcome: Outcome) {
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
        updateScore(keepIndex)

        EventQueue.invokeLater {
            player.component.requestFocusInWindow()
        }
    }

    private fun scheduleScoreAutosave() {
        saveNow()
    }

    fun saveNow() {
        try {
            val dir = projectDir ?: return
            val map = LinkedHashMap(outcomesByPointId) // snapshot
            val s1 = player1Name
            val s2 = player2Name
            val c1 = player1ColorHex
            val c2 = player2ColorHex
            ScoreIO.writeForProjectDir(dir, ScoreV1(outcomes = map, version = 1, player1Name = s1, player2Name = s2, player1ColorHex = c1, player2ColorHex = c2))
        } catch (t: Throwable) {
            // Non-fatal; show error similarly to Markup autosave
            Dialogs.showError(this, t, "Autosave failed")
        }
    }

    private fun refreshNameDependentUi() {
        val name1 = displayNameP1()
        val name2 = displayNameP2()
        if (::leftPlayerPanel.isInitialized) leftPlayerPanel.setPlayerName(name1)
        if (::rightPlayerPanel.isInitialized) rightPlayerPanel.setPlayerName(name2)
    }

    private fun updateScore(index: Int) {
        // Delegate pure computation to ScoringEngine
        val (states, setsPerPoint) = ScoringEngine.computeTimeline(points, outcomesByPointId)
        statesAfterPoint = states
        setHistoryAfterPoint = setsPerPoint

        // Apply side-effects (UI) based on current selection
        if (index in points.indices) {
            updateBottomPanels(statesAfterPoint[index])
        } else {
            updateBottomPanels(MatchState.INITIAL)
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
        val p1PtsDisp = displayPoints(true, state.p1Pts, state.p2Pts, state.isTiebreak)
        val p2PtsDisp = displayPoints(false, state.p1Pts, state.p2Pts, state.isTiebreak)
        if (::leftPlayerPanel.isInitialized) {
            leftPlayerPanel.render(
                pointsDisplay = p1PtsDisp,
                games = state.gamesP1,
                sets = state.setsP1,
                gameWon = state.lastGameWonBy == 1,
                setWon = state.lastSetWonBy == 1,
            )
        }
        if (::rightPlayerPanel.isInitialized) {
            rightPlayerPanel.render(
                pointsDisplay = p2PtsDisp,
                games = state.gamesP2,
                sets = state.setsP2,
                gameWon = state.lastGameWonBy == 2,
                setWon = state.lastSetWonBy == 2,
            )
        }
    }
}
