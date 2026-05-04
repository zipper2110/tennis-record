package org.litvin

import org.litvin.media.PlayerStatus
import org.litvin.media.VlcjSwingMediaPlayerAdapter
import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.roundToInt

/**
 * Adjustments Inspector UI (epic 5.2).
 * - Sliders for zoom, pan X/Y, rotation, brightness, contrast, saturation, white balance (temp/tint)
 * - Numeric readouts with formatting
 * - Reset All and per-section resets
 * - Debounced autosave to adjustments.json using AdjustmentsIO
 * - Exposes onAdjustmentsChanged callback to allow live preview wiring in later tasks
 */
class SwingAdjustmentsPanel : JPanel(BorderLayout()) {

    // Center player & transport
    private val player = VlcjSwingMediaPlayerAdapter()
    private lateinit var centerPanel: JPanel
    private lateinit var seek: JSlider
    private lateinit var playPauseBtn: JButton
    private lateinit var currentTimeLabel: JLabel
    private lateinit var durationLabel: JLabel
    private var isSeeking = false
    private val SEEK_MAX = 1000
    private val RIGHT_PANEL_WIDTH = 420

    // Public hooks
    var onAdjustmentsChanged: ((AdjustmentsV1) -> Unit)? = null

    // Project path/state
    private var manifestPath: String? = null
    private var projectDir: String? = null

    // Current model (copy)
    private var model: AdjustmentsV1 = AdjustmentsV1()

    // Subscriptions
    private var adjUnsubscribe: (() -> Unit)? = null

    // Geometry viewport wrapper for player component (Task 5.4 fallback)
    private lateinit var geometryViewport: GeometryViewportPanel

    // Controls + readouts
    private lateinit var sZoom: JSlider
    private lateinit var sPanX: JSlider
    private lateinit var sPanY: JSlider
    private lateinit var sRot: JSlider
    private lateinit var sBright: JSlider
    private lateinit var sContrast: JSlider
    private lateinit var sSat: JSlider
    private lateinit var sWbTemp: JSlider
    private lateinit var sWbTint: JSlider

    private lateinit var rZoom: JLabel
    private lateinit var rPanX: JLabel
    private lateinit var rPanY: JLabel
    private lateinit var rRot: JLabel
    private lateinit var rBright: JLabel
    private lateinit var rContrast: JLabel
    private lateinit var rSat: JLabel
    private lateinit var rWbTemp: JLabel
    private lateinit var rWbTint: JLabel

    init {
        background = UiStyles.DARK_BG
        buildUi()
    }

    fun onActivated() { /* reserved */ }
    fun onDeactivated() { /* reserved */ }

    fun saveNow() {
        try {
            val dir = projectDir ?: return
            AdjustmentsStore.save(dir)
        } catch (_: Throwable) { }
    }

    fun setProjectManifest(path: String) {
        manifestPath = path
        projectDir = File(path).parentFile.absolutePath
        // Load adjustments via central store
        try {
            AdjustmentsStore.load(projectDir!!)
            model = AdjustmentsStore.get()
        } catch (_: Throwable) {
            model = AdjustmentsV1()
        }
        // (Re)subscribe to store updates
        try { adjUnsubscribe?.invoke() } catch (_: Throwable) { }
        try {
            adjUnsubscribe = AdjustmentsStore.subscribe { adj ->
                // Avoid feedback cycles by ignoring if identical
                if (adj == model) return@subscribe
                model = adj
                try { applyModelToUi() } catch (_: Throwable) { }
                try { player.applyColorAdjustments(model) } catch (_: Throwable) { }
                try { player.applyGeometryAdjustments(model) } catch (_: Throwable) { }
            }
        } catch (_: Throwable) { }
        // Load media from manifest and prepare player
        try {
            val manifest = ManifestIO.read(path)
            val src = manifest.sourceVideo
            if (src.isNullOrBlank() || !File(src).exists()) {
                SwingDialogUtils.showError(this, IllegalStateException("Source video missing"), "Select source video for project: ${'$'}{manifest.name}")
            } else {
                player.load(File(src))
            }
        } catch (t: Throwable) {
            SwingDialogUtils.showError(this, t, "Failed to load project")
        }
        // Reflect in UI and apply adjustments to preview (color + geometry)
        applyModelToUi()
        try { player.applyColorAdjustments(model) } catch (_: Throwable) { }
        try { player.applyGeometryAdjustments(model) } catch (_: Throwable) { }
    }

