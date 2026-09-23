package org.litvin.ui.tabs.scoring.ui

import org.litvin.ui.UiStyles
import org.litvin.ui.commons.TransportBar
import org.litvin.ui.tabs.scoring.VideoPlayerActions
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.abs

/**
 * Compact scoring-video controls: transport on the first row, speed and frame-step mode on the second row.
 *
 * Shared transport behavior is delegated to [TransportBar].
 */
class VideoSyncPanel(
    private val actions: VideoPlayerActions,
) : JPanel() {

    private val transportBar = TransportBar(
        onTogglePlayPause = { actions.playPause() },
        onSeek = { delta -> actions.seekBy(delta) },
    ).apply {
        name = "scoring-video-controls"
    }

    private val speedRow = JPanel(FlowLayout(FlowLayout.CENTER, 8, 0))
    private val speedCombo: JComboBox<String>
    private val frameStepCheckbox: JCheckBox

    private val speedPresets: FloatArray = floatArrayOf(2.0f, 1.5f, 1.25f, 1.0f, 0.5f)

    /** Short seek labels for narrow screens. See [TransportBar.compact]. */
    var compact: Boolean
        get() = transportBar.compact
        set(value) {
            transportBar.compact = value
        }

    /** Width that shows the full seek labels. */
    val fullPreferredWidth: Int
        get() = maxOf(transportBar.fullPreferredWidth, speedRow.preferredSize.width)

    /** Smallest width that shows all controls in one row each (compact seek labels). */
    val compactPreferredWidth: Int
        get() = maxOf(transportBar.compactPreferredWidth, speedRow.preferredSize.width)

    init {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)

        speedRow.isOpaque = false
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
        speedRow.add(JLabel("Up / Down speed").apply {
            foreground = UiStyles.FG_SECONDARY
            font = font.deriveFont(10f)
            toolTipText = "Use Up/Down to change speed"
        })
        speedRow.add(frameStepCheckbox)

        add(transportBar)
        add(Box.createVerticalStrut(8))
        add(speedRow)
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
        frameStepCheckbox.isSelected = enabled
    }
}
