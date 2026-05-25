package org.litvin.ui.tabs.markup.ui

import org.litvin.ui.UiStyles
import org.litvin.ui.commons.TransportBar
import org.litvin.ui.tabs.markup.MarkupActions
import java.awt.BorderLayout
import java.awt.Container
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

/**
 * TransportControls — play/pause + time, with Start/End and Jump actions for the Markup tab.
 *
 * Responsibilities:
 * - Render a shared TransportBar (seek/back/forward + play/pause + time label)
 * - Expose setPlaying() and setTimeText() to update UI from container state
 * - Surface buttons for Start at Playhead, End at Playhead, and Jump to Selected point
 * - Emit all actions strictly via MarkupActions and the provided onNudge(deltaMs) callback
 */
class TransportControls(
    private val actions: MarkupActions,
    /** Called when user clicks one of the seek buttons; deltaMs can be negative. */
    private val onNudge: (deltaMs: Long) -> Unit,
) : JPanel(BorderLayout()) {

    private val btnStart = JButton("Point Start [C]")
    private val btnEnd = JButton("Point End [V]")

    private val transportBar = TransportBar(
        onTogglePlayPause = { actions.togglePlayPause() },
        onSeek = { delta -> onNudge(delta) }
    )

    init {
        isOpaque = false

        // Style point boundary buttons
        listOf(btnStart, btnEnd).forEach { UiStyles.styleSecondary(it) }
        btnStart.addActionListener { actions.setStartAtPlayhead() }
        btnEnd.addActionListener { actions.setEndAtPlayhead() }

        val bar = JPanel()
        bar.isOpaque = false
        bar.layout = BoxLayout(bar, BoxLayout.X_AXIS)

        // Left: Start/End
        bar.add(btnStart)
        bar.add(Box.createHorizontalStrut(8))
        bar.add(btnEnd)

        // Center: shared transport bar
        bar.add(Box.createHorizontalGlue())
        bar.add(transportBar)
        bar.add(Box.createHorizontalGlue())

        val controlsBar = JPanel(BorderLayout())
        controlsBar.isOpaque = true
        controlsBar.background = UiStyles.SURFACE_HIGH
        controlsBar.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UiStyles.CARD_BORDER),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        )
        controlsBar.add(bar, BorderLayout.CENTER)

        add(controlsBar, BorderLayout.CENTER)
    }

    fun setPlaying(playing: Boolean) {
        transportBar.setPlaying(playing)
    }

    fun setTimeText(text: String) {
        transportBar.setTimeText(text)
    }
}