package org.litvin.ui.help

import org.litvin.ui.UiStyles
import org.litvin.ui.commons.applyDarkScrollbar
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.JDialog
import javax.swing.JEditorPane
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel
import javax.swing.WindowConstants
import javax.swing.border.EmptyBorder

class HelpDialog(owner: Window?) : JDialog(owner, "Tennis Record Help", ModalityType.MODELESS) {
    private val helpPanel = HelpPanel()

    init {
        defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
        contentPane = helpPanel
        minimumSize = Dimension(720, 520)
        size = Dimension(900, 650)
        setLocationRelativeTo(owner)
    }

    val selectedPage: HelpPage
        get() = helpPanel.selectedPage

    fun open(page: HelpPage) {
        helpPanel.selectPage(page)
        if (!isVisible) isVisible = true
        toFront()
        requestFocus()
    }
}

class HelpPanel : JPanel(BorderLayout()) {
    private val navigation = JList(HelpPage.entries.toTypedArray())
    private val content = WrappingHtmlPane()

    var selectedPage: HelpPage = HelpPage.OVERVIEW
        private set

    init {
        background = UiStyles.DARK_BG
        border = EmptyBorder(12, 12, 12, 12)

        navigation.name = "help-navigation"
        navigation.selectionMode = ListSelectionModel.SINGLE_SELECTION
        navigation.background = UiStyles.CARD_BG
        navigation.foreground = UiStyles.FG_PRIMARY
        navigation.fixedCellHeight = 42
        navigation.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? HelpPage)?.title.orEmpty()
                border = EmptyBorder(0, 12, 0, 12)
                background = if (isSelected) UiStyles.SURFACE_HIGH else UiStyles.CARD_BG
                foreground = if (isSelected) UiStyles.LIME else UiStyles.FG_PRIMARY
                return label
            }
        }
        navigation.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                navigation.selectedValue?.let(::renderPage)
            }
        }

        val navigationScroll = JScrollPane(navigation).apply {
            border = BorderFactory.createLineBorder(UiStyles.CARD_BORDER)
            preferredSize = Dimension(180, 0)
            applyDarkScrollbar(this, UiStyles.CARD_BG)
        }

        content.name = "help-content"
        content.background = UiStyles.CARD_BG
        content.foreground = UiStyles.FG_PRIMARY
        content.border = EmptyBorder(18, 22, 18, 22)
        val contentScroll = JScrollPane(content).apply {
            border = BorderFactory.createLineBorder(UiStyles.CARD_BORDER)
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            applyDarkScrollbar(this, UiStyles.CARD_BG)
        }

        add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigationScroll, contentScroll).apply {
            dividerLocation = 180
            dividerSize = 8
            isContinuousLayout = true
            resizeWeight = 0.0
            border = BorderFactory.createEmptyBorder()
            background = UiStyles.DARK_BG
        }, BorderLayout.CENTER)

        selectPage(HelpPage.OVERVIEW)
    }

    fun selectPage(page: HelpPage) {
        navigation.setSelectedValue(page, true)
        if (navigation.selectedValue != page) renderPage(page)
    }

    fun pageTitles(): List<String> = HelpPage.entries.map { it.title }

    private fun renderPage(page: HelpPage) {
        selectedPage = page
        content.text = renderHtml(HelpCatalog.content(page))
        content.caretPosition = 0
    }

    private fun renderHtml(help: HelpContent): String {
        fun items(values: List<String>): String =
            values.joinToString("") { "<li>${escape(it)}</li>" }

        val shortcuts = help.shortcuts.joinToString("") {
            "<tr><td class='key'>${escape(it.shortcut.display)}</td><td>${escape(it.action)}</td></tr>"
        }
        val notes = if (help.notes.isEmpty()) "" else """
            <h2>Notes</h2>
            <ul>${items(help.notes)}</ul>
        """.trimIndent()

        return """
            <html>
            <head>
              <style>
                body { color: #d8d8d8; font-family: sans-serif; font-size: 13px; margin: 0; }
                h1 { color: #a1fe00; font-size: 24px; margin: 0 0 10px 0; }
                h2 { color: #f0f0f0; font-size: 16px; margin: 22px 0 8px 0; }
                p { margin: 0 0 8px 0; }
                ul { margin: 4px 0 0 20px; }
                li { margin: 0 0 7px 0; }
                table { border-collapse: collapse; width: 100%; }
                td { border-bottom: 1px solid #303030; padding: 7px 8px; vertical-align: top; }
                td.key { color: #ffd54a; font-family: monospace; font-weight: bold; width: 130px; }
              </style>
            </head>
            <body>
              <h1>${escape(help.page.title)}</h1>
              <p>${escape(help.summary)}</p>
              <h2>Main workflow</h2>
              <ul>${items(help.workflow)}</ul>
              <h2>Available actions</h2>
              <ul>${items(help.actions)}</ul>
              <h2>Keyboard shortcuts</h2>
              <table>$shortcuts</table>
              $notes
            </body>
            </html>
        """.trimIndent()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private class WrappingHtmlPane : JEditorPane("text/html", "") {
        init {
            isEditable = false
            isOpaque = true
            putClientProperty("JEditorPane.honorDisplayProperties", true)
            font = font.deriveFont(Font.PLAIN, 14f)
        }

        override fun getScrollableTracksViewportWidth(): Boolean = true
    }
}
