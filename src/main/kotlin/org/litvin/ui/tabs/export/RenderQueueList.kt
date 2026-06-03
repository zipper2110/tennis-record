package org.litvin.ui.tabs.export

import org.litvin.RenderJob
import org.litvin.RenderQueueManager
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.applyDarkScrollbar
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.io.File
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JButton
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListCellRenderer

/**
 * Shows render jobs that are queued but not yet running.
 */
class RenderQueueList {
    private val model = DefaultListModel<RenderJob>()
    private val list = JList(model)

    private val panel: JPanel = JPanel(BorderLayout()).apply {
        background = UiStyles.CARD_BG
        val scroll = JScrollPane(list)
        scroll.background = UiStyles.CARD_BG
        scroll.border = BorderFactory.createEmptyBorder()
        scroll.viewport.background = UiStyles.CARD_BG
        scroll.viewport.isOpaque = true
        applyDarkScrollbar(scroll, UiStyles.CARD_BG)
        add(scroll, BorderLayout.CENTER)
        preferredSize = java.awt.Dimension(10, 150)
    }

    init {
        list.isOpaque = true
        list.background = UiStyles.CARD_BG
        list.foreground = UiStyles.FG_PRIMARY
        list.fixedCellHeight = 68
        list.visibleRowCount = 3
        list.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)

        list.cellRenderer = object : ListCellRenderer<RenderJob> {
            override fun getListCellRendererComponent(
                list: JList<out RenderJob>?,
                value: RenderJob?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val outer = JPanel(BorderLayout())
                outer.isOpaque = true
                outer.background = UiStyles.CARD_BG
                outer.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)

                val card = JPanel(BorderLayout(10, 0))
                card.isOpaque = true
                card.background = if (isSelected) UiStyles.SURFACE_HIGH.brighter() else UiStyles.SURFACE_HIGH
                card.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiStyles.CARD_BORDER, 1, true),
                    BorderFactory.createEmptyBorder(7, 10, 7, 10)
                )

                val text = JLabel(value?.let { formatItem(it) } ?: "")
                text.foreground = UiStyles.FG_PRIMARY
                card.add(text, BorderLayout.CENTER)

                val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
                actions.isOpaque = false
                val cancelButton = JButton("Cancel")
                UiStyles.styleSecondary(cancelButton)
                actions.add(cancelButton)
                card.add(actions, BorderLayout.EAST)

                outer.add(card, BorderLayout.CENTER)
                return outer
            }
        }

        val cancelActionWidth = try {
            JButton("Cancel").also { UiStyles.styleSecondary(it) }.preferredSize.width
        } catch (_: Throwable) { 90 }
        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val idx = list.locationToIndex(e.point)
                if (idx < 0) return
                val bounds = list.getCellBounds(idx, idx) ?: return
                val margin = 22
                if (e.x >= bounds.x + bounds.width - cancelActionWidth - margin) {
                    list.selectedIndex = idx
                    cancelSelected()
                }
            }
        })
    }

    fun component(): JComponent = panel

    fun setJobs(jobs: List<RenderJob>) {
        model.removeAllElements()
        jobs.forEach { model.addElement(it) }
    }

    private fun cancelSelected() {
        val job = list.selectedValue ?: return
        val r = JOptionPane.showConfirmDialog(panel, "Cancel queued render?", "Confirm", JOptionPane.YES_NO_OPTION)
        if (r == JOptionPane.YES_OPTION) {
            RenderQueueManager.cancelQueued(job.id)
        }
    }

    private fun formatItem(job: RenderJob): String {
        val file = File(job.outputPath).name
        val project = job.projectName?.takeIf { it.isNotBlank() }?.let { "[$it] " }.orEmpty()
        val resolution = if (job.outHeight >= 2160 || job.outWidth >= 3840) "4K" else "1080p"
        val trim = when {
            job.favoriteOnly -> "Idle trim: ${job.edlSnapshot.size} favorite points"
            job.idleTrim -> "Idle trim: ${job.edlSnapshot.size} points"
            else -> "Full render"
        }
        val scoreboard = if (job.includeScoreboard) "Scoreboard on" else "Scoreboard off"
        val details = listOf(
            "Preset: ${job.presetId}",
            "Resolution: $resolution (${job.outWidth} x ${job.outHeight})",
            "Encoder: ${job.encoderLabel}",
            trim,
            scoreboard,
            "Output: ${job.outputPath}"
        ).joinToString("  |  ")
        return "<html><b>${escape(project + file)}</b><br><span style='color:#ADAAAA'>${escape(details)}</span></html>"
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
