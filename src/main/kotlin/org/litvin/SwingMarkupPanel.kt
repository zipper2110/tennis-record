package org.litvin

import org.litvin.media.PlayerStatus
import org.litvin.media.VlcjSwingMediaPlayerAdapter
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

    // Media
    private val player = VlcjSwingMediaPlayerAdapter()

    // EDL Dispatcher
    private val dispatcher = MarkupDispatcher()

    // Current project manifest path (projectDir derived from it)
    private var manifestPath: String? = null
    private var projectDir: String? = null

    // UI controls
    private val btnPlayPause = JButton("Play")
    private val btnStart = JButton("Point Start [C]")
    private val btnEnd = JButton("Point End [V]")
    private val timeLabel = JLabel("00:00:00.000")
    private val countBadge = JLabel("0 MARKED")

    private val pointsModel = PointsTableModel()
    private val pointsTable = JTable(pointsModel)
    private var lastPreviewWasStart = true
    
    // Renders pending row (row 0) in italic gray when a Start is set but End not yet
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
    }

    override fun removeNotify() {
        try { KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(spaceDispatcher) } catch (_: Throwable) { }
        super.removeNotify()
    }

    init {
        isOpaque = true
        background = Color(0x16, 0x16, 0x16)
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        // Top: Transport and point controls
        val top = JPanel()
        top.layout = BoxLayout(top, BoxLayout.X_AXIS)
        top.isOpaque = false
        btnPlayPause.addActionListener { togglePlayPause() }
        btnStart.addActionListener { onStartAtPlayhead() }
        btnEnd.addActionListener { onEndAtPlayhead() }
        listOf(btnPlayPause, btnStart, btnEnd).forEach {
            top.add(it); top.add(Box.createHorizontalStrut(8))
        }
        top.add(Box.createHorizontalStrut(12))
        top.add(JLabel("Time:"))
        top.add(Box.createHorizontalStrut(4))
        top.add(timeLabel)
        add(top, BorderLayout.NORTH)

        // Center: video and points list side-by-side
        val center = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
        center.leftComponent = player.component

        val rightPanel = JPanel(BorderLayout())
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
        
        // Install per-row mouse interactions
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

        rightPanel.add(JScrollPane(pointsTable), BorderLayout.CENTER)
        center.rightComponent = rightPanel
        center.resizeWeight = 0.7
        add(center, BorderLayout.CENTER)
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
        player.onReady = { EventQueue.invokeLater { updateTimeUI(player.currentTimeMs()) ; timeline.repaint() ; updatePlayPauseText() } }
        player.onStatusChanged = { _ -> EventQueue.invokeLater { updatePlayPauseText() ; refreshUiAtCurrentTime() } }

        // Dispatcher callback to refresh UI
        dispatcher.onPointsChanged = {
            EventQueue.invokeLater {
                val pts = dispatcher.getCompletedPoints()
                pointsModel.setPoints(pts)
                countBadge.text = "${pts.size} MARKED"
                timeline.repaint()
                scheduleAutosave()
            }
        }

        installKeyBindings()
    }

    fun setProjectManifest(path: String) {
        manifestPath = path
        projectDir = File(path).parentFile.absolutePath
        // Load manifest and media
        try {
            val manifest = ManifestIO.read(path)
            val src = manifest.sourceVideo
            if (src.isNullOrBlank() || !File(src).exists()) {
                SwingDialogUtils.showError(this, IllegalStateException("Source video missing"), "Select source video for project: ${manifest.name}")
                return
            }
            // Load EDL if present
            val edl = EdlIO.readForProjectDir(projectDir!!)
            dispatcher.setPoints(edl.points)
            // Load media
            player.load(File(src))
        } catch (t: Throwable) {
            SwingDialogUtils.showError(this, t, "Failed to load project")
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

    private fun jumpToSelected() {
        val row = pointsTable.selectedRow
        if (row >= 0) {
            // Ignore action on pending row (row 0 when pending exists)
            if (dispatcher.getPendingStart() != null && row == 0) return
            val p = pointsModel.get(row)
            player.seek(p.startMs.toLong())
            // Ensure timeline/playhead updates immediately even when paused and focus player for Space
            EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
        }
    }

    private fun deleteSelected() {
        val row = pointsTable.selectedRow
        if (row >= 0) {
            // Ignore delete on pending row (row 0 when pending exists)
            if (dispatcher.getPendingStart() != null && row == 0) return
            val p = pointsModel.get(row)
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
    }

    private fun autoActivatePoint(t: Long) {
        val p = dispatcher.getCompletedPoints()
        val hasPending = dispatcher.getPendingStart() != null
        // half-open interval [start, end)
        val idx = p.indexOfFirst { it.startMs <= t && t < it.endMs }
        if (idx >= 0) {
            val visual = idx + if (hasPending) 1 else 0
            pointsTable.selectionModel.setSelectionInterval(visual, visual)
            pointsTable.scrollRectToVisible(pointsTable.getCellRect(visual, 0, true))
        } else {
            // If a pending row exists and is currently selected, keep it selected
            val sel = pointsTable.selectedRow
            if (!(hasPending && sel == 0)) {
                pointsTable.clearSelection()
            }
        }
    }

    private fun updatePlayPauseText() {
        btnPlayPause.text = when (player.status()) {
            PlayerStatus.PLAYING -> "Pause"
            else -> "Play"
        }
    }

    private fun updateTimeUI(ms: Long) {
        timeLabel.text = Timecode.format(max(0, ms))
    }

    private fun refreshUiAtCurrentTime() {
        val t = player.currentTimeMs()
        updateTimeUI(t)
        timeline.repaint()
        autoActivatePoint(t)
    }

    private fun togglePlayPause() {
        when (player.status()) {
            PlayerStatus.PLAYING -> player.pause()
            else -> player.play()
        }
    }

    private fun onStartAtPlayhead() {
        dispatcher.onPointStart(player.currentTimeMs())
        maybeShowDispatcherHint()
        // Select the pending row (visual row 0) and scroll it into view
        EventQueue.invokeLater {
            if (dispatcher.getPendingStart() != null) {
                pointsTable.selectionModel.setSelectionInterval(0, 0)
                pointsTable.scrollRectToVisible(pointsTable.getCellRect(0, 0, true))
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
            SwingDialogUtils.showError(this, t, "Autosave failed")
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
                JComponent.WHEN_IN_FOCUSED_WINDOW,
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
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

    // Renderer for the Action column: shows a small "Go" button
    private inner class ActionButtonRenderer : JButton(), TableCellRenderer {
        init {
            isOpaque = true
            text = "Go"
            toolTipText = "Go to marked point"
            margin = Insets(2, 6, 2, 6)
        }
        override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
            try {
                isEnabled = !(dispatcher.getPendingStart() != null && row == 0)
            } catch (_: Throwable) { isEnabled = true }
            return this
        }
    }

    // Editor for the Action column: actual click handling
    private inner class ActionButtonEditor : AbstractCellEditor(), TableCellEditor {
        private val button = JButton("Go").apply {
            margin = Insets(2, 6, 2, 6)
            toolTipText = "Go to marked point"
            addActionListener {
                try {
                    val row = currentRow
                    if (row >= 0) {
                        // Ignore pending row
                        if (dispatcher.getPendingStart() != null && row == 0) {
                            cancelCellEditing(); return@addActionListener
                        }
                        val p = pointsModel.get(row)
                        player.seek(p.startMs.toLong())
                        EventQueue.invokeLater { refreshUiAtCurrentTime(); player.component.requestFocusInWindow() }
                    }
                } catch (_: Throwable) { }
                stopCellEditing()
            }
        }
        private var currentRow: Int = -1
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
