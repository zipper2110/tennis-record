package org.litvin.ui.tabs.adjustments

import org.litvin.ManifestIO
import org.litvin.media.PlayerStatus
import org.litvin.media.VlcjSwingMediaPlayerAdapter
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.ui.UiStyles
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.prefs.Preferences
import javax.swing.*
import org.litvin.ui.commons.applyDarkScrollbar

/**
 * Adjustments: Color Tab — T3 (Player integration and transport wiring)
 *
 * Implements split layout per spec v0.3.0 T1/T2 and wires VLCJ player per T3:
 * - Left: player stack container (min 640x360)
 * - Right: controls container (min 280px width)
 * - Play/Pause button, seek slider, time labels, and SPACE key toggle
 * Component IDs: adj-color-root, adj-color-left, adj-color-right, adj-color-viewport, adj-color-transport, adj-color-left-header
 */
class SwingColorAdjustmentsPanel : JPanel(BorderLayout()) {
    private var projectManifestPath: String? = null

    // Adjustments store subscription and feedback guard (T6)
    private var unsubscribeStore: (() -> Unit)? = null
    private var updatingFromModel: Boolean = false

    private val prefs: Preferences = Preferences.userNodeForPackage(SwingColorAdjustmentsPanel::class.java)
    private val dividerPrefKey = "adj.color.split.divider"

    // Media player and media loading state
    private val player = VlcjSwingMediaPlayerAdapter()
    private var pendingMediaFile: File? = null
    private var isMediaLoaded: Boolean = false

    // Transport UI refs
    private var playPauseBtn: JButton
    private var currentLbl: JLabel
    private var durationLbl: JLabel
    private var seekSlider: JSlider

    // Dragging flag for seek slider
    private var isDraggingSeek: Boolean = false

    private val viewportPanel = JPanel(BorderLayout()).apply {
        name = "adj-color-viewport"
        isOpaque = true
        background = java.awt.Color.BLACK
        minimumSize = Dimension(640, 360)
    }
    private val transportPanel = JPanel(java.awt.BorderLayout()).apply {
        name = "adj-color-transport"
        isOpaque = true
        background = UiStyles.DARK_BG
    }

    private val leftPanel = JPanel(BorderLayout()).apply {
        name = "adj-color-left"
        minimumSize = Dimension(640, 360)
        isOpaque = true
        background = UiStyles.DARK_BG
        // Compose left stack: header (NORTH), viewport (CENTER), transport (SOUTH)
        add(viewportPanel, BorderLayout.CENTER)
        add(transportPanel, BorderLayout.SOUTH)
    }

    // Right controls panel (T4)
    private var brightnessSlider: JSlider
    private var contrastSlider: JSlider
    private var saturationSlider: JSlider
    private var tempSlider: JSlider
    private var tintSlider: JSlider
    private var colorResetBtn: JButton

    private val RIGHT_PANEL_WIDTH = 400

    private val rightPanel = JPanel(BorderLayout()).apply {
        name = "adj-color-right"
        minimumSize = Dimension(RIGHT_PANEL_WIDTH, 360)
        preferredSize = Dimension(RIGHT_PANEL_WIDTH, 600)
        maximumSize = Dimension(RIGHT_PANEL_WIDTH, Int.MAX_VALUE)
        isOpaque = true
        background = UiStyles.DARK_BG
    }

