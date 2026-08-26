package org.litvin.ui.tabs.export
import org.litvin.ActiveQueueSnapshot
import org.litvin.ApplicationLayout
import org.litvin.CompletedRender
import org.litvin.FFmpegCapabilities
import org.litvin.ExportPresetsIO
import org.litvin.RenderJob
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.export.ProductionCompletedRendersRepository
import org.litvin.export.CompletedRendersRepository
import org.litvin.export.EncoderCapabilities
import org.litvin.export.ProductionRenderService
import org.litvin.export.RenderService
import org.litvin.RenderStatus
import org.litvin.export.ExportPlanner
import org.litvin.export.ExportFrameRateOption
import org.litvin.export.ExportFrameRateProbe
import org.litvin.export.ExportFrameRates
import org.litvin.export.ExportRenderPlanRequest
import org.litvin.export.RenderFormatting
import org.litvin.markup.EdlIO
import org.litvin.markup.EdlV1
import org.litvin.markup.PointV1
import org.litvin.projects.ManifestIO
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.Html
import org.litvin.ui.commons.FilePicker
import org.litvin.ui.commons.SwingFilePicker
import org.litvin.ui.commons.SwingUserDialogService
import org.litvin.ui.commons.UserDialogService

import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phase 4 — Export pipeline (Swing)
 *
 * Provides a Swing UI to configure and start an export job using the existing
 * FFmpegCommandBuilder and RenderQueueManager. Shows live progress with Cancel.
 */
