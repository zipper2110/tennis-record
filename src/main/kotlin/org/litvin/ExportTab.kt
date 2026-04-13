package org.litvin

import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.stage.FileChooser
import javafx.application.Platform
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.prefs.Preferences

/**
 * Export tab shell (Task 3.1) + Task 3.2 (Presets loading and binding):
 * - Left configuration panel: resolution selector, idle-trim toggle, encoder selector, Initialize Render button
 * - Presets are loaded from docs/export-presets.json and bound to UI. Default = Balanced.
 * - Selecting a preset updates resolution (simple mapping) and quality/bitrate display.
 * - Right area: sections Active Processing and Completed Renders (placeholders for now)
 * - Sidebar consistent with other tabs; Export marked active
 */
class ExportTab {
    // Navigation callbacks for host app (MainApp) to switch views
    var onRequestNavigateProjects: (() -> Unit)? = null
    var onRequestNavigateMarkup: (() -> Unit)? = null
    var onRequestNavigateVideoTest: (() -> Unit)? = null

    private val root = BorderPane()

    // EDL state snapshot for Task 3.4
    private var edlRaw: EdlV1? = null
    private var edlValidated: List<PointV1> = emptyList()
    private var sourceDurationMs: Long? = null // unknown at this step (no media probe yet)

    // UI refs (left panel)
    private var idleTrimCheck: CheckBox? = null
    private var edlInfoLabel: Label? = null
    private var encoderBox: ComboBox<String>? = null
    private var encoderSummaryLabel: Label? = null
    private var presetBoxRef: ComboBox<String>? = null
    private var resBoxRef: ComboBox<String>? = null
    private var presetsList: List<ExportPreset> = emptyList()

    // Right panel containers (Task 3.6 — show job immediately under Active)
    private var activeListBox: VBox? = null
    private var completedListBox: VBox? = null

    // Active card UI refs (Task 3.9)
    private var activeCardJobId: String? = null
    private var activeCardPercentLbl: Label? = null
    private var activeCardEtaLbl: Label? = null
    private var activeCardProgress: ProgressBar? = null
    private var activeCardTitle: Label? = null
    private var activeCardIdLbl: Label? = null

    private fun buildSidebar(): Node {
        return org.litvin.markup.AppSidebar.build(
            active = org.litvin.markup.AppSidebar.Active.EXPORT,
            onProjects = { onRequestNavigateProjects?.invoke() },
            onMarkup = { onRequestNavigateMarkup?.invoke() },
            onExport = { /* already here */ },
            onVideoTest = { onRequestNavigateVideoTest?.invoke() }
        )
    }

    private fun buildLeftPanel(): Node {
        val title = Label("Export Settings").apply { style = "-fx-font-weight: bold; -fx-text-fill: #ddd;" }

        val presetLabel = Label("Preset").apply { style = "-fx-text-fill: #bbb;" }
        val presetBox = ComboBox<String>()
        presetBoxRef = presetBox

        val resLabel = Label("Resolution").apply { style = "-fx-text-fill: #bbb;" }
        val resBox = ComboBox<String>().apply {
            items.addAll("1080p", "4K")
            selectionModel.select(0)
        }
        resBoxRef = resBox
        val resSummary = Label("").apply { style = "-fx-text-fill: #9aa; -fx-font-size: 12;" }
        val scalePlan = Label("").apply { style = "-fx-text-fill: #8a8; -fx-font-size: 12; -fx-font-family: 'Consolas', 'Courier New', monospace;" }
        val upscalingNote = Label("If the source is lower than selected, basic upscaling will be applied (no smart scaling in v0.1.0).").apply {
            style = "-fx-text-fill: #777; -fx-font-size: 11;"
            isWrapText = true
            maxWidth = Double.MAX_VALUE
        }

        val qualityLabel = Label("").apply { style = "-fx-text-fill: #aaa; -fx-font-size: 12;" }

        val idleTrim = CheckBox("Remove idle time (use EDL)").apply {
            isSelected = true
        }
        idleTrimCheck = idleTrim
        val edlInfo = Label("").apply { style = "-fx-text-fill: #9aa; -fx-font-size: 12;" }
        edlInfoLabel = edlInfo

        val encLabel = Label("Encoder").apply { style = "-fx-text-fill: #bbb;" }
        val encoder = ComboBox<String>().apply {
            items.addAll(
                "H.264 (libx264)",
                "H.264 (NVENC)",
                "H.264 (QSV) — disabled",
                "H.264 (AMF) — disabled"
            )
            selectionModel.select(0)
            // Render disabled HW options grayed out
            setCellFactory {
                object : ListCell<String>() {
                    override fun updateItem(item: String?, empty: Boolean) {
                        super.updateItem(item, empty)
                        text = if (empty) null else item
                        if (!empty && item != null) {
                            val disabled = item.contains("— disabled")
                            isDisable = disabled
                            style = if (disabled) "-fx-text-fill: #777;" else ""
                        } else {
                            isDisable = false
                            style = ""
                        }
                    }
                }
            }
            buttonCell = object : ListCell<String>() {
                override fun updateItem(item: String?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty) null else item
                }
            }
        }
        encoderBox = encoder
        val encoderSummary = Label("").apply { style = "-fx-text-fill: #9aa; -fx-font-size: 12;" }
        encoderSummaryLabel = encoderSummary
        fun isHw(item: String?): Boolean = item?.contains("NVENC") == true || item?.contains("QSV") == true || item?.contains("AMF") == true
        fun isDisabledHw(item: String?): Boolean = item?.contains("QSV") == true || item?.contains("AMF") == true
        fun updateEncoderSummary() {
            val sel = encoder.selectionModel.selectedItem ?: "H.264 (libx264)"
            if (isDisabledHw(sel)) {
                // prevent selecting disabled options
                encoder.selectionModel.select(0)
            }
            val current = encoder.selectionModel.selectedItem ?: "H.264 (libx264)"
            val availability = when {
                current.contains("NVENC") -> "GPU accelerated (NVENC)"
                isDisabledHw(current) -> "Unavailable in v0.1.0"
                else -> "Available"
            }
            encoderSummary.text = "Selected: $current · $availability"
        }
        encoder.setOnAction { updateEncoderSummary() }

