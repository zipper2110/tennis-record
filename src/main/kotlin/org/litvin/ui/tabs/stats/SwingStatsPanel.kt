package org.litvin.ui.tabs.stats

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.adjustments.AdjustmentsIO
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.projects.ManifestIO
import org.litvin.scoring.MatchStats
import org.litvin.scoring.PerPlayer
import org.litvin.stats.Momentum
import org.litvin.stats.MatchStat
import org.litvin.stats.StatGroup
import org.litvin.stats.StatRow
import org.litvin.stats.StatRows
import org.litvin.stats.StatsCard
import org.litvin.stats.StatValue
import org.litvin.stats.StatsIO
import org.litvin.stats.StatsReport
import org.litvin.stats.StatsSettingsV1
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.UserDialogService
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JTextArea
import javax.swing.JSeparator
import javax.swing.JSlider
import javax.swing.JToggleButton
import javax.swing.ScrollPaneConstants
import javax.swing.SpinnerNumberModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.border.EmptyBorder

/**
 * The Stats tab: the match statistics of the project, for the full match or for one set.
 * The user also selects here the rows that the exported video shows.
 *
 * The tab reads edl.json and score.json each time it opens, so it always shows the latest scoring.
 */
class SwingStatsPanel(
    private val dialogs: UserDialogService,
    private val onOpenScoring: () -> Unit = {},
    /** Opens the point with this id in the Scoring tab. */
    private val onOpenPoint: (String) -> Unit = {},
    private val frameLoader: StatsFrameLoader = StatsFrameLoader(),
) : JPanel(BorderLayout()) {
    private val logger = KotlinLogging.logger {}

    private var projectDir: String? = null
    private var manifestPath: String? = null
    /** The video frame that the preview shows or waits for. */
    private var frameRequest: StatsFrameRequest? = null
    private var report: StatsReport? = null
    private var settings = StatsSettingsV1()

    /** 0 is the full match. 1 and more are the sets. */
    private var scope = 0

    private val cards = CardLayout()
    private val body = JPanel(cards)
    private val titleLabel = JLabel().apply {
        name = "stats-title"
        font = font.deriveFont(Font.BOLD, font.size2D + 4f)
        foreground = UiStyles.FG_PRIMARY
    }
    private val scoreLabel = JLabel().apply {
        name = "stats-score"
        font = font.deriveFont(font.size2D + 1f)
        foreground = UiStyles.FG_SECONDARY
    }
    private val scopeBar = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }
    private val coverageLabel = JLabel().apply {
        name = "stats-coverage"
        foreground = UiStyles.YELLOW
    }
    private val table = JPanel(GridBagLayout()).apply {
        name = "stats-table"
        isOpaque = false
    }
    private val emptyTitle = JLabel().apply {
        name = "stats-empty"
        font = font.deriveFont(Font.BOLD, font.size2D + 2f)
        foreground = UiStyles.FG_PRIMARY
        alignmentX = 0.5f
    }
    private val emptyText = JLabel().apply {
        foreground = UiStyles.FG_SECONDARY
        alignmentX = 0.5f
    }
    private val momentumChart = MomentumChart { pointId -> onOpenPoint(pointId) }
    private val momentumInVideo = JCheckBox("In video").apply {
        name = "stats-in-video-momentum"
        isFocusable = false
        toolTipText = "Add a page with this chart to the statistics card of the exported video."
        UiStyles.styleCheckBox(this)
        addActionListener { setMomentumInVideo(isSelected) }
    }
    private val preview = StatsCardPreview()
    private val previewPageLabel = JLabel("", SwingConstants.CENTER).apply {
        name = "stats-preview-page"
        foreground = UiStyles.FG_SECONDARY
    }
    private val previewPrevious = UiStyles.smallIconButton(UiStyles.seekLeftIcon(), "Previous page") { showPreviewPage(preview.page - 1) }
        .apply { name = "stats-preview-previous" }
    private val previewNext = UiStyles.smallIconButton(UiStyles.seekRightIcon(), "Next page") { showPreviewPage(preview.page + 1) }
        .apply { name = "stats-preview-next" }
    private val previewPager = JPanel(FlowLayout(FlowLayout.CENTER, 8, 0)).apply {
        isOpaque = false
        add(previewPrevious)
        add(previewPageLabel)
        add(previewNext)
    }
    private val transparencyValue = JLabel().apply {
        name = "stats-card-transparency-value"
        foreground = UiStyles.FG_SECONDARY
        // Space for the widest value, so the slider does not move when the value changes.
        preferredSize = Dimension(getFontMetrics(font).stringWidth("100 %") + 4, preferredSize.height)
    }
    private val transparencySlider = JSlider(0, StatsSettingsV1.MAX_CARD_TRANSPARENCY_PERCENT, StatsSettingsV1.DEFAULT_CARD_TRANSPARENCY_PERCENT).apply {
        name = "stats-card-transparency"
        isOpaque = false
        isFocusable = false
        toolTipText = "How much of the video shows through the card. The exported video uses the same value."
        addChangeListener { setCardTransparency(value, valueIsAdjusting) }
    }
    /** True while the tab sets the slider value from the settings. */
    private var showingTransparency = false
    // The rows keep these spinners when the table is made again, so the user can continue to change a value.
    private val shortLimit = limitSpinner("stats-short-point-limit")
    private val longLimit = limitSpinner("stats-long-point-limit")
    /** True while the tab sets the spinner values from the settings. */
    private var showingLimits = false
    private val openScoringButton = UiStyles.primarySmallButton("Open Scoring") { onOpenScoring() }.apply {
        name = "stats-open-scoring"
        alignmentX = 0.5f
    }

    init {
        border = EmptyBorder(10, 10, 10, 10)
        background = UiStyles.DARK_BG
        body.background = UiStyles.CARD_BG
        body.border = BorderFactory.createLineBorder(UiStyles.CARD_BORDER)
        body.add(statsView(), CARD_STATS)
        body.add(emptyView(), CARD_EMPTY)
        add(body, BorderLayout.CENTER)
        showEmpty("No project", "Open a project in the Projects tab.")
    }

    fun setProjectManifest(path: String?) {
        projectDir = path?.let { File(it).parentFile.absolutePath }
        manifestPath = path
        scope = 0
        // The frame of another project is not correct for this project.
        frameRequest = null
        preview.background = null
    }

    /** Reads the project again and shows the latest statistics. */
    fun onActivated() {
        val dir = projectDir ?: return showEmpty("No project", "Open a project in the Projects tab.")
        val loaded = try {
            settings = StatsIO.readForProjectDir(dir)
            StatsReport.load(dir)
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot read the statistics of $dir" }
            return showEmpty("Cannot read the statistics", failure.message ?: failure.toString())
        }
        report = loaded
        if (!loaded.hasScoredPoints) {
            return showEmpty("No scored points", "Score the points in the Scoring tab. The statistics then show here.", scoringButton = true)
        }
        if (scope > loaded.sets.size) scope = 0
        rebuild()
        cards.show(body, CARD_STATS)
    }

    private fun showEmpty(title: String, text: String, scoringButton: Boolean = false) {
        report = null
        emptyTitle.text = title
        emptyText.text = text
        openScoringButton.isVisible = scoringButton
        cards.show(body, CARD_EMPTY)
    }

    private fun statsView(): JComponent {
        val header = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = EmptyBorder(16, 20, 8, 20)
            listOf(titleLabel, scoreLabel, Box.createRigidArea(Dimension(0, 12)), scopeBar, Box.createRigidArea(Dimension(0, 8)), coverageLabel)
                .forEach { component ->
                    (component as JComponent).alignmentX = 0f
                    add(component)
                }
        }
        // The chart has the width of the table.
        val column = JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(momentumSection(), GridBagConstraints().apply {
                gridy = 0
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(0, 0, 16, 0)
            })
            add(table, GridBagConstraints().apply {
                gridy = 1
                anchor = GridBagConstraints.WEST
            })
        }
        // The column keeps its preferred width at the top left. The holder fills the rest of the view.
        val holder = JPanel(GridBagLayout()).apply {
            isOpaque = false
            border = EmptyBorder(0, 20, 20, 20)
            add(column, GridBagConstraints().apply {
                anchor = GridBagConstraints.NORTHWEST
                weightx = 1.0
                weighty = 1.0
            })
        }
        val scroll = object : JScrollPane(holder) {
            // The table column keeps its preferred width. The preview gets the rest of the width.
            override fun getPreferredSize(): Dimension {
                val insets = insets
                val width = holder.preferredSize.width + verticalScrollBar.preferredSize.width + insets.left + insets.right
                return Dimension(width, super.getPreferredSize().height)
            }
        }.apply {
            border = BorderFactory.createEmptyBorder()
            isOpaque = false
            viewport.isOpaque = false
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBar.unitIncrement = 16
        }
        // The header and the table are in the left column. The preview column starts at the top of the tab.
        val left = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(header, BorderLayout.NORTH)
            add(scroll, BorderLayout.CENTER)
        }
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(left, BorderLayout.WEST)
            add(previewColumn(), BorderLayout.CENTER)
        }
    }

    /** The momentum chart, with a title and a short help text. */
    private fun momentumSection(): JComponent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        // An unlimited width keeps the last characters visible when the text paints wider than measured.
        val help = caption("Click the chart to open a point in the Scoring tab.").apply {
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
        val title = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(groupHeader("Momentum").apply { border = EmptyBorder(0, 0, 0, 16) })
            add(momentumInVideo)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
        listOf(title, Box.createRigidArea(Dimension(0, 2)), help, Box.createRigidArea(Dimension(0, 6)), momentumChart)
            .forEach { component ->
                (component as JComponent).alignmentX = 0f
                add(component)
            }
        momentumChart.maximumSize = Dimension(Int.MAX_VALUE, momentumChart.preferredSize.height)
    }

    /**
     * The card as the exported video shows it, with buttons for its pages.
     * The preview uses the full width of the column. The height of the column can make it smaller.
     */
    private fun previewColumn(): JComponent {
        val caption = JLabel("In the video").apply {
            font = font.deriveFont(Font.BOLD)
            foreground = UiStyles.FG_PRIMARY
        }
        val transparency = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(JLabel("Card transparency").apply { foreground = UiStyles.FG_PRIMARY }, BorderLayout.WEST)
            add(transparencySlider, BorderLayout.CENTER)
            add(transparencyValue, BorderLayout.EAST)
        }
        val help = JTextArea(
            "The exported video shows this card after the last point. " +
                "The card shows the rows that you select and that have a value, " +
                "${StatsCard.ROWS_PER_PAGE} rows on each page. A group of rows stays on one page when possible. " +
                "When you select In video for the momentum chart, the last page shows the chart.",
        ).apply {
            isEditable = false
            isFocusable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            border = null
            font = UIManager.getFont("Label.font")
            foreground = UiStyles.FG_SECONDARY
        }
        return object : JPanel(null) {
            init {
                isOpaque = false
                // The top space is the same as the top space of the header, so the captions align.
                border = EmptyBorder(16, 20, 20, 20)
                add(caption)
                add(preview)
                add(previewPager)
                add(transparency)
                add(help)
            }

            override fun getPreferredSize(): Dimension {
                val insets = insets
                return Dimension(preview.preferredSize.width + insets.left + insets.right, preview.preferredSize.height)
            }

            override fun doLayout() {
                val insets = insets
                val left = insets.left
                val width = (this.width - insets.left - insets.right).coerceAtLeast(1)
                var y = insets.top
                val captionHeight = caption.preferredSize.height
                caption.setBounds(left, y, width, captionHeight)
                y += captionHeight + 8
                help.setSize(width, Short.MAX_VALUE.toInt())
                val helpHeight = help.preferredSize.height
                // A card with one page has no page buttons, so the help text moves up.
                val pagerHeight = if (previewPager.isVisible) previewPager.preferredSize.height else 0
                val transparencyHeight = transparency.preferredSize.height
                // The space under the preview: the pager, the transparency slider and the help text.
                val below = 6 + pagerHeight + 6 + transparencyHeight + 10 + helpHeight
                val maxHeight = (this.height - insets.bottom - y - below).coerceAtLeast(preview.minimumSize.height)
                val previewHeight = minOf(width * 9 / 16, maxHeight)
                val previewWidth = previewHeight * 16 / 9
                preview.setBounds(left, y, previewWidth, previewHeight)
                y += previewHeight + 6
                previewPager.setBounds(left, y, previewWidth, pagerHeight)
                y += pagerHeight + 6
                // A slider across a wide preview is difficult to use, so the row has a maximum width.
                transparency.setBounds(left, y, minOf(previewWidth, MAX_SLIDER_ROW_WIDTH), transparencyHeight)
                y += transparencyHeight + 10
                // A narrower preview wraps the help text into more lines.
                help.setSize(previewWidth, Short.MAX_VALUE.toInt())
                help.setBounds(left, y, previewWidth, help.preferredSize.height)
            }
        }
    }

    private fun updatePreview() {
        val report = report ?: return
        updatePreviewPages(report)
        updatePreviewFrame(report)
    }

    private fun updatePreviewPages(report: StatsReport) {
        preview.pages = StatsCard.pages(StatsCard.content(report, settings, scope))
        showPreviewPage(preview.page)
    }

    /**
     * Loads the video frame under the card: the end of the last point for the match card,
     * or the end of the last point of the set for a set card. The export freezes the same frame
     * when the video has these points.
     */
    private fun updatePreviewFrame(report: StatsReport) {
        val request = try {
            val source = manifestPath?.let { ManifestIO.read(it).sourceVideo }?.takeIf { File(it).isFile }
            val lastPoint = if (scope == 0) report.points.lastOrNull() else report.setRanges.getOrNull(scope - 1)?.let { report.points[it.last] }
            if (source == null || lastPoint == null) {
                null
            } else {
                val adjustments = projectDir?.let(AdjustmentsIO::readForProjectDir) ?: AdjustmentsV1()
                StatsFrameRequest(source, lastPoint.endMs.toLong(), adjustments)
            }
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot find the video frame for the statistics preview" }
            null
        }
        if (request == frameRequest) return
        frameRequest = request
        if (request == null) {
            preview.background = null
            return
        }
        // Keep the old frame until the new frame is ready, so the preview does not flash the drawn court.
        frameLoader.cached(request)?.let { preview.background = it }
        frameLoader.load(request) { image ->
            if (frameRequest == request && image != null) preview.background = image
        }
    }

    private fun showPreviewPage(page: Int) {
        val count = preview.pages.size
        preview.page = page.coerceIn(0, (count - 1).coerceAtLeast(0))
        previewPageLabel.text = "Page ${preview.page + 1} of $count"
        if (previewPager.isVisible != count > 1) {
            previewPager.isVisible = count > 1
            previewPager.parent?.revalidate()
        }
        previewPrevious.isEnabled = preview.page > 0
        previewNext.isEnabled = preview.page < count - 1
    }

    private fun emptyView(): JComponent {
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(emptyTitle)
            add(Box.createRigidArea(Dimension(0, 8)))
            add(emptyText)
            add(Box.createRigidArea(Dimension(0, 16)))
            add(openScoringButton)
        }
        return JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(content)
        }
    }

    private fun rebuild() {
        val report = report ?: return
        val score = report.score
        titleLabel.text = "${displayName(score.player1Name, "Player 1")}  vs  ${displayName(score.player2Name, "Player 2")}"
        scoreLabel.text = report.setScores().joinToString("   ").ifEmpty { " " }
        rebuildScopeBar(report)
        val stats = if (scope == 0) report.match else report.sets[scope - 1]
        coverageLabel.text = listOfNotNull(
            "Based on ${stats.scoredPoints} of ${stats.points} points. The other points have no winner."
                .takeIf { stats.scoredPoints < stats.points },
            (if (report.pointsAfterMatchEnd == 1) "The statistics do not use the point after the end of the match."
            else "The statistics do not use the ${report.pointsAfterMatchEnd} points after the end of the match.")
                .takeIf { report.pointsAfterMatchEnd > 0 },
        ).let { lines -> if (lines.size > 1) lines.joinToString("<br>", "<html>", "</html>") else lines.joinToString("") }
        coverageLabel.isVisible = coverageLabel.text.isNotEmpty()
        momentumChart.names = PerPlayer(displayName(score.player1Name, "Player 1"), displayName(score.player2Name, "Player 2"))
        momentumChart.colors = PerPlayer(parseColor(score.player1ColorHex), parseColor(score.player2ColorHex))
        momentumChart.momentum = Momentum.of(report, scope)
        momentumInVideo.isSelected = settings.videoMomentum
        showTransparency(settings.normalized().cardTransparencyPercent)
        rebuildTable(stats, report)
        updatePreview()
        revalidate()
        repaint()
    }

    private fun rebuildScopeBar(report: StatsReport) {
        scopeBar.removeAll()
        // One set is the same as the match, so the selector shows only when there are two sets or more.
        scopeBar.isVisible = report.sets.size > 1
        if (!scopeBar.isVisible) {
            scope = 0
            return
        }
        val group = ButtonGroup()
        val titles = listOf("Match") + report.sets.indices.map { "Set ${it + 1}" }
        titles.forEachIndexed { index, title ->
            val button = JToggleButton(title, index == scope).apply {
                name = "stats-scope-$index"
                isFocusable = false
                addActionListener {
                    if (scope != index) {
                        scope = index
                        rebuild()
                    }
                }
            }
            group.add(button)
            scopeBar.add(button)
        }
    }

    private fun rebuildTable(stats: MatchStats, report: StatsReport) {
        table.removeAll()
        val score = report.score
        var y = 0
        addCell(caption("In video"), 0, y, anchor = GridBagConstraints.CENTER)
        addCell(playerHeader(displayName(score.player1Name, "Player 1"), score.player1ColorHex), 2, y)
        addCell(playerHeader(displayName(score.player2Name, "Player 2"), score.player2ColorHex), 3, y)
        y++

        val rows = StatRows.build(stats, score.rules.normalized(), settings)
        for (group in StatGroup.entries) {
            val groupRows = rows.filter { it.stat.group == group }
            if (groupRows.isEmpty()) continue
            addCell(groupHeader(group.title), 1, y, width = 3, top = if (y == 1) 4 else 14)
            y++
            addCell(JSeparator().apply { foreground = UiStyles.CARD_BORDER }, 0, y, width = 4, fill = GridBagConstraints.HORIZONTAL)
            y++
            if (group == StatGroup.POINT_LENGTH) {
                addCell(pointLengthControls(), 1, y, width = 3)
                y++
            }
            for (row in groupRows) {
                addRow(row, y)
                y++
            }
        }
    }

    private fun addRow(row: StatRow, y: Int) {
        val key = row.stat.key
        val check = JCheckBox().apply {
            name = "stats-in-video-$key"
            isSelected = settings.inVideo(row.stat)
            isFocusable = false
            toolTipText = if (row.available) "Show this row in the exported video." else
                "Show this row in the exported video. The video leaves out the row while it has no value."
            UiStyles.styleCheckBox(this)
            addActionListener { setInVideo(row.stat, isSelected, this) }
        }
        addCell(check, 0, y, anchor = GridBagConstraints.CENTER)

        val label = JLabel(row.label).apply {
            name = "stats-label-$key"
            foreground = if (row.available) UiStyles.FG_PRIMARY else UiStyles.FG_DISABLED
            toolTipText = description(row.stat)
            if (!row.available) {
                icon = UiStyles.infoIcon()
                horizontalTextPosition = SwingConstants.LEFT
                iconTextGap = 6
                toolTipText = listOfNotNull(row.unavailableReason, toolTipText).joinToString(" ")
            }
        }
        addCell(label, 1, y)

        val values = row.values
        when {
            !row.available -> {
                addCell(valueCell(StatValue("—"), "stats-value-$key-p1", muted = true), 2, y, anchor = GridBagConstraints.CENTER)
                addCell(valueCell(StatValue("—"), "stats-value-$key-p2", muted = true), 3, y, anchor = GridBagConstraints.CENTER)
            }
            values != null -> {
                addCell(valueCell(values.p1, "stats-value-$key-p1", pointId = row.pointIds?.p1), 2, y, anchor = GridBagConstraints.CENTER)
                addCell(valueCell(values.p2, "stats-value-$key-p2", pointId = row.pointIds?.p2), 3, y, anchor = GridBagConstraints.CENTER)
            }
            else -> addCell(
                valueCell(row.shared!!, "stats-value-$key", pointId = row.sharedPointId),
                2, y, width = 2, anchor = GridBagConstraints.CENTER,
            )
        }
    }

    /** A longer explanation of a row, or null when the label is sufficient. */
    private fun description(stat: MatchStat): String? = when (stat) {
        MatchStat.SHORT_POINTS_WON -> "The points that last ${settings.normalized().shortPointMaxSeconds} s or less, " +
            "from the start mark to the end mark. The default is about 0-4 shots: the ATP \"first strike\" range."
        MatchStat.LONG_POINTS_WON -> "The points that last ${settings.normalized().longPointMinSeconds} s or more, " +
            "from the start mark to the end mark. The default is about 9 or more shots: the ATP \"extended rally\" range."
        MatchStat.RETURN_GAMES_WON -> "The service games of the opponent that the player won (breaks)."
        else -> null
    }

    /** The limits of a short point and a long point, above the rows that use them. */
    private fun pointLengthControls(): JComponent {
        showingLimits = true
        try {
            val normalized = settings.normalized()
            shortLimit.value = normalized.shortPointMaxSeconds
            longLimit.value = normalized.longPointMinSeconds
        } finally {
            showingLimits = false
        }
        // The padding keeps the last characters visible when the text paints wider than measured.
        fun text(value: String, left: Int) = caption(value).apply { border = EmptyBorder(0, left, 0, 10) }
        return JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            toolTipText = "Set the limits for your marks. Some marks start before the serve or end after the point."
            add(text("Short point: up to", 0))
            add(shortLimit)
            add(text("s", 6))
            add(text("Long point:", 14))
            add(longLimit)
            add(text("s or more", 6))
        }
    }

    private fun limitSpinner(name: String) = JSpinner(
        SpinnerNumberModel(
            StatsSettingsV1.DEFAULT_SHORT_POINT_MAX_SECONDS,
            StatsSettingsV1.MIN_POINT_LIMIT_SECONDS,
            StatsSettingsV1.MAX_POINT_LIMIT_SECONDS,
            1,
        ),
    ).apply {
        this.name = name
        (editor as? JSpinner.DefaultEditor)?.textField?.columns = 3
        addChangeListener { if (!showingLimits) setPointLimits() }
    }

    private fun setPointLimits() {
        val dir = projectDir ?: return
        val updated = settings.copy(
            shortPointMaxSeconds = shortLimit.value as Int,
            longPointMinSeconds = longLimit.value as Int,
        ).normalized()
        try {
            StatsIO.writeForProjectDir(dir, updated)
            settings = updated
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot save stats.json in $dir" }
            dialogs.showError(this, failure.message ?: failure.toString(), "Cannot save the statistics setup")
        }
        // Make the table again after the spinner event. The table gets the same spinners again.
        SwingUtilities.invokeLater { rebuild() }
    }

    private fun showTransparency(percent: Int) {
        showingTransparency = true
        try {
            transparencySlider.value = percent
        } finally {
            showingTransparency = false
        }
        transparencyValue.text = "$percent %"
    }

    /**
     * Shows the new transparency in the preview while the user moves the slider.
     * Saves stats.json when the user releases the slider.
     */
    private fun setCardTransparency(percent: Int, adjusting: Boolean) {
        transparencyValue.text = "$percent %"
        if (showingTransparency) return
        val report = report ?: return
        val dir = projectDir ?: return
        settings = settings.copy(cardTransparencyPercent = percent)
        updatePreviewPages(report)
        if (adjusting) return
        try {
            StatsIO.writeForProjectDir(dir, settings)
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot save stats.json in $dir" }
            dialogs.showError(this, failure.message ?: failure.toString(), "Cannot save the statistics setup")
        }
    }

    private fun setMomentumInVideo(selected: Boolean) {
        val dir = projectDir ?: return
        val updated = settings.copy(videoMomentum = selected)
        try {
            StatsIO.writeForProjectDir(dir, updated)
            settings = updated
            updatePreview()
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot save stats.json in $dir" }
            momentumInVideo.isSelected = !selected
            dialogs.showError(this, failure.message ?: failure.toString(), "Cannot save the statistics setup")
        }
    }

    private fun setInVideo(stat: MatchStat, selected: Boolean, check: JCheckBox) {
        val dir = projectDir ?: return
        val updated = settings.withInVideo(stat, selected)
        try {
            StatsIO.writeForProjectDir(dir, updated)
            settings = updated
            updatePreview()
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot save stats.json in $dir" }
            check.isSelected = !selected
            dialogs.showError(this, failure.message ?: failure.toString(), "Cannot save the statistics setup")
        }
    }

    private fun addCell(
        component: JComponent,
        x: Int,
        y: Int,
        width: Int = 1,
        anchor: Int = GridBagConstraints.WEST,
        fill: Int = GridBagConstraints.NONE,
        top: Int = 3,
    ) {
        table.add(component, GridBagConstraints().also {
            it.gridx = x
            it.gridy = y
            it.gridwidth = width
            it.anchor = anchor
            it.fill = fill
            it.insets = Insets(top, if (x == 0) 0 else 12, 3, 12)
        })
    }

    private fun caption(text: String) = JLabel(text).apply {
        // A small right padding keeps the last character visible when the text paints wider than measured.
        border = EmptyBorder(0, 0, 0, 4)
        foreground = UiStyles.FG_SECONDARY
        font = font.deriveFont(font.size2D - 1f)
    }

    private fun groupHeader(text: String) = JLabel(text).apply {
        foreground = UiStyles.ACCENT_TEXT
        font = font.deriveFont(Font.BOLD)
    }

    private fun playerHeader(name: String, colorHex: String) = JLabel(name, SwingConstants.CENTER).apply {
        foreground = parseColor(colorHex)
        font = font.deriveFont(Font.BOLD, font.size2D + 1f)
        preferredSize = Dimension(maxOf(VALUE_WIDTH, preferredSize.width), preferredSize.height)
    }

    /**
     * The main text, and the detail in a smaller, muted text next to it.
     * A value that comes from one point has an icon, and a click opens the point in the Scoring tab.
     */
    private fun valueCell(value: StatValue, name: String, muted: Boolean = false, pointId: String? = null): JComponent =
        JPanel(FlowLayout(FlowLayout.CENTER, 6, 0)).apply {
            isOpaque = false
            this.name = name
            add(JLabel(value.text).apply {
                this.name = "$name-text"
                foreground = if (muted) UiStyles.FG_DISABLED else UiStyles.FG_PRIMARY
                font = font.deriveFont(Font.BOLD)
                if (pointId != null) {
                    icon = UiStyles.openPointIcon()
                    horizontalTextPosition = SwingConstants.LEFT
                    iconTextGap = 6
                }
            })
            if (pointId != null) {
                // The labels have no mouse listeners, so the panel gets the clicks on the whole cell.
                toolTipText = "Open this point in the Scoring tab."
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) = onOpenPoint(pointId)
                })
            }
            value.detail?.let { detail ->
                add(JLabel(detail).apply {
                    this.name = "$name-detail"
                    foreground = UiStyles.FG_SECONDARY
                    font = font.deriveFont(font.size2D - 1f)
                })
            }
        }

    private fun displayName(name: String, fallback: String) = name.trim().ifEmpty { fallback }

    /** The player color, lighter when it is too dark for the dark tab. The card uses the same color. */
    private fun parseColor(hex: String): Color =
        hex.removePrefix("#").toIntOrNull(16)?.let { Color(StatsCard.onPanel(it)) } ?: UiStyles.FG_PRIMARY

    private companion object {
        const val CARD_STATS = "stats"
        const val CARD_EMPTY = "empty"
        const val VALUE_WIDTH = 120
        const val MAX_SLIDER_ROW_WIDTH = 480
    }
}
