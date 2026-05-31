package org.litvin.ui.commons

import org.litvin.shared.util.Timecode
import org.litvin.ui.UiStyles
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.plaf.basic.BasicSliderUI
import kotlin.math.min

class ScrubBar(
    private val onUserScrub: (targetMs: Long) -> Unit,
    tooltip: String = "Seek",
    private val rightAccessory: JComponent? = null,
    sliderMaximum: Int = 10000,
) : JPanel(BorderLayout(8, 0)) {
    private val startLabel = JLabel("00:00:00")
    private val endLabel = JLabel("00:00:00  ")
    private val slider = JSlider(0, sliderMaximum, 0)
    private val rightBox = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))

    private var startMs: Long = 0L
    private var endMs: Long = 0L
    private var isUpdatingFromExternal = false

    init {
        background = Color(0x11, 0x11, 0x11)

        listOf(startLabel, endLabel).forEach { label ->
            label.font = Font(Font.MONOSPACED, Font.PLAIN, 10)
            label.foreground = Color(0xAD, 0xAA, 0xAA)
        }

        slider.name = "scrub"
        slider.toolTipText = tooltip
        slider.isOpaque = false
        slider.paintTicks = false
        slider.snapToTicks = false
        slider.ui = DualColorSliderUI(
            slider,
            baseColor = Color(0x33, 0xAA, 0x55),
            remainingColor = Color(0x88, 0x88, 0x88)
        )

        val updateFromMouse: (MouseEvent) -> Unit = { e ->
            val ui = slider.ui
            if (ui is DualColorSliderUI) {
                val newValue = if (slider.orientation == JSlider.HORIZONTAL) ui.valueForX(e.x) else ui.valueForY(e.y)
                slider.value = newValue.coerceIn(slider.minimum, slider.maximum)
            }
        }
        slider.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                updateFromMouse(e)
            }
        })
        slider.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                updateFromMouse(e)
            }
        })
        slider.addChangeListener {
            if (isUpdatingFromExternal) return@addChangeListener
            if (endMs <= startMs) return@addChangeListener
            val target = valueToMs(slider.value)
            onUserScrub(target)
            setPosition(target)
        }

        rightBox.isOpaque = false
        rightBox.add(endLabel)
        rightAccessory?.let { rightBox.add(it) }

        add(startLabel, BorderLayout.WEST)
        add(slider, BorderLayout.CENTER)
        add(rightBox, BorderLayout.EAST)
    }

    fun setRange(startMs: Long, endMs: Long) {
        val previousStart = this.startMs
        val previousEnd = this.endMs
        this.startMs = startMs.coerceAtLeast(0L)
        this.endMs = endMs.coerceAtLeast(this.startMs)
        startLabel.text = formatTime(this.startMs)
        endLabel.text = formatTime(this.endMs) + "  "
        if (previousStart != this.startMs || previousEnd != this.endMs) {
            setPosition(currentPositionMs().coerceIn(this.startMs, playableEndMs()))
        }
    }

    fun setPosition(absMs: Long) {
        val value = if (endMs > startMs) {
            val rel = (absMs - startMs).coerceAtLeast(0L)
            val dur = (endMs - startMs).coerceAtLeast(1L)
            val frac = (rel.toDouble() / dur.toDouble()).coerceIn(0.0, 0.999)
            (frac * slider.maximum).toInt().coerceIn(0, slider.maximum)
        } else {
            0
        }

        isUpdatingFromExternal = true
        try {
            slider.value = value
        } finally {
            isUpdatingFromExternal = false
        }
        endLabel.text = formatTime(endMs) + "  "
    }

    fun reset() {
        setRange(0L, 0L)
        setPosition(0L)
    }

    fun setBarBackground(color: Color) {
        background = color
        slider.background = color
        repaint()
    }

    private fun valueToMs(value: Int): Long {
        val ratio = value.toDouble() / slider.maximum.coerceAtLeast(1).toDouble()
        val target = startMs + ((endMs - startMs).toDouble() * ratio).toLong()
        return target.coerceIn(startMs, playableEndMs())
    }

    private fun currentPositionMs(): Long = valueToMs(slider.value)

    private fun playableEndMs(): Long = if (endMs > startMs) (endMs - 1).coerceAtLeast(startMs) else startMs

    private fun formatTime(ms: Long): String = Timecode.format(ms.coerceAtLeast(0L)).substring(0, 8)
}

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
            val baseY = r.y + (r.height - trackHeight) / 2
            val thumbCenterX = thumbRect.x + thumbRect.width / 2
            val leftWidth = (thumbCenterX - r.x).coerceIn(0, r.width)
            val rightStart = (r.x + leftWidth).coerceIn(r.x, r.x + r.width)
            val rightWidth = (r.x + r.width - rightStart).coerceAtLeast(0)

            g2.color = baseColor
            g2.fillRoundRect(r.x, baseY, leftWidth, trackHeight, trackArc, trackArc)

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

    fun valueForX(x: Int): Int = valueForXPosition(x)
    fun valueForY(y: Int): Int = valueForYPosition(y)
}