    private val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel).apply {
        name = "adj-color-root"
        resizeWeight = 1.0
        setContinuousLayout(true)
        isEnabled = false
        dividerSize = 0
        background = UiStyles.DARK_BG
    }

    init {
        // Embed VLCJ video component into viewport
        viewportPanel.add(player.component, BorderLayout.CENTER)

        // Populate transport: play/pause, time labels, seek slider
        playPauseBtn = JButton("Play")
        val timesPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 4))
        currentLbl = JLabel("00:00")
        val sepLbl = JLabel("/")
        durationLbl = JLabel("00:00")
        timesPanel.add(currentLbl)
        timesPanel.add(sepLbl)
        timesPanel.add(durationLbl)
        seekSlider = JSlider(0, 1000, 0).apply {
            majorTickSpacing = 0
            paintTicks = false
            paintLabels = false
            putClientProperty("JSlider.isFilled", true)
            toolTipText = "Seek"
        }
        val transportTop = JPanel(BorderLayout())
        transportTop.add(timesPanel, BorderLayout.WEST)
        transportTop.add(playPauseBtn, BorderLayout.EAST)
        transportPanel.add(transportTop, BorderLayout.NORTH)
        transportPanel.add(seekSlider, BorderLayout.CENTER)

        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS); background = UiStyles.DARK_BG; isOpaque = true
        }
        val sectionHeader = JPanel(BorderLayout()).apply { isOpaque = false }
        val sectionTitle = JLabel("Color Grade").apply { foreground = UiStyles.FG_PRIMARY }
        colorResetBtn =
            JButton("Reset").apply { name = "adj-color-reset"; toolTipText = "Reset Color Grade to defaults" }
        colorResetBtn.addActionListener {
            AdjustmentsStore.set { prev -> AdjustmentsUiConverter.DEFAULTS }
        }
        val headerRight2 = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 4)).apply {
            isOpaque = true
            background = UiStyles.DARK_BG
        }

        headerRight2.add(colorResetBtn)
        sectionHeader.add(sectionTitle, BorderLayout.WEST)
        sectionHeader.add(headerRight2, BorderLayout.EAST)
        sectionHeader.border = BorderFactory.createEmptyBorder(8, 8, 4, 8)
        content.add(sectionHeader)

        // Sliders
        brightnessSlider = JSlider()
        contrastSlider = JSlider()
        saturationSlider = JSlider()
        tempSlider = JSlider()
        tintSlider = JSlider()
        content.add(
            labeledSliderRow(
                "Brightness",
                brightnessSlider,
                "adj-brightness",
                "Brightness [-100..+100], default 0"
            )
        )
        content.add(labeledSliderRow("Contrast", contrastSlider, "adj-contrast", "Contrast [-100..+100], default 0"))
        content.add(
            labeledSliderRow(
                "Saturation",
                saturationSlider,
                "adj-saturation",
                "Saturation [-100..+100], default 0"
            )
        )
        content.add(labeledSliderRow("WB Temp", tempSlider, "adj-wb-temp", "Temperature [-100..+100], default 0"))
        content.add(labeledSliderRow("WB Tint", tintSlider, "adj-wb-tint", "Tint [-100..+100], default 0"))
        val scroll = JScrollPane(content).apply {
            background = UiStyles.DARK_BG
            viewport.background = UiStyles.DARK_BG
            border = BorderFactory.createEmptyBorder()
        }
        try { applyDarkScrollbar(scroll, UiStyles.DARK_BG) } catch (_: Throwable) { }
        rightPanel.add(scroll, BorderLayout.CENTER)

        val adjustSupported = try {
            player.isAdjustSupported()
        } catch (_: Throwable) {
            false
        }
        val unsupportedTip =
            "Live preview for this control may not be available on this system; values will still be saved for export."
        val sliders = arrayOf(brightnessSlider, contrastSlider, saturationSlider, tempSlider, tintSlider)
        if (!adjustSupported) {
            sliders.forEach { sld -> sld.toolTipText = (sld.toolTipText?.let { it + "\n" } ?: "") + unsupportedTip }
        }
        sliders.forEach { slider ->
            slider.addChangeListener {
                if (!updatingFromModel) {
                    val adjustments = uiToModel()
                    applyPreview(adjustments)
                    AdjustmentsStore.set { prev -> adjustments }
                }
            }
        }

        // Wire controls
        playPauseBtn.addActionListener { togglePlayPause() }
        installKeyBindings()
        installSeekHandlers()
        installPlayerCallbacks()

        add(split, BorderLayout.CENTER)

        // Keep right panel fixed width on first show and on resize
        val fixDivider: () -> Unit = {
            val total = split.size.width
            if (total > 0) {
                split.setDividerLocation((total - RIGHT_PANEL_WIDTH).coerceAtLeast(0))
            }
        }
        SwingUtilities.invokeLater { fixDivider() }
        this.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) {
                fixDivider()
            }
        })
    }

    // T5 — Live preview wiring
    fun uiToModel(): AdjustmentsV1 {
        return AdjustmentsUiConverter.slidersToModel(
            brightnessSlider.value,
            contrastSlider.value,
            saturationSlider.value,
            tempSlider.value,
            tintSlider.value
        )
    }

    fun applyPreview(adjustments: AdjustmentsV1) {
        player.applyColorAdjustments(adjustments)
    }

    // Build right controls for T4
    fun labeledSliderRow(title: String, slider: JSlider, id: String, tip: String): JPanel {
        slider.minimum = -100
        slider.maximum = 100
        slider.value = 0
        slider.name = id
        slider.toolTipText = tip
        slider.putClientProperty("JSlider.isFilled", true)
        val lbl = JLabel(title).apply { UiStyles.styleHelper(this) }
        val valueLbl = JLabel("0").apply { foreground = UiStyles.FG_PRIMARY }
        val left = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 2)).apply { isOpaque = false; add(lbl) }
        val center = JPanel(BorderLayout()).apply { isOpaque = false; add(slider, BorderLayout.CENTER) }
        val right =
            JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 2)).apply { isOpaque = false; add(valueLbl) }
        val row = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
            add(left, BorderLayout.WEST)
            add(center, BorderLayout.CENTER)
            add(right, BorderLayout.EAST)
        }
        slider.addChangeListener { valueLbl.text = slider.value.toString() }
        return row
    }

    private fun modelToUi(adjustments: AdjustmentsV1) {
        updatingFromModel = true
        try {
            val sliderValues = AdjustmentsUiConverter.modelToSliderValues(adjustments)
            brightnessSlider.value = sliderValues.brightness
            contrastSlider.value = sliderValues.contrast
            saturationSlider.value = sliderValues.saturation
            tempSlider.value = sliderValues.temperature
            tintSlider.value = sliderValues.tint
            // Also update live preview
            applyPreview(adjustments)
        } finally {
            updatingFromModel = false
        }
    }

    override fun addNotify() {
        super.addNotify()
        SwingUtilities.invokeLater { ensurePlayerLoaded() }
        // Ensure UI reflects current adjustments on first show
        modelToUi(AdjustmentsStore.get())
    }

    private fun ensurePlayerLoaded() {
        if (isMediaLoaded) return
        val videoFile = pendingMediaFile ?: return
        val wnd = SwingUtilities.getWindowAncestor(player.component)
        if (!player.component.isDisplayable || wnd == null || !wnd.isShowing) return
        player.load(videoFile)
        player.pause()
        isMediaLoaded = true
        // Re-apply current adjustments after media is loaded to ensure VLC picks them up
        try {
            applyPreview(AdjustmentsStore.get())
        } catch (_: Throwable) { /* ignore */ }
    }

    private var lastTimeUiUpdateAt: Long = 0L
    private val timeUiCadenceMs: Long = 80L

    private fun installPlayerCallbacks() {
        player.onTimeChanged = { ms ->
            EventQueue.invokeLater {
                val dur = player.totalDurationMs()
                // Duration label: update only if changed
                if (dur > 0) {
                    val durText = formatTime(dur)
                    if (durationLbl.text != durText) durationLbl.text = durText
                    if (!isDraggingSeek) {
                        // Throttle slider updates to lighten EDT load
                        val now = System.currentTimeMillis()
                        if ((now - lastTimeUiUpdateAt) >= timeUiCadenceMs) {
                            val pos = ((ms.coerceIn(0, dur).toDouble() / dur.toDouble()) * seekSlider.maximum).toInt()
                            if (seekSlider.value != pos) seekSlider.value = pos
                            lastTimeUiUpdateAt = now
                        }
                        val curText = formatTime(ms)
                        if (currentLbl.text != curText) currentLbl.text = curText
                    }
                } else {
                    val curText = formatTime(ms)
                    if (currentLbl.text != curText) currentLbl.text = curText
                }
                updatePlayPauseUi()
            }
        }
        player.onStatusChanged = { _ -> EventQueue.invokeLater { updatePlayPauseUi() } }
        // onReady not used here
    }

    private fun installSeekHandlers() {
        // While dragging, show the time under the thumb; on release, seek accurately
        seekSlider.addChangeListener {
            if (!seekSlider.isEnabled) return@addChangeListener
            val dur = player.totalDurationMs()
            if (dur <= 0) return@addChangeListener
            val ms = sliderToMs(seekSlider.value, dur)
            if (seekSlider.valueIsAdjusting) {
                isDraggingSeek = true
                currentLbl.text = formatTime(ms)
            } else {
                // final position changed programmatically or by keyboard
                if (isDraggingSeek) {
                    isDraggingSeek = false
                    player.seek(ms)
                }
            }
        }
        // Also listen for mouse release explicitly to ensure seek fires
        seekSlider.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                val dur = player.totalDurationMs()
                if (dur <= 0) return
                val ms = sliderToMs(seekSlider.value, dur)
                isDraggingSeek = false
                player.seek(ms)
            }
        })
    }

    private fun sliderToMs(value: Int, durationMs: Long): Long {
        val ratio = value.toDouble() / seekSlider.maximum.toDouble()
        val target = (durationMs.toDouble() * ratio).toLong()
        return target.coerceIn(0L, durationMs)
    }

    private fun togglePlayPause() {
        val wasPlaying = player.status() == PlayerStatus.PLAYING
        if (wasPlaying) player.pause() else player.play()
        updatePlayPauseUi()
        player.component.requestFocusInWindow()
    }

    private fun updatePlayPauseUi() {
        val playing = player.status() == PlayerStatus.PLAYING
        playPauseBtn.text = if (playing) "Pause" else "Play"
    }

    private fun installKeyBindings() {
        fun bind(key: String, actionName: String, runnable: () -> Unit) {
            val am = this.actionMap
            val ims = arrayOf(
                JComponent.WHEN_IN_FOCUSED_WINDOW,
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
            )
            ims.forEach { cond ->
                val im = this.getInputMap(cond)
                im.put(KeyStroke.getKeyStroke(key), actionName)
            }
            am.put(actionName, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    runnable()
                    EventQueue.invokeLater { player.component.requestFocusInWindow() }
                }
            })
        }
        // Space toggles play/pause
        bind("SPACE", "adjTogglePlayPause") { togglePlayPause() }
    }

    fun setProjectManifest(path: String) {
        projectManifestPath = path
        // Load adjustments for this project directory
        val projectDir = File(path).parentFile?.absolutePath
        if (!projectDir.isNullOrBlank()) {
            AdjustmentsStore.load(projectDir)
        }

        val manifest = ManifestIO.read(path)
        val src = manifest.sourceVideo
        if (!src.isNullOrBlank()) {
            val f = File(src)
            if (f.exists()) {
                pendingMediaFile = f
                isMediaLoaded = false
                ensurePlayerLoaded()
            }
        }
        // Ensure UI reflects the loaded adjustments
        modelToUi(AdjustmentsStore.get())
    }

    fun onActivated() {
        // Subscribe to adjustments changes to reflect external updates and live-apply preview (T6)
        unsubscribeStore?.invoke()
        unsubscribeStore = AdjustmentsStore.subscribe { adj ->
            EventQueue.invokeLater { modelToUi(adj) }
        }
        // Push current state immediately
        modelToUi(AdjustmentsStore.get())
    }

    fun onDeactivated() {
        player.pause()
        unsubscribeStore?.invoke(); unsubscribeStore = null
        // Persist current adjustments immediately when leaving the tab
        val dir = projectManifestPath?.let { File(it).parentFile?.absolutePath }
        AdjustmentsStore.save(dir)
    }

    private fun formatTime(ms: Long): String {
        var total = if (ms < 0) 0 else ms
        val hours = total / 3_600_000
        total -= hours * 3_600_000
        val minutes = total / 60_000
        total -= minutes * 60_000
        val seconds = total / 1_000
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds) else String.format(
            "%02d:%02d",
            minutes,
            seconds
        )
    }
}
