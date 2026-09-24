package org.litvin.ui.tabs.projects.components

import org.litvin.ui.UiStyles
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ProjectCard(
    title: String,
    secondary: String,
    titleComponentName: String? = null,
    openButtonComponentName: String? = null,
    renameButtonComponentName: String? = null,
    deleteButtonComponentName: String? = null,
    /** Shows a red crossed-out video icon with this tooltip before the video path. Null shows no icon. */
    videoMissingTooltip: String? = null,
    videoMissingComponentName: String? = null,
    /** A tooltip that tells why the delete button is disabled. Null enables the delete button. */
    deleteDisabledReason: String? = null,
    /** Texts for the [ProjectsTableColumns.FIGURE_COLUMNS]. Null shows no figure columns. */
    figures: List<String>? = null,
    figureComponentNames: List<String?> = emptyList(),
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
) : JPanel(BorderLayout()) {
    init {
        isOpaque = true
        background = UiStyles.CARD_BG
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiStyles.CARD_BORDER, 1, true),
            BorderFactory.createEmptyBorder(10, 12, 10, 12),
        )
        alignmentX = 0f
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        minimumSize = Dimension(200, 48)

        add(textContent(title, secondary, titleComponentName, videoMissingTooltip, videoMissingComponentName), BorderLayout.CENTER)

        val openButton = onOpen?.let { openButton(openButtonComponentName, it) }
        val deleteButton = onDelete?.let { deleteButton(deleteButtonComponentName, it) }?.apply {
            if (deleteDisabledReason != null) {
                isEnabled = false
                toolTipText = deleteDisabledReason
            }
        }
        val actions = actionsPanel(onRename?.let { renameButton(renameButtonComponentName, it) }, deleteButton, openButton)
        openButton?.let { button ->
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) button.doClick()
                }
            })
        }
        val east = JPanel(BorderLayout()).apply { isOpaque = false }
        if (figures != null) {
            east.add(
                ProjectsTableColumns.figureCells(figures, figureComponentNames, UiStyles.FG_PRIMARY),
                BorderLayout.CENTER,
            )
        }
        if (actions.componentCount > 0) east.add(actions, BorderLayout.EAST)
        if (east.componentCount > 0) add(east, BorderLayout.EAST)
        if (onOpen != null) {
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    companion object {
        /** Returns the width of the actions of a list row: the rename, the delete, and the open buttons. */
        fun rowActionsWidth(): Int =
            actionsPanel(renameButton(null) {}, deleteButton(null) {}, openButton(null) {}).preferredSize.width

        private fun renameButton(componentName: String?, onRename: () -> Unit): JButton =
            UiStyles.smallIconButton(UiStyles.pencilIcon(), "Rename project") { onRename() }.apply { name = componentName }

        private fun deleteButton(componentName: String?, onDelete: () -> Unit): JButton =
            UiStyles.smallIconButton(UiStyles.deleteIcon(), "Delete project") { onDelete() }.apply {
                name = componentName
                // Swing makes no gray variant of a font icon. Thus the disabled button needs its own icon.
                disabledIcon = UiStyles.deleteIcon(color = UiStyles.FG_DISABLED)
            }

        private fun openButton(componentName: String?, onOpen: () -> Unit): JButton =
            UiStyles.primarySmallButton("Open Project") { onOpen() }.apply { name = componentName }

        private fun actionsPanel(renameButton: JButton?, deleteButton: JButton?, openButton: JButton?): JPanel {
            // BorderLayout makes the buttons as tall as the row.
            val iconButtons = JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                renameButton?.let { add(it, BorderLayout.WEST) }
                deleteButton?.let { add(it, BorderLayout.EAST) }
            }
            return JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                if (iconButtons.componentCount > 0) add(iconButtons, BorderLayout.WEST)
                openButton?.let { add(it, BorderLayout.EAST) }
            }
        }
    }

    private fun textContent(
        title: String,
        secondary: String,
        titleComponentName: String?,
        videoMissingTooltip: String?,
        videoMissingComponentName: String?,
    ): JComponent {
        return JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JLabel(title).apply {
                name = titleComponentName
                foreground = UiStyles.FG_PRIMARY
                font = font.deriveFont(Font.BOLD, font.size2D + 1f)
            })
            add(Box.createVerticalStrut(4))
            add(JLabel(secondary).apply {
                foreground = UiStyles.FG_SECONDARY
                if (videoMissingTooltip != null) {
                    icon = UiStyles.videoMissingIcon()
                    iconTextGap = 6
                    toolTipText = videoMissingTooltip
                    name = videoMissingComponentName
                }
            })
        }
    }
}
