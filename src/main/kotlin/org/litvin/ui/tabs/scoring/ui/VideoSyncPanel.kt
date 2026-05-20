package org.litvin.ui.tabs.scoring.ui

import org.litvin.ui.UiStyles
import org.litvin.ui.tabs.scoring.ScoringActions
import org.litvin.ui.tabs.scoring.ScoringViewState
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder
import kotlin.math.abs

/**
 * VideoSyncPanel (E-SC-001 T7)
 *
 * Purpose
 * - Leaf Swing component hosting a compact transport/timecode sync cluster for the Scoring tab.
 *   Provides: −10s, −1s, Play/Pause, +1s, +10s and a simple speed selector.
 *
 * Contracts
 * - Emits callbacks strictly via [org.litvin.ui.tabs.scoring.ScoringActions]; no direct media/player references.
 * - Optionally consumes immutable snapshots via [render] to reflect play/pause icon and speed.
 * - Does not depend on domain/media services; styles are delegated to [org.litvin.ui.UiStyles].
 */
class VideoSyncPanel(
    private val actions: ScoringActions,
) : JPanel(BorderLayout()) {

    private val playPauseBtn: JButton
    private val speedCombo: JComboBox<String>

    // Internal guard to avoid feedback loop when updating speed from state
    private var updatingFromState: Boolean = false

    // Local presets kept in UI (do not couple to SessionSettings here)
    private val speedPresets: FloatArray = floatArrayOf(2.0f, 1.0f, 0.5f, 0.25f, 0.1f)

    init {
        isOpaque = true
        background = Color(0x12, 0x12, 0x12)
        border = EmptyBorder(6, 8, 6, 8)

        // Transport controls row
        val transport = JPanel()
        transport.layout = BoxLayout(transport, BoxLayout.X_AXIS)
        transport.isOpaque = false

        fun styleSeek(b: JButton) {
            UiStyles.styleSecondary(b)
            b.iconTextGap = 6
            b.preferredSize = Dimension(100, 44)
            b.minimumSize = Dimension(100, 40)
        }
        fun styleSeekLarge(b: JButton) {
            UiStyles.styleSecondary(b)
            b.iconTextGap = 6
            b.preferredSize = Dimension(150, 44)
            b.minimumSize = Dimension(120, 40)
        }

        val btnSeekBack10 = JButton()
        val btnSeekBack1 = JButton()
        val btnSeekFwd1 = JButton()
        val btnSeekFwd10 = JButton()
        styleSeekLarge(btnSeekBack10); styleSeek(btnSeekBack1); styleSeek(btnSeekFwd1); styleSeekLarge(btnSeekFwd10)
        btnSeekBack10.icon = UiStyles.seekIcon(false, 18); btnSeekBack10.text = "-10s [shift+←]"
        btnSeekBack1.icon = UiStyles.seekIcon(false, 18); btnSeekBack1.text = "-1s [←]"
        btnSeekFwd1.icon = UiStyles.seekIcon(true, 18); btnSeekFwd1.text = "+1s [→]"
        btnSeekFwd10.icon = UiStyles.seekIcon(true, 18); btnSeekFwd10.text = "+10s [shift+→]"
        try {
            btnSeekBack10.accessibleContext.accessibleName = "Seek back 10 seconds"
            btnSeekBack1.accessibleContext.accessibleName = "Seek back 1 second"
            btnSeekFwd1.accessibleContext.accessibleName = "Seek forward 1 second"
            btnSeekFwd10.accessibleContext.accessibleName = "Seek forward 10 seconds"
        } catch (_: Throwable) {}
        btnSeekBack10.horizontalTextPosition = SwingConstants.RIGHT
        btnSeekBack1.horizontalTextPosition = SwingConstants.RIGHT
        btnSeekFwd1.horizontalTextPosition = SwingConstants.LEFT
        btnSeekFwd10.horizontalTextPosition = SwingConstants.LEFT
        // Wire actions
        btnSeekBack10.addActionListener { actions.seekBy(-10_000) }
        btnSeekBack1.addActionListener { actions.seekBy(-1_000) }
        btnSeekFwd1.addActionListener { actions.seekBy(1_000) }
        btnSeekFwd10.addActionListener { actions.seekBy(10_000) }

        playPauseBtn = UiStyles.squarePrimaryButton(UiStyles.playIcon(28)) { actions.playPause() }
        try {
            playPauseBtn.name = "play-pause"
            playPauseBtn.accessibleContext.accessibleName = "Play or Pause"
            playPauseBtn.toolTipText = "SPACE — Play/Pause"
        } catch (_: Throwable) {}

        // Assemble row similar to Markup
        transport.add(btnSeekBack10); transport.add(Box.createHorizontalStrut(6))
        transport.add(btnSeekBack1); transport.add(Box.createHorizontalStrut(12))
        transport.add(playPauseBtn); transport.add(Box.createHorizontalStrut(12))
        transport.add(btnSeekFwd1); transport.add(Box.createHorizontalStrut(6))
        transport.add(btnSeekFwd10)

        // Speed row
        val speedRow = JPanel(FlowLayout(FlowLayout.CENTER, 8, 0))
        speedRow.isOpaque = false
        val speedLabels = arrayOf("2×", "1×", "0.5×", "0.25×", "0.1×")
        speedCombo = JComboBox(speedLabels)
        speedCombo.isFocusable = true
        speedCombo.name = "speed-dropdown"
        try { speedCombo.accessibleContext.accessibleName = "Playback speed" } catch (_: Throwable) {}
        speedCombo.toolTipText = "Use ↑/↓ to change speed"
        speedCombo.addActionListener {
            if (updatingFromState) return@addActionListener
            val idx = speedCombo.selectedIndex.coerceIn(0, speedPresets.lastIndex)
            actions.setSpeedMultiplier(speedPresets[idx])
        }
        speedRow.add(speedCombo)
        speedRow.add(JLabel("↑ / ↓ speed").apply {
            foreground = Color(0xAD, 0xAA, 0xAA)
            font = font.deriveFont(10f)
            toolTipText = "Use ↑/↓ to change speed"
        })

        val center = JPanel()
        center.layout = BoxLayout(center, BoxLayout.Y_AXIS)
        center.isOpaque = false
        center.add(transport)
        center.add(Box.createVerticalStrut(2))
        center.add(speedRow)

        add(center, BorderLayout.CENTER)
    }

    /**
     * Update the transport visuals from a [org.litvin.ui.tabs.scoring.ScoringViewState] snapshot.
     * - Sets play/pause icon and tooltip.
     * - Adjusts speed dropdown to the closest preset.
     */
    fun render(state: ScoringViewState) {
        try {
            playPauseBtn.icon = if (state.isPlaying) UiStyles.pauseIcon(28) else UiStyles.playIcon(28)
            playPauseBtn.toolTipText = if (state.isPlaying) "SPACE — Pause" else "SPACE — Play"
        } catch (_: Throwable) {}
        // Map multiplier to nearest preset index
        val m = state.speedMultiplier
        var best = 0
        var bestDiff = Float.MAX_VALUE
        for (i in speedPresets.indices) {
            val d = abs(speedPresets[i] - m)
            if (d < bestDiff) { bestDiff = d; best = i }
        }
        try {
            updatingFromState = true
            speedCombo.selectedIndex = best
        } catch (_: Throwable) { /* ignore */ } finally {
            updatingFromState = false
        }
    }
}