class SwingExportPanel(
    private val settingsPreferences: ExportSettingsPreferences,
    private val renderService: RenderService,
    private val completedRepository: CompletedRendersRepository,
    private val filePicker: FilePicker,
    private val dialogs: UserDialogService,
    encoderCapabilities: EncoderCapabilities,
    private val onHelp: () -> Unit = {},
) : JPanel(BorderLayout()), AutoCloseable {
    constructor(onHelp: () -> Unit = {}) : this(
        ExportSettingsPreferences(),
        ProductionRenderService(AdjustmentsStore.legacySession(), ProductionCompletedRendersRepository),
        ProductionCompletedRendersRepository,
        SwingFilePicker(),
        SwingUserDialogService(),
        EncoderCapabilities.production(),
        onHelp,
    )

    private val closed = AtomicBoolean(false)
    private var queueSubscription: AutoCloseable? = null

    fun onActivated() {
        // Ensure Completed list reflects latest persisted items (global across projects)
        refreshCompletedFromStore()
        // Refresh points summary and button gating on activation to reflect current project context
        updatePointsSummary()
        updateInitButtonState()
    }
    // Project context (manifest path) — optional; user can still pick output file.
    private var manifestPath: String? = null
    // Keep last observed snapshot to expose Details dialog
    private var lastSnapshot: ActiveQueueSnapshot? = null

    // Left controls
    private val presets = ExportPresetsIO.load()
    private val savedVideoSettings = settingsPreferences.load()
    private val presetCombo = JComboBox(presets.map { it.label }.toTypedArray()).apply { name = "export-preset" }
    private val resCombo = JComboBox(arrayOf("1920x1080", "3840x2160")).apply { name = "export-resolution" }
    private val frameRateCombo = JComboBox<ExportFrameRateOption>()
    private var refreshingFrameRateOptions = false
    private val idleTrimCheck = JCheckBox("Cut idle time between points", true).apply { name = "export-idle-trim" }
    private val favoriteOnlyCheck = JCheckBox("Only favorite points", false).apply {
        name = "export-favorites-only"
        toolTipText = "Render only points marked with a star. Requires idle-trim because the export is assembled from point intervals."
    }
    private val scoreboardCheck = JCheckBox("Include Scoreboard", false).apply {
        name = "export-scoreboard"
        toolTipText = "Burn in a simple scoreboard overlay that updates after each point. Uses current Scoring data; fixed English labels in v0.1.0."
    }
    private val initButton = UiStyles.primaryButton("Initialize Render") { onInitializeRender() }.apply {
        name = "export-initialize"
    }
    private val encoderPanel = EncoderSummaryPanel(
        savedVideoSettings.encoderId ?: encoderCapabilities.preferredId,
        encoderCapabilities.availableIds,
    )

    // Right side — Active + Completed
    private val progressBar = JProgressBar(0, 100).apply { name = "export-progress" }
    private val progressLabel = JLabel("Idle")
    private val cancelButton = JButton("Cancel").apply { name = "export-cancel" }
    private var lastFailureNotifiedJobId: String? = null
    private var lastFailureJob: RenderJob? = null

    private val renderQueue = RenderQueueList(renderService, dialogs)
    private val completed = CompletedRendersList(completedRepository, dialogs)

    // Theming — reuse UiStyles palette
    private val DARK_BG = UiStyles.DARK_BG
    private val CARD_BG = UiStyles.CARD_BG
    private val CARD_BORDER = UiStyles.CARD_BORDER
    private val FG_PRIMARY = UiStyles.FG_PRIMARY
    private val FG_SECONDARY = UiStyles.FG_SECONDARY

    // Helper text labels (from JavaFX ExportTab)
    private val qualityLabel = JLabel("")
    private val resSummaryLabel = JLabel("")
    private val scalePlanLabel = JLabel("")
    private val pointsCountLabel = JLabel("")
    private val pointsTotalLabel = JLabel("")
    private val pointsScoredLabel = JLabel("")

    init {
        border = EmptyBorder(10, 10, 10, 10)
        background = DARK_BG

        // Left configuration column (fixed width, dark theme)
        val left = JPanel()
        left.layout = BoxLayout(left, BoxLayout.Y_AXIS)
        left.border = EmptyBorder(12, 12, 12, 16)
        left.background = CARD_BG
        left.foreground = FG_PRIMARY
        left.preferredSize = Dimension(420, 10)
        left.minimumSize = Dimension(320, 10)
        left.maximumSize = Dimension(420, Int.MAX_VALUE)

        val title = JLabel("Export Settings").apply {
            font = font.deriveFont(Font.BOLD)
            foreground = FG_PRIMARY
        }
        left.add(JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = 0f
            maximumSize = Dimension(Int.MAX_VALUE, 36)
            add(title, BorderLayout.WEST)
            add(JButton("Help [F1]").apply {
                name = "export-help"
                toolTipText = "F1 - Help"
                UiStyles.styleSecondary(this)
                addActionListener { onHelp() }
            }, BorderLayout.EAST)
        })
        left.add(Box.createRigidArea(Dimension(0, 8)))
        left.add(JSeparator())
        left.add(Box.createRigidArea(Dimension(0, 12)))

        // Points and timing summary
        fun stylePointsLabel(l: JLabel) {
            l.alignmentX = 0f
            l.foreground = FG_PRIMARY
            l.font = l.font.deriveFont(Font.BOLD, l.font.size + 4f)
        }
        listOf(pointsCountLabel, pointsTotalLabel, pointsScoredLabel).forEach { stylePointsLabel(it) }
        left.add(pointsCountLabel)
        left.add(Box.createRigidArea(Dimension(0, 2)))
        left.add(pointsTotalLabel)
        left.add(Box.createRigidArea(Dimension(0, 2)))
        left.add(pointsScoredLabel)
        left.add(Box.createRigidArea(Dimension(0, 8)))

        // Idle-trim + EDL info
        UiStyles.styleCheckBox(idleTrimCheck)
        left.add(idleTrimCheck)
        UiStyles.styleCheckBox(favoriteOnlyCheck)
        left.add(favoriteOnlyCheck)
        UiStyles.styleCheckBox(scoreboardCheck)
        left.add(scoreboardCheck)
        left.add(Box.createRigidArea(Dimension(0, 12)))

        // Video settings
        left.add(sectionLabel("Video Settings"))
        left.add(Box.createRigidArea(Dimension(0, 8)))
        left.add(sectionLabel("Preset"))
        presetCombo.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 28)
        UiStyles.styleComboBox(presetCombo)
        left.add(presetCombo)
        UiStyles.styleHelper(qualityLabel)
        qualityLabel.text = ""
        left.add(Box.createRigidArea(Dimension(0, 4)))
        left.add(qualityLabel)
        left.add(Box.createRigidArea(Dimension(0, 12)))

        left.add(sectionLabel("Resolution"))
        val resModel = DefaultComboBoxModel(arrayOf("1080p", "4K"))
        resCombo.model = resModel
        resCombo.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 28)
        UiStyles.styleComboBox(resCombo)
        left.add(resCombo)
        UiStyles.styleHelper(resSummaryLabel)
        UiStyles.styleMono(scalePlanLabel)
        left.add(Box.createRigidArea(Dimension(0, 6)))
        left.add(resSummaryLabel)
        left.add(scalePlanLabel)
        left.add(Box.createRigidArea(Dimension(0, 12)))

        left.add(sectionLabel("FPS"))
        frameRateCombo.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 28)
        frameRateCombo.toolTipText = "Output frame rate. The source video frame rate is marked."
        UiStyles.styleComboBox(frameRateCombo)
        frameRateCombo.isEnabled = false
        left.add(frameRateCombo)
        left.add(Box.createRigidArea(Dimension(0, 12)))

        left.add(sectionLabel("Encoder"))
        left.add(this.encoderPanel.component())
        left.add(Box.createVerticalGlue())

        // Primary action (neon green)
        initButton.alignmentX = 0f
        left.add(Box.createRigidArea(Dimension(0, 12)))
        left.add(initButton)

        // Right column with Active + Completed
        val right = JPanel(BorderLayout())
        right.background = DARK_BG
        right.foreground = FG_PRIMARY

        // Active card
        val nameLabel = JLabel("")
        val jobIdLabel = JLabel("")
        UiStyles.styleMono(jobIdLabel)
        jobIdLabel.foreground = FG_SECONDARY
        val stats = JPanel(BorderLayout())
        stats.background = CARD_BG
        stats.add(progressLabel, BorderLayout.WEST)
        val rightButtons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        rightButtons.background = CARD_BG
        UiStyles.styleSecondary(cancelButton)
        cancelButton.isEnabled = false
        rightButtons.add(cancelButton)
        stats.add(rightButtons, BorderLayout.EAST)
        cancelButton.addActionListener { renderService.cancelCurrent() }

        progressBar.value = 0
        progressBar.isStringPainted = true

        val placeholder = JLabel("No active renders").apply {
            horizontalAlignment = SwingConstants.CENTER
            foreground = FG_SECONDARY
        }

        val activeBody = JPanel()
        activeBody.layout = BoxLayout(activeBody, BoxLayout.Y_AXIS)
        activeBody.background = CARD_BG
        listOf(placeholder, nameLabel, jobIdLabel, Box.createRigidArea(Dimension(0,6)), progressBar, Box.createRigidArea(Dimension(0,6)), stats).forEach { activeBody.add(it) }
        // Initial state: show placeholder, hide active controls
        placeholder.isVisible = true
        nameLabel.isVisible = false
        jobIdLabel.isVisible = false
        progressBar.isVisible = false
        stats.isVisible = false

        val activeCardPanel = UiStyles.card("Active Renders", activeBody)

        val renderQueueCardPanel = UiStyles.card("Render Queue", renderQueue.component()).apply {
            isVisible = false
            maximumSize = Dimension(Int.MAX_VALUE, 190)
        }

        val rightTop = JPanel()
        rightTop.layout = BoxLayout(rightTop, BoxLayout.Y_AXIS)
        rightTop.background = DARK_BG
        activeCardPanel.alignmentX = 0f
        renderQueueCardPanel.alignmentX = 0f
        rightTop.add(activeCardPanel)
        rightTop.add(Box.createRigidArea(Dimension(0, 10)))
        rightTop.add(renderQueueCardPanel)
        right.add(rightTop, BorderLayout.NORTH)

        // Completed card takes the rest of vertical space
        val completedCardPanel = UiStyles.card("Completed Renders", completed.component())
        right.add(completedCardPanel, BorderLayout.CENTER)

        // Load persisted completed renders initially
        refreshCompletedFromStore()

        val center = JPanel(BorderLayout())
        center.background = DARK_BG
        center.add(right, BorderLayout.CENTER)

        add(left, BorderLayout.WEST)
        add(center, BorderLayout.CENTER)

        // --- Wiring updates like JavaFX implementation ---
        fun defaultResForPreset(presetId: String): String = if (presetId.equals("quality", true)) "4K" else "1080p"
        fun targetDimsFor(sel: String): Pair<Int, Int> = if (sel == "4K") 3840 to 2160 else 1920 to 1080
        fun updateQualitySummary(idx: Int) {
            if (idx !in presets.indices) return
            val p = presets[idx]
            val v = p.video
            val parts = mutableListOf<String>()
            v.crf?.let { parts.add("CRF $it") }
            v.x264Preset?.let { parts.add("x264 $it") }
            v.vbvMaxrateK?.let { parts.add("Maxrate ${it}k") }
            if (parts.isEmpty()) p.description?.let { parts.add(it) }
            Html.setWrapped(qualityLabel, parts.filter { it.isNotBlank() }.joinToString(" · "))
        }
        fun updateResolutionPreview() {
            val sel = resCombo.selectedItem as? String ?: "1080p"
            val (w, h) = targetDimsFor(sel)
            resSummaryLabel.text = "Output: $w x $h ($sel)"
        }

        // Restore the last video settings, falling back to the original defaults.
        val defIdx = ExportPresetsIO.defaultBalancedIndex(presets)
        if (presets.isNotEmpty()) {
            val restoredPresetIndex = presets.indexOfFirst { it.id == savedVideoSettings.presetId }
                .takeIf { it >= 0 }
                ?: defIdx
            presetCombo.selectedIndex = restoredPresetIndex
            val res = savedVideoSettings.resolution
                ?.takeIf { it == "1080p" || it == "4K" }
                ?: defaultResForPreset(presets[restoredPresetIndex].id)
            resCombo.selectedItem = res
            updateQualitySummary(restoredPresetIndex)
        }
        updateResolutionPreview()
        updatePointsSummary()
        updateFavoriteOnlyAvailability()

        presetCombo.addActionListener {
            val idx = presetCombo.selectedIndex
            if (idx in presets.indices) {
                val res = defaultResForPreset(presets[idx].id)
                resCombo.selectedItem = res
                updateQualitySummary(idx)
                updateResolutionPreview()
                settingsPreferences.savePreset(presets[idx].id)
                settingsPreferences.saveResolution(res)
            }
        }
        resCombo.addActionListener {
            updateResolutionPreview()
            (resCombo.selectedItem as? String)?.let(settingsPreferences::saveResolution)
        }
        frameRateCombo.addActionListener {
            if (refreshingFrameRateOptions) return@addActionListener
            (frameRateCombo.selectedItem as? ExportFrameRateOption)
                ?.frameRate
                ?.ffmpegArgument
                ?.let(settingsPreferences::saveOutputFrameRate)
        }
        encoderPanel.addChangeListener {
            settingsPreferences.saveEncoder(encoderPanel.selectedEncoderId())
        }
        idleTrimCheck.addActionListener {
            if (!idleTrimCheck.isSelected) favoriteOnlyCheck.isSelected = false
            updateFavoriteOnlyAvailability()
            updatePointsSummary()
            updateInitButtonState()
        }
        favoriteOnlyCheck.addActionListener {
            if (favoriteOnlyCheck.isSelected && (!idleTrimCheck.isSelected || validFavoriteCount() <= 0)) {
                favoriteOnlyCheck.isSelected = false
                dialogs.showInfo(this, "Favorite-only export requires idle-trim and at least one valid favorite point.", "Favorite export unavailable")
            }
            updatePointsSummary()
            updateInitButtonState()
        }
        scoreboardCheck.addActionListener {
            // If user tries to enable scoreboard with no scored points, prevent and explain
            if (scoreboardCheck.isSelected && !hasAnyScoredPoints()) {
                scoreboardCheck.isSelected = false
                dialogs.showInfo(this, "Cannot include scoreboard: there are no scored points in the current project.", "Scoreboard unavailable")
                return@addActionListener
            }
            updatePointsSummary()
        }

        // Observe queue updates to refresh UI
        queueSubscription = renderService.observe { snap ->
            SwingUtilities.invokeLater {
                lastSnapshot = snap
                val cur = snap.current
                if (cur == null) {
                    // No active export — show placeholder and hide controls
                    placeholder.isVisible = true
                    nameLabel.isVisible = false
                    jobIdLabel.isVisible = false
                    progressBar.isVisible = false
                    stats.isVisible = false

                    nameLabel.text = ""
                    jobIdLabel.text = ""
                    progressBar.value = 0
                    progressBar.string = ""
                    progressLabel.text = "Idle"
                    cancelButton.isEnabled = false
                } else {
                    // Active export — show controls and hide placeholder
                    placeholder.isVisible = false
                    nameLabel.isVisible = true
                    jobIdLabel.isVisible = true
                    progressBar.isVisible = true
                    stats.isVisible = true

                    val sbFlag = if (cur.includeScoreboard) "  ·  Scoreboard" else ""
                    nameLabel.text = File(cur.outputPath).name + sbFlag
                    jobIdLabel.text = "Job ID: ${cur.id}"
                    progressBar.value = (cur.progress * 100).toInt()
                    progressBar.string = "${(cur.progress * 100).toInt()}%"
                    val eta = cur.etaSeconds?.let { formatEta(it) } ?: "--"
                    val sz = RenderFormatting.formatSize(cur.bytesWritten)
                    cancelButton.isEnabled = cur.status == RenderStatus.RUNNING
                    when (cur.status) {
                        RenderStatus.FAILED -> {
                            val reason = cur.failureReason ?: "Unknown error"
                            progressLabel.text = "FAILED — $reason"
                            if (lastFailureNotifiedJobId != cur.id) {
                                lastFailureNotifiedJobId = cur.id
                                lastFailureJob = cur
                                dialogs.showError(this, reason, "Render failed")
                            }
                        }
                        RenderStatus.COMPLETED -> {
                            progressLabel.text = "Completed — Size: $sz"
                            refreshCompletedFromStore()
                        }
                        RenderStatus.CANCELED -> {
                            progressLabel.text = "Canceled"
                        }
                        else -> {
                            progressLabel.text = "ETA: $eta    Size: $sz"
                        }
                    }
                }
                val q = snap.queued.size
                renderQueue.setJobs(snap.queued)
                renderQueueCardPanel.isVisible = q > 0
                activeCardPanel.toolTipText = if (q > 0) "queued: $q" else null
                renderQueueCardPanel.revalidate()
                renderQueueCardPanel.repaint()
                rightTop.revalidate()
                rightTop.repaint()
            }
        }
    }

    fun setProjectManifest(path: String?) {
        manifestPath = path
        refreshFrameRateOptions()
        updatePointsSummary()
        updateFavoriteOnlyAvailability()
        updateInitButtonState()
        try {
            val mp = manifestPath
            val projectDir = if (!mp.isNullOrBlank()) EdlIO.projectDirFromManifest(mp) else null
            if (projectDir != null) {
                val score = ScoreIO.readForProjectDir(projectDir)
                val anyWon = score.outcomes.values.any { it == Outcome.P1 || it == Outcome.P2 }
                scoreboardCheck.isSelected = anyWon
            } else {
                scoreboardCheck.isSelected = false
            }
        } catch (_: Throwable) {
            scoreboardCheck.isSelected = false
        }
    }

    private fun onInitializeRender() {
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
        val favoriteOnly = favoriteOnlyCheck.isSelected
        val keeps: List<PointV1> = ExportPlanner.selectedKeepPoints(
            validPoints = allValidPoints,
            idleTrim = idleTrimCheck.isSelected,
            favoriteOnly = favoriteOnly,
        )
        if (favoriteOnly && keeps.isEmpty()) {
            dialogs.showInfo(this, "Cannot render only favorite points: no valid favorite points are available.", "Favorite export unavailable")
            updateFavoriteOnlyAvailability()
            updateInitButtonState()
            return
        }
        if (idleTrimCheck.isSelected && keeps.isEmpty()) {
            if (!dialogs.confirm(this, "EDL is empty or invalid. Continue with full render?", "EDL warning")) return
        }
        // Choose output path
        val initialDir = settingsPreferences.loadOutputDirectory()
            ?: projectDir?.let { File(it) }
            ?: File(source).parentFile
        val selPreset = presets.getOrNull(presetCombo.selectedIndex) ?: presets[ExportPresetsIO.defaultBalancedIndex(presets)]
        val suggestedFile = File(
            initialDir,
            ExportPlanner.suggestFilename(
                projectName = manifest?.name ?: File(source).nameWithoutExtension,
                presetId = selPreset.id,
                resolutionLabel = resCombo.selectedItem as String,
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

        val resolution = ExportPlanner.parseResolution(resCombo.selectedItem as String)
        val outputFrameRate = (frameRateCombo.selectedItem as? ExportFrameRateOption)
            ?.frameRate
            ?.ffmpegArgument
        if (outputFrameRate == null) {
            dialogs.showError(this, "Source video frame rate unavailable", "Cannot determine source FPS")
            return
        }
        val encoderLabel = encoderPanel.selectedEncoderLabel().substringBefore(" — ")

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
                resolution = resolution,
                outputFrameRate = outputFrameRate,
                encoderLabel = encoderLabel,
                idleTrim = idleTrimCheck.isSelected,
                favoriteOnly = favoriteOnly,
                includeScoreboard = scoreboardCheck.isSelected,
                outputPath = out.absolutePath,
            )
        )

        renderService.enqueue(plan.job)
        dialogs.showInfo(this, "Render initialized: ${out.name}")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        queueSubscription?.close()
        queueSubscription = null
    }

    private fun refreshFrameRateOptions() {
        val sourcePath = try {
            manifestPath?.let(ManifestIO::read)?.sourceVideo
        } catch (_: Throwable) {
            null
        }
        val sourceRate = sourcePath
            ?.takeIf { File(it).isFile }
            ?.let { ExportFrameRateProbe.probe(it, ApplicationLayout.current().ffprobeExecutable) }
        val options = sourceRate?.let(ExportFrameRates::availableFor).orEmpty()
        refreshingFrameRateOptions = true
        try {
            frameRateCombo.model = DefaultComboBoxModel(options.toTypedArray())
            frameRateCombo.isEnabled = options.isNotEmpty()
            frameRateCombo.selectedItem = ExportFrameRates.preferredOption(
                options = options,
                savedFrameRate = settingsPreferences.load().outputFrameRate,
            )
        } finally {
            refreshingFrameRateOptions = false
        }
    }

    private fun sectionLabel(text: String): JComponent {
        val l = JLabel(text)
        l.font = l.font.deriveFont(Font.BOLD)
        l.foreground = FG_PRIMARY
        l.alignmentX = 0f
        return l
    }

    private fun refreshCompletedFromStore() {
        completed.refreshFromStore()
    }

    private fun formatCompletedItem(item: CompletedRender): String {
        val size = formatSize(item.bytesWritten)
        val sb = if (item.includeScoreboard) "  ·  Scoreboard" else ""
        val res = if (item.outHeight >= 2160 || item.outWidth >= 3840) "4K" else "1080p"
        val proj = item.projectName?.takeIf { it.isNotBlank() }
        val left = if (proj != null) "[$proj] ${item.fileName}" else item.fileName
        return "$left$sb  —  ${item.encoderLabel} / $res  —  $size"
    }

    private fun formatEta(secs: Long): String {
        val h = secs / 3600
        val m = (secs % 3600) / 60
        val s = secs % 60
        return String.format("%d:%02d:%02d", h, m, s)
    }

    private fun formatSize(bytes: Long): String {
        return RenderFormatting.formatSize(bytes)
    }

    private fun validFavoriteCount(): Int {
        return try {
            loadExportSummary().favoriteCount
        } catch (_: Throwable) {
            0
        }
    }

    private fun updateFavoriteOnlyAvailability() {
        val available = manifestPath != null && idleTrimCheck.isSelected
        favoriteOnlyCheck.isEnabled = available
        if (!available) favoriteOnlyCheck.isSelected = false
        favoriteOnlyCheck.toolTipText = if (available && validFavoriteCount() > 0) {
            "Render only points marked with a star."
        } else if (available) {
            "No valid favorite points are available. Selecting this option explains how to recover."
        } else {
            "Requires an open project with idle-trim enabled."
        }
    }

    private fun hasAnyScoredPoints(): Boolean {
        return try {
            loadExportSummary().scoredCount > 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun loadExportSummary() = ExportPlanner.summarize(
        readCurrentProjectEdl(),
        readCurrentProjectScore(),
    )

    private fun readCurrentProjectEdl(): EdlV1? {
        val mp = manifestPath
        val projectDir = if (!mp.isNullOrBlank()) EdlIO.projectDirFromManifest(mp) else null
        return try {
            if (projectDir != null) EdlIO.readForProjectDir(projectDir) else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun readCurrentProjectScore(): ScoreV1 {
        val mp = manifestPath
        val projectDir = if (!mp.isNullOrBlank()) EdlIO.projectDirFromManifest(mp) else null
        return try {
            if (projectDir != null) ScoreIO.readForProjectDir(projectDir) else ScoreV1()
        } catch (_: Throwable) {
            ScoreV1()
        }
    }

    private fun updatePointsSummary() {
        try {
            val summary = loadExportSummary()

            // Set texts
            pointsCountLabel.text = "${summary.pointCount} points / ${summary.favoriteCount} favorites"
            pointsTotalLabel.text = "total ${RenderFormatting.formatDuration(summary.totalMs)} / favorites ${RenderFormatting.formatDuration(summary.favoriteTotalMs)}"
            pointsScoredLabel.text = "scored ${summary.scoredCount}/${summary.pointCount}"

            // Colors
            pointsCountLabel.foreground = if (summary.pointCount == 0) UiStyles.YELLOW else FG_PRIMARY
            pointsScoredLabel.foreground = if (summary.allScored) UiStyles.GREEN else FG_PRIMARY
            pointsTotalLabel.foreground = FG_PRIMARY
            updateFavoriteOnlyAvailability()
        } catch (_: Throwable) {
            pointsCountLabel.text = ""
            pointsTotalLabel.text = ""
            pointsScoredLabel.text = ""
            updateFavoriteOnlyAvailability()
        }
    }

    private fun formatSeconds(ms: Long): String {
        return RenderFormatting.formatDuration(ms)
    }

    // Task 3.15 — Gate Initialize button based on project context and prerequisites
    private fun updateInitButtonState() {
        try {
            val mp = manifestPath
            val manifest = try { mp?.let(ManifestIO::read) } catch (_: Throwable) { null }
            val validPoints = if (mp.isNullOrBlank()) {
                emptyList()
            } else {
                val projectDir = EdlIO.projectDirFromManifest(mp)
                ExportPlanner.validateEdl(try { EdlIO.readForProjectDir(projectDir) } catch (_: Throwable) { null })
            }
            val readiness = ExportPlanner.initializationReadiness(
                hasProject = !mp.isNullOrBlank(),
                sourceVideoExists = manifest?.sourceVideo?.let { File(it).isFile } == true,
                idleTrim = idleTrimCheck.isSelected,
                favoriteOnly = favoriteOnlyCheck.isSelected,
                validPoints = validPoints,
            )
            initButton.isEnabled = readiness.enabled
            initButton.toolTipText = readiness.disabledReason
            initButton.accessibleContext.accessibleDescription = readiness.disabledReason
        } catch (_: Throwable) {
            initButton.isEnabled = false
            initButton.toolTipText = "Initialization unavailable due to an unexpected error."
            initButton.accessibleContext.accessibleDescription = initButton.toolTipText
        }
    }
}
