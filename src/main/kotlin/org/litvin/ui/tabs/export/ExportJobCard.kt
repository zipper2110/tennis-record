package org.litvin.ui.tabs.export

import com.formdev.flatlaf.ui.FlatProgressBarUI
import org.litvin.export.ExportCardInfo
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.UserDialogService
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JTextArea
import javax.swing.JTextPane
import javax.swing.UIManager
import javax.swing.text.AbstractDocument
import javax.swing.text.BoxView
import javax.swing.text.ComponentView
import javax.swing.text.DefaultCaret
import javax.swing.text.IconView
import javax.swing.text.LabelView
import javax.swing.text.ParagraphView
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledEditorKit
import javax.swing.text.View
import javax.swing.text.ViewFactory

/**
 * One export on the Export tab. The title row has the full path of the file, an optional status badge
 * and the buttons. The file name in the path has the accent color. The details show the content and video settings,
 * and the written size against the expected size. A wide card shows the details in two columns.
 * The active, queued and completed lists use this card.
 *
 * [componentPrefix] starts the names of the child components, for example "export-active".
 * A null [onCancel] removes the "Cancel export" button. [showProgress] adds a progress bar.
 */
class ExportJobCard(
    componentPrefix: String,
    onOpenFolder: () -> Unit,
    onCancel: (() -> Unit)?,
    showProgress: Boolean,
) : JPanel(GridBagLayout()) {
    private val statusTag = TagLabel("").apply { isVisible = false }
    private val pathText = PathText().apply { name = "$componentPrefix-path" }

    // In two columns, the first column has the settings and the second column has the size and the extra row.
    private val projectRow = DetailRow("Project", "$componentPrefix-project")
    private val contentRow = DetailRow("Content", "$componentPrefix-content")
    private val videoRow = DetailRow("Video", "$componentPrefix-video")
    private val sizeRow = DetailRow("Size", "$componentPrefix-size")
    private val extraRow = DetailRow("", null).apply { visible = false }
    private val settingsRows = listOf(projectRow, contentRow, videoRow)
    private val statusRows = listOf(sizeRow, extraRow)
    private val details = JPanel(GridBagLayout()).apply { isOpaque = false }
    private var detailsLayoutKey: List<Any>? = null

    val progressBar = JProgressBar(0, 100).apply {
        name = "$componentPrefix-progress"
        foreground = UiStyles.GREEN
        isStringPainted = true
        isVisible = showProgress
        // FlatLaf paints the percent text over the fill in selectionForeground, which has no style key.
        // The default light text is not legible on the green fill, so this UI returns a contrasting color.
        setUI(object : FlatProgressBarUI() {
            override fun getSelectionForeground(): Color = UiStyles.contrastingTextColor(UiStyles.GREEN)
        })
    }

    val openFolderButton: JButton = UiStyles.primarySmallButton(OPEN_FOLDER) { onOpenFolder() }.apply {
        name = "$componentPrefix-open-folder"
        toolTipText = "Open the folder of the exported file."
    }

    val cancelButton: JButton? = onCancel?.let { cancel ->
        JButton(CANCEL_EXPORT).apply {
            name = "$componentPrefix-cancel"
            UiStyles.styleSecondary(this)
            addActionListener { cancel() }
        }
    }

    init {
        isOpaque = true
        background = UiStyles.SURFACE_HIGH
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiStyles.CARD_BORDER, 1, true),
            BorderFactory.createEmptyBorder(8, 12, 10, 12),
        )
        alignmentX = 0f

        val header = JPanel(GridBagLayout()).apply {
            isOpaque = false
            // A long path wraps. The badge and the buttons stay on the first line.
            add(pathText, GridBagConstraints().apply {
                gridx = 0
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.CENTER
            })
            add(statusTag, GridBagConstraints().apply {
                gridx = 1
                anchor = GridBagConstraints.NORTH
                insets = Insets(3, 8, 0, 0)
            })
            add(openFolderButton, GridBagConstraints().apply {
                gridx = 2
                anchor = GridBagConstraints.NORTH
                insets = Insets(0, 12, 0, 0)
            })
            cancelButton?.let { button ->
                add(button, GridBagConstraints().apply {
                    gridx = 3
                    anchor = GridBagConstraints.NORTH
                    insets = Insets(0, 8, 0, 0)
                })
            }
        }
        add(header, fullRow(0))
        add(details, fullRow(1, top = 6))
        add(progressBar, fullRow(2, top = 8))
        layoutDetails()

        // The number of detail columns depends on the width of the card.
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = layoutDetails()
        })
    }

    /** Shows the information of an export. A null [status] hides the status tag. */
    fun update(info: ExportCardInfo, status: String? = null) {
        pathText.setPath(info.outputPath)
        projectRow.visible = info.projectName != null
        projectRow.value.setTextIfChanged(info.projectName.orEmpty())
        contentRow.value.setTextIfChanged(info.content)
        videoRow.value.setTextIfChanged(info.video)
        sizeRow.value.setTextIfChanged(info.size)
        statusTag.isVisible = status != null
        if (status != null) setIfChanged(statusTag, status)
        layoutDetails()
    }

    /** Shows one more row under the size, for example the time left. A null [label] hides the row. */
    fun setExtraRow(label: String?, value: String?) {
        extraRow.visible = label != null
        if (label != null) {
            setIfChanged(extraRow.label, label)
            extraRow.value.setTextIfChanged(value.orEmpty())
        }
        layoutDetails()
    }

    fun setProgress(percent: Int) {
        progressBar.value = percent
        progressBar.string = "$percent%"
    }

    /**
     * Puts the visible detail rows in one column, or in two columns when the card is wide.
     * The layout changes only when the column count or the visible rows change.
     */
    private fun layoutDetails() {
        val twoColumns = width >= TWO_COLUMN_MIN_WIDTH_PX
        val settings = settingsRows.filter { it.visible }
        val status = statusRows.filter { it.visible }
        val key = listOf(twoColumns, settings, status)
        if (key == detailsLayoutKey) return
        detailsLayoutKey = key
        details.removeAll()
        if (twoColumns) {
            settings.forEachIndexed { row, detail -> addDetail(detail, row, column = 0, weight = 0.65) }
            status.forEachIndexed { row, detail -> addDetail(detail, row, column = 1, weight = 0.35) }
        } else {
            (settings + status).forEachIndexed { row, detail -> addDetail(detail, row, column = 0, weight = 1.0) }
        }
        details.revalidate()
        details.repaint()
    }

    private fun addDetail(detail: DetailRow, row: Int, column: Int, weight: Double) {
        details.add(detail.label, GridBagConstraints().apply {
            gridx = column * 2
            gridy = row
            anchor = GridBagConstraints.BASELINE_LEADING
            insets = Insets(2, if (column == 0) 0 else COLUMN_GAP_PX, 2, 12)
        })
        details.add(detail.value, GridBagConstraints().apply {
            gridx = column * 2 + 1
            gridy = row
            weightx = weight
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.BASELINE_LEADING
            insets = Insets(2, 0, 2, 0)
        })
    }

    private fun fullRow(row: Int, top: Int = 0) = GridBagConstraints().apply {
        gridx = 0
        gridy = row
        weightx = 1.0
        fill = GridBagConstraints.HORIZONTAL
        insets = Insets(top, 0, 0, 0)
    }

    private fun setIfChanged(label: JLabel, text: String) {
        if (label.text != text) label.text = text
    }

    /** A row name and its value. */
    private class DetailRow(name: String, valueName: String?) {
        val label = rowLabel(name)
        val value = WrappingText(wrapWords = true).apply { this.name = valueName }
        var visible = true
            set(shown) {
                field = shown
                label.isVisible = shown
                value.isVisible = shown
            }
    }

    /**
     * The full path of the output file. The folder is grey and the file name has the accent color, so the name is easy to find.
     * A long path wraps at the width that the layout gives it. The user can select and copy the path.
     */
    private class PathText : JTextPane() {
        private var laidOutWidth = -1
        private var shownPath: String? = null

        init {
            editorKit = CharWrapEditorKit()
            isEditable = false
            isOpaque = false
            border = BorderFactory.createEmptyBorder()
            background = UiStyles.SURFACE_HIGH
            UIManager.getFont("Label.font")?.let { font = it.deriveFont(it.size2D + 1f) }
            (caret as? DefaultCaret)?.updatePolicy = DefaultCaret.NEVER_UPDATE
            addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    if (width != laidOutWidth) {
                        laidOutWidth = width
                        revalidate()
                    }
                }
            })
        }

        fun setPath(path: String) {
            if (path == shownPath) return
            shownPath = path
            val fileName = File(path).name
            val folder = path.removeSuffix(fileName)
            val document = styledDocument
            document.remove(0, document.length)
            document.insertString(0, folder, style(UiStyles.FG_SECONDARY, bold = false))
            document.insertString(folder.length, fileName, style(UiStyles.ACCENT_TEXT, bold = true))
        }

        private fun style(color: Color, bold: Boolean) = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, color)
            StyleConstants.setBold(this, bold)
            StyleConstants.setFontFamily(this, font.family)
            StyleConstants.setFontSize(this, font.size)
        }

        // The layout sets the width. The height is the height of the wrapped lines at that width.
        override fun getPreferredSize(): Dimension = Dimension(1, super.getPreferredSize().height)

        override fun getMinimumSize(): Dimension = preferredSize
    }

    /**
     * A styled editor kit that can break a long word at any character. A path has almost no spaces,
     * and the default view does not break a word, so without this kit the path goes past the card.
     */
    private class CharWrapEditorKit : StyledEditorKit() {
        private val factory = ViewFactory { element ->
            when (element.name) {
                AbstractDocument.ContentElementName -> object : LabelView(element) {
                    override fun getMinimumSpan(axis: Int): Float =
                        if (axis == View.X_AXIS) 0f else super.getMinimumSpan(axis)
                }
                AbstractDocument.ParagraphElementName -> ParagraphView(element)
                AbstractDocument.SectionElementName -> BoxView(element, View.Y_AXIS)
                StyleConstants.ComponentElementName -> ComponentView(element)
                StyleConstants.IconElementName -> IconView(element)
                else -> LabelView(element)
            }
        }

        override fun getViewFactory(): ViewFactory = factory
    }

    /**
     * Read-only text that wraps at the width that the layout gives it, so a long text shows completely.
     * The user can select and copy the text.
     */
    private class WrappingText(wrapWords: Boolean) : JTextArea() {
        private var laidOutWidth = -1

        init {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = wrapWords
            border = BorderFactory.createEmptyBorder()
            background = UiStyles.SURFACE_HIGH
            foreground = UiStyles.FG_PRIMARY
            UIManager.getFont("Label.font")?.let { font = it }
            // Keep the list at its scroll position when the user clicks in the text.
            (caret as? DefaultCaret)?.updatePolicy = DefaultCaret.NEVER_UPDATE
            // The height depends on the width. Do the layout again when the width changes.
            addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    if (width != laidOutWidth) {
                        laidOutWidth = width
                        revalidate()
                    }
                }
            })
        }

        fun setTextIfChanged(value: String) {
            if (text != value) text = value
        }

        // The layout sets the width. The height is the height of the wrapped lines at that width.
        override fun getPreferredSize(): Dimension = Dimension(1, super.getPreferredSize().height)

        override fun getMinimumSize(): Dimension = preferredSize
    }

    companion object {
        const val OPEN_FOLDER = "Open folder"
        const val CANCEL_EXPORT = "Cancel export"
        // The names of the fixed rows and of the extra rows that the Export tab uses.
        private val ROW_NAMES = listOf("Project", "Content", "Video", "Size", "Time left", "Finished", "Error")
        // Offscreen and scaled text can paint some pixels wider than the measured width.
        private const val ROW_NAME_PADDING_PX = 4
        // Below this width, two detail columns wrap the settings on too many lines.
        private const val TWO_COLUMN_MIN_WIDTH_PX = 900
        private const val COLUMN_GAP_PX = 24

        /** A row name. All row names have the width of the longest name, so the values of all cards align. */
        private fun rowLabel(text: String): JLabel = object : JLabel(text) {
            override fun getPreferredSize(): Dimension {
                val size = super.getPreferredSize()
                val metrics = getFontMetrics(font ?: return size)
                val width = (ROW_NAMES + listOfNotNull(this.text)).maxOf(metrics::stringWidth) + ROW_NAME_PADDING_PX
                return Dimension(width, size.height)
            }
            override fun getMinimumSize(): Dimension = preferredSize
        }.apply {
            UiStyles.styleHelper(this)
            font = font.deriveFont(Font.BOLD)
            foreground = UiStyles.ACCENT_TEXT
        }

        /** Opens the folder of [outputPath] in the file manager. */
        fun openFolder(outputPath: String, parent: Component, dialogs: UserDialogService) {
            try {
                val dir = File(outputPath).absoluteFile.parentFile ?: return
                try {
                    java.awt.Desktop.getDesktop().open(dir)
                } catch (_: Throwable) {
                    Runtime.getRuntime().exec(arrayOf("explorer.exe", dir.absolutePath))
                }
            } catch (t: Throwable) {
                dialogs.showError(parent, t.message ?: t.toString(), "Failed to open folder")
            }
        }
    }
}
