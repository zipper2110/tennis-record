package org.litvin.ui.tabs.markup
import org.litvin.*
import org.litvin.markup.components.MarkupDispatcher
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.markup.EdlIO
import org.litvin.markup.EdlV1
import org.litvin.markup.PointV1
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.Dialogs
import org.litvin.ui.commons.SwingTimelineComponent
import org.litvin.ui.tabs.markup.ui.TransportControls
import org.litvin.ui.tabs.markup.ui.MarkupToolbar
import org.litvin.ui.tabs.markup.ui.EditPointDialog
import org.litvin.ui.tabs.markup.components.Keybindings
import org.litvin.ui.tabs.markup.components.MarkupKeyActions
import org.litvin.ui.tabs.markup.ui.PointsCardsView
import org.litvin.ui.tabs.markup.ui.PointsTableView

import org.litvin.media.PlayerStatus
import org.litvin.media.VlcjSwingMediaPlayerAdapter
import org.litvin.shared.util.Timecode
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
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

    // Geometry viewport wrapper for VLC component (Task 5.4)
    private lateinit var geometryViewport: GeometryViewportPanel

    // Lifecycle hooks controlled by navigation
    fun onActivated() {
        ensurePlayerLoaded()
        try { player.pause() } catch (_: Throwable) {}
        // Do not auto-play; optionally restore focus to player area
        EventQueue.invokeLater { try { player.component.requestFocusInWindow() } catch (_: Throwable) {} }
    }
    fun onDeactivated() {
        try { player.pause() } catch (_: Throwable) {}
    }

    // Media
    private val player = VlcjSwingMediaPlayerAdapter()
    // Deferred media loading
    private var pendingMediaFile: File? = null
    private var isMediaLoaded: Boolean = false
    private var loadErrorShown: Boolean = false

    // Note: another addNotify exists below for key dispatcher; we consolidate into one there.
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

    // EDL Dispatcher
    private val dispatcher = MarkupDispatcher()

    // Current project manifest path (projectDir derived from it)
    private var manifestPath: String? = null
    private var projectDir: String? = null

    // UI controls
    private lateinit var toolbar: MarkupToolbar
    private lateinit var transport: TransportControls
    private val btnStart = JButton("Point Start [C]")
    private val btnEnd = JButton("Point End [V]")
    // Legacy fields kept for compile stability; no longer used after TransportBar extraction
    @Suppress("unused") private val btnSeekBack10 = JButton()
    @Suppress("unused") private val btnSeekBack1 = JButton()
    @Suppress("unused") private val btnSeekFwd1 = JButton()
    @Suppress("unused") private val btnSeekFwd10 = JButton()
    @Suppress("unused") private val timeLabel = JLabel("00:00:00.000")
    private val countBadge = JLabel("0 MARKED")

    private var lastPreviewWasStart = true

    // Cards/Table views (replaces legacy inline cards list/table)
    private lateinit var cardsView: PointsCardsView
    private lateinit var tableView: PointsTableView
    private var selectedVisualIndex: Int = -1 // visual index within composed list (pending at 0 when present)

    // Consolidated keybindings helper
    private var keybindings: Keybindings? = null

    // Fixed sizing constants (right panel/cards)
    private val RIGHT_PANEL_WIDTH = 420
    private val CARD_H_MARGIN = 10 // equal left/right margin inside right panel
    private val CARD_WIDTH = RIGHT_PANEL_WIDTH - CARD_H_MARGIN * 2
    private val CARD_HEIGHT = 68

    // Build a view snapshot for leaf components
    private fun buildViewState(): MarkupViewState {
        val playing = try { player.status() == org.litvin.media.PlayerStatus.PLAYING } catch (_: Throwable) { false }
        val time = try { player.currentTimeMs() } catch (_: Throwable) { 0L }
        val pending = try { dispatcher.getPendingStart()?.toLong() } catch (_: Throwable) { null }
        val list = try { dispatcher.getCompletedPoints() } catch (_: Throwable) { emptyList() }
        val dto = list.map { p -> PointDto(id = p.id, startMs = p.startMs.toLong(), endMs = p.endMs.toLong(), label = p.label, flags = emptySet()) }
        val autos = try { AutosaveState(pending = autosave.isPending(), lastSavedAtMs = autosave.lastSavedAtMs) } catch (_: Throwable) { AutosaveState(pending = false, lastSavedAtMs = null) }
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
        try { cardsView.setState(buildViewState()) } catch (_: Throwable) { }
    }

    private fun pushTableState() {
        try { tableView.setState(buildViewState()) } catch (_: Throwable) { }
    }

    private fun pushToolbarState() {
        try { toolbar.setState(buildViewState()) } catch (_: Throwable) { }
    }
    

    private val timeline = SwingTimelineComponent(
        timeProvider = { player.currentTimeMs() },
        durationProvider = { player.totalDurationMs() },
        pointsProvider = { dispatcher.getCompletedPoints() },
        onSeekRequested = { t ->
            player.seek(t)
            // Immediate UI refresh so the handle moves even when paused
            EventQueue.invokeLater { refreshUiAtCurrentTime() }
        }
    )

    // Autosave controller (debounced, off-EDT persistence)
    private val autosave = AutosaveController(
        debounceMs = 300,
        saver = {
            try {
                val dir = projectDir ?: return@AutosaveController
                val list = dispatcher.getCompletedPoints()
                EdlIO.writeForProjectDir(dir, EdlV1(points = list, version = 1))
            } catch (t: Throwable) {
                // Surface error on EDT
                EventQueue.invokeLater { Dialogs.showError(this, t, "Autosave failed") }
            }
        },
        onStateChanged = { _ ->
            // Reflect pending/lastSaved in toolbar indicator
            try { pushToolbarState() } catch (_: Throwable) { }
        }
    )

    // Idle UI refresher to keep time label and timeline handle in sync when paused/seeking
    private val idleUiTimer = Timer(100) { _ ->
        try {
            if (player.status() != org.litvin.media.PlayerStatus.PLAYING) {
                refreshUiAtCurrentTime()
            }
        } catch (_: Throwable) { }
    }.apply { isRepeats = true }

    private var spaceDown: Boolean = false
    private var cDown: Boolean = false
    private var vDown: Boolean = false
    private var leftDown: Boolean = false
    private var rightDown: Boolean = false
    private val spaceDispatcher = KeyEventDispatcher { e ->
        try {
            if (!this.isShowing) return@KeyEventDispatcher false
            // Respect text editing focus: do not hijack Space/C/V while typing into text fields
            if (isTextEditingFocus()) return@KeyEventDispatcher false
            when (e.id) {
                java.awt.event.KeyEvent.KEY_PRESSED -> {
                    when (e.keyCode) {
                        java.awt.event.KeyEvent.VK_SPACE -> {
                            if (!spaceDown) {
                                togglePlayPause()
                            }
                            spaceDown = true
                            e.consume()
                            return@KeyEventDispatcher true
                        }
                        java.awt.event.KeyEvent.VK_C -> {
                            if (!cDown) {
                                onStartAtPlayhead()
                            }
                            cDown = true
                            e.consume()
                            return@KeyEventDispatcher true
                        }
                        java.awt.event.KeyEvent.VK_V -> {
                            if (!vDown) {
                                onEndAtPlayhead()
                            }
                            vDown = true
                            e.consume()
                            return@KeyEventDispatcher true
                        }
                        java.awt.event.KeyEvent.VK_LEFT -> {
                            if (!leftDown) {
                                val delta = if (e.isShiftDown) 10_000 else 1_000
                                player.seek(max(0, player.currentTimeMs() - delta))
                                EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
                            }
                            leftDown = true
                            e.consume()
                            return@KeyEventDispatcher true
                        }
                        java.awt.event.KeyEvent.VK_RIGHT -> {
                            if (!rightDown) {
                                val delta = if (e.isShiftDown) 10_000 else 1_000
                                player.seek(player.currentTimeMs() + delta)
                                EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
                            }
                            rightDown = true
                            e.consume()
                            return@KeyEventDispatcher true
                        }
                    }
                }
                java.awt.event.KeyEvent.KEY_RELEASED -> {
                    when (e.keyCode) {
                        java.awt.event.KeyEvent.VK_SPACE -> {
                            spaceDown = false
                            e.consume()
                            return@KeyEventDispatcher true
                        }
                        java.awt.event.KeyEvent.VK_C -> {
                            cDown = false
                            e.consume()
                            return@KeyEventDispatcher true
                        }
                        java.awt.event.KeyEvent.VK_V -> {
                            vDown = false
                            e.consume()
                            return@KeyEventDispatcher true
                        }
                        java.awt.event.KeyEvent.VK_LEFT -> {
                            leftDown = false
                            e.consume()
                            return@KeyEventDispatcher true
                        }
                        java.awt.event.KeyEvent.VK_RIGHT -> {
                            rightDown = false
                            e.consume()
                            return@KeyEventDispatcher true
                        }
                    }
                }
            }
        } catch (_: Throwable) { }
        false
    }

    override fun addNotify() {
        super.addNotify()
        // Keybindings are installed in init via helper; no global KeyEventDispatcher needed anymore
        try { SwingUtilities.invokeLater { ensurePlayerLoaded() } } catch (_: Throwable) { }
    }

    override fun removeNotify() {
        // Uninstall keybindings
        try { keybindings?.uninstall() } catch (_: Throwable) { }
        // Unsubscribe from adjustments bus if subscribed
        try {
            val unsub = geometryViewport.getClientProperty("adj_unsub") as? (() -> Unit)
            unsub?.invoke()
            geometryViewport.putClientProperty("adj_unsub", null)
        } catch (_: Throwable) { }
        super.removeNotify()
    }

    init {
        isOpaque = true
        background = Color(0x16, 0x16, 0x16)
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        // Bottom area: transport/mark controls above the timeline
        // New TransportControls component: encapsulates Start/End/Jump + TransportBar
        val transportActions = object : MarkupActions {
            override fun togglePlayPause() { this@SwingMarkupPanel.togglePlayPause() }
            override fun seekTo(ms: Long) { try { player.seek(ms); EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() } } catch (_: Throwable) { } }
            override fun jumpToSelected() { this@SwingMarkupPanel.jumpToSelected() }
            override fun setStartAtPlayhead() { this@SwingMarkupPanel.onStartAtPlayhead() }
            override fun setEndAtPlayhead() { this@SwingMarkupPanel.onEndAtPlayhead() }
            override fun createPointAt(ms: Long) { dispatcher.onPointStart(ms) }
            override fun editPoint(id: String, patch: PointPatch) { /* not used here */ }
            override fun deletePoint(id: String) { /* not used here */ }
            override fun selectByVisualIndex(index: Int) { this@SwingMarkupPanel.setSelectedVisual(index) }
            override fun saveNow() { this@SwingMarkupPanel.saveNow() }
        }
        toolbar = MarkupToolbar(transportActions)
        transport = TransportControls(
            actions = transportActions,
            onNudge = { delta ->
                val newTime = max(0L, player.currentTimeMs() + delta)
                player.seek(newTime)
                EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
            }
        )

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
        try {
            val unsub = AdjustmentsStore.subscribe { adj ->
                try { player.applyColorAdjustments(adj) } catch (_: Throwable) { }
                try { player.applyGeometryAdjustments(adj) } catch (_: Throwable) { }
            }
            // Apply current state immediately
            try {
                val cur = AdjustmentsStore.get()
                try { player.applyColorAdjustments(cur) } catch (_: Throwable) { }
                try { player.applyGeometryAdjustments(cur) } catch (_: Throwable) { }
            } catch (_: Throwable) { }
            // Store unsubscribe handle on the component for cleanup on removal
            geometryViewport.putClientProperty("adj_unsub", unsub)
        } catch (_: Throwable) { }

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
            BorderFactory.createLineBorder(Color(0x44, 0x88, 0x00)),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)
        )
        countBadge.foreground = UiStyles.LIME
        header.add(countBadge, BorderLayout.EAST)
        rightPanel.add(header, BorderLayout.NORTH)

        // Compose right panel content using extracted components
        val cardsActions = object : MarkupActions {
            override fun togglePlayPause() { togglePlayPause() }
            override fun seekTo(ms: Long) { try { player.seek(ms) ; EventQueue.invokeLater { refreshUiAtCurrentTime() } } catch (_: Throwable) { } }
            override fun jumpToSelected() { this@SwingMarkupPanel.jumpToSelected() }
            override fun setStartAtPlayhead() { onStartAtPlayhead() }
            override fun setEndAtPlayhead() { onEndAtPlayhead() }
            override fun createPointAt(ms: Long) { dispatcher.onPointStart(ms) }
            override fun editPoint(id: String, patch: PointPatch) { /* not used by cards */ }
            override fun deletePoint(id: String) { dispatcher.deletePoint(id) }
            override fun selectByVisualIndex(index: Int) { setSelectedVisual(index) }
            override fun saveNow() { this@SwingMarkupPanel.saveNow() }
        }
        cardsView = PointsCardsView(cardsActions)
        val tableActions = object : MarkupActions {
            override fun togglePlayPause() { togglePlayPause() }
            override fun seekTo(ms: Long) { try { player.seek(ms) ; EventQueue.invokeLater { refreshUiAtCurrentTime() } } catch (_: Throwable) { } }
            override fun jumpToSelected() { this@SwingMarkupPanel.jumpToSelected() }
            override fun setStartAtPlayhead() { onStartAtPlayhead() }
            override fun setEndAtPlayhead() { onEndAtPlayhead() }
            override fun createPointAt(ms: Long) { dispatcher.onPointStart(ms) }
            override fun editPoint(id: String, patch: PointPatch) {
                try {
                    val ok = dispatcher.updatePoint(id, patch.startMs ?: 0L, patch.endMs ?: 0L, patch.label ?: "")
                    if (!ok) maybeShowDispatcherHint()
                } catch (_: Throwable) { }
            }
            override fun deletePoint(id: String) { try { dispatcher.deletePoint(id) } catch (_: Throwable) { } }
            override fun selectByVisualIndex(index: Int) { setSelectedVisual(index) }
            override fun saveNow() { this@SwingMarkupPanel.saveNow() }
        }
        tableView = PointsTableView(tableActions)
        val tabs = JTabbedPane().apply {
            isOpaque = false
            addTab("Cards", cardsView)
            addTab("Table", tableView)
        }
        rightPanel.add(tabs, BorderLayout.CENTER)
        // Push initial state to views
        pushCardsState()
        pushTableState()
        center.rightComponent = rightPanel
        center.resizeWeight = 1.0
        // Add bottom controls and center split
        add(toolbar, BorderLayout.NORTH)
        add(center, BorderLayout.CENTER)
        // Push initial toolbar state
        pushToolbarState()
        // Keep right panel fixed width on first show and on resize
        try {
            val fixDivider: () -> Unit = {
                try {
                    val sp = (this.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER)
                    if (sp is JSplitPane) {
                        val total = sp.size.width
                        if (total > 0) {
                            sp.setDividerLocation((total - RIGHT_PANEL_WIDTH).coerceAtLeast(0))
                        }
                    }
                } catch (_: Throwable) { }
            }
            SwingUtilities.invokeLater { fixDivider() }
            this.addComponentListener(object: java.awt.event.ComponentAdapter(){
                override fun componentResized(e: java.awt.event.ComponentEvent) { fixDivider() }
            })
        } catch (_: Throwable) { }
        // Timeline spans full width at the bottom
        add(timeline, BorderLayout.SOUTH)

        // Start idle UI refresher
        try { idleUiTimer.start() } catch (_: Throwable) { }


        // Player callbacks → update UI and repaint timeline on EDT
        player.onTimeChanged = { t -> EventQueue.invokeLater { updateTimeUI(t) ; timeline.repaint() ; autoActivatePoint(t) } }
        player.onReady = { EventQueue.invokeLater { updateTimeUI(player.currentTimeMs()) ; timeline.repaint() ; updatePlayPauseButton() } }
        player.onStatusChanged = { _ -> EventQueue.invokeLater { updatePlayPauseButton() ; refreshUiAtCurrentTime() } }

        // Dispatcher callback to refresh UI for all leaf components
        dispatcher.onPointsChanged = {
            EventQueue.invokeLater {
                val pts = dispatcher.getCompletedPoints()
                countBadge.text = "${pts.size} MARKED"
                pushCardsState()
                pushTableState()
                pushToolbarState()
                timeline.repaint()
                scheduleAutosave()
            }
        }

        // Consolidated keybindings helper
        try {
            keybindings = Keybindings(
                this,
                { isTextEditingFocus() },
                object : MarkupKeyActions {
                    override fun toggle() { togglePlayPause() }
                    override fun startAtPlayhead() { onStartAtPlayhead() }
                    override fun endAtPlayhead() { onEndAtPlayhead() }
                    override fun deleteSelected() { deleteSelected() }
                    override fun nudge(deltaMs: Long) {
                        try {
                            val newTime = kotlin.math.max(0L, player.currentTimeMs() + deltaMs)
                            player.seek(newTime)
                            EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
                        } catch (_: Throwable) { }
                    }
                }
            )
        } catch (_: Throwable) { }
        rebuildCards()
    }

    fun setProjectManifest(path: String) {
        manifestPath = path
        projectDir = File(path).parentFile.absolutePath
        // Load adjustments for this project into the central store
        try { AdjustmentsStore.load(projectDir!!) } catch (_: Throwable) { }
        // Load manifest and media
        try {
            val manifest = ManifestIO.read(path)
            val src = manifest.sourceVideo
            if (src.isNullOrBlank() || !File(src).exists()) {
                Dialogs.showError(this, IllegalStateException("Source video missing"), "Select source video for project: ${manifest.name}")
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
        pushCardsState()
        pushTableState()
    }


    private fun buildPointCard(visualIndex: Int, dataIndex: Int, p: PointV1): JComponent {
        val card = JPanel(BorderLayout())
        card.isOpaque = true
        card.background = UiStyles.CARD_BG
        card.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiStyles.CARD_BORDER, 1, true),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        )
        card.alignmentX = Component.LEFT_ALIGNMENT
        // Fixed card size
        run {
            val sz = Dimension(CARD_WIDTH, CARD_HEIGHT)
            card.minimumSize = sz
            card.preferredSize = sz
            card.maximumSize = sz
        }
        // Left stripe for active
        val stripe = object: JComponent(){
            override fun getPreferredSize() = Dimension(4, 1)
            override fun paintComponent(g: Graphics) {
                g.color = if (visualIndex == selectedVisualIndex) UiStyles.LIME else UiStyles.CARD_BORDER
                g.fillRect(0,0,width,height)
            }
        }
        card.add(stripe, BorderLayout.WEST)
        val center = JPanel()
        center.isOpaque = false
        center.layout = BoxLayout(center, BoxLayout.Y_AXIS)
        val title = JLabel("Point ${dataIndex + 1}").apply { foreground = UiStyles.FG_PRIMARY; font = font.deriveFont(Font.BOLD) }
        val times = JPanel(FlowLayout(FlowLayout.LEFT, 14, 0)).apply {
            isOpaque = false
            add(JLabel(Timecode.format(p.startMs.toLong())).apply { foreground = UiStyles.FG_SECONDARY })
            add(JLabel(Timecode.format(p.endMs.toLong())).apply { foreground = UiStyles.FG_SECONDARY })
        }
        center.add(title)
        center.add(Box.createVerticalStrut(6))
        center.add(times)
        card.add(center, BorderLayout.CENTER)
        val actions = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
        }
        val goBtn = UiStyles.smallIconButton(UiStyles.targetIcon(), "Go to marked point") {
            // If a pending exists and user acts on a completed card, cancel pending
            if (dispatcher.getPendingStart() != null && visualIndex > 0) dispatcher.clearPending()
            player.seek(p.startMs.toLong())
            EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
            setSelectedVisual(visualIndex)
        }
        val editBtn = UiStyles.smallIconButton(UiStyles.pencilIcon(), "Edit times/label") { showEditDialog(p) }
        val delBtn = UiStyles.smallIconButton(UiStyles.crossIcon(), "Delete point") {
            setSelectedVisual(visualIndex)
            confirmDelete(p)
        }
        actions.add(goBtn); actions.add(Box.createHorizontalStrut(8)); actions.add(editBtn); actions.add(Box.createHorizontalStrut(8)); actions.add(delBtn)
        card.add(actions, BorderLayout.EAST)
        // Clicking card seeks to start
        card.addMouseListener(object: MouseAdapter(){
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    player.seek(p.startMs.toLong())
                    EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
                    setSelectedVisual(visualIndex)
                }
            }
        })
        applyCardSelectionStyle(card, visualIndex == selectedVisualIndex)
        return card
    }

    private fun applyCardSelectionStyle(card: JComponent, selected: Boolean) {
        card.background = if (selected) UiStyles.SURFACE_HIGH else UiStyles.CARD_BG
        card.repaint()
    }

    private fun setSelectedVisual(visualIndex: Int) {
        selectedVisualIndex = visualIndex
        pushCardsState()
        pushTableState()
        scrollCardIntoView(visualIndex)
    }

    private fun scrollCardIntoView(visualIndex: Int) {
        try {
            cardsView.scrollToVisualIndex(visualIndex)
        } catch (_: Throwable) { }
    }

    private fun showEditDialog(p: PointV1) {
        val dto = PointDto(id = p.id, startMs = p.startMs.toLong(), endMs = p.endMs.toLong(), label = p.label, flags = emptySet())
        val actions = object : MarkupActions {
            override fun togglePlayPause() { this@SwingMarkupPanel.togglePlayPause() }
            override fun seekTo(ms: Long) { try { player.seek(ms); EventQueue.invokeLater { refreshUiAtCurrentTime() } } catch (_: Throwable) { } }
            override fun jumpToSelected() { this@SwingMarkupPanel.jumpToSelected() }
            override fun setStartAtPlayhead() { onStartAtPlayhead() }
            override fun setEndAtPlayhead() { onEndAtPlayhead() }
            override fun createPointAt(ms: Long) { dispatcher.onPointStart(ms) }
            override fun editPoint(id: String, patch: PointPatch) {
                val ok = dispatcher.updatePoint(id, patch.startMs ?: p.startMs.toLong(), patch.endMs ?: p.endMs.toLong(), patch.label ?: p.label ?: "")
                if (!ok) maybeShowDispatcherHint()
            }
            override fun deletePoint(id: String) { dispatcher.deletePoint(id) }
            override fun selectByVisualIndex(index: Int) { setSelectedVisual(index) }
            override fun saveNow() { this@SwingMarkupPanel.saveNow() }
        }
        EditPointDialog.show(this, dto, actions)
    }

    private fun confirmDelete(p: PointV1) {
        val name = Timecode.format(p.startMs.toLong()) + " - " + Timecode.format(p.endMs.toLong())
        val res = JOptionPane.showConfirmDialog(this, "Delete marked point $name?", "Confirm delete", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
        if (res == JOptionPane.OK_OPTION) {
            if (dispatcher.deletePoint(p.id)) {
                pushCardsState()
                pushTableState()
                timeline.repaint()
                scheduleAutosave()
            }
        }
    }

    private fun jumpToSelected() {
        val hasPending = dispatcher.getPendingStart() != null
        val sel = selectedVisualIndex
        if (sel < 0) return
        if (hasPending && sel == 0) return
        val dataIndex = sel - if (hasPending) 1 else 0
        val pts = dispatcher.getCompletedPoints()
        if (dataIndex !in pts.indices) return
        val p = pts[dataIndex]
        player.seek(p.startMs.toLong())
        EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
    }

    private fun deleteSelected() {
        val hasPending = dispatcher.getPendingStart() != null
        val sel = selectedVisualIndex
        if (sel < 0) return
        if (hasPending && sel == 0) return
        val dataIndex = sel - if (hasPending) 1 else 0
        val pts = dispatcher.getCompletedPoints()
        if (dataIndex !in pts.indices) return
        val p = pts[dataIndex]
        confirmDelete(p)
    }

    private fun autoActivatePoint(t: Long) {
        val p = dispatcher.getCompletedPoints()
        val hasPending = dispatcher.getPendingStart() != null
        // half-open interval [start, end)
        val idx = p.indexOfFirst { it.startMs <= t && t < it.endMs }
        if (idx >= 0) {
            val visual = idx + if (hasPending) 1 else 0
            setSelectedVisual(visual)
        } else {
            // Keep pending selected if present; otherwise clear selection
            if (hasPending) {
                setSelectedVisual(0)
            } else {
                setSelectedVisual(-1)
            }
        }
    }

    private fun updatePlayPauseButton() {
        try {
            val playing = player.status() == PlayerStatus.PLAYING
            transport.setPlaying(playing)
        } catch (_: Throwable) { }
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
                setSelectedVisual(0)
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
        try { autosave.schedule() } catch (_: Throwable) { }
        // Reflect pending autosave in toolbar indicator
        try { pushToolbarState() } catch (_: Throwable) { }
    }

    private fun autosaveNow() {
        try { autosave.autosaveNow() } catch (_: Throwable) { }
    }

    private fun isTextEditingFocus(): Boolean {
        return try {
            val fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            (fo is javax.swing.text.JTextComponent) && fo.isEnabled && fo.isVisible && fo.isEditable
        } catch (_: Throwable) { false }
    }

    private fun installKeyBindings() {
        fun bind(key: String, actionName: String, runnable: () -> Unit) {
            val am = this.actionMap
            // Bind at multiple focus conditions to ensure reliability even when JTable has focus
            val ims = arrayOf(
                WHEN_IN_FOCUSED_WINDOW,
                WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
            )
            ims.forEach { cond ->
                val im = this.getInputMap(cond)
                im.put(KeyStroke.getKeyStroke(key), actionName)
            }
            am.put(actionName, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    // Respect text editing focus: do not hijack Space/Arrows/Delete/C/V while typing
                    if (isTextEditingFocus()) return
                    runnable()
                }
            })
        }
        // Primary controls
        bind("SPACE", "toggle") { togglePlayPause() }
        bind("C", "pointStart") { onStartAtPlayhead() }
        bind("V", "pointEnd") { onEndAtPlayhead() }
        bind("DELETE", "delete") { deleteSelected() }
        // Seeks (nudge) — request focus to player so Space works immediately after
        bind("LEFT", "seekLeft") {
            player.seek(max(0, player.currentTimeMs() - 1000))
            EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
        }
        bind("RIGHT", "seekRight") {
            player.seek(player.currentTimeMs() + 1000)
            EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
        }
        bind("shift LEFT", "seekLeftBig") {
            player.seek(max(0, player.currentTimeMs() - 10_000))
            EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
        }
        bind("shift RIGHT", "seekRightBig") {
            player.seek(player.currentTimeMs() + 10_000)
            EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
        }
    }

    fun dispose() {
        try { idleUiTimer.stop() } catch (_: Throwable) {}
        try { player.dispose() } catch (_: Throwable) {}
    }

    // Expose manual save for File -> Save All integration
    fun saveNow() {
        try { autosaveNow() } catch (_: Throwable) { }
    }



}
