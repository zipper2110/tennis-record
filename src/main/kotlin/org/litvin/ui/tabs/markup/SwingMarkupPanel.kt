package org.litvin.ui.tabs.markup

import org.litvin.GeometryViewportPanel
import org.litvin.projects.ManifestIO
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.markup.EdlIO
import org.litvin.markup.EdlV1
import org.litvin.markup.PointV1
import org.litvin.markup.components.MarkupDispatcher
import org.litvin.media.PlayerStatus
import org.litvin.media.VlcjSwingMediaPlayerAdapter
import org.litvin.shared.util.Timecode
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.Dialogs
import org.litvin.ui.tabs.markup.ui.SwingTimelineComponent
import org.litvin.ui.tabs.markup.components.Keybindings
import org.litvin.ui.tabs.markup.components.MarkupKeyActions
import org.litvin.ui.tabs.markup.ui.PointsCardsView
import org.litvin.ui.tabs.markup.ui.TransportControls
import java.awt.*
import java.io.File
import javax.swing.*
import kotlin.math.max

/**
 * Phase 3 — Swing Markup editor panel.
 *
 * Features implemented to meet Phase 3 acceptance:
 * - Custom timeline component drawing VIDEO and MARKS tracks, with playhead bound to media time
 * - Bind timeline to media time via VLCJ Swing adapter callbacks; smooth playhead updates
 * - EDL interactions via MarkupDispatcher: Start(C)/End(V) auto-create, delete, inline edit via table
 * - Keyboard mappings: Space, C, V, Delete, Left/Right and Shift+Arrows; context menu on table rows
 * - Basic autosave of EDL (edl.json) with 300 ms debounce
 *
 */
class SwingMarkupPanel : JPanel(BorderLayout()) {

    // Geometry viewport wrapper for VLC component
    private var geometryViewport: GeometryViewportPanel

    // Media
    private val player = VlcjSwingMediaPlayerAdapter()

    // Deferred media loading
    private var pendingMediaFile: File? = null
    private var isMediaLoaded: Boolean = false
    private var loadErrorShown: Boolean = false

    // EDL Dispatcher
    private val dispatcher = MarkupDispatcher()

    // Current project manifest path (projectDir derived from it)
    private var manifestPath: String? = null
    private var projectDir: String? = null

    // UI controls
    private var transport: TransportControls

    private val countBadge = JLabel("0 MARKED / 0 FAV")

    // Cards view (replaces legacy inline cards list)
    private var cardsView: PointsCardsView
    private var selectedVisualIndex: Int = -1 // visual index within composed list (pending at 0 when present)
    private var reloadingPointsFromProject: Boolean = false

    // Consolidated keybindings helper
    private var keybindings: Keybindings? = null

    // Fixed sizing constants (right panel/cards)
    private val RIGHT_PANEL_WIDTH = 340

    // Idle UI refresher to keep time label and timeline handle in sync when paused/seeking
    private val idleUiTimer = Timer(100) { _ ->
        if (player.status() != PlayerStatus.PLAYING) {
            refreshUiAtCurrentTime()
        }
    }.apply { isRepeats = true }

    // Lifecycle hooks controlled by navigation
    fun onActivated() {
        refreshPointsFromProject()
        ensurePlayerLoaded()
        player.activatePreview("markup activated")
        player.pause()

        // Do not auto-play; optionally restore focus to player area
        EventQueue.invokeLater {
            player.component.requestFocusInWindow()
        }
    }

    fun onDeactivated() {
        saveNow()
        player.pause()
        player.deactivatePreview("markup deactivated")
    }

    private fun ensurePlayerLoaded() {
        try {
            if (isMediaLoaded) return
            val f = pendingMediaFile ?: return
            val wnd = SwingUtilities.getWindowAncestor(player.component)

            if (!player.component.isDisplayable || wnd == null || !wnd.isShowing) return
            player.load(f)
            player.pause()
            isMediaLoaded = true
            // Re-apply current adjustments after media is loaded to ensure VLC picks them up
            try {
                val current = AdjustmentsStore.get()
                player.applyPreviewAdjustments(current)
                geometryViewport.refreshGeometry()
            } catch (_: Throwable) { /* ignore */ }
        } catch (t: Throwable) {
            if (!loadErrorShown) {
                loadErrorShown = true
                Dialogs.showError(this, t, "Failed to load project")

            }
        }
    }

