package org.litvin.ui.tabs.export
import org.litvin.ActiveQueueSnapshot
import org.litvin.ApplicationLayout
import org.litvin.ExportPresetsIO
import org.litvin.RenderJob
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.export.ProductionCompletedRendersRepository
import org.litvin.export.CompletedRendersRepository
import org.litvin.export.EncoderCapabilities
import org.litvin.export.ProductionRenderService
import org.litvin.export.RenderService
import org.litvin.RenderStatus
import org.litvin.export.ExportChunkPlanner
import org.litvin.export.ExportPlanner
import org.litvin.export.ExportPointSummary
import org.litvin.export.ExportReadiness
import org.litvin.export.ExportSourceInfo
import org.litvin.export.ExportSourceProbe
import org.litvin.export.ExportRenderPlanRequest
import org.litvin.export.RenderFormatting
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.projects.ManifestIO
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.stats.SetSummaryCard
import org.litvin.stats.StatsCardVideo
import org.litvin.stats.StatsIO
import org.litvin.stats.StatsSettingsV1
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.FilePicker
import org.litvin.ui.commons.SwingFilePicker
import org.litvin.ui.commons.SwingUserDialogService
import org.litvin.ui.commons.UserDialogService
import org.litvin.export.ExportCardInfo

import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.*
import java.io.File
import java.util.concurrent.CompletableFuture
import javax.swing.*
import javax.swing.border.EmptyBorder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phase 4 — Export pipeline (Swing)
 *
 * The left column has three blocks: the project summary, the content of the video, and the quality.
 * The right column shows the active export, the queue and the completed exports.
 */
