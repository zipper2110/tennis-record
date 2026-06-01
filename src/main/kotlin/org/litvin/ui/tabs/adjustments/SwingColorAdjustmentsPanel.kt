package org.litvin.ui.tabs.adjustments

import org.litvin.projects.ManifestIO
import org.litvin.media.PlayerStatus
import org.litvin.media.VlcjSwingMediaPlayerAdapter
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.AdjustmentsStore
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.ScrubBar
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.event.ActionEvent
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
    private var scrubBar: ScrubBar

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
        playPauseBtn = UiStyles.squarePrimaryButton(UiStyles.playIcon(28)) { togglePlayPause() }.apply {
            name = "adj-color-play-pause"
            accessibleContext.accessibleName = "Play or Pause"
            toolTipText = "SPACE - Play"
        }
        scrubBar = ScrubBar(
            onUserScrub = { target -> player.seek(target) },
            tooltip = "Seek"
        )
        val playRow = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 6)).apply {
            isOpaque = true
            background = UiStyles.SURFACE_HIGH
        }
        playRow.add(playPauseBtn)
        val scrubRow = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = Color(0x11, 0x11, 0x11)
            border = BorderFactory.createEmptyBorder(8, 16, 8, 16)
            scrubBar.setBarBackground(background)
            add(scrubBar, BorderLayout.CENTER)
        }
        transportPanel.add(playRow, BorderLayout.NORTH)
        transportPanel.add(scrubRow, BorderLayout.CENTER)

        val content = object : JPanel(), Scrollable {
            override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
            override fun getScrollableUnitIncrement(
                visibleRect: java.awt.Rectangle,
                orientation: Int,
                direction: Int
            ): Int = 24
            override fun getScrollableBlockIncrement(
                visibleRect: java.awt.Rectangle,
                orientation: Int,
                direction: Int
            ): Int = visibleRect.height - 24
            override fun getScrollableTracksViewportWidth(): Boolean = true
            override fun getScrollableTracksViewportHeight(): Boolean = false
        }.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = UiStyles.DARK_BG
            isOpaque = true
            border = BorderFactory.createEmptyBorder(15, 15, 15, 15)
        }
        val sectionHeader = JPanel(BorderLayout()).apply { isOpaque = false }
        val sectionTitle = JLabel("Color Grade").apply {
            foreground = UiStyles.FG_PRIMARY
            font = font.deriveFont(font.style, font.size2D + 3.0f)
        }
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
        sectionHeader.border = BorderFactory.createEmptyBorder(0, 0, 18, 0)
        sectionHeader.alignmentX = LEFT_ALIGNMENT
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
        content.add(labeledSliderRow("Contrast", contrastSlider, "adj-contrast", "Contrast [-50..+50], default 0"))
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
        configureColorSliderRanges()
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
        installKeyBindings()
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
            border = BorderFactory.createEmptyBorder(0, 0, 60, 0)
            alignmentX = LEFT_ALIGNMENT
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                add(left, BorderLayout.WEST)
                add(right, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            add(center, BorderLayout.CENTER)
        }
        row.maximumSize = Dimension(Int.MAX_VALUE, 100)
        row.preferredSize = Dimension(0, 100)
        slider.addChangeListener { valueLbl.text = slider.value.toString() }
        return row
    }

    private fun modelToUi(adjustments: AdjustmentsV1) {
        updatingFromModel = true
        try {
            val sliderValues = AdjustmentsUiConverter.modelToSliderValues(adjustments)
            brightnessSlider.value = sliderValues.brightness.coerceIn(brightnessSlider.minimum, brightnessSlider.maximum)
            contrastSlider.value = sliderValues.contrast.coerceIn(contrastSlider.minimum, contrastSlider.maximum)
            saturationSlider.value = sliderValues.saturation.coerceIn(saturationSlider.minimum, saturationSlider.maximum)
            tempSlider.value = sliderValues.temperature
            tintSlider.value = sliderValues.tint
            // Also update live preview
            applyPreview(uiToModel())
        } finally {
            updatingFromModel = false
        }
    }

    private fun configureColorSliderRanges() {
        brightnessSlider.minimum = -80
        brightnessSlider.maximum = 74
        contrastSlider.minimum = -50
        contrastSlider.maximum = 50
        saturationSlider.minimum = -85
        saturationSlider.maximum = 100
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
                if (dur > 0) {
                    val now = System.currentTimeMillis()
                    if ((now - lastTimeUiUpdateAt) >= timeUiCadenceMs) {
                        scrubBar.setRange(0L, dur)
                        scrubBar.setPosition(ms)
                        lastTimeUiUpdateAt = now
                    }
                } else {
                    scrubBar.reset()
                }
                updatePlayPauseUi()
            }
        }
        player.onStatusChanged = { _ -> EventQueue.invokeLater { updatePlayPauseUi() } }
        player.onReady = {
            EventQueue.invokeLater {
                val dur = player.totalDurationMs()
                if (dur > 0) {
                    scrubBar.setRange(0L, dur)
                    scrubBar.setPosition(player.currentTimeMs())
                }
                updatePlayPauseUi()
            }
        }
    }

    private fun togglePlayPause() {
        val wasPlaying = player.status() == PlayerStatus.PLAYING
        if (wasPlaying) player.pause() else player.play()
        updatePlayPauseUi()
        player.component.requestFocusInWindow()
    }

    private fun updatePlayPauseUi() {
        val playing = player.status() == PlayerStatus.PLAYING
        playPauseBtn.icon = if (playing) UiStyles.pauseIcon(28) else UiStyles.playIcon(28)
        playPauseBtn.toolTipText = if (playing) "SPACE - Pause" else "SPACE - Play"
        playPauseBtn.repaint()
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

}
