package org.litvin.ui.tabs.export

import org.litvin.ExportPresetsIO
import org.litvin.ui.commons.Html
import org.litvin.ui.UiStyles
import java.awt.Dimension
import java.awt.Font
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Left-side configuration column for Export: presets, resolution, idle-trim, scoreboard, and helper texts.
 *
 * This component encapsulates UI construction and local formatting logic. The hosting panel can:
 *  - read current values via getters
 *  - observe key control changes via addXxxListener methods
 *  - inject external sections (e.g., EncoderSummaryPanel) and a primary action button.
 */
class ExportSettingsPanel(private val presets: List<org.litvin.ExportPreset>) {
    private val panel = JPanel()

    private val presetCombo = JComboBox(presets.map { it.label }.toTypedArray())
    private val resCombo = JComboBox(arrayOf("1080p", "4K"))
    private val qualityLabel = JLabel("")
    private val resSummaryLabel = JLabel("")
    private val scalePlanLabel = JLabel("")
    private val upscalingNoteLabel = JLabel("If the source is lower than selected, basic upscaling will be applied (no smart scaling in v0.1.0).")

    private val idleTrimCheck = JCheckBox("Remove idle time (use EDL keeps)", true)
    private val scoreboardCheck = JCheckBox("Include Scoreboard", false).apply {
        toolTipText = "Burn in a simple scoreboard overlay that updates after each point. Uses current Scoring data; fixed English labels in v0.1.0."
    }
    private val edlInfoLabel = JLabel("")

    private val dynamicBox = Box.createVerticalBox() // space to inject external sections (encoder, etc.)

    init {
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = EmptyBorder(12, 12, 12, 16)
        panel.background = UiStyles.CARD_BG
        panel.foreground = UiStyles.FG_PRIMARY
        panel.preferredSize = Dimension(320, 10)
        panel.minimumSize = Dimension(280, 10)
        panel.maximumSize = Dimension(360, Int.MAX_VALUE)

        val title = JLabel("Export Settings").apply {
            font = font.deriveFont(Font.BOLD)
            foreground = UiStyles.FG_PRIMARY
        }
        panel.add(title)
        panel.add(Box.createRigidArea(Dimension(0, 8)))
        panel.add(JSeparator())
        panel.add(Box.createRigidArea(Dimension(0, 12)))

        // Preset
        panel.add(sectionLabel("Preset"))
        presetCombo.selectedIndex = ExportPresetsIO.defaultBalancedIndex(presets)
        presetCombo.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 28)
        panel.add(presetCombo)
        UiStyles.styleHelper(qualityLabel)
        panel.add(Box.createRigidArea(Dimension(0, 4)))
        panel.add(qualityLabel)
        panel.add(Box.createRigidArea(Dimension(0, 12)))

        // Resolution
        panel.add(sectionLabel("Resolution"))
        resCombo.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 28)
        panel.add(resCombo)
        UiStyles.styleHelper(resSummaryLabel)
        UiStyles.styleMono(scalePlanLabel)
        UiStyles.styleHelper(upscalingNoteLabel)
        upscalingNoteLabel.maximumSize = Dimension(Short.MAX_VALUE.toInt(), Int.MAX_VALUE)
        Html.setWrapped(upscalingNoteLabel, upscalingNoteLabel.text)
        panel.add(Box.createRigidArea(Dimension(0, 6)))
        panel.add(resSummaryLabel)
        panel.add(scalePlanLabel)
        panel.add(Box.createRigidArea(Dimension(0, 4)))
        panel.add(upscalingNoteLabel)
        panel.add(Box.createRigidArea(Dimension(0, 12)))

        // Idle-trim + Scoreboard + EDL info
        UiStyles.stylePrimary(idleTrimCheck)
        panel.add(idleTrimCheck)
        UiStyles.stylePrimary(scoreboardCheck)
        panel.add(scoreboardCheck)
        UiStyles.styleHelper(edlInfoLabel)
        panel.add(edlInfoLabel)
        panel.add(Box.createRigidArea(Dimension(0, 12)))

        // Placeholder for injected sections
        panel.add(dynamicBox)
        panel.add(Box.createVerticalGlue())

        // Wire internal behaviors
        val defIdx = ExportPresetsIO.defaultBalancedIndex(presets)
        if (presets.isNotEmpty()) {
            presetCombo.selectedIndex = defIdx
            val res = defaultResForPreset(presets[defIdx].id)
            resCombo.selectedItem = res
            updateQualitySummary(defIdx)
        }
        updateResolutionPreview()

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
    }

    // --- Public API ---
    fun component(): JComponent = panel

    fun addEncoderSection(section: JComponent) {
        dynamicBox.add(section)
    }

    fun addPrimaryAction(button: JComponent) {
        button.alignmentX = 0f
        panel.add(Box.createRigidArea(Dimension(0, 12)))
        panel.add(button)
    }

    fun edlInfoLabel(): JLabel = edlInfoLabel
    fun presetCombo(): JComboBox<String> = presetCombo as JComboBox<String>
    fun resCombo(): JComboBox<String> = resCombo as JComboBox<String>
    fun idleTrimCheck(): JCheckBox = idleTrimCheck
    fun scoreboardCheck(): JCheckBox = scoreboardCheck

    fun selectedPresetId(): String = presets.getOrNull(presetCombo.selectedIndex)?.id ?: presets[ExportPresetsIO.defaultBalancedIndex(presets)].id
    fun selectedResolutionLabel(): String = (resCombo.selectedItem as? String) ?: "1080p"

    // --- Internal helpers migrated from SwingExportPanel ---
    private fun sectionLabel(text: String): JComponent {
        val l = JLabel(text)
        l.font = l.font.deriveFont(Font.BOLD)
        l.alignmentX = 0f
        return l
    }


    private fun defaultResForPreset(presetId: String): String = if (presetId.equals("quality", true)) "4K" else "1080p"
    private fun targetDimsFor(sel: String): Pair<Int, Int> = if (sel == "4K") 3840 to 2160 else 1920 to 1080

    private fun updateQualitySummary(idx: Int) {
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

    private fun updateResolutionPreview() {
        val sel = resCombo.selectedItem as? String ?: "1080p"
        val (w, h) = targetDimsFor(sel)
        resSummaryLabel.text = "Output: ${w} x ${h} (${sel})"
        scalePlanLabel.text = "Filter plan: -vf scale=${w}:-2"
    }
}