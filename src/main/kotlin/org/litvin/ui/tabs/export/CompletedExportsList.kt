package org.litvin.ui.tabs.export

import org.litvin.CompletedRender
import org.litvin.export.CompletedRendersRepository
import org.litvin.export.ExportCardInfo
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.UserDialogService
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.text.DateFormat
import java.util.Date
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * The "Completed Exports" card: one card for each finished export, with the same information
 * as the active export and an "Open folder" button. No dependency on other tabs.
 */
class CompletedExportsList(
    private val repository: CompletedRendersRepository,
    private val dialogs: UserDialogService,
) {
    private val column = ExportCardColumn()
    private val placeholder = JLabel("No completed exports").apply { foreground = UiStyles.FG_SECONDARY }
    private var shownItems: List<CompletedRender>? = null

    private val panel: JPanel = JPanel(BorderLayout()).apply {
        background = UiStyles.CARD_BG
        add(column.scrollPane, BorderLayout.CENTER)
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 4))
        buttons.background = UiStyles.CARD_BG
        val clearButton = JButton("Clear list").apply {
            name = "export-completed-clear"
            toolTipText = "Remove all entries from this list. The exported files stay on the disk."
        }
        UiStyles.styleSecondary(clearButton)
        clearButton.addActionListener { clearAll() }
        buttons.add(clearButton)
        add(buttons, BorderLayout.SOUTH)
    }

    init {
        show(emptyList())
    }

    fun component(): JComponent = panel

    fun refreshFromStore() {
        val items = try {
            repository.loadAll()
        } catch (_: Throwable) {
            return
        }
        show(items)
    }

    private fun show(items: List<CompletedRender>) {
        if (items == shownItems) return
        shownItems = items
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val cards = items.mapIndexed { index, item ->
            ExportJobCard(
                componentPrefix = "export-completed-$index",
                onOpenFolder = { ExportJobCard.openFolder(item.outputPath, panel, dialogs) },
                onCancel = null,
                showProgress = false,
            ).apply {
                update(ExportCardInfo.of(item))
                item.createdAtEpochMs.takeIf { it > 0 }?.let { setExtraRow("Finished", dateFormat.format(Date(it))) }
            }
        }
        column.show(cards, placeholder)
    }

    private fun clearAll() {
        if (dialogs.confirm(panel, "Clear the list of completed exports? The exported files stay on the disk.", "Confirm")) {
            try { repository.clear() } catch (_: Throwable) { }
            show(emptyList())
        }
    }
}