        val initBtn = Button("Initialize Render").apply {
            styleClass.add("cta-primary")
            isDisable = false // Enabled for Task 3.6; TODO: tighter validation in Task 3.15
            setOnAction {
                handleInitializeRender()
            }
        }

        // Load presets and bind to UI (Task 3.2)
        val presets = ExportPresetsIO.load()
        presetsList = presets
        presetBox.items.addAll(presets.map { it.label })
        presetBox.isDisable = false
        val defaultIdx = ExportPresetsIO.defaultBalancedIndex(presets)
        if (presets.isNotEmpty()) {
            presetBox.selectionModel.select(defaultIdx)
        }

        fun defaultResForPreset(presetId: String): String {
            return when (presetId.lowercase()) {
                "quality" -> "4K"
                else -> "1080p"
            }
        }

        fun updateQualitySummary(idx: Int) {
            if (idx !in presets.indices) return
            val p = presets[idx]
            val v = p.video
            val parts = mutableListOf<String>()
            v.crf?.let { parts.add("CRF $it") }
            v.x264Preset?.let { parts.add("x264 ${it}") }
            v.vbvMaxrateK?.let { parts.add("Maxrate ${it}k") }
            if (parts.isEmpty()) parts.add(p.description ?: "")
            qualityLabel.text = parts.filter { it.isNotBlank() }.joinToString(" · ")
        }

        fun targetDimsFor(sel: String): Pair<Int, Int> = when (sel) {
            "4K" -> 3840 to 2160
            else -> 1920 to 1080
        }

        fun updateResolutionPreview() {
            val sel = resBox.selectionModel.selectedItem ?: "1080p"
            val (w, h) = targetDimsFor(sel)
            resSummary.text = "Output: ${w}x${h} (${sel})"
            scalePlan.text = "Filter plan: -vf scale=${w}:-2"
        }

        // Apply default resolution based on default preset
        if (presets.isNotEmpty()) {
            val preset = presets[defaultIdx]
            val res = defaultResForPreset(preset.id)
            val resIdx = resBox.items.indexOf(res)
            if (resIdx >= 0) resBox.selectionModel.select(resIdx)
            updateQualitySummary(defaultIdx)
            updateResolutionPreview()
        } else {
            updateResolutionPreview()
        }
        // Initialize encoder summary for Task 3.5
        updateEncoderSummary()

        presetBox.setOnAction {
            val idx = presetBox.selectionModel.selectedIndex
            if (idx in presets.indices) {
                val p = presets[idx]
                val res = defaultResForPreset(p.id)
                val resIdx = resBox.items.indexOf(res)
                if (resIdx >= 0) resBox.selectionModel.select(resIdx)
                updateQualitySummary(idx)
                updateResolutionPreview()
            }
        }
        resBox.setOnAction { updateResolutionPreview() }