    // Build a view snapshot for leaf components
    private fun buildViewState(): MarkupViewState {
        val playing = player.status() == PlayerStatus.PLAYING
        val time = player.currentTimeMs()
        val pending = dispatcher.getPendingStart()?.toLong()
        val list = dispatcher.getCompletedPoints()

        val dto = list.map { p ->
            PointDto(
                id = p.id,
                startMs = p.startMs.toLong(),
                endMs = p.endMs.toLong(),
                label = p.label,
                flags = emptySet(),
                favorite = p.favorite,
            )
        }
        val autos = AutosaveState(pending = autosave.isPending(), lastSavedAtMs = autosave.lastSavedAtMs)

        val sel = selectedVisualIndex.takeIf { it >= 0 }
        return MarkupViewState(
            isPlaying = playing,
            currentTimeMs = time,
            selectedVisualIndex = sel,
            pendingDraftStartMs = pending,
            points = dto,
            autosave = autos,
        )
    }

    private fun pushCardsState() {
        cardsView.setState(buildViewState())
    }

    private val timeline = SwingTimelineComponent(
        timeProvider = { player.currentTimeMs() },
        durationProvider = { player.totalDurationMs() },
        pointsProvider = { dispatcher.getCompletedPoints() },
        onSeekRequested = { t ->
            player.seek(t)
            // Immediate UI refresh so the handle moves even when paused
            EventQueue.invokeLater {
                refreshUiAtCurrentTime()
                // If user clicked on a point interval on the timeline, select and scroll to it

                val pts = dispatcher.getCompletedPoints()
                val idx = pts.indexOfFirst { it.startMs <= t && t < it.endMs }
                if (idx >= 0) {
                    setSelectedVisualAndScroll(idx)
                }

                player.component.requestFocusInWindow()

            }
        })

    // Autosave controller (debounced, off-EDT persistence)
    private val autosave = AutosaveController(
        debounceMs = 300, saver = {
            try {
                val dir = projectDir ?: return@AutosaveController
                val list = dispatcher.getCompletedPoints()
                EdlIO.writeForProjectDir(dir, EdlV1(points = list, version = 1))
            } catch (t: Throwable) {
                // Surface error on EDT
                EventQueue.invokeLater { Dialogs.showError(this, t, "Autosave failed") }
            }
        })


    override fun addNotify() {
        super.addNotify()
        // Keybindings are installed in init via helper; no global KeyEventDispatcher needed anymore
        SwingUtilities.invokeLater { ensurePlayerLoaded() }
    }

    override fun removeNotify() {
        // Uninstall keybindings
        keybindings?.uninstall()
        super.removeNotify()
    }