    private fun buildUi() {
        val rightPanel = JPanel()
        rightPanel.isOpaque = true
        rightPanel.background = UiStyles.DARK_BG
        rightPanel.layout = BoxLayout(rightPanel, BoxLayout.Y_AXIS)
        rightPanel.border = BorderFactory.createEmptyBorder(10, 12, 10, 12)

        // Header with Reset All
        run {
            val header = JPanel(BorderLayout())
            header.isOpaque = false
            val title = JLabel("Inspector — Adjustments")
            title.foreground = UiStyles.FG_PRIMARY
            title.font = title.font.deriveFont(Font.BOLD)
            val resetAll = UiStyles.primarySmallButton("Reset All") {
                resetAll()
            }
            header.add(title, BorderLayout.WEST)
            header.add(resetAll, BorderLayout.EAST)
            header.alignmentX = Component.LEFT_ALIGNMENT
            // Make header fill full width under BoxLayout
            val ph = header.preferredSize.height
            header.maximumSize = Dimension(Int.MAX_VALUE, ph)
            rightPanel.add(header)
            rightPanel.add(Box.createVerticalStrut(10))
        }

        // Transform section
        rightPanel.add(sectionLabel("Transform").apply { alignmentX = Component.LEFT_ALIGNMENT })
        rightPanel.add(transformSection().apply { alignmentX = Component.LEFT_ALIGNMENT })
        rightPanel.add(Box.createVerticalStrut(12))

        // Color Grade section
        rightPanel.add(sectionLabel("Color Grade").apply { alignmentX = Component.LEFT_ALIGNMENT })
        rightPanel.add(colorSection().apply { alignmentX = Component.LEFT_ALIGNMENT })

        // glue
        rightPanel.add(Box.createVerticalGlue())

        val rightScroll = wrapIntoScroll(rightPanel)

        // Center: video viewport + transport
        centerPanel = JPanel(BorderLayout()).apply { background = UiStyles.CARD_BG }
        // Video surface (VLCJ component) wrapped with geometry viewport for live zoom/pan
        geometryViewport = GeometryViewportPanel(player.component)
        centerPanel.add(geometryViewport, BorderLayout.CENTER)
        // Transport bar
        centerPanel.add(buildTransportBar(), BorderLayout.SOUTH)

        // Split layout to keep right panel fixed width
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
        split.leftComponent = centerPanel
        split.rightComponent = rightScroll
        split.isOneTouchExpandable = false
        split.dividerSize = 4
        split.resizeWeight = 1.0
        split.setContinuousLayout(true)
        split.border = BorderFactory.createEmptyBorder()
        add(split, BorderLayout.CENTER)

        // Fix right width on initial layout and resizes
        SwingUtilities.invokeLater {
            val total = split.size.width
            if (total > 0) {
                split.setDividerLocation((total - RIGHT_PANEL_WIDTH).coerceAtLeast(0))
            }
        }
        this.addComponentListener(object: java.awt.event.ComponentAdapter(){
            override fun componentResized(e: java.awt.event.ComponentEvent?) {
                val total = split.size.width
                if (total > 0) {
                    split.setDividerLocation((total - RIGHT_PANEL_WIDTH).coerceAtLeast(0))
                }
            }
        })

        // Player callbacks
        player.onReady = {
            EventQueue.invokeLater {
                updateDurationUI(player.totalDurationMs())
                updateTimeUI(player.currentTimeMs())
                updatePlayPauseButton()
            }
        }
        player.onStatusChanged = { _ -> EventQueue.invokeLater { updatePlayPauseButton() } }
        player.onTimeChanged = { t -> EventQueue.invokeLater { if(!isSeeking){ updateTimeUI(t); syncSeekToTime(t) } } }
    }

