package org.litvin.ui.tabs.markup

import org.litvin.GeometryViewportPanel
import org.litvin.projects.ManifestIO
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.markup.EdlIO
import org.litvin.markup.EdlV1
import org.litvin.markup.PointV1
import org.litvin.markup.components.CommentDispatcher
import org.litvin.markup.components.CommentPatch as DispatcherCommentPatch
import org.litvin.markup.components.CommentState
import org.litvin.markup.components.MarkupDispatcher
import org.litvin.media.PlayerStatus
import org.litvin.media.mpv.MpvSwingMediaPlayerAdapter
import org.litvin.media.SwingMediaPlayer
import org.litvin.shared.util.Timecode
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.SwingUserDialogService
import org.litvin.ui.commons.UserDialogService
import org.litvin.ui.tabs.markup.ui.SwingTimelineComponent
import org.litvin.ui.tabs.markup.ui.EditCommentDialog
import org.litvin.ui.tabs.markup.components.Keybindings
import org.litvin.ui.tabs.markup.components.MarkupKeyActions
import org.litvin.ui.tabs.markup.ui.PointsCardsView
import org.litvin.ui.tabs.markup.ui.TransportControls
import java.awt.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*
import kotlin.math.max

/**
 * Phase 3 — Swing Markup editor panel.
 *
 * Features implemented to meet Phase 3 acceptance:
 * - Custom timeline component drawing VIDEO and MARKS tracks, with playhead bound to media time
 * - Bind timeline to media time via the Swing player adapter callbacks; smooth playhead updates
 * - EDL interactions via MarkupDispatcher: Start(C)/End(V) auto-create, delete, inline edit via table
 * - Keyboard mappings: Space, C, V, Delete, Left/Right and Shift+Arrows; context menu on table rows
 * - Basic autosave of EDL (edl.json) with 300 ms debounce
 *
 */
