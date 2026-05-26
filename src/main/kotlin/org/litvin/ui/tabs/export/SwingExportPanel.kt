package org.litvin.ui.tabs.export
import org.litvin.*
import org.litvin.markup.EdlIO
import org.litvin.markup.EdlV1
import org.litvin.markup.PointV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.ui.commons.Dialogs
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.Html

import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Phase 4 — Export pipeline (Swing)
 *
 * Provides a Swing UI to configure and start an export job using the existing
 * FFmpegCommandBuilder and RenderQueueManager. Shows live progress with Cancel.
 */
class SwingExportPanel : JPanel(BorderLayout()) {
    fun onActivated() {
        // Ensure Completed list reflects latest persisted items (global across projects)
        try { refreshCompletedFromStore() } catch (_: Throwable) { }
        // Refresh EDL info and button gating on activation to reflect current project context
        try { updateEdlInfo() } catch (_: Throwable) { }
        try { updateInitButtonState() } catch (_: Throwable) { }
    }
    // Project context (manifest path) — optional; user can still pick output file.
    private var manifestPath: String? = null
    // Keep last observed snapshot to expose Details dialog
    private var lastSnapshot: ActiveQueueSnapshot? = null

    // Left controls
    private val presets = ExportPresetsIO.load()
    private val presetCombo = JComboBox(presets.map { it.label }.toTypedArray())
    private val resCombo = JComboBox(arrayOf("1920x1080", "3840x2160"))
    private val idleTrimCheck = JCheckBox("Remove idle time (use EDL keeps)", true)
    private val scoreboardCheck = JCheckBox("Include Scoreboard", false).apply {
        toolTipText = "Burn in a simple scoreboard overlay that updates after each point. Uses current Scoring data; fixed English labels in v0.1.0."
    }
    private val initButton = UiStyles.primaryButton("Initialize Render") { onInitializeRender() }
    private val encoderPanel = EncoderSummaryPanel()

    // Right side — Active + Completed
    private val progressBar = JProgressBar(0, 100)
    private val progressLabel = JLabel("Idle")
    private val cancelButton = JButton("Cancel")
    private val detailsButton = JButton("Details…")
    private var lastFailureNotifiedJobId: String? = null
    private var lastFailureJob: RenderJob? = null

    private val completed = CompletedRendersList()

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
    private val upscalingNoteLabel = JLabel("If the source is lower than selected, basic upscaling will be applied (no smart scaling in v0.1.0).")
    private val edlInfoLabel = JLabel("")
    private val encoderSummaryLabel = JLabel("")

    init {
        border = EmptyBorder(10, 10, 10, 10)
        background = DARK_BG

        // Left configuration column (fixed width, dark theme)
        val left = JPanel()
        left.layout = BoxLayout(left, BoxLayout.Y_AXIS)
        left.border = EmptyBorder(12, 12, 12, 16)
        left.background = CARD_BG
        left.foreground = FG_PRIMARY
        left.preferredSize = Dimension(320, 10)
        left.minimumSize = Dimension(280, 10)
        left.maximumSize = Dimension(360, Int.MAX_VALUE)

        val title = JLabel("Export Settings").apply {
            font = font.deriveFont(Font.BOLD)
            foreground = FG_PRIMARY
        }
        left.add(title)
        left.add(Box.createRigidArea(Dimension(0, 8)))
        left.add(JSeparator())
        left.add(Box.createRigidArea(Dimension(0, 12)))

        // Preset section
        left.add(sectionLabel("Preset"))
        presetCombo.selectedIndex = ExportPresetsIO.defaultBalancedIndex(presets)
        presetCombo.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 28)
        UiStyles.styleComboBox(presetCombo)
        left.add(presetCombo)
        UiStyles.styleHelper(qualityLabel)
        qualityLabel.text = ""
        left.add(Box.createRigidArea(Dimension(0, 4)))
        left.add(qualityLabel)
        left.add(Box.createRigidArea(Dimension(0, 12)))

