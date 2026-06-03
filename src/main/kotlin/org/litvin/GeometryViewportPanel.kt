package org.litvin

import org.litvin.adjustments.AdjustmentsV1
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JPanel
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
        addComponentListener(object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent) {
                scheduleApply()
            }

            override fun componentResized(e: ComponentEvent) {
                scheduleApply()
            }
        })
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
        if (changed || content.width <= 1 || content.height <= 1) scheduleApply()
    }

    fun refreshGeometry() {
        scheduleApply()
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

    override fun addNotify() {
        super.addNotify()
        scheduleApply()
    }

    override fun getPreferredSize(): Dimension = Dimension(640, 360)

    override fun getMinimumSize(): Dimension = Dimension(1, 1)

    private fun applyLayoutNow() {
        try {
            val W = width.coerceAtLeast(1)
            val H = height.coerceAtLeast(1)
            val z = if (zoom <= 0f) 0.0001f else zoom
            val cropW = (W / z).toInt().coerceAtLeast(1)
            val cropH = (H / z).toInt().coerceAtLeast(1)
            val cropX = ((W - cropW) / 2f + panX * (W - cropW) / 2f)
                .coerceIn(0f, (W - cropW).toFloat())
            val cropY = ((H - cropH) / 2f - panY * (H - cropH) / 2f)
                .coerceIn(0f, (H - cropH).toFloat())
            // Size the child to the zoomed frame and offset the crop origin to
            // viewport (0,0). This keeps aspect stable and makes pan linear.
            val childW = (W * z).roundToInt()
            val childH = (H * z).roundToInt()
            val childX = -(cropX * z).roundToInt()
            val childY = -(cropY * z).roundToInt()
            content.setBounds(childX, childY, childW, childH)
            content.revalidate()
            content.repaint()
        } catch (_: Throwable) { }
    }
}
