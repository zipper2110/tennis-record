package org.litvin.ui.tabs.crop

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.CropGeometryMath
import org.litvin.adjustments.CropRect
import org.litvin.ui.UiStyles
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

class CropRotateCanvas(
    private val onAdjustmentsChanged: (AdjustmentsV1) -> Unit,
) : JComponent() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    private enum class HitTarget {
        NONE, MOVE, N, S, E, W, NW, NE, SW, SE, ROTATE
    }

    private var frame: BufferedImage? = null
    private var adjustments = AdjustmentsV1()
    private var outputAspect = 16.0 / 9.0
    private var loading = false
    private var banner: String? = null
    private var hoverTarget = HitTarget.NONE
    private var dragTarget = HitTarget.NONE
    private var dragStartPoint = java.awt.Point()
    private var dragStartRect = CropRect(0.0, 0.0, 0.0, 0.0)
    private var dragStartAdjustments = AdjustmentsV1()

    init {
        name = "adj-cr-canvas"
        isOpaque = true
        background = Color.BLACK
        isDoubleBuffered = true
        isFocusable = true
        installMouseHandlers()
        installNudgeKeys()
    }

    fun render(frame: BufferedImage?, adjustments: AdjustmentsV1, outputAspect: Double, loading: Boolean, banner: String?) {
        this.frame = frame
        this.adjustments = adjustments
        this.outputAspect = outputAspect.takeIf { it > 0.0 } ?: 16.0 / 9.0
        this.loading = loading
        this.banner = banner
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        val start = System.nanoTime()
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.color = background
            g2.fillRect(0, 0, width, height)

            val content = contentBounds()
            g2.color = Color(0x13, 0x13, 0x13)
            g2.fill(content)

            drawFrame(g2, content)
            val overlay = overlayRect(content)
            drawDimMask(g2, content, overlay)
            drawOverlay(g2, overlay)
            drawMessages(g2, content)
        } finally {
            g2.dispose()
            if (System.getProperty("tennisrecord.crop.debug") == "true") {
                val paintMs = (System.nanoTime() - start) / 1_000_000L
                if (paintMs > 16L) logger.debug { "Crop/rotate paint=${paintMs}ms" }
            }
        }
    }

    private fun drawFrame(g2: Graphics2D, content: Rectangle2D.Double) {
        val image = frame ?: return
        val old = g2.transform
        val clip = g2.clip
        try {
            g2.clip = content
            val cx = content.centerX
            val cy = content.centerY
            g2.translate(cx, cy)
            g2.rotate(Math.toRadians(adjustments.rotationDeg.toDouble()))
            g2.drawImage(
                image,
                (-content.width / 2.0).toInt(),
                (-content.height / 2.0).toInt(),
                content.width.toInt(),
                content.height.toInt(),
                null,
            )
        } finally {
            g2.transform = old
            g2.clip = clip
        }
    }

    private fun drawDimMask(g2: Graphics2D, content: Rectangle2D.Double, overlay: Rectangle2D.Double) {
        val area = Area(content)
        area.subtract(Area(overlay))
        val oldComposite = g2.composite
        g2.composite = AlphaComposite.SrcOver.derive(0.58f)
        g2.color = Color.BLACK
        g2.fill(area)
        g2.composite = oldComposite
    }

    private fun drawOverlay(g2: Graphics2D, overlay: Rectangle2D.Double) {
        val scale = deviceScale()
        val handle = (10.0 * scale).coerceAtLeast(8.0)
        val active = hoverTarget != HitTarget.NONE || dragTarget != HitTarget.NONE
        val strokeColor = if (active) UiStyles.LIME else Color.WHITE
        g2.stroke = BasicStroke((2.0 * scale).coerceAtLeast(1.5).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.color = strokeColor
        g2.draw(overlay)

        handleRects(overlay, handle).forEach { (_, rect) ->
            g2.color = UiStyles.DARK_BG
            g2.fill(rect)
            g2.color = strokeColor
            g2.draw(rect)
        }

        val rotationCenter = rotationHandleCenter(overlay)
        g2.stroke = BasicStroke((1.4 * scale).coerceAtLeast(1.0).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.color = strokeColor
        g2.drawLine(overlay.centerX.toInt(), overlay.y.toInt(), rotationCenter.x.toInt(), rotationCenter.y.toInt())
        val rotateCircle = Ellipse2D.Double(rotationCenter.x - handle / 2.0, rotationCenter.y - handle / 2.0, handle, handle)
        g2.color = UiStyles.DARK_BG
        g2.fill(rotateCircle)
        g2.color = strokeColor
        g2.draw(rotateCircle)

        if (hasFocus()) {
            g2.color = Color(0xFF, 0xD5, 0x4A)
            val focusRect = Rectangle2D.Double(overlay.x - 4.0, overlay.y - 4.0, overlay.width + 8.0, overlay.height + 8.0)
            g2.stroke = BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f, floatArrayOf(4.0f, 4.0f), 0.0f)
            g2.draw(focusRect)
        }
    }

    private fun drawMessages(g2: Graphics2D, content: Rectangle2D.Double) {
        if (frame == null) {
            g2.color = UiStyles.FG_SECONDARY
            val text = if (loading) "Loading frame..." else "Open a project source video to preview crop."
            val fm = g2.fontMetrics
            g2.drawString(text, (content.centerX - fm.stringWidth(text) / 2.0).toInt(), content.centerY.toInt())
        }
        if (loading && frame != null) {
            g2.color = Color(255, 255, 255, 180)
            g2.drawString("Updating frame...", content.x.toInt() + 14, content.y.toInt() + 24)
        }
        banner?.let { message ->
            g2.color = Color(0x25, 0x25, 0x25, 235)
            val h = 34
            g2.fillRect(content.x.toInt(), content.y.toInt(), content.width.toInt(), h)
            g2.color = UiStyles.FG_PRIMARY
            g2.drawString(message, content.x.toInt() + 12, content.y.toInt() + 22)
        }
    }

    private fun installMouseHandlers() {
        val adapter = object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                hoverTarget = hitTest(e.x.toDouble(), e.y.toDouble())
                cursor = cursorFor(hoverTarget)
                repaint()
            }

            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                dragTarget = hitTest(e.x.toDouble(), e.y.toDouble())
                dragStartPoint = e.point
                dragStartRect = overlayRect(contentBounds()).toCropRect()
                dragStartAdjustments = adjustments
                repaint()
            }

            override fun mouseDragged(e: MouseEvent) {
                if (dragTarget == HitTarget.NONE) return
                handleDrag(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                dragTarget = HitTarget.NONE
                hoverTarget = hitTest(e.x.toDouble(), e.y.toDouble())
                cursor = cursorFor(hoverTarget)
                repaint()
            }

            override fun mouseExited(e: MouseEvent) {
                if (dragTarget == HitTarget.NONE) {
                    hoverTarget = HitTarget.NONE
                    cursor = Cursor.getDefaultCursor()
                    repaint()
                }
            }
        }
        addMouseListener(adapter)
        addMouseMotionListener(adapter)
    }

    private fun handleDrag(e: MouseEvent) {
        val content = contentBounds()
        val bounds = allowedBounds(content).toCropRect()
        val aspect = outputAspect
        val dx = e.x - dragStartPoint.x
        val dy = e.y - dragStartPoint.y
        if (dragTarget == HitTarget.ROTATE) {
            val center = overlayRect(content).let { it.centerX to it.centerY }
            val angle = Math.toDegrees(atan2(e.y - center.second, e.x - center.first)).toFloat() + 90.0f
            val snapped = CropGeometryMath.snapRotation(angle, (e.modifiersEx and InputEvent.SHIFT_DOWN_MASK) != 0)
            emit(dragStartAdjustments.copy(rotationDeg = snapped))
            return
        }

        val nextRect = when (dragTarget) {
            HitTarget.MOVE -> CropGeometryMath.clampInside(
                dragStartRect.copy(x = dragStartRect.x + dx, y = dragStartRect.y + dy),
                bounds,
                aspect,
            )
            HitTarget.N, HitTarget.S -> CropGeometryMath.centeredResize(dragStartRect, bounds, aspect, -2.0 * signedVerticalDelta(dy))
            HitTarget.E, HitTarget.W -> CropGeometryMath.centeredResize(dragStartRect, bounds, aspect, 2.0 * signedHorizontalDelta(dx))
            HitTarget.NW, HitTarget.NE, HitTarget.SW, HitTarget.SE -> {
                val delta = signedCornerDelta(dx, dy)
                CropGeometryMath.centeredResize(dragStartRect, bounds, aspect, 2.0 * delta)
            }
            else -> dragStartRect
        }
        val local = nextRect.copy(x = nextRect.x - content.x, y = nextRect.y - content.y)
        val next = CropGeometryMath.modelFromOverlay(content.width, content.height, local, adjustments)
        emit(next.copy(rotationDeg = adjustments.rotationDeg))
    }

    private fun signedVerticalDelta(dy: Int): Double {
        return if (dragTarget == HitTarget.N) dy.toDouble() else -dy.toDouble()
    }

    private fun signedHorizontalDelta(dx: Int): Double {
        return if (dragTarget == HitTarget.E) dx.toDouble() else -dx.toDouble()
    }

    private fun signedCornerDelta(dx: Int, dy: Int): Double {
        val sx = when (dragTarget) {
            HitTarget.NE, HitTarget.SE -> dx.toDouble()
            HitTarget.NW, HitTarget.SW -> -dx.toDouble()
            else -> 0.0
        }
        val sy = when (dragTarget) {
            HitTarget.SW, HitTarget.SE -> dy.toDouble()
            HitTarget.NW, HitTarget.NE -> -dy.toDouble()
            else -> 0.0
        }
        return if (abs(sx) > abs(sy)) sx else sy * outputAspect
    }

    private fun emit(next: AdjustmentsV1) {
        adjustments = next
        onAdjustmentsChanged(next)
        repaint()
    }

    private fun installNudgeKeys() {
        fun bind(key: String, action: String, dx: Int, dy: Int) {
            inputMap.put(KeyStroke.getKeyStroke(key), action)
            actionMap.put(action, object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent) {
                    nudge(dx, dy)
                }
            })
        }
        bind("LEFT", "cropNudgeLeft", -1, 0)
        bind("RIGHT", "cropNudgeRight", 1, 0)
        bind("UP", "cropNudgeUp", 0, -1)
        bind("DOWN", "cropNudgeDown", 0, 1)
        bind("shift LEFT", "cropNudgeLeftFast", -10, 0)
        bind("shift RIGHT", "cropNudgeRightFast", 10, 0)
        bind("shift UP", "cropNudgeUpFast", 0, -10)
        bind("shift DOWN", "cropNudgeDownFast", 0, 10)
    }

    private fun nudge(dx: Int, dy: Int) {
        val content = contentBounds()
        val bounds = allowedBounds(content).toCropRect()
        val nextRect = CropGeometryMath.clampInside(
            overlayRect(content).toCropRect().copy(
                x = overlayRect(content).x + dx,
                y = overlayRect(content).y + dy,
            ),
            bounds,
            outputAspect,
        )
        val local = nextRect.copy(x = nextRect.x - content.x, y = nextRect.y - content.y)
        val next = CropGeometryMath.modelFromOverlay(content.width, content.height, local, adjustments)
        emit(next.copy(rotationDeg = adjustments.rotationDeg))
    }

    private fun hitTest(x: Double, y: Double): HitTarget {
        val overlay = overlayRect(contentBounds())
        val handle = 12.0 * deviceScale()
        val rotate = rotationHandleCenter(overlay)
        if (rotate.distance(x, y) <= handle) return HitTarget.ROTATE

        handleRects(overlay, handle).forEach { (target, rect) ->
            if (rect.contains(x, y)) return target
        }
        if (overlay.contains(x, y)) return HitTarget.MOVE
        return HitTarget.NONE
    }

    private fun cursorFor(target: HitTarget): Cursor = when (target) {
        HitTarget.MOVE -> Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
        HitTarget.N, HitTarget.S -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
        HitTarget.E, HitTarget.W -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
        HitTarget.NW, HitTarget.SE -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR)
        HitTarget.NE, HitTarget.SW -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR)
        HitTarget.ROTATE -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        HitTarget.NONE -> Cursor.getDefaultCursor()
    }

    private fun contentBounds(): Rectangle2D.Double {
        val margin = 24.0
        val availableW = max(1.0, width - margin * 2.0)
        val availableH = max(1.0, height - margin * 2.0)
        val wByHeight = availableH * outputAspect
        val contentW = if (wByHeight <= availableW) wByHeight else availableW
        val contentH = contentW / outputAspect
        return Rectangle2D.Double((width - contentW) / 2.0, (height - contentH) / 2.0, contentW, contentH)
    }

    private fun overlayRect(content: Rectangle2D.Double): Rectangle2D.Double {
        val modelRect = CropGeometryMath.overlayFromModel(content.width, content.height, adjustments)
        val allowed = allowedBounds(content).toCropRect()
        val clamped = CropGeometryMath.clampInside(
            modelRect.copy(x = modelRect.x + content.x, y = modelRect.y + content.y),
            allowed,
            outputAspect,
        )
        return clamped.toRectangle2D()
    }

    private fun allowedBounds(content: Rectangle2D.Double): Rectangle2D.Double {
        val local = CropGeometryMath.largestCenteredInscribedRect(
            content.width,
            content.height,
            adjustments.rotationDeg.toDouble(),
            outputAspect,
        )
        return Rectangle2D.Double(content.x + local.x, content.y + local.y, local.width, local.height)
    }

    private fun handleRects(overlay: Rectangle2D.Double, handle: Double): List<Pair<HitTarget, Rectangle2D.Double>> {
        val half = handle / 2.0
        val cx = overlay.centerX
        val cy = overlay.centerY
        val left = overlay.x
        val right = overlay.maxX
        val top = overlay.y
        val bottom = overlay.maxY
        fun rect(x: Double, y: Double) = Rectangle2D.Double(x - half, y - half, handle, handle)
        return listOf(
            HitTarget.NW to rect(left, top),
            HitTarget.N to rect(cx, top),
            HitTarget.NE to rect(right, top),
            HitTarget.E to rect(right, cy),
            HitTarget.SE to rect(right, bottom),
            HitTarget.S to rect(cx, bottom),
            HitTarget.SW to rect(left, bottom),
            HitTarget.W to rect(left, cy),
        )
    }

    private fun rotationHandleCenter(overlay: Rectangle2D.Double): java.awt.geom.Point2D.Double {
        return java.awt.geom.Point2D.Double(overlay.centerX, overlay.y - 24.0 * deviceScale())
    }

    private fun deviceScale(): Double {
        return try {
            graphicsConfiguration?.defaultTransform?.scaleX?.coerceAtLeast(1.0) ?: 1.0
        } catch (_: Throwable) {
            1.0
        }
    }

    private fun Rectangle2D.Double.toCropRect(): CropRect = CropRect(x, y, width, height)
    private fun CropRect.toRectangle2D(): Rectangle2D.Double = Rectangle2D.Double(x, y, width, height)

    private val Rectangle2D.Double.centerX: Double get() = x + width / 2.0
    private val Rectangle2D.Double.centerY: Double get() = y + height / 2.0

    override fun getPreferredSize() = java.awt.Dimension(640, 360)
    override fun getMinimumSize() = java.awt.Dimension(640, 360)
}