class SwingExportPanel(
    private val settingsPreferences: ExportSettingsPreferences,
    private val renderService: RenderService,
    private val completedRepository: CompletedRendersRepository,
    private val filePicker: FilePicker,
    private val dialogs: UserDialogService,
    encoderCapabilities: CompletableFuture<EncoderCapabilities>,
) : JPanel(BorderLayout()), AutoCloseable {
    constructor() : this(
        ExportSettingsPreferences(),
        ProductionRenderService(AdjustmentsStore.legacySession(), ProductionCompletedRendersRepository),
        ProductionCompletedRendersRepository,
        SwingFilePicker(),
        SwingUserDialogService(),
        CompletableFuture.supplyAsync(EncoderCapabilities::production),
    )

    private val logger = KotlinLogging.logger {}
    private val closed = AtomicBoolean(false)
    private var queueSubscription: AutoCloseable? = null

    fun onActivated() {
        // Ensure Completed list reflects latest persisted items (global across projects)
        refreshCompletedFromStore()
        // Refresh points summary and button gating on activation to reflect current project context
        updatePointsSummary()
        updateInitButtonState()
        updateScoreboardDefault()
        updateCommentsDefault()
        updateStatsCardDefault()
        updateSetSummariesDefault()
    }
    // Project context (manifest path) — optional; user can still pick output file.
    private var manifestPath: String? = null
    // Keep last observed snapshot to expose Details dialog
    private var lastSnapshot: ActiveQueueSnapshot? = null
    // The source video that was probed last, so that a tab switch does not run ffprobe again.
    private var probedSourcePath: String? = null
    private var sourceInfo = ExportSourceInfo.UNKNOWN

    // Left controls: content of the video
    private val fullVideoRadio = JRadioButton("Full video").apply {
        name = "export-content-full"
        toolTipText = "Export the complete source video, with the time between points."
    }
    private val pointsRadio = JRadioButton("Only points", true).apply {
        name = "export-content-points"
        toolTipText = "Export only the marked points. The time between points is cut."
    }
    private val favoritesRadio = JRadioButton("Only favorites").apply {
        name = "export-content-favorites"
    }
    private var lastContentRadio: JRadioButton = pointsRadio
    private val scoreboardCheck = JCheckBox("Include scoreboard", false).apply {
        name = "export-scoreboard"
        toolTipText = "Burn in a scoreboard overlay that updates after each point. Uses the data of the Scoring tab."
    }
    private val commentsCheck = JCheckBox("Include comments", false).apply {
        name = "export-comments"
        toolTipText = "Burn the comments from the Points tab into the video as centered lower-third text."
    }
    private val statsCardCheck = JCheckBox("Include statistics card", false).apply {
        name = "export-stats-card"
        toolTipText = "Add a card with the match statistics after the last point. Select the statistics in the Stats tab."
    }
    private val statsCardLabel = JLabel().apply { name = "export-stats-card-note" }
    private val setSummariesCheck = JCheckBox("Include set summaries", false).apply {
        name = "export-set-summaries"
        toolTipText = "Add a card with the statistics of each set after the last point of the set. Select the statistics in the Stats tab."
    }
    private val setSummariesLabel = JLabel().apply { name = "export-set-summaries-note" }
    private val contentTable = ExportContentTable(fullVideoRadio, pointsRadio, favoritesRadio)
    private val scoredLabel = JLabel().apply { name = "export-scoreboard-scored" }
    private val qualityPanel = ExportQualityPanel(settingsPreferences)
    private val initButton = UiStyles.primaryButton(START_EXPORT) { onInitializeRender() }.apply {
        name = "export-initialize"
    }

    // Right side — the active export, the queue and the completed exports
    private var activeOutputPath: String? = null
    private val activeCard = ExportJobCard(
        componentPrefix = "export-active",
        onOpenFolder = { activeOutputPath?.let { ExportJobCard.openFolder(it, this, dialogs) } },
        onCancel = { cancelActiveExport() },
        showProgress = true,
    ).apply {
        // The UI tests find the progress bar and the cancel button of the active export by these names.
        progressBar.name = "export-progress"
        cancelButton?.name = "export-cancel"
    }
    private var lastFailureNotifiedJobId: String? = null

    private val exportQueue = ExportQueueList(renderService, dialogs)
    private val completed = CompletedExportsList(completedRepository, dialogs)

    // Theming — reuse UiStyles palette
    private val DARK_BG = UiStyles.DARK_BG
    private val CARD_BG = UiStyles.CARD_BG
    private val CARD_BORDER = UiStyles.CARD_BORDER
    private val FG_PRIMARY = UiStyles.FG_PRIMARY
    private val FG_SECONDARY = UiStyles.FG_SECONDARY

    init {
        border = EmptyBorder(10, 10, 10, 10)
        background = DARK_BG

        // Left configuration column (fixed width, dark theme)
        val left = JPanel(BorderLayout())
        left.border = EmptyBorder(12, 12, 12, 4)
        left.background = CARD_BG
        left.preferredSize = Dimension(LEFT_WIDTH, 10)
        left.minimumSize = Dimension(360, 10)

        val blocks = object : JPanel(), Scrollable {
            // Follow the width of the scroll pane, so that only a vertical scroll bar can show.
            override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
            override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 16
            override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = visibleRect.height
            override fun getScrollableTracksViewportWidth() = true
            override fun getScrollableTracksViewportHeight() = false
        }
        blocks.layout = BoxLayout(blocks, BoxLayout.Y_AXIS)
        blocks.background = CARD_BG
        blocks.border = EmptyBorder(0, 0, 0, 12)

        blocks.add(block("Content", contentControls()))
        blocks.add(Box.createRigidArea(Dimension(0, 32)))
        blocks.add(block("Quality and file size", qualityPanel))

        val scroll = JScrollPane(blocks).apply {
            border = BorderFactory.createEmptyBorder()
            viewport.background = CARD_BG
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = 16
        }
        left.add(scroll, BorderLayout.CENTER)

        // Primary action (neon green). It fills the width of the column; the button centers its icon and text.
        left.add(JPanel(BorderLayout()).apply {
            isOpaque = false
            border = EmptyBorder(12, 0, 0, 12)
            add(initButton, BorderLayout.CENTER)
        }, BorderLayout.SOUTH)

        // Right column with Active + Completed
        val right = JPanel(BorderLayout())
        right.background = DARK_BG
        right.foreground = FG_PRIMARY

        // Active card: a placeholder while no export runs, else the card of the running export.
        val placeholder = JLabel("No active exports").apply {
            foreground = FG_SECONDARY
            alignmentX = 0f
        }
        val activeBody = JPanel()
        activeBody.layout = BoxLayout(activeBody, BoxLayout.Y_AXIS)
        activeBody.background = CARD_BG
        activeBody.border = EmptyBorder(8, 0, 0, 0)
        activeCard.alignmentX = 0f
        activeBody.add(placeholder)
        activeBody.add(activeCard)
        activeCard.isVisible = false

        val activeCardPanel = UiStyles.card("Active Exports", activeBody)

        val exportQueueCardPanel = UiStyles.card("Export Queue", exportQueue.component()).apply {
            isVisible = false
        }

        // The active card and the queue follow the width of the column, so that long text wraps.
        val rightTop = JPanel(GridBagLayout())
        rightTop.background = DARK_BG
        rightTop.add(activeCardPanel, GridBagConstraints().apply {
            gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(0, 0, 10, 0)
        })
        rightTop.add(exportQueueCardPanel, GridBagConstraints().apply {
            gridy = 1
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(0, 0, 10, 0)
        })
        right.add(rightTop, BorderLayout.NORTH)

        // The completed card takes the rest of the height.
        val completedCardPanel = UiStyles.card("Completed Exports", completed.component())
        right.add(completedCardPanel, BorderLayout.CENTER)

        // Load the saved completed exports.
        refreshCompletedFromStore()

        val center = JPanel(BorderLayout())
        center.background = DARK_BG
        center.add(right, BorderLayout.CENTER)

        add(left, BorderLayout.WEST)
        add(center, BorderLayout.CENTER)

        updatePointsSummary()
        updateFavoriteOnlyAvailability()

        listOf(fullVideoRadio, pointsRadio).forEach { radio ->
            radio.addActionListener {
                lastContentRadio = radio
                onContentChanged()
            }
        }
        favoritesRadio.addActionListener {
            if (validFavoriteCount() <= 0) {
                lastContentRadio.isSelected = true
                dialogs.showInfo(
                    this,
                    "Only favorites needs at least one valid favorite point. Mark a point with a star on the Points tab.",
                    "Favorite export unavailable",
                )
            } else {
                lastContentRadio = favoritesRadio
            }
            onContentChanged()
        }
        scoreboardCheck.addActionListener {
            // If user tries to enable scoreboard with no scored points, prevent and explain
            if (scoreboardCheck.isSelected && !hasAnyScoredPoints()) {
                scoreboardCheck.isSelected = false
                dialogs.showInfo(this, "Cannot include scoreboard: there are no scored points in the current project.", "Scoreboard unavailable")
            }
        }
        commentsCheck.addActionListener {
            // Keep the choice of the user for this project. It replaces the default.
            currentProjectDir()?.let { settingsPreferences.saveIncludeComments(it, commentsCheck.isSelected) }
        }
        statsCardCheck.addActionListener {
            if (statsCardCheck.isSelected && currentStatsCard() == null) {
                statsCardCheck.isSelected = false
                dialogs.showInfo(
                    this,
                    "Cannot include the statistics card: no selected statistic has a value. Select the statistics in the Stats tab.",
                    "Statistics card unavailable",
                )
            }
            currentProjectDir()?.let { settingsPreferences.saveIncludeStatsCard(it, statsCardCheck.isSelected) }
            updateOutputDuration()
        }
        setSummariesCheck.addActionListener {
            currentProjectDir()?.let { settingsPreferences.saveIncludeSetSummaries(it, setSummariesCheck.isSelected) }
            updateOutputDuration()
        }

        // The encoder detection runs test encodes in the background. Show its result when it is ready.
        val detection = encoderCapabilities.exceptionally { EncoderCapabilities.NONE }
        val detected = detection.getNow(null)
        if (detected != null) {
            qualityPanel.setEncoders(detected)
        } else {
            detection.thenAccept { capabilities ->
                SwingUtilities.invokeLater { if (!closed.get()) qualityPanel.setEncoders(capabilities) }
            }
        }

        // Observe queue updates to refresh UI
        queueSubscription = renderService.observe { snap ->
            SwingUtilities.invokeLater {
                lastSnapshot = snap
                updateStartButtonText(snap)
                val cur = snap.current
                placeholder.isVisible = cur == null
                activeCard.isVisible = cur != null
                activeOutputPath = cur?.outputPath
                if (cur != null) showActiveExport(cur)
                val q = snap.queued.size
                exportQueue.setJobs(snap.queued)
                exportQueueCardPanel.isVisible = q > 0
                activeCardPanel.toolTipText = if (q > 0) "Exports in the queue: $q" else null
                rightTop.revalidate()
                rightTop.repaint()
            }
        }
    }

    /** Shows the settings, the size and the progress of the running export on the active card. */
    private fun showActiveExport(job: RenderJob) {
        val percent = (job.progress * 100).toInt()
        // The card is in the Active Exports section, so a running export needs no badge.
        // A badge shows only the states that are not a normal running export.
        val status = when (job.status) {
            RenderStatus.QUEUED -> "Starting"
            RenderStatus.RUNNING, RenderStatus.COMPLETED -> null
            RenderStatus.FAILED -> "Failed"
            RenderStatus.CANCELED -> "Canceled"
        }
        activeCard.update(ExportCardInfo.of(job), status)
        activeCard.setProgress(percent)
        activeCard.cancelButton?.isEnabled = job.status == RenderStatus.RUNNING || job.status == RenderStatus.QUEUED
        when (job.status) {
            RenderStatus.FAILED -> {
                val reason = job.failureReason ?: "Unknown error"
                activeCard.setExtraRow("Error", reason)
                if (lastFailureNotifiedJobId != job.id) {
                    lastFailureNotifiedJobId = job.id
                    dialogs.showError(this, reason, "Export failed")
                }
            }
            RenderStatus.COMPLETED -> {
                activeCard.setExtraRow(null, null)
                refreshCompletedFromStore()
            }
            RenderStatus.CANCELED -> activeCard.setExtraRow(null, null)
            else -> activeCard.setExtraRow("Time left", job.etaSeconds?.let(::formatEta) ?: "calculating…")
        }
    }

    private fun cancelActiveExport() {
        if (dialogs.confirm(this, "Cancel the current export? The partly written file is deleted.", "Confirm")) {
            renderService.cancelCurrent()
        }
    }

    fun setProjectManifest(path: String?) {
        manifestPath = path
        refreshSourceInfo()
        updatePointsSummary()
        updateFavoriteOnlyAvailability()
        updateInitButtonState()
        updateScoreboardDefault()
        updateCommentsDefault()
    }

    private fun contentControls(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.isOpaque = false
        panel.alignmentX = 0f
        val group = ButtonGroup()
        listOf(fullVideoRadio, pointsRadio, favoritesRadio).forEach { radio ->
            UiStyles.styleRadioButton(radio)
            group.add(radio)
        }
        contentTable.maximumSize = Dimension(Int.MAX_VALUE, contentTable.preferredSize.height)
        panel.add(contentTable)
        panel.add(Box.createRigidArea(Dimension(0, 8)))
        listOf(scoreboardCheck, commentsCheck, statsCardCheck, setSummariesCheck).forEach(UiStyles::styleCheckBox)
        UiStyles.styleHelper(scoredLabel)
        UiStyles.styleHelper(statsCardLabel)
        UiStyles.styleHelper(setSummariesLabel)
        // The scored count is next to the scoreboard option, because the scoreboard uses it.
        // CENTER gives the note the rest of the width, so that its last characters are not cut off.
        val scoreboardRow = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            alignmentX = 0f
            add(scoreboardCheck, BorderLayout.WEST)
            add(scoredLabel, BorderLayout.CENTER)
        }
        scoreboardRow.maximumSize = Dimension(Int.MAX_VALUE, scoreboardRow.preferredSize.height)
        panel.add(scoreboardRow)
        commentsCheck.alignmentX = 0f
        panel.add(commentsCheck)
        val statsCardRow = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            alignmentX = 0f
            add(statsCardCheck, BorderLayout.WEST)
            add(statsCardLabel, BorderLayout.CENTER)
        }
        statsCardRow.maximumSize = Dimension(Int.MAX_VALUE, statsCardRow.preferredSize.height)
        panel.add(statsCardRow)
        val setSummariesRow = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            alignmentX = 0f
            add(setSummariesCheck, BorderLayout.WEST)
            add(setSummariesLabel, BorderLayout.CENTER)
        }
        setSummariesRow.maximumSize = Dimension(Int.MAX_VALUE, setSummariesRow.preferredSize.height)
        panel.add(setSummariesRow)
        return panel
    }

    /** A block of the left column: a bold title, a thin line, and the content. */
    private fun block(title: String, content: JComponent): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.isOpaque = false
        panel.alignmentX = 0f
        panel.add(JLabel(title).apply {
            font = font.deriveFont(Font.BOLD, font.size2D + 1f)
            foreground = FG_PRIMARY
            alignmentX = 0f
        })
        panel.add(Box.createRigidArea(Dimension(0, 4)))
        panel.add(JSeparator().apply {
            alignmentX = 0f
            maximumSize = Dimension(Int.MAX_VALUE, 2)
        })
        panel.add(Box.createRigidArea(Dimension(0, 8)))
        content.alignmentX = 0f
        panel.add(content)
        return panel
    }

    private fun idleTrimSelected(): Boolean = !fullVideoRadio.isSelected

    private fun favoriteOnlySelected(): Boolean = favoritesRadio.isSelected

    private fun onContentChanged() {
        updateSetSummariesState()
        updateOutputDuration()
        updateInitButtonState()
    }

    private fun onInitializeRender() {
        val readiness = initializationReadiness()
        if (!readiness.enabled) {
            dialogs.showInfo(
                this,
                readiness.disabledReason ?: "Export cannot start right now.",
                INIT_BLOCKED_TITLE,
            )
            updateFavoriteOnlyAvailability()
            updateInitButtonState()
            return
        }

        // Resolve manifest and source video
        val manifestPath = this.manifestPath
        val manifest = try {
            if (manifestPath.isNullOrBlank()) null else ManifestIO.read(manifestPath)
        } catch (t: Throwable) {
            dialogs.showError(this, t.message ?: t.toString(), "Failed to read manifest")
            return
        }
        val source = manifest?.sourceVideo
        if (source.isNullOrBlank() || !File(source).exists()) {
            dialogs.showError(this, "Source video missing", "Select source video")
            return
        }

        val projectDir = if (manifestPath != null) EdlIO.projectDirFromManifest(manifestPath) else null
        val edl = try { if (projectDir != null) EdlIO.readForProjectDir(projectDir) else null } catch (_: Throwable) { null }
        val allValidPoints = ExportPlanner.validateEdl(edl)
        val idleTrim = idleTrimSelected()
        val favoriteOnly = favoriteOnlySelected()
        val keeps: List<PointV1> = ExportPlanner.selectedKeepPoints(
            validPoints = allValidPoints,
            idleTrim = idleTrim,
            favoriteOnly = favoriteOnly,
        )
        if (favoriteOnly && keeps.isEmpty()) {
            dialogs.showInfo(this, "Cannot export only favorite points: no valid favorite points are available.", "Favorite export unavailable")
            updateFavoriteOnlyAvailability()
            updateInitButtonState()
            return
        }
        if (idleTrim && keeps.isEmpty()) {
            if (!dialogs.confirm(this, "The project has no valid points. Export the full video?", "No points")) return
        }
        val quality = qualityPanel.selection()
        val target = quality.target
        val presets = ExportPresetsIO.load()
        val selPreset = presets.firstOrNull { it.id == target.presetId } ?: presets[ExportPresetsIO.defaultBalancedIndex(presets)]

        // Choose output path
        val initialDir = settingsPreferences.loadOutputDirectory()
            ?: projectDir?.let { File(it) }
            ?: File(source).parentFile
        val suggestedFile = File(
            initialDir,
            ExportPlanner.suggestFilename(
                projectName = manifest?.name ?: File(source).nameWithoutExtension,
                presetId = selPreset.id,
                resolutionLabel = target.resolution.label,
            )
        )
        var out = filePicker.chooseExportDestination(
            parent = this,
            title = "Save Export As…",
            initialDirectory = initialDir,
            suggestedFile = suggestedFile,
        ) ?: return
        settingsPreferences.saveOutputDirectory(out)
        // Ensure extension if user omitted
        val defaultExt = selPreset.container?.format?.lowercase()?.let { if (it.startsWith(".")) it.drop(1) else it } ?: "mp4"
        out = ExportPlanner.ensureExtension(out, defaultExt)

        if (out.exists()) {
            if (!dialogs.confirm(this, "File exists. Overwrite?", "Confirm overwrite")) return
        }

        val score = try {
            if (projectDir != null) ScoreIO.readForProjectDir(projectDir) else ScoreV1()
        } catch (_: Throwable) {
            ScoreV1()
        }
        val plan = ExportPlanner.buildRenderPlan(
            ExportRenderPlanRequest(
                manifest = manifest,
                sourcePath = source,
                edl = edl,
                score = score,
                preset = selPreset,
                resolution = target.resolution,
                outputFrameRate = target.frameRate?.ffmpegArgument,
                videoBitrateK = target.bitrateK,
                encoderLabel = quality.encoder.jobLabel,
                idleTrim = idleTrim,
                favoriteOnly = favoriteOnly,
                includeScoreboard = scoreboardCheck.isSelected,
                includeComments = commentsCheck.isSelected,
                outputPath = out.absolutePath,
                sourceDurationMs = sourceInfo.durationMs,
                includeStatsCard = statsCardCheck.isSelected,
                includeSetSummaries = setSummariesCheck.isEnabled && setSummariesCheck.isSelected,
                statsSettings = readCurrentStatsSettings(),
            )
        )

        val queued = hasActiveExports(lastSnapshot)
        renderService.enqueue(plan.job)
        dialogs.showInfo(
            this,
            if (queued) "Export queued: ${out.name}. It starts when the current exports end." else "Export started: ${out.name}",
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        queueSubscription?.close()
        queueSubscription = null
    }

    /** Reads the size, frame rate, bitrate and duration of the source video with ffprobe. */
    private fun refreshSourceInfo() {
        val sourcePath = try {
            manifestPath?.let(ManifestIO::read)?.sourceVideo
        } catch (_: Throwable) {
            null
        }?.takeIf { File(it).isFile }
        if (sourcePath != null && sourcePath == probedSourcePath) return
        probedSourcePath = sourcePath
        sourceInfo = sourcePath
            ?.let { ExportSourceProbe.probe(it, ApplicationLayout.current().ffprobeExecutable) }
            ?: ExportSourceInfo.UNKNOWN
        qualityPanel.setSource(sourceInfo)
    }

    /** The duration of the exported video, for the file size estimates. */
    private fun updateOutputDuration() {
        val durationMs = if (!idleTrimSelected()) {
            sourceInfo.durationMs
        } else {
            val validPoints = ExportPlanner.validateEdl(readCurrentProjectEdl())
            val kept = ExportPlanner.selectedKeepPoints(validPoints, idleTrim = true, favoriteOnly = favoriteOnlySelected())
            // Without valid points the export falls back to the full video.
            if (kept.isEmpty()) sourceInfo.durationMs else ExportChunkPlanner.keptDurationMs(kept)
        }
        val cardMs = if (statsCardCheck.isSelected) currentStatsCard()?.durationMs ?: 0L else 0L
        val setCardsMs = if (setSummariesCheck.isEnabled && setSummariesCheck.isSelected) {
            currentSetSummaries().sumOf { it.card.durationMs }
        } else {
            0L
        }
        qualityPanel.setOutputDurationMs(durationMs?.plus(cardMs + setCardsMs))
    }

    private fun refreshCompletedFromStore() {
        completed.refreshFromStore()
    }

    private fun formatEta(secs: Long): String {
        val h = secs / 3600
        val m = (secs % 3600) / 60
        val s = secs % 60
        return String.format("%d:%02d:%02d", h, m, s)
    }

    private fun validFavoriteCount(): Int {
        return try {
            loadExportSummary().favoriteCount
        } catch (_: Throwable) {
            0
        }
    }

    private fun updateFavoriteOnlyAvailability() {
        val available = manifestPath != null
        favoritesRadio.isEnabled = available
        if (!available && favoritesRadio.isSelected) {
            pointsRadio.isSelected = true
            lastContentRadio = pointsRadio
        }
        favoritesRadio.toolTipText = when {
            !available -> "Requires an open project."
            validFavoriteCount() > 0 -> "Export only the points that are marked with a star."
            else -> "No valid favorite points are available. Mark a point with a star on the Points tab."
        }
    }

    private fun hasAnyScoredPoints(): Boolean {
        return try {
            readCurrentProjectScore().outcomes.values.any { it == Outcome.P1 || it == Outcome.P2 }
        } catch (_: Throwable) {
            false
        }
    }

    private fun updateScoreboardDefault() {
        scoreboardCheck.isSelected = hasAnyScoredPoints()
    }

    /** Selects the checkbox if the project has comments. A saved choice of the user has priority. */
    private fun updateCommentsDefault() {
        val saved = currentProjectDir()?.let(settingsPreferences::loadIncludeComments)
        commentsCheck.isSelected = saved ?: hasAnyComments()
    }

    /**
     * Selects the checkbox if the project has a statistics card. A saved choice of the user has priority.
     * The note shows how long the card is, or why the export has no card.
     */
    private fun updateStatsCardDefault() {
        val card = currentStatsCard()
        statsCardLabel.text = when {
            card != null -> "${card.durationMs / 1000} s, " + if (card.pages.size == 1) "1 page" else "${card.pages.size} pages"
            hasAnyScoredPoints() -> "No selected statistics"
            else -> "No scored points"
        }
        val saved = currentProjectDir()?.let(settingsPreferences::loadIncludeStatsCard)
        statsCardCheck.isSelected = card != null && (saved ?: true)
        updateOutputDuration()
    }

    /** The statistics card of the current project at 1080p, or null when the export cannot have a card. */
    private fun currentStatsCard(): StatsCardVideo? = try {
        StatsCardVideo.of(readCurrentProjectEdl(), readCurrentProjectScore(), readCurrentStatsSettings(), 1920, 1080)
    } catch (t: Throwable) {
        logger.warn(t) { "Export: cannot make the statistics card" }
        null
    }

    /** Selects the checkbox from the saved choice of the user. Set summaries are off by default. */
    private fun updateSetSummariesDefault() {
        val saved = currentProjectDir()?.let(settingsPreferences::loadIncludeSetSummaries)
        setSummariesCheck.isSelected = saved ?: false
        updateSetSummariesState()
        updateOutputDuration()
    }

    /**
     * Enables the checkbox when the selected content has at least one set card.
     * The note shows how long the set cards are, or why the export has no set cards.
     */
    private fun updateSetSummariesState() {
        val summaries = currentSetSummaries()
        setSummariesCheck.isEnabled = summaries.isNotEmpty()
        setSummariesLabel.text = when {
            !idleTrimSelected() -> "Only for Only points and Only favorites"
            summaries.isNotEmpty() -> {
                val seconds = summaries.sumOf { it.card.durationMs } / 1000
                (if (summaries.size == 1) "1 set" else "${summaries.size} sets") + ", $seconds s"
            }
            !hasAnyScoredPoints() -> "No scored points"
            currentStatsCard() == null -> "No selected statistics"
            else -> "No completed set in the video"
        }
    }

    /** The set cards of the selected content at 1080p. A full video export has no set cards. */
    private fun currentSetSummaries(): List<SetSummaryCard> = try {
        if (!idleTrimSelected()) {
            emptyList()
        } else {
            val edl = readCurrentProjectEdl()
            val kept = ExportPlanner.selectedKeepPoints(
                ExportPlanner.validateEdl(edl),
                idleTrim = true,
                favoriteOnly = favoriteOnlySelected(),
            )
            ExportPlanner.setSummaryCards(edl, readCurrentProjectScore(), readCurrentStatsSettings(), kept, 1920, 1080)
        }
    } catch (t: Throwable) {
        logger.warn(t) { "Export: cannot make the set cards" }
        emptyList()
    }

    private fun readCurrentStatsSettings(): StatsSettingsV1 = try {
        currentProjectDir()?.let(StatsIO::readForProjectDir) ?: StatsSettingsV1()
    } catch (t: Throwable) {
        logger.warn(t) { "Export: cannot read stats.json" }
        StatsSettingsV1()
    }

    private fun hasAnyComments(): Boolean =
        readCurrentProjectEdl()?.comments.orEmpty().any { it.text.isNotBlank() }

    private fun currentProjectDir(): String? {
        val mp = manifestPath
        if (mp.isNullOrBlank()) return null
        return try {
            EdlIO.projectDirFromManifest(mp)
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadExportSummary() = ExportPlanner.summarize(
        readCurrentProjectEdl(),
        readCurrentProjectScore(),
    )

    private fun readCurrentProjectEdl(): EdlV1? {
        val projectDir = currentProjectDir()
        if (projectDir == null) {
            logger.info { "Export summary: no project directory (manifestPath=$manifestPath)" }
            return null
        }
        return try {
            EdlIO.readForProjectDir(projectDir).also {
                logger.info { "Export summary: read ${it.points.size} points from ${EdlIO.edlFilePath(projectDir)}" }
            }
        } catch (t: Throwable) {
            logger.warn(t) { "Export summary: failed to read EDL from ${EdlIO.edlFilePath(projectDir)}" }
            null
        }
    }

    private fun readCurrentProjectScore(): ScoreV1 {
        val projectDir = currentProjectDir()
        return try {
            if (projectDir != null) ScoreIO.readForProjectDir(projectDir) else ScoreV1()
        } catch (t: Throwable) {
            logger.warn(t) { "Export summary: failed to read score for $projectDir" }
            ScoreV1()
        }
    }

    private fun showScoredCount(summary: ExportPointSummary?) {
        scoredLabel.text = when {
            summary == null -> ""
            summary.pointCount == 0 -> "(no points)"
            else -> "(${summary.scoredCount}/${summary.pointCount} points scored)"
        }
        scoredLabel.foreground = if (summary?.allScored == true) UiStyles.GREEN else UiStyles.FG_SECONDARY
    }

    private fun updatePointsSummary() {
        val summary = try {
            if (manifestPath == null) null else loadExportSummary()
        } catch (_: Throwable) {
            null
        }
        contentTable.show(summary, sourceInfo.durationMs.takeIf { manifestPath != null })
        showScoredCount(summary)
        updateFavoriteOnlyAvailability()
        updateOutputDuration()
    }

    // Task 3.15 — Read project context and prerequisites for the start button
    private fun initializationReadiness(): ExportReadiness = try {
        val mp = manifestPath
        val manifest = try { mp?.let(ManifestIO::read) } catch (_: Throwable) { null }
        val validPoints = if (mp.isNullOrBlank()) {
            emptyList()
        } else {
            val projectDir = EdlIO.projectDirFromManifest(mp)
            ExportPlanner.validateEdl(try { EdlIO.readForProjectDir(projectDir) } catch (_: Throwable) { null })
        }
        ExportPlanner.initializationReadiness(
            hasProject = !mp.isNullOrBlank(),
            sourceVideoExists = manifest?.sourceVideo?.let { File(it).isFile } == true,
            idleTrim = idleTrimSelected(),
            favoriteOnly = favoriteOnlySelected(),
            validPoints = validPoints,
        )
    } catch (_: Throwable) {
        ExportReadiness(false, "Export is not available because of an unexpected error.")
    }

    // The button stays clickable even when an export cannot start; clicking it explains why.
    private fun updateInitButtonState() {
        val readiness = initializationReadiness()
        initButton.isEnabled = true
        initButton.toolTipText = readiness.disabledReason ?: INIT_BUTTON_TOOLTIP
        initButton.accessibleContext.accessibleDescription = readiness.disabledReason
    }

    /** "Start export" when nothing runs, "Enqueue export" when an export runs or waits in the queue. */
    private fun updateStartButtonText(snapshot: ActiveQueueSnapshot?) {
        val text = if (hasActiveExports(snapshot)) ENQUEUE_EXPORT else START_EXPORT
        if (initButton.text != text) {
            initButton.text = text
            initButton.revalidate()
            initButton.repaint()
        }
    }

    private fun hasActiveExports(snapshot: ActiveQueueSnapshot?): Boolean {
        if (snapshot == null) return false
        val current = snapshot.current
        val running = current != null && (current.status == RenderStatus.RUNNING || current.status == RenderStatus.QUEUED)
        return running || snapshot.queued.isNotEmpty()
    }

    private companion object {
        const val LEFT_WIDTH = 440
        const val START_EXPORT = "Start export"
        const val ENQUEUE_EXPORT = "Enqueue export"
        const val INIT_BUTTON_TOOLTIP = "Choose an output file and start the export."
        const val INIT_BLOCKED_TITLE = "Cannot start export"
    }
}
