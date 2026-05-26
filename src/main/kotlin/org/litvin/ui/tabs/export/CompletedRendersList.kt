package org.litvin.ui.tabs.export

import org.litvin.CompletedRender
import org.litvin.CompletedRendersStore
import org.litvin.RenderJob
import org.litvin.ui.UiStyles
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import javax.swing.*
import org.litvin.ui.commons.applyDarkScrollbar

/**
 * Extracted from SwingExportPanel: encapsulates the "Completed Renders" card
 * with list, renderer, and actions. No dependency on other tabs.
 */
class CompletedRendersList {
    private val model = DefaultListModel<CompletedRender>()
    private val list = JList(model)

    private val panel: JPanel = JPanel(BorderLayout()).apply {
        background = UiStyles.CARD_BG
        val scroll = JScrollPane(list)
        scroll.background = UiStyles.CARD_BG
        scroll.border = BorderFactory.createEmptyBorder()
        // Ensure viewport has same dark background and is opaque (avoid white gaps)

        val vp = scroll.viewport
        vp.background = UiStyles.CARD_BG
        vp.isOpaque = true

        // Let the scroll take all available vertical space in its parent card
        applyDarkScrollbar(scroll, UiStyles.CARD_BG)
        add(scroll, BorderLayout.CENTER)

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 4))
        buttons.background = UiStyles.CARD_BG
        val clearBtn = JButton("Clear Finished")
        UiStyles.styleSecondary(clearBtn)
        clearBtn.addActionListener { clearAll() }
        buttons.add(clearBtn)
        add(buttons, BorderLayout.SOUTH)
    }

    init {
        // Tighten list visuals
        list.isOpaque = true
        list.background = UiStyles.CARD_BG
        list.foreground = UiStyles.FG_PRIMARY
        // Slightly taller to allow visible spacing around 40px content cards
        list.fixedCellHeight = 48
        list.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)

        // Custom renderer with a per-item "Open Folder" button on the right
        list.cellRenderer = object : ListCellRenderer<CompletedRender> {
            private val openText = "Open Folder"
            override fun getListCellRendererComponent(list: JList<out CompletedRender>?, value: CompletedRender?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                // Outer row provides spacing between rounded cards
                val outer = JPanel(BorderLayout())
                outer.isOpaque = true
                outer.background = UiStyles.CARD_BG
                outer.border = BorderFactory.createEmptyBorder(4, 8, 4, 8) // top/btm spacing + side gutters

                // Inner card replicates Projects list card style
                val card = JPanel(BorderLayout(8, 0))
                card.isOpaque = true
                card.background = UiStyles.SURFACE_HIGH
                card.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiStyles.CARD_BORDER, 1, true),
                    BorderFactory.createEmptyBorder(6, 10, 6, 10)
                )

                val label = JLabel(value?.let { formatItem(it) } ?: "")
                label.foreground = UiStyles.FG_PRIMARY
                card.add(label, BorderLayout.CENTER)

                val btn = UiStyles.primarySmallButton(openText) { }
                // Ensure text is black like "Open Project" on Projects tab
//                btn.foreground = java.awt.Color.BLACK
                btn.isEnabled = true // renderer component; click is handled via mouse listener
                card.add(btn, BorderLayout.EAST)

                // Selection state subtly brightens the card background
                if (isSelected) {
                    card.background = UiStyles.SURFACE_HIGH.brighter()
                }

                outer.add(card, BorderLayout.CENTER)
                return outer
            }
        }
        // Measure preferred width of the inline action for precise hit area
        val openActionWidth = try { UiStyles.primarySmallButton("Open Folder") { }.preferredSize.width } catch (_: Throwable) { 120 }
        // Mouse handler to trigger per-item button action when clicking near the right edge
        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val idx = list.locationToIndex(e.point)
                if (idx < 0) return
                val bounds = list.getCellBounds(idx, idx) ?: return
                val margin = 22 // account for outer (8) + inner (10) paddings and a small slop
                if (e.x >= bounds.x + bounds.width - openActionWidth - margin) {
                    list.selectedIndex = idx
                    openSelectedFolder()
                }
            }
        })
    }

    fun component(): JComponent = panel

    fun refreshFromStore() {
        try {
            val items = CompletedRendersStore.loadAll()
            model.removeAllElements()
            items.forEach { model.addElement(it) }
        } catch (_: Throwable) { }
    }

    fun addCompleted(job: CompletedRender) {
        model.addElement(job)
    }

    fun addCompletedFrom(job: RenderJob) {
        val item = CompletedRender(
            id = job.id,
            projectId = job.projectId,
            projectName = job.projectName,
            outputPath = job.outputPath,
            fileName = File(job.outputPath).name,
            encoderLabel = job.encoderLabel,
            outWidth = job.outWidth,
            outHeight = job.outHeight,
            bytesWritten = job.bytesWritten,
            includeScoreboard = job.includeScoreboard,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        addCompleted(item)
    }

    private fun clearAll() {
        val r = JOptionPane.showConfirmDialog(panel, "Clear all completed entries?", "Confirm", JOptionPane.YES_NO_OPTION)
        if (r == JOptionPane.YES_OPTION) {
            try { CompletedRendersStore.clear() } catch (_: Throwable) { }
            model.removeAllElements()
        }
    }

    private fun openSelectedFolder() {
        val sel = list.selectedValue ?: return
        try {
            val f = File(sel.outputPath)
            val dir = f.parentFile ?: return
            try {
                java.awt.Desktop.getDesktop().open(dir)
            } catch (_: Throwable) {
                Runtime.getRuntime().exec(arrayOf("explorer.exe", dir.absolutePath))
            }
        } catch (t: Throwable) {
            JOptionPane.showMessageDialog(panel, t.message ?: t.toString(), "Failed to open folder", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun formatItem(item: CompletedRender): String {
        val size = formatSize(item.bytesWritten)
        val sb = if (item.includeScoreboard) "  ·  Scoreboard" else ""
        val res = if (item.outHeight >= 2160 || item.outWidth >= 3840) "4K" else "1080p"
        val proj = item.projectName?.takeIf { it.isNotBlank() }
        val left = if (proj != null) "[$proj] ${item.fileName}" else item.fileName
        return "$left$sb  —  ${item.encoderLabel} / $res  —  $size"
    }

    private fun formatSize(bytes: Long): String {
        val kb = 1000.0
        val mb = kb * 1000
        val gb = mb * 1000
        return when {
            bytes >= gb -> String.format("%.2f GB", bytes / gb)
            bytes >= mb -> String.format("%.2f MB", bytes / mb)
            bytes >= kb -> String.format("%.2f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}