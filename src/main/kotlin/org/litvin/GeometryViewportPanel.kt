package org.litvin

import org.litvin.adjustments.AdjustmentsV1
import java.awt.*
import javax.swing.*
import kotlin.math.roundToInt

/**
 * UI-layer geometry preview for VLC canvas.
 *
 * Applies zoom/pan by resizing and offsetting the child component (VLC Canvas)
 * within a clipping container. Rotation is not applied here due to heavyweight
 * component limitations; rotation may be attempted via VLC filters by the player
 * adapter. This class focuses on smooth pan/zoom at 30–60 FPS.
 */
class GeometryViewportPanel(private val content: Component) : JPanel(null /* absolute layout */) {
    @Volatile private var zoom: Float = 1.0f
    @Volatile private var panX: Float = 0.0f
    @Volatile private var panY: Float = 0.0f

    // Coalescing timer (≤120 Hz) for bursts from sliders
    private val repaintTimer = javax.swing.Timer(8) { _ ->
        applyLayoutNow()
    }.apply { isRepeats = false }

    init {
        isOpaque = true
        background = Color.BLACK
        add(content)
    }

    fun applyGeometry(adj: AdjustmentsV1) {
        // Clamp to safe ranges per spec
        val z = adj.zoom.coerceIn(0.1f, 4.0f)
        val px = adj.panX.coerceIn(-1.0f, 1.0f)
        val py = adj.panY.coerceIn(-1.0f, 1.0f)
        var changed = false
        if (zoom != z) { zoom = z; changed = true }
        if (panX != px) { panX = px; changed = true }
        if (panY != py) { panY = py; changed = true }
        if (changed) scheduleApply()
    }

    private fun scheduleApply() {
        try {
            repaintTimer.restart()
        } catch (_: Throwable) {
            applyLayoutNow()
        }
    }

    override fun doLayout() {
        super.doLayout()
        // Also apply layout when the container is resized
        applyLayoutNow()
    }

    private fun applyLayoutNow() {
        try {
            val W = width.coerceAtLeast(1)
            val H = height.coerceAtLeast(1)
            val z = if (zoom <= 0f) 0.0001f else zoom
            val cropW = (W / z).toInt().coerceAtLeast(1)
            val cropH = (H / z).toInt().coerceAtLeast(1)
            val offX = ((W - cropW) / 2f + panX * (W - cropW) / 2f).roundToInt()
            val offY = ((H - cropH) / 2f - panY * (H - cropH) / 2f).roundToInt()
            // We want to display the crop area scaled back to viewport size.
            // Achieve by sizing the child to W*z x H*z and offsetting so that the
            // crop rect (cropW x cropH at (offX,offY)) maps to viewport.
            val childW = (W * z).roundToInt()
            val childH = (H * z).roundToInt()
            val childX = -((childW - W) / 2) + ((W - cropW) / 2 - offX)
            val childY = -((childH - H) / 2) + ((H - cropH) / 2 - offY)
            content.setBounds(childX, childY, childW, childH)
            content.revalidate()
            content.repaint()
        } catch (_: Throwable) { }
    }
}
