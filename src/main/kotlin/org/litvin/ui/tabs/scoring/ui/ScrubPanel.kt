package org.litvin.ui.tabs.scoring.ui

import org.litvin.shared.util.Timecode
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.border.EmptyBorder
import javax.swing.event.ChangeListener

/**
 * ScrubPanel — per-point scrub bar with time labels.
 *
 * Interaction:
 * - onUserScrub callback is invoked with absolute target time in ms when user changes the slider.
 */
class ScrubPanel(
    private val onUserScrub: (targetMs: Long) -> Unit
) : JPanel(BorderLayout(8, 0)) {

    private val segmentStartLabel = JLabel("00:00:00")
    private val segmentNowLabel = JLabel("00:00:00  ")
    private val scrubSlider = JSlider(0, 100, 0)
    private val segDescrLabel = JLabel("Point segment")
    private val rightBox = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))

    private val errorLabel = JLabel("Invalid point duration — fix on Markup tab")

    private var startMs: Long = 0L
    private var endMs: Long = 0L
    private var isUpdatingFromExternal = false

    init {
        border = EmptyBorder(8, 16, 8, 16)
        background = Color(0x11, 0x11, 0x11)

        // Left time label
        segmentStartLabel.font = Font(Font.MONOSPACED, Font.PLAIN, 10)
        segmentStartLabel.foreground = Color(0xAD, 0xAA, 0xAA)

        // Slider
        scrubSlider.name = "scrub"
        scrubSlider.toolTipText = "Scrub within the selected point segment"
        scrubSlider.background = background
        scrubSlider.addChangeListener(ChangeListener {
            if (isUpdatingFromExternal) return@ChangeListener
            if (endMs <= startMs) return@ChangeListener
            val frac = scrubSlider.value / 100.0
            var target = startMs + ((endMs - startMs) * frac).toLong()
            val maxPlayable = (endMs - 1).coerceAtLeast(startMs)
            if (target > maxPlayable) target = maxPlayable
            onUserScrub(target)
            // Reflect the new position immediately
            setPosition(target)
        })

        // Right side labels
        segDescrLabel.font = segDescrLabel.font.deriveFont(9f)
        segDescrLabel.foreground = Color(150, 150, 150)

        segmentNowLabel.font = Font(Font.MONOSPACED, Font.PLAIN, 10)
        segmentNowLabel.foreground = Color(0xAD, 0xAA, 0xAA)

        rightBox.isOpaque = false
        rightBox.add(segmentNowLabel)
        rightBox.add(segDescrLabel)

        errorLabel.foreground = Color(0xFF, 0x66, 0x66)
        errorLabel.font = segDescrLabel.font.deriveFont(Font.BOLD, 10f)
        errorLabel.isVisible = false
        rightBox.add(errorLabel)

        add(segmentStartLabel, BorderLayout.WEST)
        add(scrubSlider, BorderLayout.CENTER)
        add(rightBox, BorderLayout.EAST)
    }

    fun setSegment(startMs: Long, endMs: Long) {
        this.startMs = startMs
        this.endMs = endMs
        segmentStartLabel.text = Timecode.format(startMs).substring(0, 8)
        // Right label shows the end of segment (not current position)
        segmentNowLabel.text = Timecode.format(endMs).substring(0, 8) + "  "
        setPosition(startMs)
    }

    fun setPosition(absMs: Long) {
        if (endMs > startMs) {
            val rel = (absMs - startMs).coerceAtLeast(0L)
            val dur = (endMs - startMs).coerceAtLeast(1L)
            val frac = (rel.toDouble() / dur.toDouble()).coerceIn(0.0, 0.999)
            val sliderVal = (frac * 100).toInt().coerceIn(0, 100)
            isUpdatingFromExternal = true
            try {
                scrubSlider.value = sliderVal
            } finally {
                isUpdatingFromExternal = false
            }
            // Keep right label showing segment end
            segmentNowLabel.text = Timecode.format(endMs).substring(0, 8) + "  "
        } else {
            isUpdatingFromExternal = true
            try {
                scrubSlider.value = 0
            } finally {
                isUpdatingFromExternal = false
            }
            segmentNowLabel.text = "00:00:00  "
        }
    }

    fun setErrorVisible(visible: Boolean) {
        errorLabel.isVisible = visible
        // Also tint background subtly if error to hint at invalid segment
        background = if (visible) {
            Color(0x18, 0x10, 0x10)
        } else {
            Color(0x11, 0x11, 0x11)
        }
        // ensure child inherits new bg for slider track area
        scrubSlider.background = background
        repaint()
    }

    fun reset() {
        setSegment(0L, 0L)
        setErrorVisible(false)
    }
}
