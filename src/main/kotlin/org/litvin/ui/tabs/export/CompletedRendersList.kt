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
        scroll.preferredSize = Dimension(400, 200)
        add(scroll, BorderLayout.CENTER)

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 4))
        buttons.background = UiStyles.CARD_BG
        val openFolderBtn = JButton("Open folder")
        UiStyles.styleSecondary(openFolderBtn)
        openFolderBtn.addActionListener { openSelectedFolder() }
        val clearBtn = JButton("Clear Finished")
        UiStyles.styleSecondary(clearBtn)
        clearBtn.addActionListener { clearAll() }
        buttons.add(openFolderBtn)
        buttons.add(clearBtn)
        add(buttons, BorderLayout.SOUTH)
    }

    init {
        list.cellRenderer = object: DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                c.background = if (isSelected) UiStyles.CARD_BG.brighter() else UiStyles.CARD_BG
                c.foreground = UiStyles.FG_PRIMARY
                if (c is JLabel) {
                    val item = value as? CompletedRender
                    c.text = if (item != null) formatItem(item) else (value?.toString() ?: "")
                }
                return c
            }
        }
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