package org.litvin.ui.commons

import org.litvin.ui.UiStyles
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.event.ActionListener
import javax.swing.*

/**
 * Shared transport controls strip: seek back/forward and play/pause.
 *
 * API:
 * - onTogglePlayPause: called when the central button is clicked.
 * - onSeek(deltaMs): called when one of the seek buttons is clicked.
 * - setPlaying(playing): updates the play/pause icon and tooltip.
 */
class TransportBar(
    private val onTogglePlayPause: () -> Unit,
    private val onSeek: (deltaMs: Long) -> Unit,
    playPauseComponentName: String? = null,
) : JPanel() {

    private val btnSeekBack10 = JButton()
    private val btnSeekBack1 = JButton()
    private val btnSeekFwd1 = JButton()
    private val btnSeekFwd10 = JButton()
    private val btnPlayPause = UiStyles.squarePrimaryButton(UiStyles.playIcon(28)) {
        onTogglePlayPause()
    }
    private val seekButtons = listOf(btnSeekBack10, btnSeekBack1, btnSeekFwd1, btnSeekFwd10)

    /**
     * Compact mode shows only the seek amounts ("-5s") and sizes the buttons to their content.
     * The tooltips still name the hotkeys. Use it when the full labels do not fit.
     */
    var compact: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            applyLabels()
        }

    /** Preferred width with the full labels, measured at construction. */
    val fullPreferredWidth: Int

    /** Preferred width with the compact labels, measured at construction. */
    val compactPreferredWidth: Int

    init {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        btnPlayPause.name = playPauseComponentName

        seekButtons.forEach { b ->
            UiStyles.styleSecondary(b)
            b.iconTextGap = 6
        }
        applyLabels()

        btnSeekBack10.icon = UiStyles.backward5Icon();
        btnSeekBack1.icon = UiStyles.seekLeftIcon();
        btnSeekFwd1.icon = UiStyles.seekRightIcon();
        btnSeekFwd10.icon = UiStyles.forward5Icon();

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

        compact = true
        compactPreferredWidth = preferredSize.width
        compact = false
        fullPreferredWidth = preferredSize.width
    }

    private fun applyLabels() {
        val labels = if (compact) COMPACT_LABELS else FULL_LABELS
        seekButtons.forEachIndexed { i, b ->
            b.text = labels[i]
            if (compact) {
                b.preferredSize = null
                b.minimumSize = null
            } else {
                val large = b === btnSeekBack10 || b === btnSeekFwd10
                b.preferredSize = Dimension(if (large) 150 else 100, 44)
                b.minimumSize = Dimension(if (large) 120 else 100, 40)
            }
        }
        // invalidate() clears the BoxLayout size cache also before this bar has a parent;
        // revalidate() does nothing without a parent.
        invalidate()
        revalidate()
    }

    private fun seekHandler(delta: Long): ActionListener = ActionListener {
        onSeek(delta)
    }

    fun setPlaying(playing: Boolean) {
        btnPlayPause.icon = if (playing) UiStyles.pauseIcon(28) else UiStyles.playIcon(28)
        btnPlayPause.toolTipText = if (playing) "SPACE — Pause" else "SPACE — Play"
        btnPlayPause.repaint()
    }

    private companion object {
        // Order matches seekButtons: -5s, -1s, +1s, +5s
        val FULL_LABELS = listOf("-5s [shift+←]", "-1s [←]", "+1s [→]", "+5s [shift+→]")
        val COMPACT_LABELS = listOf("-5s", "-1s", "+1s", "+5s")
    }
}
