package org.litvin.ui.tabs.crop

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.CropGeometryMath
import org.litvin.ui.UiStyles
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class CropTransformControls(
    private val onChanged: (AdjustmentsV1) -> Unit,
    private val onResetTransform: () -> Unit,
) : JPanel(BorderLayout()) {
    private val zoomSlider = JSlider(0, 200, 100).apply {
        name = "adj-zoom"
        toolTipText = "Zoom"
    }
    private val panXSlider = JSlider(-100, 100, 0).apply {
        name = "adj-pan-x"
        toolTipText = "Pan X"
    }
    private val panYSlider = JSlider(-100, 100, 0).apply {
        name = "adj-pan-y"
        toolTipText = "Pan Y"
    }
    private val rotationSlider = JSlider(-360, 360, 0).apply {
        name = "adj-rot"
        toolTipText = "Rotation"
    }

    private var updating = false
    private var current = AdjustmentsV1()

    init {
        isOpaque = true
        background = UiStyles.DARK_BG

        val content = object : JPanel(), Scrollable {
            override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
            override fun getScrollableUnitIncrement(visibleRect: java.awt.Rectangle, orientation: Int, direction: Int) = 24
            override fun getScrollableBlockIncrement(visibleRect: java.awt.Rectangle, orientation: Int, direction: Int) =
                visibleRect.height - 24
            override fun getScrollableTracksViewportWidth() = true
            override fun getScrollableTracksViewportHeight() = false
        }.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = true
            background = UiStyles.DARK_BG
            border = BorderFactory.createEmptyBorder(15, 15, 15, 15)
        }

        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, 0, 18, 0)
            maximumSize = Dimension(Int.MAX_VALUE, 48)
            val title = JLabel("Transform").apply {
                foreground = UiStyles.FG_PRIMARY
                font = font.deriveFont(font.style, font.size2D + 3.0f)
            }
            val reset = JButton("Reset").apply {
                name = "adj-transform-reset"
                toolTipText = "Reset Transform"
                addActionListener { onResetTransform() }
            }
            add(title, BorderLayout.WEST)
            add(reset, BorderLayout.EAST)
        }
        content.add(header)
        content.add(sliderRow("Zoom", zoomSlider, "%", ::zoomSliderToModel, ::modelZoomToSlider))
        content.add(sliderRow("Pan X", panXSlider, "", { it / 100.0f }, { (it.panX * 100.0f).toInt() }))
        content.add(sliderRow("Pan Y", panYSlider, "", { it / 100.0f }, { (it.panY * 100.0f).toInt() }))
        content.add(sliderRow("Rotation", rotationSlider, "deg", { it / 2.0f }, { (it.rotationDeg * 2.0f).toInt() }))
        content.add(Box.createVerticalGlue())

        val scroll = JScrollPane(content).apply {
            border = BorderFactory.createEmptyBorder()
            background = UiStyles.DARK_BG
            viewport.background = UiStyles.DARK_BG
        }
        add(scroll, BorderLayout.CENTER)
    }

    fun render(adjustments: AdjustmentsV1) {
        updating = true
        try {
            current = adjustments
            zoomSlider.value = modelZoomToSlider(adjustments).coerceIn(zoomSlider.minimum, zoomSlider.maximum)
            panXSlider.value = (adjustments.panX * 100.0f).toInt().coerceIn(-100, 100)
            panYSlider.value = (adjustments.panY * 100.0f).toInt().coerceIn(-100, 100)
            rotationSlider.value = (adjustments.rotationDeg * 2.0f).toInt().coerceIn(-360, 360)
        } finally {
            updating = false
        }
    }

    private fun sliderRow(
        title: String,
        slider: JSlider,
        suffix: String,
        sliderToValue: (Int) -> Float,
        modelToSlider: (AdjustmentsV1) -> Int,
    ): JPanel {
        slider.putClientProperty("JSlider.isFilled", true)
        val label = JLabel(title).apply { UiStyles.styleHelper(this) }
        val readout = JTextField(6).apply {
            horizontalAlignment = SwingConstants.RIGHT
            foreground = UiStyles.FG_PRIMARY
            background = UiStyles.SURFACE_HIGH
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiStyles.CARD_BORDER),
                BorderFactory.createEmptyBorder(2, 6, 2, 6),
            )
        }

        fun displayValue(): String {
            val value = sliderToValue(slider.value)
            return when (slider) {
                rotationSlider -> String.format(java.util.Locale.US, "%.1f", value)
                zoomSlider -> slider.value.toString()
                else -> slider.value.toString()
            }
        }

        fun publishFromSlider() {
            if (updating) return
            current = when (slider) {
                zoomSlider -> current.copy(zoom = zoomSliderToModel(slider.value))
                panXSlider -> current.copy(panX = sliderToValue(slider.value).coerceIn(-1.0f, 1.0f))
                panYSlider -> current.copy(panY = sliderToValue(slider.value).coerceIn(-1.0f, 1.0f))
                rotationSlider -> current.copy(rotationDeg = CropGeometryMath.normalizeRotation(sliderToValue(slider.value)))
                else -> current
            }
            onChanged(current)
        }

        slider.addChangeListener {
            readout.text = displayValue()
            publishFromSlider()
        }
        readout.text = displayValue()
        readout.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateFromText()
            override fun removeUpdate(e: DocumentEvent) = updateFromText()
            override fun changedUpdate(e: DocumentEvent) = updateFromText()

            private fun updateFromText() {
                if (updating || !readout.hasFocus()) return
                val parsed = readout.text.trim().toFloatOrNull() ?: return
                val sliderValue = when (slider) {
                    zoomSlider -> parsed.toInt()
                    rotationSlider -> (parsed * 2.0f).toInt()
                    panXSlider, panYSlider -> parsed.toInt()
                    else -> modelToSlider(current)
                }.coerceIn(slider.minimum, slider.maximum)
                slider.value = sliderValue
            }
        })

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, 0, 42, 0)
            maximumSize = Dimension(Int.MAX_VALUE, 92)
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                add(label, BorderLayout.WEST)
                add(JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                    isOpaque = false
                    add(readout)
                    if (suffix.isNotBlank()) {
                        add(JLabel(suffix).apply { UiStyles.styleHelper(this) })
                    }
                }, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            add(slider, BorderLayout.CENTER)
        }
    }

    private fun zoomSliderToModel(value: Int): Float {
        return if (value <= 100) {
            (0.1f + (value.coerceAtLeast(0) / 100.0f) * 0.9f).coerceIn(0.1f, 1.0f)
        } else {
            (1.0f + ((value - 100) / 100.0f) * 3.0f).coerceIn(1.0f, 4.0f)
        }
    }

    private fun modelZoomToSlider(adjustments: AdjustmentsV1): Int {
        val zoom = adjustments.zoom.coerceIn(0.1f, 4.0f)
        return if (zoom <= 1.0f) {
            (((zoom - 0.1f) / 0.9f) * 100.0f).toInt()
        } else {
            (100.0f + ((zoom - 1.0f) / 3.0f) * 100.0f).toInt()
        }
    }
}
