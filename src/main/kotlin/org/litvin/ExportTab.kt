package org.litvin

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.*

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

    private fun buildSidebar(): Node {
        val brandIcon = Label("⬢").apply { styleClass.add("brand-icon") }
        val version = Label("v1.0.4").apply { styleClass.add("brand-version") }
        val brandBox = VBox(4.0, brandIcon, version).apply { alignment = Pos.CENTER }

        fun navItem(text: String, active: Boolean = false, onClick: (() -> Unit)? = null): Node = VBox(4.0).apply {
            val icon = Label("●").apply { styleClass.add(if (active) "nav-icon-active" else "nav-icon") }
            val label = Label(text).apply { styleClass.add(if (active) "nav-label-active" else "nav-label") }
            children.addAll(icon, label)
            alignment = Pos.CENTER
            styleClass.add("nav-item")
            isFocusTraversable = false
            if (onClick != null) setOnMouseClicked { onClick.invoke() }
        }

        val nav = VBox(16.0,
            navItem("Projects", active = false) { onRequestNavigateProjects?.invoke() },
            navItem("Markup", active = false) { onRequestNavigateMarkup?.invoke() },
            navItem("Adjust"),
            navItem("Scoring"),
            navItem("Export", active = true)
        ).apply { alignment = Pos.TOP_CENTER }

        val settingsBtn = Button("⚙").apply {
            styleClass.add("settings-btn")
            isFocusTraversable = false
        }
        val settingsBox = VBox(settingsBtn).apply { alignment = Pos.CENTER }

        return VBox().apply {
            prefWidth = 80.0; minWidth = 80.0; maxWidth = 80.0
            spacing = 24.0
            padding = Insets(16.0, 0.0, 16.0, 0.0)
            styleClass.add("sidebar")
            children.addAll(VBox(10.0, brandBox, nav).apply { alignment = Pos.TOP_CENTER; VBox.setVgrow(nav, Priority.ALWAYS) }, settingsBox)
        }
    }

    private fun buildLeftPanel(): Node {
        val title = Label("Export Settings").apply { style = "-fx-font-weight: bold; -fx-text-fill: #ddd;" }

        val presetLabel = Label("Preset").apply { style = "-fx-text-fill: #bbb;" }
        val presetBox = ComboBox<String>()

        val resLabel = Label("Resolution").apply { style = "-fx-text-fill: #bbb;" }
        val resBox = ComboBox<String>().apply {
            items.addAll("1080p", "4K")
            selectionModel.select(0)
        }
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
                "H.264 (NVENC) — disabled",
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
        fun updateEncoderSummary() {
            val sel = encoder.selectionModel.selectedItem ?: "H.264 (libx264)"
            val hw = isHw(sel)
            if (hw) {
                // prevent selecting disabled options
                encoder.selectionModel.select(0)
            }
            val current = encoder.selectionModel.selectedItem ?: "H.264 (libx264)"
            val availability = if (isHw(current)) "Unavailable in v0.1.0" else "Available"
            encoderSummary.text = "Selected: $current · $availability"
        }
        encoder.setOnAction { updateEncoderSummary() }

        val initBtn = Button("Initialize Render").apply {
            styleClass.add("cta-primary")
            isDisable = true // enabled in later tasks when prerequisites are met
            setOnAction {
                // Placeholder: will open Save As in task 3.6
                println("[EXPORT] Initialize Render clicked (stub)")
            }
        }

        // Load presets and bind to UI (Task 3.2)
        val presets = ExportPresetsIO.load()
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
        // Placeholder cards/labels
        (active.children[1] as VBox).children.add(Label("No active renders").apply { style = "-fx-text-fill: #777;" })
        (completed.children[1] as VBox).children.add(Label("No completed renders yet").apply { style = "-fx-text-fill: #777;" })
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

    val view: Node get() = viewInternal
}
