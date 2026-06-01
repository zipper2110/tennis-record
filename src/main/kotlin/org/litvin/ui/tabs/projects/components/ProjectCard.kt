package org.litvin.ui.tabs.projects.components

import org.litvin.ui.UiStyles
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ProjectCard(
    title: String,
    secondary: String,
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

        add(textContent(title, secondary), BorderLayout.CENTER)

        if (onOpen != null) {
            val openButton = UiStyles.primarySmallButton("Open Project") { onOpen() }
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                add(openButton, BorderLayout.EAST)
            }, BorderLayout.EAST)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) openButton.doClick()
                }
            })
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun textContent(title: String, secondary: String): JComponent {
        return JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JLabel(title).apply {
                foreground = UiStyles.FG_PRIMARY
                font = font.deriveFont(Font.BOLD, font.size2D + 1f)
            })
            add(Box.createVerticalStrut(4))
            add(JLabel(secondary).apply { foreground = UiStyles.FG_SECONDARY })
        }
    }
}
