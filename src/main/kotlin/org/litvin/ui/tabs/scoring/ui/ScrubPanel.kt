package org.litvin.ui.tabs.scoring.ui

import org.litvin.shared.util.Timecode
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Dimension
import java.awt.RenderingHints
import javax.swing.BorderFactory
import kotlin.math.min
import org.litvin.ui.UiStyles
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.border.EmptyBorder
import javax.swing.event.ChangeListener
import javax.swing.plaf.basic.BasicSliderUI
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlin.math.roundToInt

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
    private val scrubSlider = JSlider(0, 10000, 0)
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
        scrubSlider.isOpaque = false
        scrubSlider.paintTicks = false
        scrubSlider.snapToTicks = false
        // Paint remaining part in green via custom UI
        scrubSlider.ui = DualColorSliderUI(
            scrubSlider,
            baseColor = Color(0x33, 0xAA, 0x55),
            remainingColor = Color(0x88, 0x88, 0x88)
        )
        // Jump to exact click position and update continuously while dragging
        val updateFromMouse: (MouseEvent) -> Unit = { e ->
            val ui = scrubSlider.ui
            if (ui is DualColorSliderUI) {
                val newVal = if (scrubSlider.orientation == JSlider.HORIZONTAL) ui.valueForX(e.x) else ui.valueForY(e.y)
                val bounded = newVal.coerceIn(scrubSlider.minimum, scrubSlider.maximum)
                scrubSlider.value = bounded
            }
        }
        scrubSlider.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                updateFromMouse(e)
            }
        })
        scrubSlider.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                updateFromMouse(e)
            }
        })
        scrubSlider.addChangeListener(ChangeListener {
            if (isUpdatingFromExternal) return@ChangeListener
            if (endMs <= startMs) return@ChangeListener
            val frac = scrubSlider.value.toDouble() / scrubSlider.maximum.toDouble()
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
            val max = scrubSlider.maximum.coerceAtLeast(1)
            val sliderVal = (frac * max).toInt().coerceIn(0, max)
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

/**
 * Custom slider UI that paints the elapsed part (left of thumb) in the slider's `foreground`
 * and the remaining part (right of thumb) in `remainingColor`.
 */
class DualColorSliderUI(
    slider: JSlider,
    private val baseColor: Color,
    private val remainingColor: Color,
) : BasicSliderUI(slider) {

    private val trackHeight = 4
    private val trackArc = 4

    override fun paintTrack(g: Graphics) {
        val g2 = g as Graphics2D
        val old = g2.create()
        try {
            val r: Rectangle = trackRect
            // Base track background (subtle dark line)
            g2.color = baseColor
            val baseY = r.y + (r.height - trackHeight) / 2
            g2.fillRoundRect(r.x, baseY, r.width, trackHeight, trackArc, trackArc)

            // Compute thumb center
            val thumbCenterX = thumbRect.x + thumbRect.width / 2
            val leftWidth = (thumbCenterX - r.x).coerceIn(0, r.width)
            val rightStart = (r.x + leftWidth).coerceIn(r.x, r.x + r.width)
            val rightWidth = (r.x + r.width - rightStart).coerceAtLeast(0)

            // Elapsed (left) uses slider.foreground
            g2.color = baseColor
            g2.fillRoundRect(r.x, baseY, trackHeight, trackHeight, trackHeight, trackHeight)

            g2.color = remainingColor
            g2.fillRect(rightStart, baseY, rightWidth, trackHeight)
        } finally {
            old.dispose()
        }
    }

    override fun paintFocus(g: Graphics?) {
        // no default focus ring
    }

    override fun getThumbSize(): Dimension {
        val d = 12
        return Dimension(d, d)
    }

    override fun paintThumb(g: Graphics) {
        val g2 = g as Graphics2D
        val oldAA = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        try {
            val r = thumbRect
            val size = min(r.width, r.height)
            val x = r.x + (r.width - size) / 2
            val y = r.y + (r.height - size) / 2
            g2.color = UiStyles.GREEN
            g2.fillOval(x, y, size, size)
        } finally {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA)
        }
    }

    // Expose helpers so outer code can compute values from pixel coords precisely
    fun valueForX(x: Int): Int = valueForXPosition(x)
    fun valueForY(y: Int): Int = valueForYPosition(y)
}
