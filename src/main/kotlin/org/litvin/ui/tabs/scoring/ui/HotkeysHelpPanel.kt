package org.litvin.ui.tabs.scoring.ui

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.util.function.Supplier
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * HotkeysHelpPanel (E-SC-001 T6)
 *
 * Purpose
 * - Compact leaf Swing component that displays current scoring hotkeys.
 * - Visual aid for users to learn/recall shortcuts while scoring.
 *
 * Contracts
 * - No domain or service references inside.
 * - Accepts a supplier of hotkey mappings so it can stay in sync if hotkeys become configurable.
 * - Public API keeps it simple: provide a supplier in constructor; call [refresh()] to re-read values.
 *
 * Notes
 * - Initial population may come from a static map in the container. If hotkeys become dynamic,
 *   the container can pass a supplier reading from the central config.
 *
 * TODO(E-SC-001 T6): Centralize hotkey definitions/config so both bindings and this panel use a single source of truth.
 */
class HotkeysHelpPanel(
    private val hotkeysSupplier: Supplier<Map<String, String>>,
) : JPanel(BorderLayout()) {

    private val tableModel = object : DefaultTableModel(arrayOf("Key", "Action"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val table = JTable(tableModel)

    init {
        isOpaque = true
        background = Color(0x14, 0x14, 0x14)
        border = EmptyBorder(6, 8, 6, 8)

        val title = JLabel("Hotkeys")
        title.font = title.font.deriveFont(Font.BOLD)
        title.foreground = Color(0xCC, 0xCC, 0xCC)
        title.border = EmptyBorder(0, 0, 4, 0)

        styleTable()

        val scroll = JScrollPane(table)
        scroll.isOpaque = false
        scroll.viewport.isOpaque = false
        scroll.border = BorderFactory.createEmptyBorder()

        val wrap = JPanel(BorderLayout())
        wrap.isOpaque = false
        wrap.add(title, BorderLayout.NORTH)
        wrap.add(scroll, BorderLayout.CENTER)

        add(wrap, BorderLayout.CENTER)

        // Initial fill
        refresh()
    }

    private fun styleTable() {
        table.fillsViewportHeight = true
        table.showHorizontalLines = false
        table.showVerticalLines = false
        table.rowHeight = 20
        table.background = Color(0x18, 0x18, 0x18)
        table.foreground = Color(0xE0, 0xE0, 0xE0)
        table.selectionBackground = Color(0x33, 0x66, 0x99)
        table.selectionForeground = Color.WHITE
        table.tableHeader.reorderingAllowed = false
        table.tableHeader.resizingAllowed = false
        table.tableHeader.background = Color(0x18, 0x18, 0x18)
        table.tableHeader.foreground = Color(0xAA, 0xAA, 0xAA)
        table.tableHeader.font = table.tableHeader.font.deriveFont(Font.BOLD, table.tableHeader.font.size.toFloat())

        val keyRenderer = DefaultTableCellRenderer()
        keyRenderer.horizontalAlignment = SwingConstants.CENTER
        table.columnModel.getColumn(0).cellRenderer = keyRenderer

        table.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        table.preferredScrollableViewportSize = Dimension(240, 120)

        // Narrow key column
        table.columnModel.getColumn(0).preferredWidth = 80
        table.columnModel.getColumn(1).preferredWidth = 180
    }

    /** Re-read the hotkey mapping and update the table. */
    fun refresh() {
        val map = hotkeysSupplier.get()
        tableModel.setRowCount(0)
        // Keep a stable order: by key (case-insensitive)
        map.entries.sortedBy { it.key.lowercase() }.forEach { (key, action) ->
            tableModel.addRow(arrayOf(formatKey(key), action))
        }
    }

    private fun formatKey(key: String): String {
        // Normalize common representations just for display (e.g., "Ctrl+S" vs "control S")
        return key
            .replace("control", "Ctrl", ignoreCase = true)
            .replace("shift", "Shift", ignoreCase = true)
            .replace("alt", "Alt", ignoreCase = true)
            .replace("meta", "Meta", ignoreCase = true)
    }
}