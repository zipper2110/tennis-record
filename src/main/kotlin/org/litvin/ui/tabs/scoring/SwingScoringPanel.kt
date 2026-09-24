package org.litvin.ui.tabs.scoring

import org.litvin.GeometryViewportPanel
import org.litvin.OverlaySpan
import org.litvin.ScoreboardComponent
import org.litvin.ScoreboardDisplay
import org.litvin.ScoreboardTimelineBuilder
import org.litvin.projects.ManifestIO
import org.litvin.SessionSettings
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.export.scoreboard.ScoreboardAss
import org.litvin.export.scoreboard.ScoreboardLayouts
import org.litvin.media.PlayerStatus
import org.litvin.media.VideoOverlay
import org.litvin.media.mpv.MpvSwingMediaPlayerAdapter
import org.litvin.media.SwingMediaPlayer
import org.litvin.scoring.ManualScoreMarks
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoringEngine
import org.litvin.scoring.ScoringEngine.MatchState
import org.litvin.scoring.ScoringEngine.SetScore
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.AspectPanel
import org.litvin.ui.commons.SwingUserDialogService
import org.litvin.ui.commons.UserDialogService
import org.litvin.ui.commons.uiSafe
import org.litvin.ui.commons.AppShortcuts
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
 * - Left points list with header badges, the current point counter, and footer buttons
 *   (Next/Previous Point, Score Settings, Scoreboard Style)
 * - Center video area with a scoreboard overlay placeholder
 * - Per-point scrub bar under the video
 * - Bottom area (ScoringControlsPanel): outcome buttons row (P1 / No Point / P2), then games/sets cards
 *   around the transport and speed controls
 *
 * No data wiring or persistence yet. All actions are no-ops for v0.1.0 task 4.1.
 *
 * v0.3.0 — E-SC-001 (T1): Component map and contracts draft
 * Componentization target (leaves composed by this container):
 * - entry/ScoreEntryPanel — primary scoring inputs (points, undo/redo)
 * - timeline/TimelineSection — wraps existing PointsListPanel
 * - video/VideoSyncPanel — basic video/timecode sync controls for scoring
 *
 * Shared contracts for wiring (defined in `org.litvin.ui.tabs.scoring`):
 * - `ScoringActions` — view-to-domain/container commands (pointWon, undo, redo, toggleServe, navigate, play/seek, save)
 * - `ScoringViewState` — immutable snapshot consumed by leaves (names, serving, points/games/sets, selection, player status)
 *
 * SwingScoringPanel remains the container that composes leaves and binds them to domain services,
 * while leaves depend only on the above contracts, per architecture rules.
 */