class SwingMarkupPanel(
    private val player: SwingMediaPlayer,
    private val adjustments: AdjustmentsSession,
    autosaveExecutor: ExecutorService,
    private val dialogs: UserDialogService,
    private val onHelp: () -> Unit = {},
) : JPanel(BorderLayout()), AutoCloseable {

    constructor(onHelp: () -> Unit = {}) : this(
        MpvSwingMediaPlayerAdapter(),
        AdjustmentsStore.legacySession(),
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "markup-autosave") },
        SwingUserDialogService(),
        onHelp,
    )

    // Geometry viewport wrapper for the video component
    private var geometryViewport: GeometryViewportPanel

    // Media
    private val closed = AtomicBoolean(false)
    private var unsubscribeAdjustments: (() -> Unit)? = null

    // Deferred media loading
    private var pendingMediaFile: File? = null
    private var isMediaLoaded: Boolean = false
    private var loadErrorShown: Boolean = false

    // EDL Dispatcher
    private val dispatcher = MarkupDispatcher()
    private val commentDispatcher = CommentDispatcher()

    // Current project manifest path (projectDir derived from it)
    private var manifestPath: String? = null
    private var projectDir: String? = null

    // UI controls
    private var transport: TransportControls

    private val countBadge = JLabel("0 MARKED / 0 FAV").apply { name = "rallies-point-count" }

    // Cards view (replaces legacy inline cards list)
    private var cardsView: PointsCardsView
    private var selectedVisualIndex: Int = -1 // visual index within composed list (pending at 0 when present)
    private var reloadingProjectFromDisk: Boolean = false

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
            // Re-apply current adjustments after media is loaded so the player picks them up
            try {
                val current = adjustments.get()
                player.applyPreviewAdjustments(current)
                geometryViewport.refreshGeometry()
            } catch (_: Throwable) { /* ignore */ }
        } catch (t: Throwable) {
            if (!loadErrorShown) {
                loadErrorShown = true
                dialogs.showError(this, t.message ?: t.toString(), "Failed to load project")

            }
        }
    }

    // Build a view snapshot for leaf components
    private fun buildViewState(): MarkupViewState {
        val playing = player.status() == PlayerStatus.PLAYING
        val time = player.currentTimeMs()
        val pending = dispatcher.getPendingStart()?.toLong()
        val events = buildEvents()
        val autos = AutosaveState(pending = autosave.isPending(), lastSavedAtMs = autosave.lastSavedAtMs)

        val sel = selectedVisualIndex.takeIf { it >= 0 }
        return MarkupViewState(
            isPlaying = playing,
            currentTimeMs = time,
            selectedVisualIndex = sel,
            pendingDraftStartMs = pending,
            events = events,
            autosave = autos,
        )
    }

    private fun buildEvents(): List<MarkupEventDto> {
        val rallies = dispatcher.getCompletedPoints().map { point ->
            RallyEventDto(PointDto(
                id = point.id,
                startMs = point.startMs.toLong(),
                endMs = point.endMs.toLong(),
                label = point.label,
                flags = emptySet(),
                favorite = point.favorite,
            ))
        }
        val comments = commentDispatcher.state().comments.map { comment ->
            CommentDto(
                id = comment.id,
                startMs = comment.startMs.toLong(),
                durationMs = comment.durationMs.toLong(),
                text = comment.text,
                colorHex = comment.colorHex,
            )
        }
        return (rallies + comments).sortedWith(compareBy<MarkupEventDto> { it.startMs }.thenBy { it.stableKey })
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

                val point = dispatcher.getCompletedPoints().firstOrNull { it.startMs <= t && t < it.endMs }
                if (point != null) {
                    selectEventAndScroll("rally:${point.id}")
                }

                player.component.requestFocusInWindow()

            }
        },
        commentsProvider = { commentDispatcher.state().comments },
        onCommentSelected = { id -> EventQueue.invokeLater { selectEventAndScroll("comment:$id") } },
    ).apply { name = "rallies-seek" }

    // Autosave controller (debounced, off-EDT persistence)
    private val autosave = AutosaveController(
        debounceMs = 300, executor = autosaveExecutor, saver = {
            try {
                val dir = projectDir ?: return@AutosaveController
                val comments = commentDispatcher.state()
                EdlIO.writeForProjectDir(
                    dir,
                    EdlV1(
                        points = dispatcher.getCompletedPoints(),
                        comments = comments.comments,
                        commentDefaults = comments.defaults,
                        nextCommentId = comments.nextCommentId,
                        version = 1,
                    ),
                )
            } catch (t: Throwable) {
                // Surface error on EDT
                EventQueue.invokeLater { dialogs.showError(this, t.message ?: t.toString(), "Autosave failed") }
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

            override fun editPoint(id: String, patch: PointPatch) {
                this@SwingMarkupPanel.editPoint(id, patch)
            }

            override fun deletePoint(id: String) {
                this@SwingMarkupPanel.deletePoint(id)
            }

            override fun toggleFavorite(id: String) {
                this@SwingMarkupPanel.toggleFavorite(id)
            }

            override fun addCommentAtPlayhead() = this@SwingMarkupPanel.addCommentAtPlayhead()
            override fun createComment(startMs: Long, durationMs: Long, text: String, colorHex: String) =
                this@SwingMarkupPanel.createComment(startMs, durationMs, text, colorHex)
            override fun editComment(id: Int, patch: CommentPatch) = this@SwingMarkupPanel.editComment(id, patch)
            override fun deleteComment(id: Int) = this@SwingMarkupPanel.deleteComment(id)
            override fun updateCommentColor(id: Int, colorHex: String) = this@SwingMarkupPanel.updateCommentColor(id, colorHex)

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
        // Wrap the video component with the geometry viewport for live zoom/pan (Task 5.4)
        geometryViewport = GeometryViewportPanel(player.component)
        geometryViewport.name = "rallies-video"
        leftColumn.add(geometryViewport, BorderLayout.CENTER)
        leftColumn.add(bottom, BorderLayout.SOUTH)
        leftColumn.minimumSize = Dimension(320, 0)
        center.leftComponent = leftColumn
        // Subscribe to central adjustments store to live-apply color and geometry
        unsubscribeAdjustments = adjustments.subscribe { adj ->
            player.applyPreviewAdjustments(adj)
            geometryViewport.refreshGeometry()
        }
        // Apply current state immediately
        val currentAdjustments = adjustments.get()
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
        header.add(JLabel("Rallies & events"), BorderLayout.WEST)
        countBadge.horizontalAlignment = SwingConstants.CENTER
        countBadge.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0x44, 0x88, 0x00)), BorderFactory.createEmptyBorder(2, 6, 2, 6)
        )
        countBadge.foreground = UiStyles.LIME
        val headerActions = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
            add(countBadge)
            add(JButton("Help [F1]").apply {
                name = "rallies-help"
                toolTipText = "F1 - Help"
                UiStyles.styleSecondary(this)
                addActionListener { onHelp() }
            })
        }
        header.add(headerActions, BorderLayout.EAST)
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

            override fun editPoint(id: String, patch: PointPatch) {
                this@SwingMarkupPanel.editPoint(id, patch)
            }

            override fun deletePoint(id: String) {
                this@SwingMarkupPanel.deletePoint(id)
            }

            override fun toggleFavorite(id: String) {
                this@SwingMarkupPanel.toggleFavorite(id)
            }

            override fun addCommentAtPlayhead() = this@SwingMarkupPanel.addCommentAtPlayhead()
            override fun createComment(startMs: Long, durationMs: Long, text: String, colorHex: String) =
                this@SwingMarkupPanel.createComment(startMs, durationMs, text, colorHex)
            override fun editComment(id: Int, patch: CommentPatch) = this@SwingMarkupPanel.editComment(id, patch)
            override fun deleteComment(id: Int) = this@SwingMarkupPanel.deleteComment(id)
            override fun updateCommentColor(id: Int, colorHex: String) = this@SwingMarkupPanel.updateCommentColor(id, colorHex)

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
        dispatcher.onPointsChanged = ::onMarkupChanged
        commentDispatcher.onCommentsChanged = ::onMarkupChanged

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
        adjustments.load(projectDir!!)

        // Load manifest and media
        try {
            val manifest = ManifestIO.read(path)
            val src = manifest.sourceVideo
            if (src.isNullOrBlank() || !File(src).exists()) {
                dialogs.showError(
                    this,
                    "Source video missing",
                    "Select source video for project: ${manifest.name}"
                )
                return
            }
            // Load EDL if present
            loadEdl(EdlIO.readForProjectDir(projectDir!!))
            // Defer the media load until the component becomes displayable
            pendingMediaFile = File(src)
            isMediaLoaded = false
            loadErrorShown = false
            ensurePlayerLoaded()
        } catch (t: Throwable) {
            dialogs.showError(this, t.message ?: t.toString(), "Failed to load project")
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
        val selectedKey = selectedEvent()?.stableKey
        val wasPendingSelected = dispatcher.getPendingStart() != null && selectedVisualIndex == buildEvents().size
        try {
            loadEdl(EdlIO.readForProjectDir(dir))
        } catch (t: Throwable) {
            dialogs.showError(this, t.message ?: t.toString(), "Failed to refresh rallies and events")
            return
        }

        val refreshedEvents = buildEvents()
        val restoredIndex = selectedKey?.let { key -> refreshedEvents.indexOfFirst { it.stableKey == key } } ?: -1
        when {
            restoredIndex >= 0 -> setSelectedVisualAndScroll(restoredIndex)
            wasPendingSelected && dispatcher.getPendingStart() != null -> setSelectedVisualAndScroll(refreshedEvents.size)
            selectedVisualIndex in refreshedEvents.indices -> setSelectedVisual(selectedVisualIndex)
            else -> setSelectedVisual(-1)
        }
        updateCountBadge(dispatcher.getCompletedPoints())
        pushCardsState()
        timeline.revalidate()
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

    private fun selectedEvent(): MarkupEventDto? = buildEvents().getOrNull(selectedVisualIndex)

    private fun selectEventAndScroll(stableKey: String) {
        val index = buildEvents().indexOfFirst { it.stableKey == stableKey }
        if (index >= 0) setSelectedVisualAndScroll(index)
    }

    private fun loadEdl(edl: EdlV1) {
        reloadingProjectFromDisk = true
        try {
            dispatcher.setPoints(edl.points)
            commentDispatcher.load(CommentState(edl.comments, edl.commentDefaults, edl.nextCommentId))
        } finally {
            reloadingProjectFromDisk = false
        }
    }

    private fun onMarkupChanged() {
        val skipAutosave = reloadingProjectFromDisk
        EventQueue.invokeLater {
            updateCountBadge(dispatcher.getCompletedPoints())
            pushCardsState()
            timeline.revalidate()
            timeline.repaint()
            if (!skipAutosave) scheduleAutosave()
        }
    }

    private fun addCommentAtPlayhead() {
        EditCommentDialog.showCreate(
            parent = this,
            initialStartMs = player.currentTimeMs(),
            defaultColor = commentDispatcher.state().defaults.colorHex,
            actions = commentActions,
        )
    }

    private val commentActions: MarkupActions = object : MarkupActions {
        override fun togglePlayPause() = this@SwingMarkupPanel.togglePlayPause()
        override fun seekTo(ms: Long) = player.seek(ms)
        override fun jumpToSelected() = this@SwingMarkupPanel.jumpToSelected()
        override fun setStartAtPlayhead() = this@SwingMarkupPanel.onStartAtPlayhead()
        override fun setEndAtPlayhead() = this@SwingMarkupPanel.onEndAtPlayhead()
        override fun createPointAt(ms: Long) = dispatcher.onPointStart(ms)
        override fun editPoint(id: String, patch: PointPatch) = this@SwingMarkupPanel.editPoint(id, patch)
        override fun deletePoint(id: String) = this@SwingMarkupPanel.deletePoint(id)
        override fun toggleFavorite(id: String) = this@SwingMarkupPanel.toggleFavorite(id)
        override fun selectByVisualIndex(index: Int) = this@SwingMarkupPanel.setSelectedVisualAndScroll(index)
        override fun saveNow() = this@SwingMarkupPanel.saveNow()
        override fun addCommentAtPlayhead() = this@SwingMarkupPanel.addCommentAtPlayhead()
        override fun createComment(startMs: Long, durationMs: Long, text: String, colorHex: String) =
            this@SwingMarkupPanel.createComment(startMs, durationMs, text, colorHex)
        override fun editComment(id: Int, patch: CommentPatch) = this@SwingMarkupPanel.editComment(id, patch)
        override fun deleteComment(id: Int) = this@SwingMarkupPanel.deleteComment(id)
        override fun updateCommentColor(id: Int, colorHex: String) = this@SwingMarkupPanel.updateCommentColor(id, colorHex)
    }

    private fun createComment(startMs: Long, durationMs: Long, text: String, colorHex: String) {
        val comment = commentDispatcher.create(
            startMs = startMs.toCommentIntOrNull() ?: return showCommentRangeError(),
            durationMs = durationMs.toCommentIntOrNull() ?: return showCommentRangeError(),
            text = text,
            colorHex = colorHex,
        )
        maybeShowCommentHint()
        comment?.let { EventQueue.invokeLater { selectEventAndScroll("comment:${it.id}") } }
    }

    private fun editComment(id: Int, patch: CommentPatch) {
        val updated = commentDispatcher.update(
            id,
            DispatcherCommentPatch(
                startMs = patch.startMs?.toCommentIntOrNull() ?: patch.startMs?.let { return showCommentRangeError() },
                durationMs = patch.durationMs?.toCommentIntOrNull() ?: patch.durationMs?.let { return showCommentRangeError() },
                text = patch.text,
                colorHex = patch.colorHex,
            ),
        )
        maybeShowCommentHint()
        if (updated) EventQueue.invokeLater { selectEventAndScroll("comment:$id") }
    }

    private fun deleteComment(id: Int) {
        if (commentDispatcher.delete(id)) setSelectedVisual(-1)
        maybeShowCommentHint()
    }

    private fun updateCommentColor(id: Int, colorHex: String) {
        if (commentDispatcher.update(id, DispatcherCommentPatch(colorHex = colorHex))) {
            EventQueue.invokeLater { selectEventAndScroll("comment:$id") }
        }
        maybeShowCommentHint()
    }

    private fun editPoint(id: String, patch: PointPatch) {
        val existing = dispatcher.getCompletedPoints().firstOrNull { it.id == id } ?: return
        val updated = dispatcher.updatePoint(
            id,
            patch.startMs ?: existing.startMs.toLong(),
            patch.endMs ?: existing.endMs.toLong(),
            patch.label ?: existing.label,
        )
        maybeShowDispatcherHint()
        if (updated) EventQueue.invokeLater { selectEventAndScroll("rally:$id") }
    }

    private fun deletePoint(id: String) {
        if (dispatcher.deletePoint(id)) setSelectedVisual(-1)
    }

    private fun Long.toCommentIntOrNull(): Int? = takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()

    private fun showCommentRangeError() {
        dialogs.showInfo(this, "Comment times must fit within the supported video timeline.", "Invalid comment")
    }

    private fun jumpToSelected() {
        val event = selectedEvent() ?: return
        player.seek(event.startMs)
        EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
    }

    private fun toggleFavoriteSelectedPoint() {
        val rally = selectedEvent() as? RallyEventDto ?: return
        toggleFavorite(rally.point.id)
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
            selectEventAndScroll("rally:${p[idx].id}")
        } else {
            // Keep pending selected if present; otherwise clear selection
            if (hasPending) {
                val pendingIndex = buildEvents().size // pending is visually last
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
                val pendingIndex = buildEvents().size // pending is visually last
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
                pts.firstOrNull { it.startMs == prevPending }?.let { selectEventAndScroll("rally:${it.id}") }
            }
        }
    }

    private fun maybeShowDispatcherHint() {
        val m = dispatcher.consumeUserMessage() ?: return
        dialogs.showInfo(this, m, "Hint")
    }

    private fun maybeShowCommentHint() {
        val message = commentDispatcher.consumeUserMessage() ?: return
        dialogs.showInfo(this, message, "Hint")
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

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        idleUiTimer.stop()
        keybindings?.uninstall()
        keybindings = null
        dispatcher.onPointsChanged = null
        commentDispatcher.onCommentsChanged = null
        unsubscribeAdjustments?.invoke()
        unsubscribeAdjustments = null
        autosave.flushAndClose()
        adjustments.flush()
        player.onTimeChanged = null
        player.onStatusChanged = null
        player.onReady = null
        player.close()
    }
}
