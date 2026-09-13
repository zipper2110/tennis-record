package org.litvin.ui.tabs.markup.ui

import org.litvin.ui.UiStyles
import org.litvin.ui.commons.TransportBar
import org.litvin.ui.tabs.markup.MarkupActions
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
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

    private val btnStart = JButton("Point Start [C]").apply { name = "rallies-point-start" }
    private val btnEnd = JButton("Point End [V]").apply { name = "rallies-point-end" }
    private val btnComment = JButton("Add comment").apply { name = "rallies-add-comment" }
    private val timeLabel = JLabel("00:00:00.000").apply { name = "rallies-current-time" }

    private val transportBar = TransportBar(
        onTogglePlayPause = { actions.togglePlayPause() },
        onSeek = { delta -> onNudge(delta) },
        playPauseComponentName = "rallies-play-pause",
    )

    init {
        isOpaque = false

        // Style point boundary buttons
        listOf(btnStart, btnEnd, btnComment).forEach { UiStyles.styleSecondary(it) }
        btnStart.addActionListener { actions.setStartAtPlayhead() }
        btnEnd.addActionListener { actions.setEndAtPlayhead() }
        btnComment.addActionListener { actions.addCommentAtPlayhead() }

        val pointActions = JPanel()
        pointActions.name = "markup-point-actions"
        pointActions.isOpaque = false
        pointActions.layout = BoxLayout(pointActions, BoxLayout.X_AXIS)
        pointActions.add(btnStart)
        pointActions.add(Box.createHorizontalStrut(8))
        pointActions.add(btnEnd)
        pointActions.add(Box.createHorizontalStrut(8))
        pointActions.add(btnComment)

        transportBar.name = "markup-video-controls"

        val timePanel = JPanel()
        timePanel.name = "markup-current-time"
        timePanel.isOpaque = false
        timePanel.layout = BoxLayout(timePanel, BoxLayout.X_AXIS)
        val currentTimeLabel = JLabel("Current time:")
        currentTimeLabel.foreground = UiStyles.FG_SECONDARY
        timeLabel.foreground = UiStyles.FG_PRIMARY
        timeLabel.preferredSize = Dimension(100, 24)
        timePanel.add(Box.createHorizontalGlue())
        timePanel.add(currentTimeLabel)
        timePanel.add(Box.createHorizontalStrut(6))
        timePanel.add(timeLabel)

        // Matching side widths keep the transport group centered on the full panel.
        val sideWidth = maxOf(pointActions.preferredSize.width, timePanel.preferredSize.width)
        pointActions.preferredSize = Dimension(sideWidth, pointActions.preferredSize.height)
        timePanel.preferredSize = Dimension(sideWidth, timePanel.preferredSize.height)

        val bar = CenteredTransportPanel(pointActions, transportBar, timePanel, sideWidth)

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
        timeLabel.text = text
        timeLabel.repaint()
    }
}

private class CenteredTransportPanel(
    private val left: Component,
    private val center: Component,
    private val right: Component,
    private val sideWidth: Int,
) : JPanel(null) {
    init {
        isOpaque = false
        add(left)
        add(center)
        add(right)
    }

    override fun doLayout() {
        val contentWidth = width
        val contentHeight = height
        val centerSize = center.preferredSize

        left.setBounds(0, centeredY(left, contentHeight), sideWidth, left.preferredSize.height)
        center.setBounds(
            (contentWidth - centerSize.width) / 2,
            centeredY(center, contentHeight),
            centerSize.width,
            centerSize.height,
        )
        right.setBounds(
            contentWidth - sideWidth,
            centeredY(right, contentHeight),
            sideWidth,
            right.preferredSize.height,
        )
    }

    override fun getPreferredSize(): Dimension {
        val height = maxOf(left.preferredSize.height, center.preferredSize.height, right.preferredSize.height)
        return Dimension(sideWidth * 2 + center.preferredSize.width + 24, height)
    }

    private fun centeredY(component: Component, contentHeight: Int): Int =
        (contentHeight - component.preferredSize.height) / 2
}