        // Resolution section
        left.add(sectionLabel("Resolution"))
        // Match JavaFX choices
        val resModel = DefaultComboBoxModel(arrayOf("1080p", "4K"))
        resCombo.model = resModel
        resCombo.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 28)
        UiStyles.styleComboBox(resCombo)
        left.add(resCombo)
        UiStyles.styleHelper(resSummaryLabel)
        UiStyles.styleMono(scalePlanLabel)
        UiStyles.styleHelper(upscalingNoteLabel)
        upscalingNoteLabel.maximumSize = Dimension(Short.MAX_VALUE.toInt(), Int.MAX_VALUE)
        Html.setWrapped(upscalingNoteLabel, upscalingNoteLabel.text)
        left.add(Box.createRigidArea(Dimension(0, 6)))
        left.add(resSummaryLabel)
        left.add(scalePlanLabel)
        left.add(Box.createRigidArea(Dimension(0, 4)))
        left.add(upscalingNoteLabel)
        left.add(Box.createRigidArea(Dimension(0, 12)))

        // Idle-trim + EDL info
        UiStyles.stylePrimary(idleTrimCheck)
        left.add(idleTrimCheck)
        UiStyles.stylePrimary(scoreboardCheck)
        left.add(scoreboardCheck)
        UiStyles.styleHelper(edlInfoLabel)
        left.add(edlInfoLabel)
        left.add(Box.createRigidArea(Dimension(0, 12)))

        // Encoder section
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
        val nameLabel = JLabel("No active job")
        val jobIdLabel = JLabel("")
        UiStyles.styleMono(jobIdLabel)
        jobIdLabel.foreground = FG_SECONDARY
        val stats = JPanel(BorderLayout())
        stats.background = CARD_BG
        stats.add(progressLabel, BorderLayout.WEST)
        val rightButtons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        rightButtons.background = CARD_BG
        UiStyles.styleSecondary(detailsButton)
        detailsButton.isEnabled = false
        rightButtons.add(detailsButton)
        UiStyles.styleSecondary(cancelButton)
        cancelButton.isEnabled = false
        rightButtons.add(cancelButton)
        stats.add(rightButtons, BorderLayout.EAST)
        cancelButton.addActionListener { RenderQueueManager.cancelCurrent() }
        detailsButton.addActionListener {
            val job = lastFailureJob ?: lastSnapshot?.current
            val tail = job?.stderrTail
            val reason = job?.failureReason
            val msg = buildString {
                if (!reason.isNullOrBlank()) append(reason).append("\n\n")
                if (!tail.isNullOrBlank()) {
                    append("ffmpeg stderr (tail):\n\n")
                    append(tail)
                } else {
                    append("No additional diagnostic output is available.")
                }
            }
            Dialogs.showInfo(this@SwingExportPanel, msg, "Render failure details")
        }

        progressBar.value = 0
        progressBar.isStringPainted = true

        val activeBody = JPanel()
        activeBody.layout = BoxLayout(activeBody, BoxLayout.Y_AXIS)
        activeBody.background = CARD_BG
        listOf(nameLabel, jobIdLabel, Box.createRigidArea(Dimension(0,6)), progressBar, Box.createRigidArea(Dimension(0,6)), stats).forEach { activeBody.add(it) }

        val activeCardPanel = UiStyles.card("Active Processing", activeBody)
        right.add(activeCardPanel, BorderLayout.NORTH)

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
            resSummaryLabel.text = "Output: ${w} x ${h} (${sel})"
            scalePlanLabel.text = "Filter plan: -vf scale=${w}:-2"
        }
        fun formatMs(ms: Long): String {
            var remain = ms
            val h = remain / 3600000; remain %= 3600000
            val m = remain / 60000; remain %= 60000
            val s = remain / 1000; val mm = remain % 1000
            return String.format("%d:%02d:%02d.%03d", h, m, s, mm)
        }

        // Initial defaults mirroring FX
        val defIdx = ExportPresetsIO.defaultBalancedIndex(presets)
        if (presets.isNotEmpty()) {
            presetCombo.selectedIndex = defIdx
            val res = defaultResForPreset(presets[defIdx].id)
            resCombo.selectedItem = res
            updateQualitySummary(defIdx)
        }
        updateResolutionPreview()
        updateEdlInfo()

        presetCombo.addActionListener {
            val idx = presetCombo.selectedIndex
            if (idx in presets.indices) {
                val res = defaultResForPreset(presets[idx].id)
                resCombo.selectedItem = res
                updateQualitySummary(idx)
                updateResolutionPreview()
            }
        }
        resCombo.addActionListener { updateResolutionPreview() }
        idleTrimCheck.addActionListener { 
            updateEdlInfo()
            try { updateInitButtonState() } catch (_: Throwable) { }
        }

        // Observe queue updates to refresh UI
        RenderQueueManager.addObserver { snap ->
            SwingUtilities.invokeLater {
                lastSnapshot = snap
                val cur = snap.current
                if (cur == null) {
                    nameLabel.text = "No active job"
                    jobIdLabel.text = ""
                    progressBar.value = 0
                    progressBar.string = ""
                    progressLabel.text = "Idle"
                    cancelButton.isEnabled = false
                    detailsButton.isEnabled = false
                } else {
                    val sbFlag = if (cur.includeScoreboard) "  ·  Scoreboard" else ""
                    nameLabel.text = File(cur.outputPath).name + sbFlag
                    jobIdLabel.text = "Job ID: ${cur.id}"
                    progressBar.value = (cur.progress * 100).toInt()
                    progressBar.string = "${(cur.progress * 100).toInt()}%"
                    val eta = cur.etaSeconds?.let { formatEta(it) } ?: "--"
                    val sz = formatSize(cur.bytesWritten)
                    cancelButton.isEnabled = cur.status == RenderStatus.RUNNING
                    when (cur.status) {
                        RenderStatus.FAILED -> {
                            val reason = cur.failureReason ?: "Unknown error"
                            progressLabel.text = "FAILED — $reason"
                            detailsButton.isEnabled = true
                            if (lastFailureNotifiedJobId != cur.id) {
                                lastFailureNotifiedJobId = cur.id
                                lastFailureJob = cur
                                JOptionPane.showMessageDialog(this, reason, "Render failed", JOptionPane.ERROR_MESSAGE)
                            }
                        }
                        RenderStatus.COMPLETED -> {
                            progressLabel.text = "Completed — Size: $sz"
                            detailsButton.isEnabled = false
                            addCompleted(cur)
                        }
                        RenderStatus.CANCELED -> {
                            progressLabel.text = "Canceled"
                            detailsButton.isEnabled = false
                        }
                        else -> {
                            progressLabel.text = "ETA: $eta    Size: $sz"
                            detailsButton.isEnabled = false
                        }
                    }
                }
                val q = snap.queued.size
                activeCardPanel.toolTipText = if (q > 0) "queued: $q" else null
            }
        }
    }

    fun setProjectManifest(path: String?) {
        manifestPath = path
        try { updateEdlInfo() } catch (_: Throwable) { }
        try { updateInitButtonState() } catch (_: Throwable) { }
        // Task 3.17 — default Scoreboard checkbox based on project scoring snapshot
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
            Dialogs.showError(this, t, "Failed to read manifest")
            return
        }
        val source = manifest?.sourceVideo
        if (source.isNullOrBlank() || !File(source).exists()) {
            Dialogs.showError(this, IllegalStateException("Source video missing"), "Select source video")
            return
        }

        val projectDir = if (manifestPath != null) EdlIO.projectDirFromManifest(manifestPath) else null
        val edl = try { if (projectDir != null) EdlIO.readForProjectDir(projectDir) else null } catch (_: Throwable) { null }
        val validated = validateEdl(edl)
        val keeps: List<PointV1> = if (idleTrimCheck.isSelected) validated else emptyList()
        if (idleTrimCheck.isSelected && keeps.isEmpty()) {
            val r = JOptionPane.showConfirmDialog(this, "EDL is empty or invalid. Continue with full render?", "EDL warning", JOptionPane.YES_NO_OPTION)
            if (r != JOptionPane.YES_OPTION) return
        }

        // Choose output path
        val initialDir = projectDir?.let { File(it) } ?: File(source).parentFile
        val chooser = JFileChooser(initialDir)
        chooser.dialogTitle = "Save Export As…"
        chooser.selectedFile = File(suggestFilename(manifest?.name ?: File(source).nameWithoutExtension))
        val res = chooser.showSaveDialog(this)
        if (res != JFileChooser.APPROVE_OPTION) return
        var out = chooser.selectedFile
        // Ensure extension if user omitted
        val selPreset = presets.getOrNull(presetCombo.selectedIndex) ?: presets[ExportPresetsIO.defaultBalancedIndex(presets)]
        val defaultExt = selPreset.container?.format?.lowercase()?.let { if (it.startsWith(".")) it.drop(1) else it } ?: "mp4"
        out = ensureExtension(out, defaultExt)

        if (out.exists()) {
            val ow = JOptionPane.showConfirmDialog(this, "File exists. Overwrite?", "Confirm overwrite", JOptionPane.YES_NO_OPTION)
            if (ow != JOptionPane.YES_OPTION) return
        }

        val (w, h) = parseDims(resCombo.selectedItem as String)
        val encoderLabel = encoderPanel.selectedEncoderLabel().substringBefore(" — ")

        // Build overlay timeline if requested
        val includeSb = scoreboardCheck.isSelected
        val overlayTimeline = if (includeSb) {
            try {
                val score = if (projectDir != null) ScoreIO.readForProjectDir(projectDir) else ScoreV1()
                val edlPoints = validated.ifEmpty { edl?.points ?: emptyList() }
                ScoreboardTimelineBuilder.build(
                    edlPoints,
                    score.outcomes,
                    idleTrimCheck.isSelected,
                    score.player1Name,
                    score.player2Name
                )
            } catch (_: Throwable) {
                emptyList()
            }
        } else emptyList()

        val job = RenderJob(
            projectId = manifest?.id,
            projectName = manifest?.name,
            sourcePath = source,
            edlSnapshot = if (idleTrimCheck.isSelected) keeps else emptyList(),
            presetId = selPreset.id,
            outWidth = w,
            outHeight = h,
            encoderLabel = encoderLabel,
            idleTrim = idleTrimCheck.isSelected,
            includeScoreboard = includeSb,
            overlayTimeline = overlayTimeline,
            outputPath = out.absolutePath,
        )

        RenderQueueManager.enqueue(job)
        JOptionPane.showMessageDialog(this, "Render initialized: ${out.name}")
    }

    private fun validateEdl(raw: EdlV1?): List<PointV1> {
        if (raw == null) return emptyList()
        val pts = raw.points.sortedBy { it.startMs }
        val keeps = ArrayList<PointV1>()
        var lastEnd = -1
        for (p in pts) {
            if (p.startMs >= p.endMs) continue
            if (lastEnd >= 0 && p.startMs < lastEnd) continue // overlap, skip
            keeps += p
            lastEnd = p.endMs
        }
        return keeps
    }

    private fun suggestFilename(projectName: String): String {
        val label = presets.getOrNull(presetCombo.selectedIndex)?.id ?: "balanced"
        val dims = resCombo.selectedItem as String
        val base = projectName.ifBlank { "export" }
        val fn = "$base-${label.lowercase()}-${dims.replace('x','p')}.mp4"
        return fn
    }

    private fun parseDims(sel: String): Pair<Int, Int> {
        return when (sel) {
            "4K" -> 3840 to 2160
            "1080p" -> 1920 to 1080
            else -> {
                val parts = sel.lowercase().split("x")
                if (parts.size == 2) (parts[0].toIntOrNull() ?: 1920) to (parts[1].toIntOrNull() ?: 1080)
                else 1920 to 1080
            }
        }
    }

    private fun ensureExtension(file: File, defaultExtNoDot: String = "mp4"): File {
        val safeName = file.name.trim().trimEnd('.')
        if (safeName.isEmpty()) return File(file.parentFile, "export.$defaultExtNoDot")
        return if (safeName.contains('.')) {
            File(file.parentFile, safeName)
        } else {
            File(file.parentFile, "$safeName.$defaultExtNoDot")
        }
    }

    private fun sectionLabel(text: String): JComponent {
        val l = JLabel(text)
        l.font = l.font.deriveFont(Font.BOLD)
        l.foreground = FG_PRIMARY
        l.alignmentX = 0f
        return l
    }

    private fun addCompleted(job: RenderJob) {
        completed.addCompletedFrom(job)
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
        val kb = 1000.0
        val mb = kb * 1000
        val gb = mb * 1000
        return when {
            bytes >= gb -> String.format("%.2f GB", bytes / gb)
            bytes >= mb -> String.format("%.2f MB", bytes / mb)
            bytes >= kb -> String.format("%.2f KB", bytes / kb)
            else -> "$bytes B"
        }
    }


    private fun updateEdlInfo() {
        val manifest = manifestPath
        val isOn = idleTrimCheck.isSelected
        if (manifest.isNullOrBlank()) {
            edlInfoLabel.text = if (isOn) "No project open. Full render." else "Idle-trim OFF — full source will be rendered."
            return
        }
        if (!isOn) { edlInfoLabel.text = "Idle-trim OFF — full source will be rendered."; return }
        val projectDir = EdlIO.projectDirFromManifest(manifest)
        val edl = try { EdlIO.readForProjectDir(projectDir) } catch (_: Throwable) { null }
        val keeps = validateEdl(edl)
        if (keeps.isEmpty()) { edlInfoLabel.text = "EDL empty — full source will be rendered."; return }
        val total = keeps.fold(0L) { acc, p -> acc + (p.endMs - p.startMs) }
        Html.setWrapped(edlInfoLabel, "EDL: ${keeps.size} keep intervals · total ${formatMs(total)}")
    }

    private fun formatMs(ms: Long): String {
        var remain = ms
        val h = remain / 3_600_000; remain %= 3_600_000
        val m = remain / 60_000; remain %= 60_000
        val s = remain / 1000; val mm = remain % 1000
        return String.format("%d:%02d:%02d.%03d", h, m, s, mm)
    }

    // Task 3.15 — Gate Initialize button based on project context and prerequisites
    private fun updateInitButtonState() {
        try {
            val mp = manifestPath
            // Default: disabled until proven OK
            var enabled = false
            var reason: String? = null
            if (mp.isNullOrBlank()) {
                reason = "Open a project first (Projects → Open)."
            } else {
                val manifest = try { ManifestIO.read(mp) } catch (t: Throwable) { null }
                val sourcePath = manifest?.sourceVideo
                if (sourcePath.isNullOrBlank() || !File(sourcePath).exists()) {
                    reason = "Source video not found. Set it in Projects/Markup."
                } else {
                    if (idleTrimCheck.isSelected) {
                        val projectDir = EdlIO.projectDirFromManifest(mp)
                        val edl = try { EdlIO.readForProjectDir(projectDir) } catch (_: Throwable) { null }
                        val keeps = validateEdl(edl)
                        if (keeps.isEmpty()) {
                            reason = "EDL is empty/invalid while Idle‑trim is ON. Add keep intervals or turn Idle‑trim OFF."
                        } else {
                            enabled = true
                        }
                    } else {
                        // Full render with no EDL requirements
                        enabled = true
                    }
                }
            }
            initButton.isEnabled = enabled
            initButton.toolTipText = if (enabled) null else reason
        } catch (_: Throwable) {
            initButton.isEnabled = false
            initButton.toolTipText = "Initialization unavailable due to an unexpected error."
        }
    }
}
