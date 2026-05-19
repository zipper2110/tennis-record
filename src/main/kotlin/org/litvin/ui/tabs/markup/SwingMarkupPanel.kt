package org.litvin.ui.tabs.markup
import org.litvin.*
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.markup.EdlIO
import org.litvin.markup.EdlV1
import org.litvin.markup.PointV1
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.Dialogs
import org.litvin.ui.commons.SwingTimelineComponent
import org.litvin.ui.commons.TransportBar

import org.litvin.media.PlayerStatus
import org.litvin.media.VlcjSwingMediaPlayerAdapter
import org.litvin.shared.util.Timecode
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.AbstractCellEditor
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
    private lateinit var transport: TransportBar
    private val btnStart = JButton("Point Start [C]")
    private val btnEnd = JButton("Point End [V]")
    // Legacy fields kept for compile stability; no longer used after TransportBar extraction
    @Suppress("unused") private val btnSeekBack10 = JButton()
    @Suppress("unused") private val btnSeekBack1 = JButton()
    @Suppress("unused") private val btnSeekFwd1 = JButton()
    @Suppress("unused") private val btnSeekFwd10 = JButton()
    @Suppress("unused") private val timeLabel = JLabel("00:00:00.000")
    private val countBadge = JLabel("0 MARKED")

    private val pointsModel = PointsTableModel()
    private val pointsTable = JTable(pointsModel) // legacy, no longer shown; kept for compile simplicity
    private var lastPreviewWasStart = true

    // Cards list (replaces table view)
    private val cardsListPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private val cardsScroll = JScrollPane(cardsListPanel).apply {
        border = BorderFactory.createEmptyBorder()
        verticalScrollBar.unitIncrement = 16
        verticalScrollBar.blockIncrement = 120
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        isOpaque = false
        viewport.isOpaque = false
    }
    private val cardComponents: MutableList<JComponent> = mutableListOf()
    private var selectedVisualIndex: Int = -1 // visual index within cards collection (pending at 0 when present)

    // Fixed sizing constants (right panel/cards)
    private val RIGHT_PANEL_WIDTH = 420
    private val CARD_H_MARGIN = 10 // equal left/right margin inside right panel
    private val CARD_WIDTH = RIGHT_PANEL_WIDTH - CARD_H_MARGIN * 2
    private val CARD_HEIGHT = 68
    
    // Renders pending row (row 0) in italic gray when a Start is set but End not yet (table legacy)
    private val pendingRenderer = object : javax.swing.table.DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            try {
                val hasPending = dispatcher.getPendingStart() != null
                if (hasPending && row == 0) {
                    c.font = c.font.deriveFont(Font.ITALIC)
                    if (!isSelected) {
                        c.foreground = Color(0xAA, 0xAA, 0xAA)
                    }
                    if (c is JComponent) c.toolTipText = "Pending point — press V to set End"
                } else {
                    if (!isSelected) c.foreground = UIManager.getColor("Table.foreground")
                    if (c is JComponent) c.toolTipText = null
                }
            } catch (_: Throwable) { }
            return c
        }
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

    // Autosave debounce timer (~300 ms)
    private val autosaveTimer = Timer(300) { _ -> autosaveNow() }.apply { isRepeats = false }

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
        try { KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(spaceDispatcher) } catch (_: Throwable) { }
        try { SwingUtilities.invokeLater { ensurePlayerLoaded() } } catch (_: Throwable) { }
    }

    override fun removeNotify() {
        try { KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(spaceDispatcher) } catch (_: Throwable) { }
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
        // Shared transport bar with play/pause, seek and time label
        transport = TransportBar(
            onTogglePlayPause = { togglePlayPause() },
            onSeek = { delta ->
                val newTime = max(0, player.currentTimeMs() + delta)
                player.seek(newTime)
                EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
            }
        )

        // Point buttons styled and placed on the left
        listOf(btnStart, btnEnd).forEach { UiStyles.styleSecondary(it) }
        btnStart.addActionListener { onStartAtPlayhead() }
        btnEnd.addActionListener { onEndAtPlayhead() }

        val controls = JPanel()
        controls.layout = BoxLayout(controls, BoxLayout.X_AXIS)
        controls.isOpaque = false
        // Left group: Start/End
        controls.add(btnStart); controls.add(Box.createHorizontalStrut(8)); controls.add(btnEnd)
        controls.add(Box.createHorizontalGlue())
        // Middle group: shared transport bar
        controls.add(transport)
        controls.add(Box.createHorizontalGlue())

        val bottom = JPanel(BorderLayout())
        bottom.isOpaque = false
        // Opaque dark controls bar container
        val controlsBar = JPanel(BorderLayout())
        controlsBar.isOpaque = true
        controlsBar.background = UiStyles.SURFACE_HIGH
        controlsBar.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UiStyles.CARD_BORDER),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        )
        controlsBar.add(controls, BorderLayout.CENTER)
        bottom.add(controlsBar, BorderLayout.NORTH)

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

        pointsTable.fillsViewportHeight = true
        pointsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        pointsTable.componentPopupMenu = buildTableContextMenu()
        // Set column widths and install Action button renderer/editor
        try {
            val cm = pointsTable.columnModel
            if (cm.columnCount >= 6) {
                cm.getColumn(0).preferredWidth = 40
                cm.getColumn(1).preferredWidth = 110
                cm.getColumn(2).preferredWidth = 110
                cm.getColumn(3).preferredWidth = 110
                cm.getColumn(4).preferredWidth = 200
                cm.getColumn(5).preferredWidth = 60
                cm.getColumn(5).cellRenderer = ActionButtonRenderer()
                cm.getColumn(5).cellEditor = ActionButtonEditor()
            }
        } catch (_: Throwable) { }
        
        // Install per-row mouse interactions (legacy table, kept for logic compatibility)
        pointsTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                try {
                    val row = pointsTable.rowAtPoint(e.point)
                    val col = pointsTable.columnAtPoint(e.point)
                    if (row < 0) return
                    // Ignore pending row (visual row 0 when pending exists)
                    if (dispatcher.getPendingStart() != null && row == 0) return
                    // Double-click toggles quick preview between Start and End
                    if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) {
                        val p = pointsModel.get(row)
                        val target = if (lastPreviewWasStart) {
                            // Seek near End (a bit before the end) to preview boundary
                            val dur = (p.endMs - p.startMs).toLong()
                            val back = minOf(250L, dur / 4)
                            (p.endMs - back).toLong().coerceAtLeast(p.startMs.toLong())
                        } else {
                            p.startMs.toLong()
                        }
                        player.seek(target)
                        lastPreviewWasStart = !lastPreviewWasStart
                        EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
                        return
                    }
                    // Single-click anywhere on the row seeks to Start, except on editable columns (Start/End/Label)
                    if (e.clickCount == 1 && SwingUtilities.isLeftMouseButton(e)) {
                        val header = pointsTable.tableHeader
                        val labelColName = pointsModel.getColumnName(4)
                        val startColName = pointsModel.getColumnName(1)
                        val endColName = pointsModel.getColumnName(2)
                        val clickedColName = pointsModel.getColumnName(col)
                        val editable = clickedColName == labelColName || clickedColName == startColName || clickedColName == endColName
                        if (!editable) {
                            val p = pointsModel.get(row)
                            player.seek(p.startMs.toLong())
                            EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
                        }
                    }
                } catch (_: Throwable) { }
            }
        })

        // Replace table with cards list
        rightPanel.add(cardsScroll, BorderLayout.CENTER)
        // Equal left/right margins for cards inside the right panel
        try { cardsListPanel.border = BorderFactory.createEmptyBorder(0, CARD_H_MARGIN, 0, CARD_H_MARGIN) } catch (_: Throwable) { }
        center.rightComponent = rightPanel
        center.resizeWeight = 1.0
        // Add bottom controls and center split
        add(center, BorderLayout.CENTER)
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

        // Apply pending-row renderer to all common column types
        try {
            pointsTable.setDefaultRenderer(java.lang.Object::class.java, pendingRenderer)
            pointsTable.setDefaultRenderer(java.lang.String::class.java, pendingRenderer)
            pointsTable.setDefaultRenderer(java.lang.Integer::class.java, pendingRenderer)
        } catch (_: Throwable) { }

        // Selection listener to cancel pending when user selects a completed row
        pointsTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val sel = pointsTable.selectedRow
                val hasPending = dispatcher.getPendingStart() != null
                if (hasPending && sel > 0) {
                    // Keep the same logical item selected after clearing pending
                    val target = sel - 1
                    dispatcher.clearPending()
                    // pointsModel will refresh via onPointsChanged; delay selecting until after refresh
                    EventQueue.invokeLater {
                        val rc = pointsModel.rowCount
                        if (target in 0 until rc) {
                            pointsTable.selectionModel.setSelectionInterval(target, target)
                            pointsTable.scrollRectToVisible(pointsTable.getCellRect(target, 0, true))
                        }
                    }
                }
            }
        }

        // Player callbacks → update UI and repaint timeline on EDT
        player.onTimeChanged = { t -> EventQueue.invokeLater { updateTimeUI(t) ; timeline.repaint() ; autoActivatePoint(t) } }
        player.onReady = { EventQueue.invokeLater { updateTimeUI(player.currentTimeMs()) ; timeline.repaint() ; updatePlayPauseButton() } }
        player.onStatusChanged = { _ -> EventQueue.invokeLater { updatePlayPauseButton() ; refreshUiAtCurrentTime() } }

        // Dispatcher callback to refresh UI
        dispatcher.onPointsChanged = {
            EventQueue.invokeLater {
                val pts = dispatcher.getCompletedPoints()
                pointsModel.setPoints(pts) // kept for potential table-related logic
                countBadge.text = "${pts.size} MARKED"
                rebuildCards()
                timeline.repaint()
                scheduleAutosave()
            }
        }

        installKeyBindings()
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

    private fun buildTableContextMenu(): JPopupMenu {
        val menu = JPopupMenu()
        val jump = JMenuItem("Go to start").apply { addActionListener { jumpToSelected() } }
        val del = JMenuItem("Delete").apply { addActionListener { deleteSelected() } }
        menu.add(jump)
        menu.add(del)
        return menu
    }

    // Build cards list based on dispatcher state (pending + completed)
    private fun rebuildCards() {
        try {
            cardsListPanel.removeAll()
            cardComponents.clear()
            val hasPending = dispatcher.getPendingStart() != null
            if (hasPending) {
                val s = dispatcher.getPendingStart()!!.toLong()
                val card = buildPendingCard(0, s)
                cardComponents.add(card)
                cardsListPanel.add(card)
                cardsListPanel.add(Box.createVerticalStrut(10))
            }
            val points = dispatcher.getCompletedPoints()
            points.forEachIndexed { i, p ->
                val visual = i + (if (hasPending) 1 else 0)
                val card = buildPointCard(visual, i, p)
                cardComponents.add(card)
                cardsListPanel.add(card)
                cardsListPanel.add(Box.createVerticalStrut(10))
            }
            cardsListPanel.revalidate()
            cardsListPanel.repaint()
            // Ensure divider respects fixed RIGHT_PANEL_WIDTH on first rebuild as well
            try {
                val sp = (this.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER)
                if (sp is JSplitPane) {
                    SwingUtilities.invokeLater {
                        val total = sp.size.width
                        if (total > 0) {
                            sp.setDividerLocation((total - RIGHT_PANEL_WIDTH).coerceAtLeast(0))
                        }
                    }
                }
            } catch (_: Throwable) { }
        } catch (_: Throwable) { }
    }

    private fun buildPendingCard(visualIndex: Int, startMs: Long): JComponent {
        val card = JPanel(BorderLayout())
        card.isOpaque = true
        card.background = UiStyles.SURFACE_HIGH
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
        val content = JPanel()
        content.isOpaque = false
        content.layout = BoxLayout(content, BoxLayout.Y_AXIS)
        content.add(JLabel("Pending…").apply { foreground = UiStyles.FG_SECONDARY; font = font.deriveFont(Font.BOLD) })
        content.add(Box.createVerticalStrut(4))
        content.add(JLabel(Timecode.format(startMs)).apply { foreground = UiStyles.FG_PRIMARY })
        card.add(content, BorderLayout.CENTER)
        // Highlight stripe similar to mock
        card.add(object: JComponent(){
            override fun getPreferredSize() = Dimension(4, 1)
            override fun paintComponent(g: Graphics) { g.color = UiStyles.LIME; g.fillRect(0,0,width,height) }
        }, BorderLayout.WEST)
        // not interactive
        card.toolTipText = "Pending point — press V to set End"
        // Selection state
        applyCardSelectionStyle(card, visualIndex == selectedVisualIndex)
        return card
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
        // update styles only on actual card components (exclude spacers)
        for (i in cardComponents.indices) {
            val c = cardComponents[i]
            applyCardSelectionStyle(c, i == selectedVisualIndex)
        }
        scrollCardIntoView(visualIndex)
    }

    private fun scrollCardIntoView(visualIndex: Int) {
        try {
            if (visualIndex < 0 || visualIndex >= cardComponents.size) return
            val comp = cardComponents[visualIndex]
            val r = comp.bounds
            cardsListPanel.scrollRectToVisible(r)
        } catch (_: Throwable) { }
    }

    private fun showEditDialog(p: PointV1) {
        val panel = JPanel(GridBagLayout())
        val gc = GridBagConstraints().apply { insets = Insets(4,4,4,4); anchor = GridBagConstraints.WEST }
        val tfStart = JTextField(Timecode.format(p.startMs.toLong()), 14)
        val tfEnd = JTextField(Timecode.format(p.endMs.toLong()), 14)
        val tfLabel = JTextField(p.label ?: "", 18)
        fun addRow(y:Int, label:String, comp:JComponent){
            gc.gridx=0; gc.gridy=y; panel.add(JLabel(label), gc)
            gc.gridx=1; panel.add(comp, gc)
        }
        addRow(0, "Start:", tfStart)
        addRow(1, "End:", tfEnd)
        addRow(2, "Label:", tfLabel)
        val res = JOptionPane.showConfirmDialog(this, panel, "Edit point", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        if (res == JOptionPane.OK_OPTION) {
            val ok = dispatcher.updatePoint(p.id, Timecode.parse(tfStart.text), Timecode.parse(tfEnd.text), tfLabel.text)
            if (!ok) maybeShowDispatcherHint()
        }
    }

    private fun confirmDelete(p: PointV1) {
        val name = Timecode.format(p.startMs.toLong()) + " - " + Timecode.format(p.endMs.toLong())
        val res = JOptionPane.showConfirmDialog(this, "Delete marked point $name?", "Confirm delete", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
        if (res == JOptionPane.OK_OPTION) {
            if (dispatcher.deletePoint(p.id)) {
                pointsModel.setPoints(dispatcher.getCompletedPoints())
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
                    pointsTable.selectionModel.setSelectionInterval(visual, visual)
                    pointsTable.scrollRectToVisible(pointsTable.getCellRect(visual, 0, true))
                }
            }
        }
    }

    private fun maybeShowDispatcherHint() {
        val m = dispatcher.consumeUserMessage() ?: return
        JOptionPane.showMessageDialog(this, m, "Hint", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun scheduleAutosave() {
        autosaveTimer.restart()
    }

    private fun autosaveNow() {
        try {
            val dir = projectDir ?: return
            val list = dispatcher.getCompletedPoints()
            EdlIO.writeForProjectDir(dir, EdlV1(points = list, version = 1))
        } catch (t: Throwable) {
            // Show non-fatal error
            Dialogs.showError(this, t, "Autosave failed")
        }
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

    // Renderer for the Action column: shows a small primary "Go" button
    private inner class ActionButtonRenderer : TableCellRenderer {
        private val button: JButton = UiStyles.primarySmallButton("Go") {}
        init {
            button.toolTipText = "Go to marked point"
        }
        override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
            try {
                button.isEnabled = !(dispatcher.getPendingStart() != null && row == 0)
            } catch (_: Throwable) { button.isEnabled = true }
            return button
        }
    }

    // Editor for the Action column: actual click handling
    private inner class ActionButtonEditor : AbstractCellEditor(), TableCellEditor {
        private var currentRow: Int = -1
        private val button: JButton = UiStyles.primarySmallButton("Go") {
            try {
                val row = currentRow
                if (row >= 0) {
                    // Ignore pending row
                    if (dispatcher.getPendingStart() != null && row == 0) {
                        cancelCellEditing(); return@primarySmallButton
                    }
                    val p = pointsModel.get(row)
                    player.seek(p.startMs.toLong())
                    EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
                }
            } catch (_: Throwable) { }
            stopCellEditing()
        }.apply {
            toolTipText = "Go to marked point"
        }
        override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
            currentRow = row
            button.isEnabled = !(dispatcher.getPendingStart() != null && row == 0)
            return button
        }
        override fun getCellEditorValue(): Any = "Go"
    }

    // Simple table model for points list with inline time editing (Start/End, Label)
    private inner class PointsTableModel : AbstractTableModel() {
        private var data: MutableList<PointV1> = mutableListOf()
        private val COL_ORDER = 0
        private val COL_START = 1
        private val COL_END = 2
        private val COL_DURATION = 3
        private val COL_LABEL = 4
        private val COL_ACTION = 5
        private val cols = arrayOf("#", "Start", "End", "Duration", "Label", "")

        private fun hasPending(): Boolean = dispatcher.getPendingStart() != null
        fun isPendingRow(rowIndex: Int): Boolean = hasPending() && rowIndex == 0
        private fun toDataIndex(rowIndex: Int): Int = if (hasPending()) rowIndex - 1 else rowIndex

        override fun getRowCount(): Int = data.size + if (hasPending()) 1 else 0
        override fun getColumnCount(): Int = cols.size
        override fun getColumnName(column: Int): String = cols[column]
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            if (isPendingRow(rowIndex)) return false
            return columnIndex == COL_START || columnIndex == COL_END || columnIndex == COL_LABEL || columnIndex == COL_ACTION
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            if (isPendingRow(rowIndex)) {
                val s = dispatcher.getPendingStart()?.toLong() ?: 0L
                return when (columnIndex) {
                    COL_ORDER -> "—"
                    COL_START -> Timecode.format(s)
                    COL_END -> "— no end yet"
                    COL_DURATION -> "—"
                    COL_LABEL -> ""
                    COL_ACTION -> "Go"
                    else -> ""
                }
            }
            val di = toDataIndex(rowIndex)
            val p = data[di]
            return when (columnIndex) {
                COL_ORDER -> di + 1
                COL_START -> Timecode.format(p.startMs.toLong())
                COL_END -> Timecode.format(p.endMs.toLong())
                COL_DURATION -> Timecode.format((p.endMs - p.startMs).toLong())
                COL_LABEL -> p.label ?: ""
                COL_ACTION -> "Go"
                else -> ""
            }
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (isPendingRow(rowIndex)) return // pending row not editable
            val di = toDataIndex(rowIndex)
            val p = data[di]
            when (columnIndex) {
                COL_START, COL_END, COL_LABEL -> {
                    val newStart = if (columnIndex == COL_START) Timecode.parse(aValue?.toString() ?: "0") else p.startMs.toLong()
                    val newEnd = if (columnIndex == COL_END) Timecode.parse(aValue?.toString() ?: "0") else p.endMs.toLong()
                    val newLabel = if (columnIndex == COL_LABEL) (aValue?.toString()) else p.label
                    val ok = dispatcher.updatePoint(p.id, newStart, newEnd, newLabel)
                    if (!ok) maybeShowDispatcherHint() else setPoints(dispatcher.getCompletedPoints())
                }
                COL_ACTION -> {
                    // handled by editor; no-op
                }
            }
        }

        fun setPoints(newData: List<PointV1>) {
            data = newData.toMutableList()
            fireTableDataChanged()
        }

        fun get(row: Int): PointV1 {
            if (isPendingRow(row)) throw IllegalStateException("Pending row has no completed point")
            return data[toDataIndex(row)]
        }
    }
}