class SwingScoringPanel(
    private val player: SwingMediaPlayer,
    private val adjustments: AdjustmentsSession,
    private val dialogs: UserDialogService,
    private val styleDefaults: ScoreboardStyleDefaults = ScoreboardStyleDefaults.NONE,
    private val scoreSettingsEditor: ScoreSettingsEditor = ScoreSettingsDialog,
) : JPanel(BorderLayout()), AutoCloseable {
    constructor() : this(
        MpvSwingMediaPlayerAdapter(),
        AdjustmentsStore.legacySession(),
        SwingUserDialogService(),
    )

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

        override fun goToPreviousPoint() {
            this@SwingScoringPanel.goToPreviousPoint()
        }

        override fun toggleFavorite(index: Int) {
            this@SwingScoringPanel.toggleFavorite(index)
            EventQueue.invokeLater { player.component.requestFocusInWindow() }
        }
    }

    // Active state controlled by navigation
    private var isActive: Boolean = false
    private var disposed: Boolean = false

    fun onActivated() = uiSafe {
        isActive = true
        ensurePlayerLoaded()
        player.activatePreview("scoring activated")
        player.pause()
        // Refresh points every time the tab is opened to reflect latest Points tab changes
        refreshPointsFromProject()
        // Do not auto-play; optionally restore focus
        EventQueue.invokeLater { player.component.requestFocusInWindow() }
        promptScoreSettingsOnFirstVisit()
    }

    fun onDeactivated() = uiSafe {
        isActive = false
        player.pause()
        player.deactivatePreview("scoring deactivated")
        // Flush pending autosave when leaving the tab
        saveNow()
    }

    /** Release Swing and native-player resources; safe to call more than once. */
    override fun close() {
        if (disposed) return
        disposed = true
        onDeactivated()
        unsubscribeAdjustments?.invoke()
        unsubscribeAdjustments = null
        player.onTimeChanged = null
        player.onStatusChanged = null
        player.onReady = null
        player.setPreviewOverlay(null)
        adjustments.flush()
        player.close()
    }

    fun dispose() = close()

    // Media player (reuse Points tab adapter)
    private var unsubscribeAdjustments: (() -> Unit)? = null

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
            refreshVideoScoreboardOverlay()
            // Re-apply current adjustments after media is loaded so the player picks them up
            try {
                val current = adjustments.get()
                player.applyPreviewAdjustments(current)
                if (::geometryViewport.isInitialized) {
                    geometryViewport.refreshGeometry()
                }
            } catch (_: Throwable) { /* ignore */ }
        } catch (t: Throwable) {
            dialogs.showError(this, t.message ?: t.toString(), "Failed to load project")
        }
    }



    private var projectDir: String? = null

    // Data
    private var points: List<PointV1> = emptyList()

    private var scoreboardSettings = ScoreboardSettingsV1()
    private var rules = MatchRulesV1()
    private val manualGameWins: MutableMap<String, Outcome> = LinkedHashMap()
    private val manualSetWins: MutableMap<String, Outcome> = LinkedHashMap()
    private val manualMarks: ManualScoreMarks
        get() = ManualScoreMarks(LinkedHashMap(manualGameWins), LinkedHashMap(manualSetWins))

    // False until the score settings open once for this project (see promptScoreSettingsOnFirstVisit)
    private var scoreSettingsReviewed = true

    // Settings that the open settings dialog shows on the video before the user saves them.
    private var scoreboardPreviewSettings: ScoreboardSettingsV1? = null
    private var lastScoreboardDisplay: ScoreboardDisplay? = null

    private var player1ColorHex: String = "#4DA3FF"
    private var player2ColorHex: String = "#FF6B6B"

    // Outcomes persistence (ScoreV1). "Scored" includes NONE.
    private val outcomesByPointId: MutableMap<String, Outcome> = LinkedHashMap()
    private val scoredPointIds: Set<String>
        get() = outcomesByPointId.keys

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
    private lateinit var geometryViewport: GeometryViewportPanel

    // Bottom panel: outcome buttons, player points/games/sets, and video controls
    private lateinit var controlsPanel: ScoringControlsPanel

    // Computed match state snapshot provided by ScoringEngine
    // See ScoringEngine.MatchState and ScoringEngine.SetScore

    // Wire project and media
    fun setProjectManifest(path: String) = uiSafe {
        this.projectDir = File(path).parentFile.absolutePath
        // Load adjustments for this project into the central store
        adjustments.load(projectDir!!)
        // Load points from EDL (sorted by startMs)
        val edl = EdlIO.readForProjectDir(projectDir!!)
        points = edl.points.sortedBy { it.startMs }
        // Load outcomes and player names from score.json (ignore orphans)
        outcomesByPointId.clear()
        manualGameWins.clear()
        manualSetWins.clear()

        val isNewScore = !ScoreIO.existsForProjectDir(projectDir!!)
        val score = ScoreIO.readForProjectDir(projectDir!!)
        // Names
        player1Name = score.player1Name
        player2Name = score.player2Name
        // Colors
        player1ColorHex = score.player1ColorHex
        player2ColorHex = score.player2ColorHex
        // A new project starts with the scoreboard style that the user saved last
        scoreboardSettings = if (isNewScore) styleDefaults.load() ?: score.scoreboard else score.scoreboard
        rules = score.rules.normalized()
        scoreSettingsReviewed = score.scoreSettingsReviewed
        applyPlayerSettingsToUi()
        // Outcomes and manual game/set marks
        val validIds = points.map { it.id }.toSet()
        score.outcomes.forEach { (id, out) -> if (id in validIds) outcomesByPointId[id] = out }
        score.manualGameWins.forEach { (id, winner) -> if (id in validIds) manualGameWins[id] = winner }
        score.manualSetWins.forEach { (id, winner) -> if (id in validIds) manualSetWins[id] = winner }
        // Keep the default style in the project, so that the Export tab uses it too
        if (isNewScore) saveNow()

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
        refreshVideoScoreboardOverlay()
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
        outcomesByPointId.keys.retainAll(validIds)
        manualGameWins.keys.retainAll(validIds)
        manualSetWins.keys.retainAll(validIds)
        // Rebuild list UI and restore selection if possible
        rebuildPointsList()
        val newIndex = prevSelectedId?.let { id -> newPoints.indexOfFirst { it.id == id } } ?: -1
        when {
            newIndex >= 0 -> setSelectedIndex(newIndex, userInitiated = false)
            newPoints.isNotEmpty() && selectedPointIndex !in newPoints.indices -> autoSelectInitial()
            else -> { /* keep current selection (or none) */
            }
        }
        refreshVideoScoreboardOverlay()
    }

    init {
        installKeyBindings()
        background = Color(0x1A, 0x1A, 0x1A)
        layout = BorderLayout()

        // Root content: left list (fixed width) + center content
        leftListPanel = LeftListPanel(
            navigationActions,
            onScoreSettings = { openScoreSettings() },
            onScoreboardStyle = { openScoreboardStyle() },
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
                    // The canvas stays black until the first decoded frame is shown.
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
                refreshVideoScoreboardOverlay()
            }
        }
    }

    // Rebuild left points list from current 'points' and 'scoredPointIds'
    private fun rebuildPointsList() = uiSafe {
        leftListPanel.setList(points, outcomesByPointId, player1ColorHex, player2ColorHex, rules, manualMarks)
        leftListPanel.setNextEnabled(points.isNotEmpty())
        leftListPanel.setPreviousEnabled(selectedPointIndex > 0)
        updateCurrentPointHeader()
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

    private fun setSelectedIndex(index: Int, userInitiated: Boolean, autoPlay: Boolean = false) {
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
        onSelectionChanged(autoPlay)
    }

    private fun onSelectionChanged(autoPlay: Boolean = false) = uiSafe {
        if (selectedPointIndex !in points.indices) {
            // Reset scrub labels
            segmentStartMs = 0L
            segmentEndMs = 0L
            updateCurrentPointHeader()
            if (::segmentScrub.isInitialized) segmentScrub.reset()
            updateActionButtonsState(enable = false, selectedOutcome = null)
            if (::controlsPanel.isInitialized) controlsPanel.setManualScoring(rules.manualScoring, enabled = false)
            // Disable Next/Previous on no selection or empty list
            if (::leftListPanel.isInitialized) {
                leftListPanel.setNextEnabled(false)
                leftListPanel.setPreviousEnabled(false)
            }
            player.setPreviewOverlay(null)
            // Clear bottom panels and overlay
            val zero = MatchState(0, 0, 0, 0, 0, 0, null, null, false)
            updateBottomPanels(zero)
            return@uiSafe
        }
        val p = points[selectedPointIndex]
        updateCurrentPointHeader()
        // Update segment bounds
        segmentStartMs = p.startMs.toLong()
        segmentEndMs = p.endMs.toLong()
        // Update scrub panel to show segment start and end of the point
        if (::segmentScrub.isInitialized) segmentScrub.setSegment(segmentStartMs, segmentEndMs)
        updateScrubUi(segmentStartMs)
        refreshVideoScoreboardOverlay()
        // Jump playback to start; callers can opt into autoplay after the preview frame is primed.
        player.pause()
        player.seek(segmentStartMs)
        // Prime a preview frame to avoid an initial black canvas on some systems
        if (autoPlay) player.play()

        if (isActive) player.component.requestFocusInWindow()
        // Update action buttons based on existing stored outcome and validity
        val valid = segmentEndMs > segmentStartMs
        val existing = outcomesByPointId[p.id]
        updateActionButtonsState(enable = valid, selectedOutcome = existing)
        if (::controlsPanel.isInitialized) controlsPanel.setManualScoring(rules.manualScoring, enabled = true)
        // Enable/disable Next/Previous based on whether an adjacent point exists
        if (::leftListPanel.isInitialized) {
            leftListPanel.setNextEnabled((selectedPointIndex + 1) in points.indices)
            leftListPanel.setPreviousEnabled((selectedPointIndex - 1) in points.indices)
        }
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
        setSelectedIndex(next, userInitiated = false, autoPlay = true)
        // Focus should remain in the player area for Space/arrows to work
        EventQueue.invokeLater { uiSafe { player.component.requestFocusInWindow() } }
    }

    // Mirror of advanceToNextPoint for stepping back through the list
    private fun goToPreviousPoint() = uiSafe {
        if (selectedPointIndex !in points.indices) return@uiSafe
        val previous = selectedPointIndex - 1
        if (previous !in points.indices) {
            if (::leftListPanel.isInitialized) leftListPanel.setPreviousEnabled(false)
            return@uiSafe
        }
        setSelectedIndex(previous, userInitiated = false, autoPlay = true)
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
        geometryViewport = GeometryViewportPanel(player.component)
        geometryViewport.name = "video"
        videoPanel.add(geometryViewport)

        // Apply adjustments from the central store (parity with Points/Color tabs)
        unsubscribeAdjustments?.invoke()
        unsubscribeAdjustments = adjustments.subscribe { adj ->
            player.applyPreviewAdjustments(adj)
            geometryViewport.refreshGeometry()
        }
        // Apply current adjustments immediately
        try {
            val current = adjustments.get()
            player.applyPreviewAdjustments(current)
            geometryViewport.refreshGeometry()
        } catch (_: Throwable) { /* ignore */ }

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

    private fun updateCurrentPointHeader() = uiSafe {
        if (!::leftListPanel.isInitialized) return@uiSafe
        val point = points.getOrNull(selectedPointIndex)
        if (point == null) {
            leftListPanel.setCurrentPoint(-1, points.size, favorite = false)
        } else {
            leftListPanel.setCurrentPoint(selectedPointIndex, points.size, point.favorite)
        }
    }

    fun bottomPanel(): JPanel {
        controlsPanel = ScoringControlsPanel(
            videoPlayerActions,
            onOutcome = { outcome -> setOutcomeForSelectedPoint(outcome) },
            onManualGameWon = { winner -> toggleManualMark(manualGameWins, winner) },
            onManualSetWon = { winner -> toggleManualMark(manualSetWins, winner) },
        )
        controlsPanel.player1.setPlayerName(displayNameP1())
        controlsPanel.player1.setAccentColorHex(player1ColorHex)
        controlsPanel.player2.setPlayerName(displayNameP2())
        controlsPanel.player2.setAccentColorHex(player2ColorHex)
        return controlsPanel
    }

    private fun togglePlayPause() {
        val wasPlaying = player.status() == PlayerStatus.PLAYING
        if (wasPlaying) {
            player.pause()
        } else {
            if (shouldRestartSegmentForPlay()) {
                player.seek(segmentStartMs)
                updateScrubUi(segmentStartMs)
            }
            player.play()
        }
        updateVideoControls()
        player.component.requestFocusInWindow()
    }

    private fun shouldRestartSegmentForPlay(): Boolean {
        if (segmentEndMs <= segmentStartMs) return false
        val maxPlayable = (segmentEndMs - 1).coerceAtLeast(segmentStartMs)
        return player.currentTimeMs() >= maxPlayable
    }

    private fun updateVideoControls() {
        val state = getState()
        controlsPanel.videoSync.render(state.isPlaying, state.speedMultiplier)
        // Sync frame-step checkbox from session setting
        controlsPanel.videoSync.setFrameStepEnabled(SessionSettings.frameStepWhenPaused)
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
        bind(AppShortcuts.PLAY_PAUSE.keyStroke, "togglePlayPause") { togglePlayPause() }
        // Arrow keys:
        // Default (frameStepWhenPaused == false): Left/Right = ±1s regardless of paused state; Shift = ±5s.
        // When frameStepWhenPaused == true: if paused → Left/Right step 1 frame; if playing → ±1s.
        bind(AppShortcuts.LEFT.keyStroke, "seekLeftOrPrevFrame") {
            val paused = player.status() == PlayerStatus.PAUSED
            if (SessionSettings.frameStepWhenPaused && paused) {
                val target = player.stepFrameBackward(segmentStartMs)
                updateScrubUi(target)
            } else {
                seekBy(-1_000)
            }
        }
        bind(AppShortcuts.RIGHT.keyStroke, "seekRightOrNextFrame") {
            val paused = player.status() == PlayerStatus.PAUSED
            if (SessionSettings.frameStepWhenPaused && paused) {
                val maxPlayable = (segmentEndMs - 1).coerceAtLeast(segmentStartMs)
                val target = player.stepFrameForward(maxPlayable)
                updateScrubUi(target)
            } else {
                seekBy(1_000)
            }
        }
        bind(AppShortcuts.SHIFT_LEFT.keyStroke, "seekLeft5s") { seekBy(-5_000) }
        bind(AppShortcuts.SHIFT_RIGHT.keyStroke, "seekRight5s") { seekBy(5_000) }
        // Speed control via keyboard: Up/Down when player area has focus
        bind(AppShortcuts.UP.keyStroke, "speedUp") {
            if (!isPlayerAreaFocus()) return@bind
            changeSpeedBy(-1) // presets ordered high→low; Up means go to higher preset → lower index
        }
        bind(AppShortcuts.DOWN.keyStroke, "speedDown") {
            if (!isPlayerAreaFocus()) return@bind
            changeSpeedBy(1)
        }
        // Scoring hotkeys: Q = P1, W = No Point, E = P2 (Task 4.6)
        bind(AppShortcuts.SCORE_PLAYER_1.keyStroke, "scoreP1") { setOutcomeForSelectedPoint(Outcome.P1) }
        bind(AppShortcuts.SCORE_NO_POINT.keyStroke, "scoreNone") { setOutcomeForSelectedPoint(Outcome.NONE) }
        bind(AppShortcuts.SCORE_PLAYER_2.keyStroke, "scoreP2") { setOutcomeForSelectedPoint(Outcome.P2) }
        // Next Point navigation (Task 4.10): R advances to next index and starts playback
        bind(AppShortcuts.NEXT_POINT.keyStroke, "nextPoint") { advanceToNextPoint() }
        // Shift+R steps back to the previous point
        bind(AppShortcuts.PREVIOUS_POINT.keyStroke, "previousPoint") { goToPreviousPoint() }
        bind(AppShortcuts.TOGGLE_FAVORITE.keyStroke, "toggleFavorite") { toggleFavoriteSelectedPoint() }
        // Frame-by-frame toggle: F
        bind(AppShortcuts.TOGGLE_FRAME_STEP.keyStroke, "toggleFrameStep") {
            videoPlayerActions.setFrameStepEnabled(!SessionSettings.frameStepWhenPaused)
        }
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

    private fun toggleFavoriteSelectedPoint() {
        toggleFavorite(selectedPointIndex)
    }

    private fun toggleFavorite(index: Int) = uiSafe {
        if (index !in points.indices) return@uiSafe
        val dir = projectDir ?: return@uiSafe
        val pointId = points[index].id
        val updated = points.map { p ->
            if (p.id == pointId) p.copy(favorite = !p.favorite) else p
        }.sortedBy { it.startMs }
        try {
            EdlIO.writeForProjectDir(dir, EdlV1(points = updated, version = 1))
            points = updated
            rebuildPointsList()
        } catch (t: Throwable) {
            dialogs.showError(this, t.message ?: t.toString(), "Failed to save favorite")
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
        if (!::controlsPanel.isInitialized) return
        controlsPanel.setOutcomeButtonsEnabled(enable)
        controlsPanel.setSelectedOutcome(selectedOutcome)
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
        refreshVideoScoreboardOverlay()

        EventQueue.invokeLater {
            player.component.requestFocusInWindow()
        }
    }

    /** Manual scoring: marks [winner] in [marks] for the selected point, or clears the mark when it is already there. */
    private fun toggleManualMark(marks: MutableMap<String, Outcome>, winner: Outcome) = uiSafe {
        if (!rules.manualScoring) return@uiSafe
        val point = points.getOrNull(selectedPointIndex) ?: return@uiSafe
        if (marks[point.id] == winner) marks.remove(point.id) else marks[point.id] = winner
        rebuildPointsList()
        leftListPanel.setSelectedIndex(selectedPointIndex, false)
        scheduleScoreAutosave()
        updateScore(selectedPointIndex)
        refreshVideoScoreboardOverlay()
        EventQueue.invokeLater { player.component.requestFocusInWindow() }
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
            ScoreIO.writeForProjectDir(dir, ScoreV1(
                    outcomes = map,
                    version = 1,
                    player1Name = s1,
                    player2Name = s2,
                    player1ColorHex = c1,
                    player2ColorHex = c2,
                    scoreboard = scoreboardSettings,
                    rules = rules,
                    manualGameWins = LinkedHashMap(manualGameWins),
                    manualSetWins = LinkedHashMap(manualSetWins),
                    scoreSettingsReviewed = scoreSettingsReviewed,
                ),
            )
        } catch (t: Throwable) {
            // Non-fatal; show error similarly to Points tab autosave
            dialogs.showError(this, t.message ?: t.toString(), "Autosave failed")
        }
    }

    /** Shows the player names and colors on the points list and the controls. */
    private fun applyPlayerSettingsToUi() {
        if (::leftListPanel.isInitialized) leftListPanel.setPlayerNames(displayNameP1(), displayNameP2())
        if (::controlsPanel.isInitialized) {
            controlsPanel.player1.setPlayerName(displayNameP1())
            controlsPanel.player2.setPlayerName(displayNameP2())
            controlsPanel.player1.setAccentColorHex(player1ColorHex)
            controlsPanel.player2.setAccentColorHex(player2ColorHex)
        }
    }

    /** Opens the score settings automatically the first time the user opens this tab for a project. */
    private fun promptScoreSettingsOnFirstVisit() {
        if (projectDir == null || scoreSettingsReviewed) return
        EventQueue.invokeLater {
            if (isActive && projectDir != null && !scoreSettingsReviewed) openScoreSettings()
        }
    }

    /** Opens the score settings: player names and colors, the match format, and manual scoring. */
    private fun openScoreSettings() = uiSafe {
        if (projectDir == null) return@uiSafe
        // Set the flag first, so that a second activation does not open the dialog again
        scoreSettingsReviewed = true
        val current = ScoreSettings(
            player1Name = player1Name,
            player2Name = player2Name,
            player1ColorHex = player1ColorHex,
            player2ColorHex = player2ColorHex,
            rules = rules,
        )
        val result = scoreSettingsEditor.edit(this, current)
        if (result != null) {
            player1Name = result.player1Name
            player2Name = result.player2Name
            player1ColorHex = result.player1ColorHex
            player2ColorHex = result.player2ColorHex
            rules = result.rules.normalized()
            applyPlayerSettingsToUi()
            rebuildPointsList()
            leftListPanel.setSelectedIndex(selectedPointIndex, false)
            if (::controlsPanel.isInitialized) {
                controlsPanel.setManualScoring(rules.manualScoring, enabled = selectedPointIndex in points.indices)
            }
            updateScore(selectedPointIndex)
            refreshVideoScoreboardOverlay()
        }
        saveNow()
        EventQueue.invokeLater { player.component.requestFocusInWindow() }
    }

    /**
     * Opens the scoreboard style. The video shows each change at once; Cancel restores the saved style.
     * A saved style also becomes the user's default style for new projects.
     */
    private fun openScoreboardStyle() = uiSafe {
        val sample = lastScoreboardDisplay ?: ScoreboardComponent.display(
            OverlaySpan(
                startMs = 0L,
                endMs = 1L,
                text = "",
                p1Name = displayNameP1(),
                p2Name = displayNameP2(),
                p1ColorHex = player1ColorHex,
                p2ColorHex = player2ColorHex,
                p1Pts = 3,
                p2Pts = 1,
                gamesP1 = 4,
                gamesP2 = 3,
                completedSets = listOf(6 to 4),
            ),
        )
        val result = try {
            ScoreboardSettingsDialog.show(this, scoreboardSettings, sample) { preview ->
                scoreboardPreviewSettings = preview
                refreshVideoScoreboardOverlay()
            }
        } finally {
            scoreboardPreviewSettings = null
        }
        if (result != null) {
            scoreboardSettings = result
            saveNow()
            try {
                styleDefaults.save(result)
            } catch (_: Throwable) {
                // The default style is a convenience; the project keeps its own style.
            }
        }
        refreshVideoScoreboardOverlay()
        EventQueue.invokeLater { player.component.requestFocusInWindow() }
    }

    private fun refreshVideoScoreboardOverlay() {
        if (points.isEmpty() || selectedPointIndex !in points.indices) {
            player.setPreviewOverlay(null)
            return
        }

        try {
            val spans = ScoreboardTimelineBuilder.buildSourcePointSpans(
                points = points,
                outcomes = outcomesByPointId,
                player1Name = displayNameP1(),
                player2Name = displayNameP2(),
                player1ColorHex = player1ColorHex,
                player2ColorHex = player2ColorHex,
                rules = rules,
                manualMarks = manualMarks,
            )
            val span = spans.getOrNull(selectedPointIndex)
            if (span == null) {
                player.setPreviewOverlay(null)
                return
            }
            val display = ScoreboardComponent.display(span)
            lastScoreboardDisplay = display
            val settings = scoreboardPreviewSettings ?: scoreboardSettings
            val scene = ScoreboardLayouts.scene(display, settings)
            player.setPreviewOverlay(VideoOverlay { area ->
                val placement = ScoreboardAss.place(scene, settings, area.x, area.y, area.width, area.height)
                ScoreboardAss.events(scene, placement)
            })
        } catch (_: Throwable) {
            // Preview overlay is best-effort; scoring/export data remains authoritative.
        }
    }

    private fun updateScore(index: Int) {
        // Delegate pure computation to ScoringEngine
        val (states, setsPerPoint) = ScoringEngine.computeTimeline(points, outcomesByPointId, rules, manualMarks)
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
        if (::controlsPanel.isInitialized) {
            controlsPanel.player1.render(
                pointsDisplay = p1PtsDisp,
                games = state.gamesP1,
                sets = state.setsP1,
                gameWon = state.lastGameWonBy == 1,
                setWon = state.lastSetWonBy == 1,
            )
        }
        if (::controlsPanel.isInitialized) {
            controlsPanel.player2.render(
                pointsDisplay = p2PtsDisp,
                games = state.gamesP2,
                sets = state.setsP2,
                gameWon = state.lastGameWonBy == 2,
                setWon = state.lastSetWonBy == 2,
            )
        }
    }
}