        return VBox(10.0,
            title,
            Separator(),
            presetLabel, presetBox,
            qualityLabel,
            resLabel, resBox,
            resSummary,
            scalePlan,
            upscalingNote,
            idleTrim,
            edlInfo,
            encLabel, encoder,
            encoderSummary,
            Region().apply { VBox.setVgrow(this, Priority.ALWAYS) },
            initBtn
        ).apply {
            padding = Insets(12.0)
            style = "-fx-background-color: #1f1f1f; -fx-border-color: #2d2d2d; -fx-border-width: 0 1 0 0;"
            prefWidth = 320.0
            minWidth = 280.0
            isFillWidth = true
        }
    }

    private fun section(title: String): VBox {
        val header = Label(title).apply { style = "-fx-text-fill: #ddd; -fx-font-weight: bold; -fx-padding: 8;" }
        val box = VBox(8.0).apply {
            style = "-fx-background-color: #1b1b1b; -fx-padding: 8; -fx-background-radius: 4;"
        }
        return VBox(4.0, header, box).apply { VBox.setVgrow(box, Priority.ALWAYS) }
    }

    private fun buildRightPanel(): Node {
        val active = section("Active Processing")
        val completed = section("Completed Renders")
        val activeBox = (active.children[1] as VBox)
        val completedBox = (completed.children[1] as VBox)
        // Placeholders
        val activePlaceholder = Label("No active renders").apply { style = "-fx-text-fill: #777;" }
        val completedPlaceholder = Label("No completed renders yet").apply { style = "-fx-text-fill: #777;" }
        activeBox.children.add(activePlaceholder)
        completedBox.children.add(completedPlaceholder)
        // Store refs
        activeListBox = activeBox
        completedListBox = completedBox
        return VBox(12.0, active, completed).apply {
            padding = Insets(12.0)
        }
    }

    private val viewInternal: Node by lazy {
        root.left = buildSidebar()
        val settings = buildLeftPanel()
        val queue = buildRightPanel()
        root.center = HBox(settings, queue)
        HBox.setHgrow(queue, Priority.ALWAYS)
        root
    }

    // --- Task 3.4 helpers ---
    private fun validateEdl(raw: EdlV1?): List<PointV1> {
        val pts = raw?.points ?: return emptyList()
        if (pts.isEmpty()) return emptyList()
        val sorted = pts.sortedBy { it.startMs }
        val out = ArrayList<PointV1>(sorted.size)
        var lastEnd = -1
        for (p in sorted) {
            var s = p.startMs
            var e = p.endMs
            if (e <= s) continue // skip invalid or zero-length
            // enforce half-open and non-overlap by clipping start to lastEnd
            if (s < lastEnd) s = lastEnd
            if (e <= s) continue
            out.add(PointV1(id = p.id, startMs = s, endMs = e, label = p.label, notes = p.notes))
            lastEnd = e
        }
        return out
    }

    private fun updateEdlSummaryUI() {
        val info = edlInfoLabel ?: return
        val isOn = idleTrimCheck?.isSelected ?: true
        val manifest = ProjectsDispatcher.currentProjectPath
        if (manifest.isNullOrBlank()) {
            info.text = if (isOn) "No project open. Full render." else "Idle-trim OFF — full source will be rendered."
            return
        }
        val keeps = edlValidated
        if (!isOn) {
            info.text = "Idle-trim OFF — full source will be rendered."
            return
        }
        if (keeps.isEmpty()) {
            info.text = "EDL empty — full source will be rendered."
            return
        }
        val total = Timecode.totalDuration(keeps).coerceAtLeast(0)
        info.text = "EDL: ${keeps.size} keep intervals · total ${Timecode.format(total)}"
    }

    fun onEnter() {
        try {
            val manifestPath = ProjectsDispatcher.currentProjectPath
            if (manifestPath.isNullOrBlank()) {
                edlRaw = null
                edlValidated = emptyList()
                updateEdlSummaryUI()
                return
            }
            val projectDir = EdlIO.projectDirFromManifest(manifestPath)
            val edl = EdlIO.readForProjectDir(projectDir)
            edlRaw = edl
            edlValidated = validateEdl(edl)
            // Attach listener for toggle now that view exists
            idleTrimCheck?.setOnAction { updateEdlSummaryUI() }
            updateEdlSummaryUI()
            println("[EXPORT] EDL loaded: ${edlValidated.size} intervals (raw ${edl.points.size}) from ${projectDir}")
        } catch (t: Throwable) {
            System.err.println("[ERROR] Failed to load EDL for Export: ${t.message}")
            t.printStackTrace()
            edlRaw = null
            edlValidated = emptyList()
            updateEdlSummaryUI()
        }
    }

    // Public accessors for future command builder (Task 3.7)
    fun isIdleTrimEnabled(): Boolean = idleTrimCheck?.isSelected ?: true
    fun edlKeepIntervals(): List<PointV1> = edlValidated
    fun selectedEncoderLabel(): String = encoderBox?.selectionModel?.selectedItem ?: "H.264 (libx264)"
    private fun selectedPreset(): ExportPreset? {
        val idx = presetBoxRef?.selectionModel?.selectedIndex ?: -1
        return if (idx in presetsList.indices) presetsList[idx] else null
    }
    private fun selectedDims(): Pair<Int, Int> {
        val sel = resBoxRef?.selectionModel?.selectedItem ?: "1080p"
        return if (sel == "4K") 3840 to 2160 else 1920 to 1080
    }

    // --- Task 3.6: Initialize Render → Save As dialog and enqueue UI card ---
    private fun handleInitializeRender() {
        try {
            val prefs = Preferences.userNodeForPackage(ExportTab::class.java)
            val lastDirPref = prefs.get("export.lastDir", null)

            // Build suggested file name based on current project manifest if available
            val manifestPath = ProjectsDispatcher.currentProjectPath
            val suggestedName = try {
                if (!manifestPath.isNullOrBlank()) {
                    val mf = ManifestIO.read(manifestPath)
                    val base = mf.name.ifBlank { "export" }
                    "${base}_export.mp4"
                } else {
                    val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").format(LocalDateTime.now())
                    "export_${ts}.mp4"
                }
            } catch (_: Throwable) {
                val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").format(LocalDateTime.now())
                "export_${ts}.mp4"
            }

            val chooser = FileChooser().apply {
                title = "Save Render As"
                extensionFilters.addAll(
                    FileChooser.ExtensionFilter("MP4 Video", "*.mp4"),
                    FileChooser.ExtensionFilter("All Files", "*.*")
                )
                initialFileName = suggestedName

                // Determine initial directory preference
                val initialDir = when {
                    !lastDirPref.isNullOrBlank() && File(lastDirPref).exists() -> File(lastDirPref)
                    !manifestPath.isNullOrBlank() -> File(manifestPath).parentFile
                    else -> {
                        val userHome = System.getProperty("user.home") ?: "."
                        val videos = File(userHome, "Videos")
                        val docs = File(userHome, "Documents")
                        when {
                            videos.exists() -> videos
                            docs.exists() -> docs
                            else -> File(userHome)
                        }
                    }
                }
                if (initialDir.exists()) initialDirectory = initialDir
            }

            val window = root.scene?.window
            val selected = chooser.showSaveDialog(window) ?: return

            // Ensure .mp4 extension if user omitted
            var outFile = selected
            if (!outFile.name.contains('.')) {
                outFile = File(outFile.parentFile, outFile.name + ".mp4")
            }
            if (!outFile.name.lowercase().endsWith(".mp4")) {
                outFile = File(outFile.parentFile, outFile.name.substringBeforeLast('.') + ".mp4")
            }

            if (outFile.exists()) {
                val ok = DialogUtils.confirm(
                    title = "Overwrite existing file?",
                    header = outFile.name,
                    content = "The file already exists. Do you want to replace it?"
                )
                if (!ok) return
            }

            // Remember last save directory in app prefs
            prefs.put("export.lastDir", outFile.parentFile.absolutePath)

            // Build and preview ffmpeg command (Task 3.7)
            try {
                val manifestPath2 = ProjectsDispatcher.currentProjectPath
                val manifest = if (!manifestPath2.isNullOrBlank()) ManifestIO.read(manifestPath2) else null
                val sourcePath = manifest?.sourceVideo
                if (sourcePath.isNullOrBlank()) {
                    println("[WARN] No source video in manifest; command preview skipped.")
                } else {
                    val preset = selectedPreset() ?: ExportPresetsIO.load().let { it[ExportPresetsIO.defaultBalancedIndex(it)] }
                    val dims = selectedDims()
                    val params = FFmpegCommandBuilder.BuildParams(
                        sourcePath = sourcePath,
                        outputPath = outFile.absolutePath,
                        preset = preset,
                        outWidth = dims.first,
                        outHeight = dims.second,
                        encoderLabel = selectedEncoderLabel(),
                        idleTrim = isIdleTrimEnabled(),
                        keeps = if (isIdleTrimEnabled()) edlKeepIntervals() else emptyList(),
                    )
                    val cmd = FFmpegCommandBuilder.build(params)
                    println("[DEBUG] ffmpeg command preview:\n${cmd.preview}")
                }
            } catch (tt: Throwable) {
                System.err.println("[ERROR] Failed to build command preview: ${tt.message}")
                tt.printStackTrace()
            }

            // Enqueue a render job (Task 3.8)
            try {
                val manifestPath3 = ProjectsDispatcher.currentProjectPath
                val manifest3 = if (!manifestPath3.isNullOrBlank()) ManifestIO.read(manifestPath3) else null
                val source3 = manifest3?.sourceVideo
                if (source3.isNullOrBlank()) {
                    DialogUtils.error("Cannot start render", content = "Project has no source video set.")
                    return
                }
                val preset3 = selectedPreset() ?: ExportPresetsIO.load().let { it[ExportPresetsIO.defaultBalancedIndex(it)] }
                val dims3 = selectedDims()
                val job = RenderJob(
                    projectId = manifest3?.id,
                    sourcePath = source3,
                    edlSnapshot = if (isIdleTrimEnabled()) edlKeepIntervals() else emptyList(),
                    presetId = preset3.id,
                    outWidth = dims3.first,
                    outHeight = dims3.second,
                    encoderLabel = selectedEncoderLabel(),
                    idleTrim = isIdleTrimEnabled(),
                    outputPath = outFile.absolutePath,
                )
                RenderQueueManager.enqueue(job)
                addActiveJobCard(job)
                println("[EXPORT] Enqueued render job id=${job.id}: ${job.outputPath}")
            } catch (tt: Throwable) {
                System.err.println("[ERROR] Failed to enqueue render: ${tt.message}")
                tt.printStackTrace()
                DialogUtils.error("Failed to enqueue render", content = tt.message)
            }
        } catch (t: Throwable) {
            System.err.println("[ERROR] Initialize Render failed: ${t.message}")
            t.printStackTrace()
            DialogUtils.error("Initialize Render failed", content = t.message)
        }
    }

    private fun addActiveJobCard(job: RenderJob) {
        val box = activeListBox ?: return
        // Remove placeholder if present (first child is Label)
        if (box.children.size == 1 && box.children[0] is Label) {
            box.children.clear()
        }
        activeCardJobId = job.id
        val fileName = File(job.outputPath).name
        val title = Label(fileName).apply { style = "-fx-text-fill: #ddd; -fx-font-weight: bold;" }
        activeCardTitle = title
        val shortId = job.id.take(8).uppercase()
        val idLbl = Label("ID: ${shortId}").apply { style = "-fx-text-fill: #9aa; -fx-font-size: 11; -fx-font-family: 'Consolas', 'Courier New', monospace;" }
        activeCardIdLbl = idLbl
        val percentLbl = Label("0%")
        activeCardPercentLbl = percentLbl
        val header = HBox(8.0, VBox(2.0, title, idLbl), percentLbl)
        val progress = ProgressBar(0.0).apply { prefWidth = 280.0 }
        activeCardProgress = progress
        val eta = Label("ETA: --:--    0.0 MB").apply { style = "-fx-text-fill: #888; -fx-font-size: 11; -fx-font-family: 'Consolas', 'Courier New', monospace;" }
        activeCardEtaLbl = eta
        val simNote = Label("Simulation mode: no file will be written in this build.").apply {
            style = "-fx-text-fill: #aa7; -fx-font-size: 11; -fx-font-style: italic;"
        }
        val card = VBox(6.0, header, progress, eta, simNote).apply {
            style = "-fx-background-color: #222; -fx-padding: 10; -fx-border-color: #a1fe00; -fx-border-width: 0 0 0 2;"
        }
        box.children.add(0, card)

        // Attach observer to update UI ~ on state changes
        RenderQueueManager.addObserver { snap ->
            val cur = snap.current
            if (cur == null) {
                // Job finished or no active job
                return@addObserver
            }
            if (cur.id != activeCardJobId) return@addObserver
            Platform.runLater {
                val p = cur.progress.coerceIn(0.0, 1.0)
                activeCardProgress?.progress = p
                activeCardPercentLbl?.text = "${(p * 100).toInt()}%"
                val etaStr = formatEta(cur.etaSeconds)
                val sizeStr = formatSize(cur.bytesWritten)
                activeCardEtaLbl?.text = "ETA: ${etaStr}    ${sizeStr}"
            }
        }
    }

    private fun formatEta(secs: Long?): String {
        val s = secs ?: return "--:--"
        val mm = s / 60
        val ss = s % 60
        return String.format("%02d:%02d", mm, ss)
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes / 1_000_000.0
        return String.format("%.1f MB", mb)
    }

    val view: Node get() = viewInternal
}