    private fun buildTransportBar(): JComponent {
        val bar = JPanel(BorderLayout())
        bar.isOpaque = true
        bar.background = UiStyles.CARD_BG
        bar.border = BorderFactory.createMatteBorder(1, 0, 0, 0, UiStyles.CARD_BORDER)

        // Seek slider across the top of the bar
        seek = JSlider(0, SEEK_MAX, 0)
        seek.isOpaque = false
        seek.paintTicks = false
        seek.paintLabels = false
        seek.toolTipText = "Seek"
        // Mouse/drag handling
        seek.addChangeListener(ChangeListener {
            if (!::durationLabel.isInitialized) return@ChangeListener
            if (seek.valueIsAdjusting) {
                isSeeking = true
            }
            val dur = player.totalDurationMs().coerceAtLeast(1L)
            val ratio = seek.value.toDouble() / SEEK_MAX.toDouble()
            val ms = (ratio * dur).toLong()
            if (isSeeking) {
                // update time label while dragging
                updateTimeUI(ms)
            }
        })
        seek.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) { isSeeking = true }
            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                val dur = player.totalDurationMs().coerceAtLeast(1L)
                val ratio = seek.value.toDouble() / SEEK_MAX.toDouble()
                val ms = (ratio * dur).toLong()
                player.seek(ms)
                isSeeking = false
            }
        })
        bar.add(seek, BorderLayout.NORTH)

        val content = JPanel(BorderLayout()).apply { isOpaque = false; border = BorderFactory.createEmptyBorder(6, 10, 6, 10) }

        // Left: current/duration labels
        val left = JPanel().apply { isOpaque = false; layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        currentTimeLabel = JLabel("00:00:00").apply { foreground = UiStyles.FG_PRIMARY; font = font.deriveFont(Font.BOLD, 14f) }
        durationLabel = JLabel("--:--:--").apply { foreground = UiStyles.FG_SECONDARY }
        left.add(JLabel("Current Time").apply { foreground = UiStyles.FG_SECONDARY; font = font.deriveFont(10f) })
        left.add(currentTimeLabel)
        left.add(Box.createVerticalStrut(2))
        left.add(JLabel("Duration").apply { foreground = UiStyles.FG_SECONDARY; font = font.deriveFont(10f) })
        left.add(durationLabel)
        content.add(left, BorderLayout.WEST)

        // Center: play/pause
        playPauseBtn = JButton().apply {
            isFocusPainted = false
            isBorderPainted = false
            background = UiStyles.GREEN
            foreground = Color(0x2B,0x49,0x00)
            preferredSize = Dimension(44, 44)
            icon = UiStyles.playIcon(28)
            toolTipText = "SPACE — Play"
            addActionListener { togglePlayPause() }
        }
        val centerBox = JPanel().apply { isOpaque = false }
        centerBox.add(playPauseBtn)
        content.add(centerBox, BorderLayout.CENTER)

        bar.add(content, BorderLayout.CENTER)

        // Space toggles play/pause
        try {
            val im = bar.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            val am = bar.actionMap
            im.put(KeyStroke.getKeyStroke("SPACE"), "togglePlay")
            am.put("togglePlay", object: AbstractAction(){ override fun actionPerformed(e: java.awt.event.ActionEvent?) { togglePlayPause() } })
        } catch (_: Throwable) { }

        return bar
    }

    private fun updatePlayPauseButton() {
        try {
            val playing = player.status() == PlayerStatus.PLAYING
            playPauseBtn.icon = if (playing) UiStyles.pauseIcon(28) else UiStyles.playIcon(28)
            playPauseBtn.toolTipText = if (playing) "SPACE — Pause" else "SPACE — Play"
            playPauseBtn.repaint()
        } catch (_: Throwable) { }
    }

    private fun togglePlayPause() {
        val wasPlaying = try { player.status() == PlayerStatus.PLAYING } catch (_: Throwable) { false }
        if (wasPlaying) player.pause() else player.play()
        updatePlayPauseButton()
    }

    private fun updateTimeUI(ms: Long) {
        try { currentTimeLabel.text = Timecode.format(kotlin.math.max(0, ms)) } catch (_: Throwable) { }
    }
    private fun updateDurationUI(ms: Long) {
        try { durationLabel.text = Timecode.format(kotlin.math.max(0, ms)) } catch (_: Throwable) { }
    }
    private fun syncSeekToTime(ms: Long) {
        val dur = player.totalDurationMs()
        if (dur <= 0) return
        val ratio = ms.coerceIn(0, dur) / dur.toDouble()
        val v = (ratio * SEEK_MAX).toInt().coerceIn(0, SEEK_MAX)
        if (seek.value != v) seek.value = v
    }

    private fun sectionLabel(text: String): JComponent {
        return JLabel(text).apply {
            foreground = UiStyles.FG_SECONDARY
            font = font.deriveFont(Font.BOLD, 12f)
            border = BorderFactory.createEmptyBorder(6, 0, 6, 0)
            alignmentX = Component.LEFT_ALIGNMENT
            // Ensure full-width under BoxLayout
            maximumSize = Dimension(Int.MAX_VALUE, maximumSize.height)
        }
    }

    private fun transformSection(): JComponent {
        val panel = cardPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val topRow = JPanel(BorderLayout()).apply { isOpaque = false }
        val reset = UiStyles.primarySmallButton("Reset") { resetTransform() }
        topRow.add(JPanel().apply { isOpaque = false; add(JLabel(" ")) }, BorderLayout.WEST)
        topRow.add(reset, BorderLayout.EAST)
        panel.add(topRow)

        // Zoom 0..200 (100=1.0x)
        run {
            val (row, slider, readout) = sliderRow("Zoom", 0, 200, 100)
            sZoom = slider; rZoom = readout
            sZoom.name = "adj-zoom"
            sZoom.addChangeListener(updateListener { v ->
                // map to [0.0, 2.0] but clamp to [0.1, 4.0] at sink; UI limits to 0..200 per spec
                val z = (v / 100.0f).coerceIn(0.0f, 4.0f)
                model = model.copy(zoom = z)
                renderReadouts()
                onModelChanged()
            })
            panel.add(row)
        }
        // Pan X -100..100 [-1..+1]
        run {
            val (row, slider, readout) = sliderRow("Position X", -100, 100, 0)
            sPanX = slider; rPanX = readout
            sPanX.name = "adj-pan-x"
            sPanX.addChangeListener(updateListener { v ->
                val nx = (v / 100.0f).coerceIn(-1.0f, 1.0f)
                model = model.copy(panX = nx)
                renderReadouts(); onModelChanged()
            })
            panel.add(row)
        }
        // Pan Y
        run {
            val (row, slider, readout) = sliderRow("Position Y", -100, 100, 0)
            sPanY = slider; rPanY = readout
            sPanY.name = "adj-pan-y"
            sPanY.addChangeListener(updateListener { v ->
                val ny = (v / 100.0f).coerceIn(-1.0f, 1.0f)
                model = model.copy(panY = ny)
                renderReadouts(); onModelChanged()
            })
            panel.add(row)
        }
        // Rotation -180..+180
        run {
            val (row, slider, readout) = sliderRow("Rotation", -180, 180, 0)
            sRot = slider; rRot = readout
            sRot.name = "adj-rot"
            sRot.addChangeListener(updateListener { v ->
                val r = v.toFloat().coerceIn(-180f, 180f)
                model = model.copy(rotationDeg = r)
                renderReadouts(); onModelChanged()
            })
            panel.add(row)
        }

        return panel
    }

    private fun colorSection(): JComponent {
        val panel = cardPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val topRow = JPanel(BorderLayout()).apply { isOpaque = false }
        val reset = UiStyles.primarySmallButton("Reset") { resetColor() }
        val wbLabel = JLabel("WB").apply { foreground = UiStyles.FG_SECONDARY }
        topRow.add(wbLabel, BorderLayout.WEST)
        topRow.add(reset, BorderLayout.EAST)
        panel.add(topRow)

        // Brightness -100..100 => [-1,+1]
        run {
            val (row, slider, readout) = sliderRow("Brightness", -100, 100, 0)
            sBright = slider; rBright = readout
            sBright.name = "adj-bright"
            sBright.addChangeListener(updateListener { v ->
                val b = (v / 100.0f).coerceIn(-1.0f, 1.0f)
                model = model.copy(brightness = b)
                renderReadouts(); onModelChanged()
            })
            panel.add(row)
        }
        // Contrast 0..200 => [0,2]
        run {
            val (row, slider, readout) = sliderRow("Contrast", 0, 200, 100)
            sContrast = slider; rContrast = readout
            sContrast.name = "adj-contrast"
            sContrast.addChangeListener(updateListener { v ->
                val c = (v / 100.0f).coerceIn(0.0f, 3.0f)
                model = model.copy(contrast = c)
                renderReadouts(); onModelChanged()
            })
            panel.add(row)
        }
        // Saturation 0..200 => [0,2]
        run {
            val (row, slider, readout) = sliderRow("Saturation", 0, 200, 100)
            sSat = slider; rSat = readout
            sSat.name = "adj-sat"
            sSat.addChangeListener(updateListener { v ->
                val s = (v / 100.0f).coerceIn(0.0f, 3.0f)
                model = model.copy(saturation = s)
                renderReadouts(); onModelChanged()
            })
            panel.add(row)
        }
        // White Balance group (Temp/Tint) -100..100 => [-1,+1]
        run {
            val (row, slider, readout) = sliderRow("WB Temperature", -100, 100, 0)
            sWbTemp = slider; rWbTemp = readout
            sWbTemp.name = "adj-wb-temp"
            sWbTemp.addChangeListener(updateListener { v ->
                val t = (v / 100.0f).coerceIn(-1.0f, 1.0f)
                val wb = (model.whiteBalance ?: WhiteBalanceV1()).copy(temperature = t)
                model = model.copy(whiteBalance = wb)
                renderReadouts(); onModelChanged()
            })
            panel.add(row)
        }
        run {
            val (row, slider, readout) = sliderRow("WB Tint", -100, 100, 0)
            sWbTint = slider; rWbTint = readout
            sWbTint.name = "adj-wb-tint"
            sWbTint.addChangeListener(updateListener { v ->
                val t = (v / 100.0f).coerceIn(-1.0f, 1.0f)
                val wb = (model.whiteBalance ?: WhiteBalanceV1()).copy(tint = t)
                model = model.copy(whiteBalance = wb)
                renderReadouts(); onModelChanged()
            })
            panel.add(row)
        }

        return panel
    }

    private fun updateListener(applyFromUiValue: (Int) -> Unit): ChangeListener {
        return ChangeListener { e: ChangeEvent ->
            val s = e.source as? JSlider ?: return@ChangeListener
            applyFromUiValue(s.value)
        }
    }

    private fun sliderRow(labelText: String, min: Int, max: Int, value: Int): Triple<JComponent, JSlider, JLabel> {
        val row = JPanel()
        row.layout = BoxLayout(row, BoxLayout.Y_AXIS)
        row.isOpaque = false
        row.border = BorderFactory.createEmptyBorder(6, 0, 6, 0)

        val header = JPanel(BorderLayout())
        header.isOpaque = false
        val label = JLabel(labelText).apply { foreground = UiStyles.FG_SECONDARY; font = font.deriveFont(12f) }
        val readout = JLabel("").apply { foreground = UiStyles.FG_PRIMARY; font = font.deriveFont(Font.BOLD) }
        header.add(label, BorderLayout.WEST)
        header.add(readout, BorderLayout.EAST)

        val slider = JSlider(min, max, value)
        slider.paintLabels = false
        slider.paintTicks = false
        slider.isOpaque = false

        row.add(header)
        row.add(slider)

        val card = cardPanel()
        card.layout = BorderLayout()
        card.add(row, BorderLayout.CENTER)
        return Triple(card, slider, readout)
    }

    private fun cardPanel(): JPanel {
        return JPanel().apply {
            isOpaque = true
            background = UiStyles.CARD_BG
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiStyles.CARD_BORDER, 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
            )
            alignmentX = Component.LEFT_ALIGNMENT
            // Stretch to fill available width inside the inspector
            maximumSize = Dimension(Int.MAX_VALUE, maximumSize.height)
        }
    }

    private fun wrapIntoScroll(c: JComponent): JComponent {
        // Wrap content into a container that tracks viewport width to avoid inner left gutter
        val wrapper = object: JPanel(BorderLayout()), Scrollable {
            override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
            override fun getScrollableUnitIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int = 16
            override fun getScrollableBlockIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int = 64
            override fun getScrollableTracksViewportWidth(): Boolean = true
            override fun getScrollableTracksViewportHeight(): Boolean = false
        }
        wrapper.isOpaque = false
        wrapper.add(c, BorderLayout.NORTH)

        return JScrollPane(wrapper, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER).apply {
            isOpaque = false
            viewport.isOpaque = false
            border = BorderFactory.createMatteBorder(0, 1, 0, 0, UiStyles.CARD_BORDER)
            minimumSize = Dimension(RIGHT_PANEL_WIDTH, 0)
            preferredSize = Dimension(RIGHT_PANEL_WIDTH, 0)
            maximumSize = Dimension(RIGHT_PANEL_WIDTH, Int.MAX_VALUE)
        }
    }

    private fun resetAll() {
        model = AdjustmentsV1()
        applyModelToUi()
        onModelChanged()
    }

    private fun resetTransform() {
        model = model.copy(zoom = 1.0f, panX = 0.0f, panY = 0.0f, rotationDeg = 0.0f)
        applyModelToUi()
        onModelChanged()
    }

    private fun resetColor() {
        model = model.copy(brightness = 0.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1())
        applyModelToUi()
        onModelChanged()
    }

    private fun onModelChanged() {
        // Push to central store (debounced save inside) and notify local preview
        try { AdjustmentsStore.set(model) } catch (_: Throwable) { }
        try { onAdjustmentsChanged?.invoke(model) } catch (_: Throwable) { }
        // Apply live color adjustments to the embedded preview
        try { player.applyColorAdjustments(model) } catch (_: Throwable) { }
        try { player.applyGeometryAdjustments(model) } catch (_: Throwable) { }
    }

    private fun applyModelToUi() {
        // Prevent WB nulls
        val wb = model.whiteBalance ?: WhiteBalanceV1()
        // Convert to UI ranges
        sZoom.value = (model.zoom * 100.0f).roundToInt().coerceIn(0, 200)
        sPanX.value = (model.panX * 100.0f).roundToInt().coerceIn(-100, 100)
        sPanY.value = (model.panY * 100.0f).roundToInt().coerceIn(-100, 100)
        sRot.value = model.rotationDeg.roundToInt().coerceIn(-180, 180)
        sBright.value = (model.brightness * 100.0f).roundToInt().coerceIn(-100, 100)
        sContrast.value = (model.contrast * 100.0f).roundToInt().coerceIn(0, 200)
        sSat.value = (model.saturation * 100.0f).roundToInt().coerceIn(0, 200)
        sWbTemp.value = (wb.temperature * 100.0f).roundToInt().coerceIn(-100, 100)
        sWbTint.value = (wb.tint * 100.0f).roundToInt().coerceIn(-100, 100)
        // Ensure model has non-null WB after applying
        if (model.whiteBalance == null) model = model.copy(whiteBalance = wb)
        renderReadouts()
    }

    private fun renderReadouts() {
        // Formatting helpers: ×, %, °
        rZoom.text = String.format("%.2f", (sZoom.value / 100.0)) + "\u00D7"
        rPanX.text = String.format("%+.2f", (sPanX.value / 100.0))
        rPanY.text = String.format("%+.2f", (sPanY.value / 100.0))
        rRot.text = String.format("%+.1f°", sRot.value.toDouble())
        rBright.text = String.format("%+d", sBright.value)
        rContrast.text = String.format("%.2f", (sContrast.value / 100.0))
        rSat.text = String.format("%d%%", sSat.value)
        rWbTemp.text = String.format("%+d", sWbTemp.value)
        rWbTint.text = String.format("%+d", sWbTint.value)
    }
}
