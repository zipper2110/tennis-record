package org.litvin.ui.tabs.export

import org.litvin.export.EncoderCapabilities
import org.litvin.export.ExportEncoder
import org.litvin.export.ExportFrameRateChoice
import org.litvin.export.ExportQualityLevel
import org.litvin.export.ExportResolutionChoice
import org.litvin.export.ExportSimplePreset
import org.litvin.export.ExportSourceInfo
import org.litvin.export.ExportVideoOptions
import org.litvin.export.ExportVideoTarget
import org.litvin.export.RenderFormatting
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.Html
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JToggleButton
import javax.swing.SwingConstants

/** The video settings and the encoder that the next export uses. */
data class ExportQualitySelection(
    val target: ExportVideoTarget,
    val encoder: ExportEncoder,
)

/**
 * The "Quality and file size" block. The Simple tab has three presets. The Advanced tab has
 * resolution, frame rate, bitrate and encoder controls. The selected tab decides which settings the export uses.
 */
class ExportQualityPanel(
    private val settings: ExportSettingsPreferences,
) : JPanel(BorderLayout()) {
    private val saved = settings.load()
    private var source = ExportSourceInfo.UNKNOWN
    private var outputDurationMs: Long? = null
    private var encoders: EncoderCapabilities? = null

    private val simpleTab = TabButton("Simple", "export-mode-simple")
    private val advancedTab = TabButton("Advanced", "export-mode-advanced")
    // Both tabs stay in the component tree. BoxLayout skips the hidden tab, so the block takes only the height of the visible tab.
    private val tabContent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }

    // Simple tab
    private val simpleCards = ExportSimplePreset.entries.associateWith { OptionCard("export-quality-${it.id}") }
    private val simpleGpuLabel = helperLabel("export-simple-gpu")
    private val simpleEncoderLabel = helperLabel("export-simple-encoder")
    private val simplePanel = column()

    // Advanced tab
    private val resolutionRow = JPanel(GridLayout(1, 0, 6, 0)).apply { isOpaque = false; alignmentX = 0f }
    private val frameRateRow = JPanel(GridLayout(1, 0, 6, 0)).apply { isOpaque = false; alignmentX = 0f }
    private var resolutionCards: List<Pair<ExportResolutionChoice, OptionCard>> = emptyList()
    private var frameRateCards: List<Pair<ExportFrameRateChoice, OptionCard>> = emptyList()
    private val bitrateSlider = JSlider().apply {
        name = "export-bitrate"
        isOpaque = false
        alignmentX = 0f
        putClientProperty("FlatLaf.style", "thumbColor: #A1FE00; trackValueColor: #A1FE00")
    }
    private val bitrateValueLabel = JLabel().apply {
        name = "export-bitrate-value"
        foreground = UiStyles.FG_PRIMARY
        alignmentX = 0f
    }
    private val bitrateNoteLabel = helperLabel("export-bitrate-note")
    private val advancedGpuLabel = helperLabel("export-advanced-gpu")
    private val encoderList = column()
    private var encoderCards: List<Pair<ExportEncoder, OptionCard>> = emptyList()
    private var chosenEncoderId: String? = saved.encoderId
    private val advancedPanel = column()

    init {
        name = "export-quality"
        isOpaque = false
        alignmentX = 0f

        val tabs = JPanel(GridLayout(1, 2, 0, 0)).apply {
            isOpaque = false
            add(simpleTab)
            add(advancedTab)
        }
        ButtonGroup().apply {
            add(simpleTab)
            add(advancedTab)
        }
        simpleTab.addActionListener { showMode(advanced = false) }
        advancedTab.addActionListener { showMode(advanced = true) }
        add(tabs, BorderLayout.NORTH)
        add(tabContent, BorderLayout.CENTER)

        val simpleGroup = ButtonGroup()
        simpleCards.forEach { (preset, card) ->
            simpleGroup.add(card)
            card.addActionListener { settings.saveSimplePreset(preset.id) }
            simplePanel.add(card)
            simplePanel.add(Box.createRigidArea(Dimension(0, 6)))
        }
        val simplePreset = ExportSimplePreset.fromId(saved.simplePresetId) ?: ExportSimplePreset.BEST
        simpleCards.getValue(simplePreset).isSelected = true
        simplePanel.add(Box.createRigidArea(Dimension(0, 4)))
        simplePanel.add(simpleGpuLabel)
        simplePanel.add(Box.createRigidArea(Dimension(0, 6)))
        simplePanel.add(simpleEncoderLabel)

        advancedPanel.add(sectionLabel("Resolution"))
        advancedPanel.add(resolutionRow)
        advancedPanel.add(Box.createRigidArea(Dimension(0, 10)))
        advancedPanel.add(sectionLabel("Frame rate"))
        advancedPanel.add(frameRateRow)
        advancedPanel.add(Box.createRigidArea(Dimension(0, 10)))
        advancedPanel.add(sectionLabel("Bitrate"))
        advancedPanel.add(bitrateValueLabel)
        advancedPanel.add(bitrateSlider)
        // Each end label gets half of the width, so a label does not cut off its last character.
        advancedPanel.add(JPanel(GridLayout(1, 2)).apply {
            isOpaque = false
            alignmentX = 0f
            maximumSize = Dimension(Int.MAX_VALUE, 24)
            add(helperLabel("export-bitrate-worst").apply { text = "Worst quality" })
            add(helperLabel("export-bitrate-best").apply {
                text = "Best quality"
                horizontalAlignment = SwingConstants.RIGHT
            })
        })
        advancedPanel.add(bitrateNoteLabel)
        advancedPanel.add(Box.createRigidArea(Dimension(0, 10)))
        advancedPanel.add(sectionLabel("Encoder"))
        advancedPanel.add(advancedGpuLabel)
        advancedPanel.add(Box.createRigidArea(Dimension(0, 4)))
        advancedPanel.add(encoderList)
        bitrateSlider.addChangeListener { updateBitrateLabels() }
        tabContent.add(simplePanel)
        tabContent.add(advancedPanel)

        applySource()
        applyEncoders()
        if (saved.advancedMode) advancedTab.isSelected = true else simpleTab.isSelected = true
        showMode(saved.advancedMode, save = false)
    }

    /** Shows the options for a new source video. The Advanced tab goes back to the source values. */
    fun setSource(info: ExportSourceInfo) {
        source = info
        applySource()
    }

    /** Duration of the exported video, for the file size estimate. Null when it is not known. */
    fun setOutputDurationMs(durationMs: Long?) {
        outputDurationMs = durationMs
        updateSimpleCards()
        updateBitrateLabels()
    }

    /** Shows the encoders that passed the test encode. Until this call, the export uses the software encoder. */
    fun setEncoders(capabilities: EncoderCapabilities) {
        encoders = capabilities
        applyEncoders()
    }

    fun isAdvancedMode(): Boolean = advancedTab.isSelected

    fun selection(): ExportQualitySelection {
        val capabilities = encoders ?: EncoderCapabilities.NONE
        if (!isAdvancedMode()) {
            return ExportQualitySelection(ExportVideoOptions.simpleTarget(selectedSimplePreset(), source), capabilities.best)
        }
        val resolution = resolutionCards.firstOrNull { it.second.isSelected }?.first
            ?: ExportVideoOptions.defaultResolution(resolutionCards.map { it.first })
        val frameRate = frameRateCards.firstOrNull { it.second.isSelected }?.first
            ?: ExportVideoOptions.defaultFrameRate(frameRateCards.map { it.first })
        val encoder = encoderCards.firstOrNull { it.second.isSelected }?.first ?: capabilities.best
        return ExportQualitySelection(
            ExportVideoTarget(ExportVideoOptions.CUSTOM_PRESET_ID, resolution.resolution, frameRate.frameRate, bitrateSlider.value),
            encoder,
        )
    }

    private fun selectedSimplePreset(): ExportSimplePreset =
        simpleCards.entries.firstOrNull { it.value.isSelected }?.key ?: ExportSimplePreset.BEST

    private fun showMode(advanced: Boolean, save: Boolean = true) {
        simplePanel.isVisible = !advanced
        advancedPanel.isVisible = advanced
        if (save) settings.saveAdvancedMode(advanced)
        tabContent.revalidate()
        tabContent.repaint()
    }

    private fun applySource() {
        val resolutionChoices = ExportVideoOptions.resolutionChoices(source.resolution)
        val defaultResolution = ExportVideoOptions.defaultResolution(resolutionChoices)
        resolutionRow.removeAll()
        val resolutionGroup = ButtonGroup()
        resolutionCards = resolutionChoices.map { choice ->
            val card = OptionCard(resolutionCardName(choice)).apply {
                setContent(choice.title, listOfNotNull(choice.level?.label), ORIGINAL.takeIf { choice.isSource })
                isEnabled = choice.available
                isSelected = choice == defaultResolution
            }
            resolutionGroup.add(card)
            resolutionRow.add(card)
            choice to card
        }

        val frameRateChoices = ExportVideoOptions.frameRateChoices(source.frameRate)
        val defaultFrameRate = ExportVideoOptions.defaultFrameRate(frameRateChoices)
        frameRateRow.removeAll()
        val frameRateGroup = ButtonGroup()
        frameRateCards = frameRateChoices.map { choice ->
            val card = OptionCard(frameRateCardName(choice)).apply {
                val note = when {
                    !choice.isSource -> null
                    source.variableFrameRate -> "$ORIGINAL, variable"
                    else -> ORIGINAL
                }
                setContent(choice.title, listOfNotNull(choice.level?.label), note)
                isEnabled = choice.available
                isSelected = choice == defaultFrameRate
            }
            frameRateGroup.add(card)
            frameRateRow.add(card)
            choice to card
        }

        val range = ExportVideoOptions.bitrateRange(source)
        bitrateSlider.minimum = range.minK
        bitrateSlider.maximum = range.maxK
        bitrateSlider.value = range.maxK
        Html.setWrapped(
            bitrateNoteLabel,
            if (range.estimated) {
                "The bitrate of the original video is not known. The maximum is a typical camera bitrate."
            } else {
                "The maximum is the bitrate of the original video."
            },
            SECTION_WRAP_PX,
        )
        updateSimpleCards()
        updateBitrateLabels()
        revalidate()
        repaint()
    }

    private fun updateSimpleCards() {
        val sourceBitrateKnown = source.bitrate != null
        simpleCards.forEach { (preset, card) ->
            val target = ExportVideoOptions.simpleTarget(preset, source)
            val sameResolution = source.resolution?.let { it.width == target.resolution.width && it.height == target.resolution.height } == true
            val averageRate = source.averageFrameRate?.takeIf { source.variableFrameRate }
            val bitrateTag = when {
                !sourceBitrateKnown -> null
                preset.bitrateFactor >= 1.0 -> ORIGINAL
                else -> "${(preset.bitrateFactor * 100).toInt()}% of $ORIGINAL"
            }
            val details = column().apply {
                border = BorderFactory.createEmptyBorder()
                add(detailRow(
                    "Resolution",
                    ExportVideoOptions.displayResolution(target.resolution.width, target.resolution.height),
                    ORIGINAL.takeIf { sameResolution },
                ))
                add(detailRow("FPS", target.frameRate?.displayFps ?: "unknown", ORIGINAL.takeIf { target.frameRate != null }))
                averageRate?.let { average ->
                    add(JLabel().apply {
                        UiStyles.styleHelper(this)
                        Html.setWrapped(this, "The original frame rate is variable: ${average.displayFps} fps on average.", CARD_WRAP_PX)
                        alignmentX = 0f
                        border = BorderFactory.createEmptyBorder(0, 0, 2, 0)
                    })
                }
                add(detailRow("Bitrate", ExportVideoOptions.formatBitrate(target.bitrateK), bitrateTag))
                add(detailRow("File size", estimatedSize(target.bitrateK)))
                add(detailRow("Export time", preset.exportTime))
            }
            card.setContent(preset.title)
            card.setSubtitle(preset.summary)
            card.setExpandableContent(details)
        }
    }

    /** One line of the expandable details: a bold name, the value and an optional tag. */
    private fun detailRow(name: String, value: String, tag: String? = null): JComponent =
        JPanel(FlowLayout(FlowLayout.LEFT, 0, 1)).apply {
            isOpaque = false
            alignmentX = 0f
            add(JLabel("$name:").apply {
                UiStyles.styleHelper(this)
                font = font.deriveFont(Font.BOLD)
                border = BorderFactory.createEmptyBorder(0, 0, 0, 6)
            })
            add(JLabel(value).apply {
                UiStyles.styleHelper(this)
                foreground = UiStyles.FG_PRIMARY
                border = BorderFactory.createEmptyBorder(0, 0, 0, 6)
            })
            tag?.let { add(TagLabel(it)) }
        }

    private fun updateBitrateLabels() {
        bitrateValueLabel.text = "${ExportVideoOptions.formatBitrate(bitrateSlider.value)}  ·  " +
            "file size: ${estimatedSize(bitrateSlider.value)}"
    }

    private fun applyEncoders() {
        val capabilities = encoders
        encoderList.removeAll()
        if (capabilities == null) {
            simpleGpuLabel.text = "Detecting GPUs and encoders…"
            simpleEncoderLabel.text = "Until the detection ends, the export uses the software encoder."
            advancedGpuLabel.text = "Detecting GPUs and encoders…"
            encoderCards = emptyList()
            encoderList.revalidate()
            return
        }
        val best = capabilities.best
        val gpus = gpuListHtml(capabilities.gpuNames)
        simpleGpuLabel.text = gpus
        advancedGpuLabel.text = gpus
        val noHardware = if (best.hardware) {
            ""
        } else {
            "<br>No hardware encoder works on this PC, so the processor encodes the video."
        }
        simpleEncoderLabel.text = "<html><div style='width:${SECTION_WRAP_PX}px'>" +
            "<b>Selected encoder:</b> ${Html.escapeHtml(best.title)}$noHardware</div></html>"

        val options = capabilities.options
        val selected = options.firstOrNull { it.id == chosenEncoderId } ?: best
        val group = ButtonGroup()
        encoderCards = options.map { encoder ->
            val card = OptionCard("export-encoder-${encoder.id}", wrapWidthPx = CARD_WRAP_PX).apply {
                setContent(encoder.title, listOf(encoder.description), "Best for this PC".takeIf { encoder == best })
                isSelected = encoder == selected
                addActionListener {
                    chosenEncoderId = encoder.id
                    settings.saveEncoder(encoder.id)
                }
            }
            group.add(card)
            encoderList.add(card)
            encoderList.add(Box.createRigidArea(Dimension(0, 6)))
            encoder to card
        }
        encoderList.revalidate()
        encoderList.repaint()
    }

    /** "Detected GPUs:" and one bullet line for each GPU. */
    private fun gpuListHtml(gpuNames: List<String>): String {
        val heading = if (gpuNames.size == 1) "Detected GPU:" else "Detected GPUs:"
        val lines = gpuNames.ifEmpty { listOf("none") }
            .joinToString("") { "<br>&nbsp;&nbsp;•&nbsp;${Html.escapeHtml(it)}" }
        return "<html><div style='width:${SECTION_WRAP_PX}px'><b>$heading</b>$lines</div></html>"
    }

    private fun estimatedSize(bitrateK: Int): String =
        outputDurationMs?.let { "~" + RenderFormatting.formatSize(ExportVideoOptions.estimatedBytes(bitrateK, it)) } ?: "unknown"

    private fun resolutionCardName(choice: ExportResolutionChoice): String =
        "export-resolution-" + if (choice.level == null) "source" else choice.title.lowercase()

    private fun frameRateCardName(choice: ExportFrameRateChoice): String =
        "export-fps-" + if (choice.level == null) "source" else when (choice.level) {
            ExportQualityLevel.LOW -> "24"
            ExportQualityLevel.MEDIUM -> "30"
            ExportQualityLevel.HIGH -> "60"
        }

    private fun column(): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = 0f
        border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
    }

    private fun sectionLabel(text: String): JComponent = JLabel(text).apply {
        font = font.deriveFont(Font.BOLD)
        foreground = UiStyles.FG_PRIMARY
        alignmentX = 0f
        border = BorderFactory.createEmptyBorder(0, 0, 4, 0)
    }

    private fun helperLabel(componentName: String): JLabel = JLabel().apply {
        name = componentName
        alignmentX = 0f
        UiStyles.styleHelper(this)
    }

    /** A tab header: the selected tab has bright text and a lime line under it. */
    private class TabButton(text: String, componentName: String) : JToggleButton(text) {
        init {
            name = componentName
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            font = font.deriveFont(Font.BOLD)
            border = BorderFactory.createEmptyBorder(6, 8, 8, 8)
            addItemListener { foreground = if (isSelected) UiStyles.FG_PRIMARY else UiStyles.FG_SECONDARY }
            foreground = UiStyles.FG_SECONDARY
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g.create() as Graphics2D
            try {
                g2.color = if (isSelected) UiStyles.LIME else UiStyles.CARD_BORDER
                val thickness = if (isSelected) 2f else 1f
                g2.stroke = BasicStroke(thickness)
                val y = height - thickness.toInt()
                g2.drawLine(0, y, width, y)
            } finally {
                g2.dispose()
            }
        }
    }

    private companion object {
        const val ORIGINAL = "original"
        // Swing HTML makes a CSS width wider on the screen than the number, so these values are smaller than the column.
        const val SECTION_WRAP_PX = 290
        const val CARD_WRAP_PX = 280
    }
}
