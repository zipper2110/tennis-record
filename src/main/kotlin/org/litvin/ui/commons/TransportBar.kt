package org.litvin.ui.commons

import org.litvin.ui.UiStyles
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.event.ActionListener
import javax.swing.*

/**
 * Shared transport controls strip: seek back/forward, play/pause, and time label.
 *
 * API:
 * - onTogglePlayPause: called when the central button is clicked.
 * - onSeek(deltaMs): called when one of the seek buttons is clicked.
 * - setPlaying(playing): updates the play/pause icon and tooltip.
 * - setTimeText(text): updates the time label on the right.
 */
class TransportBar(
    private val onTogglePlayPause: () -> Unit,
    private val onSeek: (deltaMs: Long) -> Unit
) : JPanel() {

    private val btnSeekBack10 = JButton()
    private val btnSeekBack1 = JButton()
    private val btnSeekFwd1 = JButton()
    private val btnSeekFwd10 = JButton()
    private val btnPlayPause = UiStyles.squarePrimaryButton(UiStyles.playIcon(28)) {
        onTogglePlayPause()
    }

    private val timeLabel = JLabel("00:00:00.000")

    init {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)

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
        styleSeekLarge(btnSeekBack10); styleSeek(btnSeekBack1); styleSeek(btnSeekFwd1); styleSeekLarge(btnSeekFwd10)

        btnSeekBack10.icon = UiStyles.backward5Icon();
        btnSeekBack10.text = "-5s [shift+←]"
        btnSeekBack1.icon = UiStyles.seekLeftIcon();
        btnSeekBack1.text = "-1s [←]"
        btnSeekFwd1.icon = UiStyles.seekRightIcon();
        btnSeekFwd1.text = "+1s [→]"
        btnSeekFwd10.icon = UiStyles.forward5Icon();
        btnSeekFwd10.text = "+5s [shift+→]"

        btnSeekBack10.toolTipText = "Shift+Left"
        btnSeekBack1.toolTipText = "Left"
        btnSeekFwd1.toolTipText = "Right"
        btnSeekFwd10.toolTipText = "Shift+Right"

        btnSeekBack10.horizontalTextPosition = SwingConstants.RIGHT
        btnSeekBack1.horizontalTextPosition = SwingConstants.RIGHT
        btnSeekFwd1.horizontalTextPosition = SwingConstants.LEFT
        btnSeekFwd10.horizontalTextPosition = SwingConstants.LEFT

        btnSeekBack10.addActionListener(seekHandler(-5_000))
        btnSeekBack1.addActionListener(seekHandler(-1_000))
        btnSeekFwd1.addActionListener(seekHandler(+1_000))
        btnSeekFwd10.addActionListener(seekHandler(+5_000))

        // Middle group: seeks + play square button
        add(btnSeekBack10); add(Box.createHorizontalStrut(6))
        add(btnSeekBack1); add(Box.createHorizontalStrut(12))
        add(btnPlayPause); add(Box.createHorizontalStrut(12))
        add(btnSeekFwd1); add(Box.createHorizontalStrut(6))
        add(btnSeekFwd10)

        add(Box.createHorizontalStrut(16))
        add(Box.createHorizontalGlue())

        // Right group: time label
        val timePanel = JPanel()
        timePanel.isOpaque = false
        val lblTime = JLabel("Time:")
        lblTime.foreground = UiStyles.FG_SECONDARY
        timeLabel.foreground = UiStyles.FG_PRIMARY
        timeLabel.preferredSize = Dimension(100, 24)
        timePanel.add(lblTime)
        timePanel.add(Box.createHorizontalStrut(6))
        timePanel.add(timeLabel)
        add(timePanel)
    }

    private fun seekHandler(delta: Long): ActionListener = ActionListener {
        onSeek(delta)
    }

    fun setPlaying(playing: Boolean) {
        btnPlayPause.icon = if (playing) UiStyles.pauseIcon(28) else UiStyles.playIcon(28)
        btnPlayPause.toolTipText = if (playing) "SPACE — Pause" else "SPACE — Play"
        btnPlayPause.repaint()
    }

    fun setTimeText(text: String) {
        timeLabel.text = text
        timeLabel.repaint()
    }
}
