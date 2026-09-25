package org.litvin.ui.tabs.export

import org.litvin.RenderJob
import org.litvin.export.ExportCardInfo
import org.litvin.export.RenderService
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.UserDialogService
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Shows the exports that wait in the queue. Each card has the settings of the export and a "Cancel export" button.
 */
class ExportQueueList(
    private val renderService: RenderService,
    private val dialogs: UserDialogService,
) {
    private val column = ExportCardColumn()
    private val panel = object : JPanel(BorderLayout()) {
        // Show at most about two cards. The scroll bar shows the other cards.
        override fun getPreferredSize(): Dimension =
            Dimension(super.getPreferredSize().width, column.contentHeight().coerceAtMost(MAX_HEIGHT_PX) + 2)
    }.apply {
        background = UiStyles.CARD_BG
        add(column.scrollPane, BorderLayout.CENTER)
    }
    private var shownIds: List<String> = emptyList()

    fun component(): JComponent = panel

    fun setJobs(jobs: List<RenderJob>) {
        // The queue sends a snapshot for each progress step of the current export. Build the cards only when the queue changes.
        val ids = jobs.map(RenderJob::id)
        if (ids == shownIds) return
        shownIds = ids
        val cards = jobs.map { job ->
            ExportJobCard(
                componentPrefix = "export-queued-${job.id}",
                onOpenFolder = { ExportJobCard.openFolder(job.outputPath, panel, dialogs) },
                onCancel = { cancel(job) },
                showProgress = false,
            ).apply { update(ExportCardInfo.of(job)) }
        }
        column.show(cards, JLabel())
        panel.revalidate()
    }

    private fun cancel(job: RenderJob) {
        if (dialogs.confirm(panel, "Cancel the queued export?", "Confirm")) {
            renderService.cancelQueued(job.id)
        }
    }

    private companion object {
        const val MAX_HEIGHT_PX = 420
    }
}