    init {
        isOpaque = true
        background = Color(0x16, 0x16, 0x16)
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        // Bottom area: transport/mark controls above the timeline
        // New TransportControls component: encapsulates Start/End/Jump + TransportBar
        val transportActions = object : MarkupActions {
            override fun togglePlayPause() {
                this@SwingMarkupPanel.togglePlayPause()
            }

            override fun seekTo(ms: Long) {
                player.seek(ms); EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
            }

            override fun jumpToSelected() {
                this@SwingMarkupPanel.jumpToSelected()
            }

            override fun setStartAtPlayhead() {
                this@SwingMarkupPanel.onStartAtPlayhead()
            }

            override fun setEndAtPlayhead() {
                this@SwingMarkupPanel.onEndAtPlayhead()
            }

            override fun createPointAt(ms: Long) {
                dispatcher.onPointStart(ms)
            }

            override fun editPoint(id: String, patch: PointPatch) { /* not used here */
            }

            override fun deletePoint(id: String) { /* not used here */
            }

            override fun toggleFavorite(id: String) {
                this@SwingMarkupPanel.toggleFavorite(id)
            }

            override fun selectByVisualIndex(index: Int) {
                this@SwingMarkupPanel.setSelectedVisualAndScroll(index)
            }

            override fun saveNow() {
                this@SwingMarkupPanel.saveNow()
            }
        }
        transport = TransportControls(
            actions = transportActions, onNudge = { delta ->
                val newTime = max(0L, player.currentTimeMs() + delta)
                player.seek(newTime)
                EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
            })

        val bottom = JPanel(BorderLayout())
        bottom.isOpaque = false
        bottom.add(transport, BorderLayout.NORTH)

        // Center: video and points list side-by-side
        val center = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
        center.isOneTouchExpandable = false
        center.setContinuousLayout(true)
        center.isEnabled = false
        center.dividerSize = 0
        center.resizeWeight = 1.0
        val leftColumn = JPanel(BorderLayout())
        leftColumn.isOpaque = false
        // Wrap VLC component with geometry viewport for live zoom/pan (Task 5.4)
        geometryViewport = GeometryViewportPanel(player.component)
        leftColumn.add(geometryViewport, BorderLayout.CENTER)
        leftColumn.add(bottom, BorderLayout.SOUTH)
        leftColumn.minimumSize = Dimension(320, 0)
        center.leftComponent = leftColumn
        // Subscribe to central adjustments store to live-apply color and geometry
        val unsub = AdjustmentsStore.subscribe { adj ->
            player.applyPreviewAdjustments(adj)
            geometryViewport.refreshGeometry()
        }
        // Apply current state immediately
        val currentAdjustments = AdjustmentsStore.get()
        player.applyPreviewAdjustments(currentAdjustments)
        geometryViewport.refreshGeometry()

        val rightPanel = JPanel(BorderLayout())
        rightPanel.minimumSize = Dimension(RIGHT_PANEL_WIDTH, 0)
        rightPanel.preferredSize = Dimension(RIGHT_PANEL_WIDTH, 0)
        rightPanel.maximumSize = Dimension(RIGHT_PANEL_WIDTH, Int.MAX_VALUE)
        rightPanel.isOpaque = true
        rightPanel.background = UiStyles.DARK_BG
        // Header with count badge on the right
        val header = JPanel(BorderLayout())
        header.isOpaque = false
        header.border = BorderFactory.createEmptyBorder(0, 0, 4, 0)
        header.add(JLabel("Marked points"), BorderLayout.WEST)
        countBadge.horizontalAlignment = SwingConstants.CENTER
        countBadge.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0x44, 0x88, 0x00)), BorderFactory.createEmptyBorder(2, 6, 2, 6)
        )
        countBadge.foreground = UiStyles.LIME
        header.add(countBadge, BorderLayout.EAST)
        rightPanel.add(header, BorderLayout.NORTH)

        // Compose right panel content using extracted components
        val cardsActions = object : MarkupActions {
            override fun togglePlayPause() {
                togglePlayPause()
            }

            override fun seekTo(ms: Long) {
                player.seek(ms); EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
            }

            override fun jumpToSelected() {
                this@SwingMarkupPanel.jumpToSelected()
            }

            override fun setStartAtPlayhead() {
                onStartAtPlayhead()
            }

            override fun setEndAtPlayhead() {
                onEndAtPlayhead()
            }

            override fun createPointAt(ms: Long) {
                dispatcher.onPointStart(ms)
            }

            override fun editPoint(id: String, patch: PointPatch) { /* not used by cards */
            }

            override fun deletePoint(id: String) {
                dispatcher.deletePoint(id)
            }

            override fun toggleFavorite(id: String) {
                this@SwingMarkupPanel.toggleFavorite(id)
            }

            override fun selectByVisualIndex(index: Int) {
                setSelectedVisualAndScroll(index)
            }

            override fun saveNow() {
                this@SwingMarkupPanel.saveNow()
            }
        }
        cardsView = PointsCardsView(cardsActions)
        // Place cards view directly without a table/tab
        rightPanel.add(cardsView, BorderLayout.CENTER)
        // Push initial state to views
        pushCardsState()
        center.rightComponent = rightPanel
        center.resizeWeight = 1.0
        // Add bottom controls and center split
        add(center, BorderLayout.CENTER)
        // Keep right panel fixed width on first show and on resize
        val fixDivider: () -> Unit = {
            val sp = (this.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER)
            if (sp is JSplitPane) {
                val total = sp.size.width
                if (total > 0) {
                    sp.setDividerLocation((total - RIGHT_PANEL_WIDTH).coerceAtLeast(0))
                }
            }
        }

        SwingUtilities.invokeLater { fixDivider() }
        this.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) {
                fixDivider()
            }
        })

        // Timeline spans full width at the bottom
        add(timeline, BorderLayout.SOUTH)

        // Start idle UI refresher
        idleUiTimer.start()

        // Player callbacks → update UI and repaint timeline on EDT
        player.onTimeChanged =
            { t -> EventQueue.invokeLater { updateTimeUI(t); timeline.repaint(); autoActivatePoint(t) } }
        player.onReady =
            { EventQueue.invokeLater { updateTimeUI(player.currentTimeMs()); timeline.repaint(); updatePlayPauseButton() } }
        player.onStatusChanged = { _ -> EventQueue.invokeLater { updatePlayPauseButton(); refreshUiAtCurrentTime() } }

        // Dispatcher callback to refresh UI for all leaf components
        dispatcher.onPointsChanged = {
            val skipAutosave = reloadingPointsFromProject
            EventQueue.invokeLater {
                val pts = dispatcher.getCompletedPoints()
                updateCountBadge(pts)
                pushCardsState()
                timeline.repaint()
                if (!skipAutosave) scheduleAutosave()
            }
        }

        // Consolidated keybindings helper
        keybindings = Keybindings(this, { isTextEditingFocus() }, object : MarkupKeyActions {
            override fun toggle() {
                togglePlayPause()
            }

            override fun startAtPlayhead() {
                onStartAtPlayhead()
            }

            override fun endAtPlayhead() {
                onEndAtPlayhead()
            }

            override fun deleteSelected() {
                deleteSelected()
            }

            override fun nudge(deltaMs: Long) {
                val newTime = max(0L, player.currentTimeMs() + deltaMs)
                player.seek(newTime)
                EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }

            }

            override fun toggleFavoriteSelected() {
                toggleFavoriteSelectedPoint()
            }
        })

        rebuildCards()
    }

    fun setProjectManifest(path: String) {
        manifestPath = path
        projectDir = File(path).parentFile.absolutePath
        // Load adjustments for this project into the central store
        AdjustmentsStore.load(projectDir!!)

        // Load manifest and media
        try {
            val manifest = ManifestIO.read(path)
            val src = manifest.sourceVideo
            if (src.isNullOrBlank() || !File(src).exists()) {
                Dialogs.showError(
                    this,
                    IllegalStateException("Source video missing"),
                    "Select source video for project: ${manifest.name}"
                )
                return
            }
            // Load EDL if present
            val edl = EdlIO.readForProjectDir(projectDir!!)
            dispatcher.setPoints(edl.points)
            // Defer VLCJ media load until component becomes displayable
            pendingMediaFile = File(src)
            isMediaLoaded = false
            loadErrorShown = false
            ensurePlayerLoaded()
        } catch (t: Throwable) {
            Dialogs.showError(this, t, "Failed to load project")
        }
    }


    // Build cards list based on dispatcher state (pending + completed)
    private fun rebuildCards() {
        // Delegated to leaf components now
        updateCountBadge(dispatcher.getCompletedPoints())
        pushCardsState()
    }

    private fun updateCountBadge(points: List<PointV1>) {
        countBadge.text = "${points.size} MARKED / ${points.count { it.favorite }} FAV"
    }

    private fun refreshPointsFromProject() {
        val dir = projectDir ?: return
        val currentPoints = dispatcher.getCompletedPoints()
        val selectedId = currentPoints.getOrNull(selectedVisualIndex)?.id
        val wasPendingSelected = dispatcher.getPendingStart() != null && selectedVisualIndex == currentPoints.size
        try {
            val edl = EdlIO.readForProjectDir(dir)
            reloadingPointsFromProject = true
            dispatcher.setPoints(edl.points)
        } catch (t: Throwable) {
            Dialogs.showError(this, t, "Failed to refresh point markers")
            return
        } finally {
            reloadingPointsFromProject = false
        }

        val refreshedPoints = dispatcher.getCompletedPoints()
        val restoredIndex = selectedId?.let { id -> refreshedPoints.indexOfFirst { it.id == id } } ?: -1
        when {
            restoredIndex >= 0 -> setSelectedVisualAndScroll(restoredIndex)
            wasPendingSelected && dispatcher.getPendingStart() != null -> setSelectedVisualAndScroll(refreshedPoints.size)
            selectedVisualIndex in refreshedPoints.indices -> setSelectedVisual(selectedVisualIndex)
            else -> setSelectedVisual(-1)
        }
        updateCountBadge(refreshedPoints)
        pushCardsState()
        timeline.repaint()
    }


    private fun setSelectedVisual(visualIndex: Int) {
        selectedVisualIndex = visualIndex
        cardsView.updateSelection(visualIndex)
    }

    private fun setSelectedVisualAndScroll(visualIndex: Int) {
        val changed = visualIndex != selectedVisualIndex
        setSelectedVisual(visualIndex)
//        if (!changed) return
        // Defer scroll to ensure any pending layout updates don’t reset viewport
//        try {
//            EventQueue.invokeLater { scrollCardIntoView(visualIndex) }
//        } catch (_: Throwable) {
            scrollCardIntoView(visualIndex)
//        }
    }

    private fun scrollCardIntoView(visualIndex: Int) {
        cardsView.scrollToVisualIndex(visualIndex)
    }

    private fun jumpToSelected() {
        val hasPending = dispatcher.getPendingStart() != null
        val sel = selectedVisualIndex
        if (sel < 0) return
        val pts = dispatcher.getCompletedPoints()
        val pendingIndex = if (hasPending) pts.size else -1
        if (sel == pendingIndex) return
        val dataIndex = sel
        if (dataIndex !in pts.indices) return
        val p = pts[dataIndex]
        player.seek(p.startMs.toLong())
        EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
    }

    private fun toggleFavoriteSelectedPoint() {
        val sel = selectedVisualIndex
        if (sel < 0) return
        val pts = dispatcher.getCompletedPoints()
        if (sel !in pts.indices) return
        toggleFavorite(pts[sel].id)
    }

    private fun toggleFavorite(id: String) {
        if (dispatcher.toggleFavorite(id)) {
            pushCardsState()
            timeline.repaint()
            scheduleAutosave()
        }
    }

    private fun autoActivatePoint(t: Long) {
        val p = dispatcher.getCompletedPoints()
        val hasPending = dispatcher.getPendingStart() != null
        // half-open interval [start, end)
        val idx = p.indexOfFirst { it.startMs <= t && t < it.endMs }
        if (idx >= 0) {
            setSelectedVisual(idx)
        } else {
            // Keep pending selected if present; otherwise clear selection
            if (hasPending) {
                val pendingIndex = p.size // pending is visually last
                setSelectedVisual(pendingIndex)
            } else {
                setSelectedVisual(-1)
            }
        }
    }

    private fun updatePlayPauseButton() {

        val playing = player.status() == PlayerStatus.PLAYING
        transport.setPlaying(playing)

    }

    private fun updateTimeUI(ms: Long) {
        transport.setTimeText(Timecode.format(max(0, ms)))
    }

    private fun refreshUiAtCurrentTime() {
        val t = player.currentTimeMs()
        updateTimeUI(t)
        timeline.repaint()
        autoActivatePoint(t)
    }

    private fun togglePlayPause() {
        val wasPlaying = player.status() == PlayerStatus.PLAYING
        if (wasPlaying) player.pause() else player.play()
        updatePlayPauseButton()
    }

    private fun onStartAtPlayhead() {
        dispatcher.onPointStart(player.currentTimeMs())
        maybeShowDispatcherHint()
        // Refresh cards and select the pending visual row (0)
        EventQueue.invokeLater {
            rebuildCards()
            if (dispatcher.getPendingStart() != null) {
                val pts = dispatcher.getCompletedPoints()
                val pendingIndex = pts.size // pending is visually last
                setSelectedVisual(pendingIndex)
            }
        }
    }

    private fun onEndAtPlayhead() {
        // Capture pending Start before finalization to select the created row afterwards
        val prevPending = dispatcher.getPendingStart()
        dispatcher.onPointEnd(player.currentTimeMs())
        maybeShowDispatcherHint()
        EventQueue.invokeLater {
            val nowPending = dispatcher.getPendingStart()
            if (prevPending != null && nowPending == null) {
                // A point was likely created; select the row matching prevPending start
                val pts = dispatcher.getCompletedPoints()
                val idx = pts.indexOfFirst { it.startMs == prevPending }
                if (idx >= 0) {
                    val visual = idx // no pending row now
                    setSelectedVisual(visual)
                }
            }
        }
    }

    private fun maybeShowDispatcherHint() {
        val m = dispatcher.consumeUserMessage() ?: return
        JOptionPane.showMessageDialog(this, m, "Hint", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun scheduleAutosave() {
        autosave.schedule()
    }

    private fun autosaveNow() {
        autosave.autosaveNow()
    }

    private fun isTextEditingFocus(): Boolean {
        val fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        return (fo is javax.swing.text.JTextComponent) && fo.isEnabled && fo.isVisible && fo.isEditable
    }

    // Expose manual save for File -> Save All integration
    fun saveNow() {
        autosaveNow()
    }
}
