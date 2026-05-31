package org.litvin.ui.tabs.scoring.ui

import org.litvin.ui.commons.ScrubBar
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/**
 * Per-point scoring scrub wrapper.
 *
 * Shared time labels, exact click/drag seeking, and slider painting live in ScrubBar.
 * This class adds only scoring-specific segment copy.
 */
class ScrubPanel(
    private val onUserScrub: (targetMs: Long) -> Unit
) : JPanel(BorderLayout(8, 0)) {
    private val segDescrLabel = JLabel("Point segment")
    private val rightAccessory = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
    private val scrubBar = ScrubBar(
        onUserScrub = { target -> onUserScrub(target) },
        tooltip = "Scrub within the selected point segment",
        rightAccessory = rightAccessory
    )

    init {
        border = EmptyBorder(8, 16, 8, 16)
        background = Color(0x11, 0x11, 0x11)

        segDescrLabel.font = segDescrLabel.font.deriveFont(9f)
        segDescrLabel.foreground = Color(150, 150, 150)

        rightAccessory.isOpaque = false
        rightAccessory.add(segDescrLabel)

        scrubBar.border = EmptyBorder(0, 0, 0, 0)
        scrubBar.setBarBackground(background)
        add(scrubBar, BorderLayout.CENTER)
    }

    fun setSegment(startMs: Long, endMs: Long) {
        scrubBar.setRange(startMs, endMs)
        scrubBar.setPosition(startMs)
    }

    fun setPosition(absMs: Long) {
        scrubBar.setPosition(absMs)
    }

    fun reset() {
        scrubBar.reset()
    }
}
