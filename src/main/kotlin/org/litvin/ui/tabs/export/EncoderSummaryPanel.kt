package org.litvin.ui.tabs.export

import org.litvin.FFmpegCapabilities
import org.litvin.ui.commons.Html
import org.litvin.ui.UiStyles
import java.awt.Dimension
import javax.swing.*

/**
 * Encapsulates encoder selection, detection hint, and summary label.
 */
class EncoderSummaryPanel {
    data class EncoderItem(val label: String, val id: String) { override fun toString() = label }

    private val encoderCombo = JComboBox<EncoderItem>()
    private val encoderHintLabel = JLabel("")
    private val encoderSummaryLabel = JLabel("")
    private val recheckButton = JButton("Re-check")

    private val panel: JPanel = JPanel()

    init {
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = UiStyles.CARD_BG
        panel.foreground = UiStyles.FG_PRIMARY

        populateEncoders()
        encoderCombo.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 28)
        panel.add(encoderCombo)

        UiStyles.styleHelper(encoderHintLabel)
        panel.add(encoderHintLabel)
        UiStyles.styleHelper(encoderSummaryLabel)
        panel.add(encoderSummaryLabel)

        recheckButton.alignmentX = 0f
        UiStyles.styleSecondary(recheckButton)
        recheckButton.addActionListener {
            try { FFmpegCapabilities.refresh() } catch (_: Throwable) {}
            populateEncoders()
            updateSummary()
        }
        panel.add(Box.createRigidArea(Dimension(0, 4)))
        panel.add(recheckButton)

        encoderCombo.addActionListener { updateSummary() }
        updateSummary()
    }

    private fun populateEncoders() {
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
        Html.setWrapped(encoderHintLabel, hint)
    }

    private fun updateSummary() {
        val item = (encoderCombo.selectedItem as? EncoderItem)
        val label = item?.label ?: "H.264 (libx264) — software"
        val availability = when {
            label.contains("NVENC") -> "GPU accelerated (NVENC)"
            label.contains("QSV") -> "GPU accelerated (QSV)"
            label.contains("AMF") -> "GPU accelerated (AMF)"
            else -> "Software encode"
        }
        Html.setWrapped(encoderSummaryLabel, "Selected: ${label.substringBefore(" — ")} · $availability")
    }

    fun component(): JComponent = panel

    fun selectedEncoderLabel(): String = (encoderCombo.selectedItem as? EncoderItem)?.label ?: "H.264 (libx264) — software"
    fun selectedEncoderId(): String = (encoderCombo.selectedItem as? EncoderItem)?.id ?: "libx264"

    fun addChangeListener(cb: () -> Unit) {
        encoderCombo.addActionListener { cb() }
    }
}