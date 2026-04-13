package org.litvin

import java.awt.*
import java.awt.event.ActionEvent
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
    // Project context (manifest path) — optional; user can still pick output file.
    private var manifestPath: String? = null
    // Keep last observed snapshot to expose Details dialog
    private var lastSnapshot: ActiveQueueSnapshot? = null

    // Left controls
    private val presets = ExportPresetsIO.load()
    private val presetCombo = JComboBox(presets.map { it.label }.toTypedArray())
    private val resCombo = JComboBox(arrayOf("1920x1080", "3840x2160"))
    private val idleTrimCheck = JCheckBox("Remove idle time (use EDL keeps)", true)
    private data class EncoderItem(val label: String, val id: String) { override fun toString(): String = label }
    private val encoderCombo = JComboBox<EncoderItem>()
    private val encoderHintLabel = JLabel("")
    private val recheckButton = JButton("Re-check")
    private val initButton = JButton("Initialize Render…")

    // Right side — Active + Completed
    private val activePanel = JPanel(BorderLayout())
    private val activeTitle = JLabel("Active Processing")
    private val activeCard = JPanel(BorderLayout())
    private val progressBar = JProgressBar(0, 100)
    private val progressLabel = JLabel("Idle")
    private val cancelButton = JButton("Cancel")
    private val detailsButton = JButton("Details…")
    private var lastFailureNotifiedJobId: String? = null
    private var lastFailureJob: RenderJob? = null

    private val completedPanel = JPanel()
    private val completedListModel = DefaultListModel<String>()
    private val completedList = JList(completedListModel)

    // Theming (mirror SwingProjectsPanel palette)
    private val DARK_BG = java.awt.Color(0x16, 0x16, 0x16)
    private val CARD_BG = java.awt.Color(0x22, 0x22, 0x22)
    private val CARD_BORDER = java.awt.Color(0x33, 0x33, 0x33)
    private val FG_PRIMARY = java.awt.Color(0xE6, 0xE6, 0xE6)
    private val FG_SECONDARY = java.awt.Color(0xAA, 0xAA, 0xAA)
    private val ACCENT_GREEN = java.awt.Color(0xA1, 0xFE, 0x00)

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
        left.add(presetCombo)
        styleHelper(qualityLabel)
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
        left.add(resCombo)
        styleHelper(resSummaryLabel)
        styleMono(scalePlanLabel)
        styleHelper(upscalingNoteLabel)
        upscalingNoteLabel.maximumSize = Dimension(Short.MAX_VALUE.toInt(), Int.MAX_VALUE)
        setWrapped(upscalingNoteLabel, upscalingNoteLabel.text)
        left.add(Box.createRigidArea(Dimension(0, 6)))
        left.add(resSummaryLabel)
        left.add(scalePlanLabel)
        left.add(Box.createRigidArea(Dimension(0, 4)))
        left.add(upscalingNoteLabel)
        left.add(Box.createRigidArea(Dimension(0, 12)))

        // Idle-trim + EDL info
        stylePrimary(idleTrimCheck)
        left.add(idleTrimCheck)
        styleHelper(edlInfoLabel)
        left.add(edlInfoLabel)
        left.add(Box.createRigidArea(Dimension(0, 12)))

        // Encoder section
        left.add(sectionLabel("Encoder"))
        fun populateEncoders() {
            val items = mutableListOf<EncoderItem>()
            items += EncoderItem("H.264 (libx264) — software", "libx264")
            val caps = try { FFmpegCapabilities.h264Encoders() } catch (_: Throwable) { emptySet() }
            val hw = mutableListOf<String>()
            if ("h264_nvenc" in caps) { items += EncoderItem("H.264 (NVENC) — hardware", "h264_nvenc"); hw += "NVENC" }
            if ("h264_qsv" in caps) { items += EncoderItem("H.264 (QSV) — hardware", "h264_qsv"); hw += "QSV" }
            if ("h264_amf" in caps) { items += EncoderItem("H.264 (AMF) — hardware", "h264_amf"); hw += "AMF" }
            val model = DefaultComboBoxModel(items.toTypedArray())
            encoderCombo.model = model
            encoderCombo.selectedIndex = 0
            val hint = if (hw.isEmpty()) "No hardware H.264 encoders detected. Ensure ffmpeg with NVENC/QSV/AMF is installed and on PATH, or set FFMPEG_PATH." else "Detected: ${hw.joinToString(", ")}"
            setWrapped(encoderHintLabel, hint)
        }
        populateEncoders()
        encoderCombo.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 28)
        left.add(encoderCombo)
        styleHelper(encoderHintLabel)
        left.add(encoderHintLabel)
        styleHelper(encoderSummaryLabel)
        left.add(encoderSummaryLabel)
        recheckButton.alignmentX = 0f
        recheckButton.addActionListener {
            try { FFmpegCapabilities.refresh() } catch (_: Throwable) {}
            populateEncoders()
            val item = (encoderCombo.selectedItem as? EncoderItem)
            val label = item?.label ?: "H.264 (libx264) — software"
            val availability = when {
                label.contains("NVENC") -> "GPU accelerated (NVENC)"
                label.contains("QSV") -> "GPU accelerated (QSV)"
                label.contains("AMF") -> "GPU accelerated (AMF)"
                else -> "Software encode"
            }
            setWrapped(encoderSummaryLabel, "Selected: ${label.substringBefore(" — ")} · $availability")
        }
        left.add(Box.createRigidArea(Dimension(0, 4)))
        left.add(recheckButton)
        left.add(Box.createVerticalGlue())

        // Primary action (neon green)
        initButton.alignmentX = 0f
        initButton.text = "Initialize Render"
        initButton.background = ACCENT_GREEN
        initButton.foreground = Color(0x2B,0x49,0x00)
        initButton.isOpaque = true
        initButton.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT_GREEN.darker()),
            EmptyBorder(8,12,8,12)
        )
        initButton.addActionListener { onInitializeRender() }
        left.add(Box.createRigidArea(Dimension(0, 12)))
        left.add(initButton)

        // Right column with Active + Completed (top-aligned cards)
        val right = JPanel()
        right.layout = BoxLayout(right, BoxLayout.Y_AXIS)
        right.background = DARK_BG
        right.foreground = FG_PRIMARY

        // Active card
        val nameLabel = JLabel("No active job")
        val stats = JPanel(BorderLayout())
        stats.background = CARD_BG
        stats.add(progressLabel, BorderLayout.WEST)
        val rightButtons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        rightButtons.background = CARD_BG
        detailsButton.isEnabled = false
        rightButtons.add(detailsButton)
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
            SwingDialogUtils.showInfo(this@SwingExportPanel, msg, "Render failure details")
        }

        progressBar.value = 0
        progressBar.isStringPainted = true

        val activeBody = JPanel()
        activeBody.layout = BoxLayout(activeBody, BoxLayout.Y_AXIS)
        activeBody.background = CARD_BG
        listOf(nameLabel, Box.createRigidArea(Dimension(0,6)), progressBar, Box.createRigidArea(Dimension(0,6)), stats).forEach { activeBody.add(it) }

        val activeCardPanel = card("Active Processing", activeBody)
        right.add(activeCardPanel)
        right.add(Box.createRigidArea(Dimension(0, 12)))

        // Completed card
        val completedListRenderer = object: DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                c.background = if (isSelected) CARD_BG.brighter() else CARD_BG
                c.foreground = FG_PRIMARY
                return c
            }
        }
        completedList.cellRenderer = completedListRenderer
        val completedScroll = JScrollPane(completedList)
        completedScroll.background = CARD_BG
        completedScroll.border = BorderFactory.createEmptyBorder()
        completedScroll.preferredSize = Dimension(400, 200)
        val completedBody = JPanel(BorderLayout())
        completedBody.background = CARD_BG
        completedBody.add(completedScroll, BorderLayout.CENTER)
        val completedCardPanel = card("Completed Renders", completedBody)
        right.add(completedCardPanel)

        val center = JPanel(BorderLayout())
        center.background = DARK_BG
        center.add(right, BorderLayout.NORTH)

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
            setWrapped(qualityLabel, parts.filter { it.isNotBlank() }.joinToString(" · "))
        }
        fun updateResolutionPreview() {
            val sel = resCombo.selectedItem as? String ?: "1080p"
            val (w, h) = targetDimsFor(sel)
            resSummaryLabel.text = "Output: ${w} x ${h} (${sel})"
            scalePlanLabel.text = "Filter plan: -vf scale=${w}:-2"
        }
        fun updateEncoderSummary() {
            val item = (encoderCombo.selectedItem as? EncoderItem)
            val label = item?.label ?: "H.264 (libx264) — software"
            val availability = when {
                label.contains("NVENC") -> "GPU accelerated (NVENC)"
                label.contains("QSV") -> "GPU accelerated (QSV)"
                label.contains("AMF") -> "GPU accelerated (AMF)"
                else -> "Software encode"
            }
            setWrapped(encoderSummaryLabel, "Selected: ${label.substringBefore(" — ")} · $availability")
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
        updateEncoderSummary()
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
        encoderCombo.addActionListener { updateEncoderSummary() }
        idleTrimCheck.addActionListener { updateEdlInfo() }

        // Observe queue updates to refresh UI
        RenderQueueManager.addObserver { snap ->
            SwingUtilities.invokeLater {
                lastSnapshot = snap
                val cur = snap.current
                if (cur == null) {
                    nameLabel.text = "No active job"
                    progressBar.value = 0
                    progressBar.string = ""
                    progressLabel.text = "Idle"
                    cancelButton.isEnabled = false
                    detailsButton.isEnabled = false
                } else {
                    nameLabel.text = File(cur.outputPath).name
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
    }

    private fun onInitializeRender() {
        // Resolve manifest and source video
        val manifestPath = this.manifestPath
        val manifest = try {
            if (manifestPath.isNullOrBlank()) null else ManifestIO.read(manifestPath)
        } catch (t: Throwable) {
            SwingDialogUtils.showError(this, t, "Failed to read manifest")
            return
        }
        val source = manifest?.sourceVideo
        if (source.isNullOrBlank() || !File(source).exists()) {
            SwingDialogUtils.showError(this, IllegalStateException("Source video missing"), "Select source video")
            return
        }

        val projectDir = if (manifestPath != null) EdlIO.projectDirFromManifest(manifestPath) else null
        val edl = try { if (projectDir != null) EdlIO.readForProjectDir(projectDir) else null } catch (_: Throwable) { null }
        val keeps = if (idleTrimCheck.isSelected) validateEdl(edl) else emptyList()
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
        val encItem = (encoderCombo.selectedItem as? EncoderItem)
        val encoderLabel = (encItem?.label ?: "H.264 (libx264) — software").substringBefore(" — ")

        val job = RenderJob(
            projectId = manifest?.id,
            sourcePath = source,
            edlSnapshot = if (idleTrimCheck.isSelected) keeps else emptyList(),
            presetId = selPreset.id,
            outWidth = w,
            outHeight = h,
            encoderLabel = encoderLabel,
            idleTrim = idleTrimCheck.isSelected,
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
        l.alignmentX = 0f
        return l
    }

    private fun addCompleted(job: RenderJob) {
        val size = formatSize(job.bytesWritten)
        completedListModel.addElement("${File(job.outputPath).name}  —  ${job.outWidth}x${job.outHeight} / ${job.encoderLabel}  —  $size")
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

    // --- Styling helpers and UI building utilities ---
    private fun styleHelper(c: JComponent) {
        c.foreground = FG_SECONDARY
        c.background = CARD_BG
        try {
            val f = c.font
            if (f != null) c.font = f.deriveFont((f.size2D - 1f).coerceAtLeast(11f))
        } catch (_: Throwable) { }
    }
    private fun stylePrimary(c: JComponent) {
        c.foreground = FG_PRIMARY
        c.background = CARD_BG
    }
    private fun styleMono(c: JComponent) {
        styleHelper(c)
        try {
            c.font = Font("Consolas", Font.PLAIN, c.font.size)
        } catch (_: Throwable) { }
    }
    // Wrap long text in JLabel using HTML container of fixed width so BoxLayout can grow height
    private fun setWrapped(label: JLabel, text: String, widthPx: Int = 296) {
        label.text = "<html><div style='width:${widthPx}px'>${escapeHtml(text)}</div></html>"
    }
    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun card(title: String, body: JComponent): JPanel {
        val container = JPanel(BorderLayout())
        container.background = CARD_BG
        container.foreground = FG_PRIMARY
        container.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER),
            EmptyBorder(12, 12, 12, 12)
        )
        val header = JLabel(title)
        header.font = header.font.deriveFont(Font.BOLD)
        header.foreground = FG_PRIMARY
        container.add(header, BorderLayout.NORTH)
        container.add(body, BorderLayout.CENTER)
        return container
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
        setWrapped(edlInfoLabel, "EDL: ${keeps.size} keep intervals · total ${formatMs(total)}")
    }

    private fun formatMs(ms: Long): String {
        var remain = ms
        val h = remain / 3_600_000; remain %= 3_600_000
        val m = remain / 60_000; remain %= 60_000
        val s = remain / 1000; val mm = remain % 1000
        return String.format("%d:%02d:%02d.%03d", h, m, s, mm)
    }
}
