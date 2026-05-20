package org.litvin.ui.tabs.markup.ui

import org.litvin.shared.util.Timecode
import org.litvin.ui.UiStyles
import org.litvin.ui.tabs.markup.AutosaveState
import org.litvin.ui.tabs.markup.MarkupActions
import org.litvin.ui.tabs.markup.MarkupViewState
import org.litvin.ui.tabs.markup.PointDto
import org.litvin.ui.tabs.markup.PointPatch
import java.awt.BorderLayout
import java.awt.Component
import java.awt.EventQueue
import javax.swing.AbstractCellEditor
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

/**
 * Encapsulated table view for markup points.
 *
 * Responsibilities:
 * - Provide a JTable with model/renderers/editors to display and edit points list
 * - Expose a narrow API to push state from the container and to emit user actions via MarkupActions
 *
 * Notes:
 * - Uses only Markup contracts (no direct domain dependencies).
 * - Keeps a pending row at the top when state.pendingDraftStartMs != null (non-editable).
 */
class PointsTableView(
    private val actions: MarkupActions,
) : JPanel(BorderLayout()) {

    private val model = PointsTableModel()
    private val table = JTable(model)

    init {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.rowHeight = 24
        // Action column renderer/editor
        table.columnModel.getColumn(model.COL_ACTION).apply {
            cellRenderer = ActionButtonRenderer()
            cellEditor = ActionButtonEditor()
            preferredWidth = 52
            maxWidth = 64
            minWidth = 40
        }
        table.columnModel.getColumn(model.COL_ORDER).preferredWidth = 36
        table.columnModel.getColumn(model.COL_DURATION).preferredWidth = 84
        table.autoCreateRowSorter = false

        // Selection -> actions
        table.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val row = table.selectedRow
                if (row >= 0) {
                    // Ignore selecting the pending row
                    if (model.isPendingRow(row)) return@addListSelectionListener
                    actions.selectByVisualIndex(row)
                }
            }
        }

        add(JScrollPane(table), BorderLayout.CENTER)
    }

    fun setState(state: MarkupViewState) {
        model.setState(state)
        // Reflect selection from state
        val target = state.selectedVisualIndex ?: -1
        if (target >= 0 && target < model.rowCount) {
            if (table.selectedRow != target) {
                try { table.setRowSelectionInterval(target, target) } catch (_: Throwable) {}
            }
        } else {
            try { table.clearSelection() } catch (_: Throwable) {}
        }
    }

    private inner class ActionButtonRenderer : TableCellRenderer {
        private val button: JButton = UiStyles.primarySmallButton("Go") {}
        init { button.toolTipText = "Go to marked point" }
        override fun getTableCellRendererComponent(
            table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            button.isEnabled = !model.isPendingRow(row)
            return button
        }
    }

    private inner class ActionButtonEditor : AbstractCellEditor(), TableCellEditor {
        private var currentRow: Int = -1
        private val button: JButton = UiStyles.primarySmallButton("Go") {
            val row = currentRow
            if (row >= 0 && !model.isPendingRow(row)) {
                val p = model.get(row)
                actions.seekTo(p.startMs)
                EventQueue.invokeLater { try { table.requestFocusInWindow() } catch (_: Throwable) {} }
            }
            stopCellEditing()
        }.apply { toolTipText = "Go to marked point" }

        override fun getTableCellEditorComponent(
            table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int
        ): Component {
            currentRow = row
            button.isEnabled = !model.isPendingRow(row)
            return button
        }
        override fun getCellEditorValue(): Any = "Go"
    }

    private inner class PointsTableModel : AbstractTableModel() {
        private var state: MarkupViewState = MarkupViewState(
            isPlaying = false,
            currentTimeMs = 0,
            selectedVisualIndex = null,
            pendingDraftStartMs = null,
            points = emptyList(),
            autosave = AutosaveState(pending = false, lastSavedAtMs = null),
        )

        // Columns
        val COL_ORDER = 0
        val COL_START = 1
        val COL_END = 2
        val COL_DURATION = 3
        val COL_LABEL = 4
        val COL_ACTION = 5
        private val cols = arrayOf("#", "Start", "End", "Duration", "Label", "")

        fun setState(newState: MarkupViewState) {
            this.state = newState
            fireTableDataChanged()
        }

        fun isPendingRow(rowIndex: Int): Boolean = state.pendingDraftStartMs != null && rowIndex == 0
        private fun hasPending(): Boolean = state.pendingDraftStartMs != null
        private fun toDataIndex(rowIndex: Int): Int = if (hasPending()) rowIndex - 1 else rowIndex

        override fun getRowCount(): Int = (state.points.size) + if (hasPending()) 1 else 0
        override fun getColumnCount(): Int = cols.size
        override fun getColumnName(column: Int): String = cols[column]
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            if (isPendingRow(rowIndex)) return false
            return columnIndex == COL_START || columnIndex == COL_END || columnIndex == COL_LABEL || columnIndex == COL_ACTION
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            if (isPendingRow(rowIndex)) {
                val s = state.pendingDraftStartMs ?: 0L
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
            val p = state.points[di]
            return when (columnIndex) {
                COL_ORDER -> di + 1
                COL_START -> Timecode.format(p.startMs)
                COL_END -> p.endMs?.let { Timecode.format(it) } ?: "—"
                COL_DURATION -> p.endMs?.let { Timecode.format(it - p.startMs) } ?: "—"
                COL_LABEL -> p.label ?: ""
                COL_ACTION -> "Go"
                else -> ""
            }
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (isPendingRow(rowIndex)) return
            val di = toDataIndex(rowIndex)
            val p = state.points[di]
            when (columnIndex) {
                COL_START, COL_END, COL_LABEL -> {
                    val newStart = if (columnIndex == COL_START) Timecode.parse(aValue?.toString() ?: "0") else null
                    val newEnd = if (columnIndex == COL_END) Timecode.parse(aValue?.toString() ?: "0") else null
                    val newLabel = if (columnIndex == COL_LABEL) (aValue?.toString()) else null
                    actions.editPoint(
                        id = p.id,
                        patch = PointPatch(
                            startMs = newStart,
                            endMs = newEnd,
                            label = newLabel,
                            flags = null,
                        )
                    )
                }
                COL_ACTION -> { /* handled by editor */ }
            }
        }

        fun get(row: Int): PointDto {
            if (isPendingRow(row)) throw IllegalStateException("Pending row has no completed point")
            return state.points[toDataIndex(row)]
        }
    }
}