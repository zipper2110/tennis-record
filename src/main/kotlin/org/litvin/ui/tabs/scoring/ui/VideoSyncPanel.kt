package org.litvin.ui.tabs.scoring.ui

import org.litvin.ui.UiStyles
import org.litvin.ui.commons.TransportBar
import org.litvin.ui.tabs.scoring.VideoPlayerActions
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.border.EmptyBorder
import kotlin.math.abs

/**
 * Compact scoring-video controls.
 *
 * Shared transport behavior is delegated to [TransportBar]. This panel owns only
 * scoring-specific controls: no-point scoring, speed, and frame-step mode.
 */
class VideoSyncPanel(
    private val actions: VideoPlayerActions,
    private val onNoPoint: () -> Unit,
) : JPanel(BorderLayout()) {

    private val transportBar = TransportBar(
        onTogglePlayPause = { actions.playPause() },
        onSeek = { delta -> actions.seekBy(delta) },
    ).apply {
        name = "scoring-video-controls"
    }

    private val speedCombo: JComboBox<String>
    private lateinit var frameStepCheckbox: JCheckBox
    private lateinit var noPointBtn: JToggleButton

    private val speedPresets: FloatArray = floatArrayOf(2.0f, 1.5f, 1.25f, 1.0f, 0.5f)

    init {
        isOpaque = true
        background = UiStyles.SIDEBAR_BG
        border = EmptyBorder(6, 8, 6, 8)

        val speedRow = JPanel(FlowLayout(FlowLayout.CENTER, 8, 0)).apply {
            isOpaque = false
        }
        val speedLabels = arrayOf("2x", "1.5x", "1.25x", "1x", "0.5x")
        speedCombo = JComboBox(speedLabels).apply {
            UiStyles.styleComboBox(this)
            isFocusable = true
            name = "speed-dropdown"
            toolTipText = "Use Up/Down to change speed"
            addActionListener {
                val idx = selectedIndex.coerceIn(0, speedPresets.lastIndex)
                actions.setSpeedMultiplier(speedPresets[idx])
            }
        }
        speedRow.add(speedCombo)

        frameStepCheckbox = JCheckBox("Seek frame-by-frame [F]").apply {
            toolTipText = "When enabled, Left/Right step a single frame while paused"
            UiStyles.styleCheckBox(this)
            addActionListener { actions.setFrameStepEnabled(isSelected) }
        }
        speedRow.add(Box.createHorizontalStrut(8))
        speedRow.add(JLabel("Up / Down speed").apply {
            foreground = UiStyles.FG_SECONDARY
            font = font.deriveFont(10f)
            toolTipText = "Use Up/Down to change speed"
        })
        speedRow.add(frameStepCheckbox)

        val actionsRow = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
            isOpaque = false
        }
        noPointBtn = JToggleButton("No point [W]").apply {
            isFocusPainted = false
            foreground = UiStyles.FG_PRIMARY
            background = UiStyles.CARD_BORDER
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiStyles.CARD_BORDER, 1),
                EmptyBorder(6, 10, 6, 10),
            )
            name = "no-point"
            toolTipText = "W - No point"
            addActionListener { onNoPoint.invoke() }
        }
        actionsRow.add(noPointBtn)

        val center = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(actionsRow)
            add(Box.createVerticalStrut(12))
            add(transportBar)
            add(Box.createVerticalStrut(8))
            add(speedRow)
        }

        add(center, BorderLayout.CENTER)
    }

    fun setNoPointEnabled(enabled: Boolean) {
        try {
            noPointBtn.isEnabled = enabled
        } catch (_: Throwable) {
        }
    }

    fun setNoPointSelected(selected: Boolean) {
        try {
            noPointBtn.model.isSelected = selected
        } catch (_: Throwable) {
        }
    }

    fun render(isPlaying: Boolean, speedMultiplier: Float) {
        transportBar.setPlaying(isPlaying)

        var best = 0
        var bestDiff = Float.MAX_VALUE
        for (i in speedPresets.indices) {
            val diff = abs(speedPresets[i] - speedMultiplier)
            if (diff < bestDiff) {
                bestDiff = diff
                best = i
            }
        }
        speedCombo.selectedIndex = best
    }

    fun setFrameStepEnabled(enabled: Boolean) {
        try {
            frameStepCheckbox.isSelected = enabled
        } catch (_: Throwable) {
        }
    }
}
