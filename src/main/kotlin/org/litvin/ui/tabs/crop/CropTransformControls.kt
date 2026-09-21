package org.litvin.ui.tabs.crop

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.CropGeometryMath
import org.litvin.ui.UiStyles
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
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
import kotlin.math.abs
import kotlin.math.roundToInt

class CropTransformControls(
    private val onChanged: (AdjustmentsV1) -> Unit,
    private val onResetTransform: () -> Unit,
    private val onHelp: () -> Unit = {},
) : JPanel(BorderLayout()) {
    private val zoomSlider = JSlider(10, 400, 100).apply {
        name = "crop-zoom"
        toolTipText = "Zoom"
    }
    private val panXSlider = JSlider(-100, 100, 0).apply {
        name = "crop-pan-x"
        toolTipText = "Pan X"
    }
    private val panYSlider = JSlider(-100, 100, 0).apply {
        name = "crop-pan-y"
        toolTipText = "Pan Y"
    }
    private val rotationSlider = JSlider(-360, 360, 0).apply {
        name = "crop-rotation"
        toolTipText = "Rotation"
    }
    private val fineRotationSlider = JSlider(-50, 50, 0).apply {
        name = "crop-rotation-fine"
        toolTipText = "Fine Rotation"
    }

    private var updating = false
    private var current = AdjustmentsV1()
    private var lastEmittedRotation: Float? = null

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
            maximumSize = Dimension(Int.MAX_VALUE, 64)
            val title = JLabel("Transform").apply {
                foreground = UiStyles.FG_PRIMARY
                font = font.deriveFont(font.style, font.size2D + 3.0f)
            }
            val reset = JButton("Reset").apply {
                name = "crop-reset"
                toolTipText = "Reset Transform"
                addActionListener { onResetTransform() }
            }
            val help = JButton("Help [F1]").apply {
                name = "crop-help"
                toolTipText = "F1 - Help"
                UiStyles.styleSecondary(this)
                addActionListener { onHelp() }
            }
            val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 4)).apply {
                isOpaque = false
                add(help)
                add(reset)
            }
            add(title, BorderLayout.WEST)
            add(actions, BorderLayout.EAST)
        }
        content.add(header)
        content.add(sliderRow("Zoom", zoomSlider, "%", ::zoomSliderToModel, ::modelZoomToSlider))
        content.add(sliderRow("Pan X", panXSlider, "", { it / 100.0f }, { (it.panX * 100.0f).toInt() }))
        content.add(sliderRow("Pan Y", panYSlider, "", { it / 100.0f }, { (it.panY * 100.0f).toInt() }))
        content.add(sliderRow("Rotation", rotationSlider, "deg", { it / 2.0f }, { (it.rotationDeg * 2.0f).toInt() }))
        content.add(sliderRow("Fine Rotation", fineRotationSlider, "deg", { it / 10.0f }, { fineRotationSlider.value }))
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
            renderRotation(adjustments.rotationDeg)
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
                rotationSlider, fineRotationSlider -> String.format(java.util.Locale.US, "%.1f", value)
                zoomSlider -> slider.value.toString()
                else -> slider.value.toString()
            }
        }

        fun syncReadoutFromSlider() {
            val value = displayValue()
            if (readout.text != value) {
                readout.text = value
            }
        }

        fun publishFromSlider() {
            if (updating) return
            current = when (slider) {
                zoomSlider -> current.copy(zoom = zoomSliderToModel(slider.value))
                panXSlider -> current.copy(panX = sliderToValue(slider.value).coerceIn(-1.0f, 1.0f))
                panYSlider -> current.copy(panY = sliderToValue(slider.value).coerceIn(-1.0f, 1.0f))
                rotationSlider, fineRotationSlider -> current.copy(rotationDeg = emitRotation())
                else -> current
            }
            onChanged(current)
        }

        slider.addChangeListener {
            if (!readout.hasFocus()) {
                syncReadoutFromSlider()
            }
            publishFromSlider()
        }
        syncReadoutFromSlider()
        readout.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                syncReadoutFromSlider()
            }
        })
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
                    fineRotationSlider -> (parsed * 10.0f).toInt()
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

    /**
     * Rotation is split across a coarse slider and a fine one that nudges it by tenths of a degree.
     * Their sum is the angle the rest of the app sees.
     */
    private fun rotationFromSliders(): Float =
        (rotationSlider.value / 2.0f + fineRotationSlider.value / 10.0f)
            .coerceIn(-ROTATION_LIMIT_DEG, ROTATION_LIMIT_DEG)

    private fun emitRotation(): Float =
        CropGeometryMath.normalizeRotation(rotationFromSliders()).also { lastEmittedRotation = it }

    /** Splits an angle that came from elsewhere back into the two sliders, leaving our own edits alone. */
    private fun renderRotation(rotationDeg: Float) {
        val emitted = lastEmittedRotation
        if (emitted != null && abs(rotationDeg - emitted) <= ROTATION_EPSILON_DEG) return
        lastEmittedRotation = rotationDeg
        val clamped = rotationDeg.coerceIn(-ROTATION_LIMIT_DEG, ROTATION_LIMIT_DEG)
        val coarse = (clamped * 2.0f).roundToInt().coerceIn(rotationSlider.minimum, rotationSlider.maximum)
        val fine = ((clamped - coarse / 2.0f) * 10.0f).roundToInt()
            .coerceIn(fineRotationSlider.minimum, fineRotationSlider.maximum)
        rotationSlider.value = coarse
        fineRotationSlider.value = fine
    }

    private fun zoomSliderToModel(value: Int): Float {
        return (value / 100.0f).coerceIn(0.1f, 4.0f)
    }

    private fun modelZoomToSlider(adjustments: AdjustmentsV1): Int {
        return (adjustments.zoom.coerceIn(0.1f, 4.0f) * 100.0f).toInt()
    }

    private companion object {
        const val ROTATION_LIMIT_DEG = 180.0f
        const val ROTATION_EPSILON_DEG = 0.0001f
    }
}
