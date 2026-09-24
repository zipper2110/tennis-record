package org.litvin.ui.commons

import org.litvin.ui.UiStyles
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyEvent
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

/**
 * A balloon hint that points left at an anchor component. The balloon has a message and a close button.
 * It lives in the popup layer of the anchor's window, so it follows the anchor when the window moves or resizes.
 * The balloon stays until the user clicks the close button or the owner calls [hideBalloon].
 */
class HintBalloon(message: String, private val onClose: () -> Unit) : JPanel(BorderLayout(8, 0)) {
    private var anchor: JComponent? = null
    private var layeredPane: JLayeredPane? = null

    private val anchorListener = object : ComponentAdapter() {
        override fun componentMoved(e: ComponentEvent) = reposition()
        override fun componentResized(e: ComponentEvent) = reposition()
        override fun componentHidden(e: ComponentEvent) = hideBalloon()
    }
    private val ancestorListener = object : HierarchyBoundsAdapter() {
        override fun ancestorMoved(e: HierarchyEvent) = reposition()
        override fun ancestorResized(e: HierarchyEvent) = reposition()
    }

    init {
        name = "hint-balloon"
        isOpaque = false
        border = EmptyBorder(PADDING, ARROW_WIDTH + PADDING + 2, PADDING, PADDING)
        val label = JLabel("<html><div style='width:${TEXT_WIDTH}px'>${Html.escapeHtml(message)}</div></html>")
        label.foreground = UiStyles.FG_PRIMARY
        add(label, BorderLayout.CENTER)
        val close = JButton(UiStyles.closeIcon()).apply {
            name = "hint-balloon-close"
            toolTipText = "Close. Do not show this hint again"
            isFocusPainted = false
            isBorderPainted = false
            isContentAreaFilled = false
            border = EmptyBorder(2, 2, 2, 2)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                hideBalloon()
                onClose()
            }
        }
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(close, BorderLayout.NORTH)
        }, BorderLayout.EAST)
    }

    val isShown: Boolean
        get() = layeredPane != null

    /** Shows the balloon to the right of [target]. Does nothing if [target] is not on the screen. */
    fun showAt(target: JComponent) {
        hideBalloon()
        val pane = SwingUtilities.getRootPane(target)?.layeredPane ?: return
        if (!target.isShowing) return
        anchor = target
        layeredPane = pane
        pane.add(this, JLayeredPane.POPUP_LAYER)
        target.addComponentListener(anchorListener)
        target.addHierarchyBoundsListener(ancestorListener)
        reposition()
    }

    /** Removes the balloon from the screen. The close callback does not run. */
    fun hideBalloon() {
        anchor?.removeComponentListener(anchorListener)
        anchor?.removeHierarchyBoundsListener(ancestorListener)
        layeredPane?.let { pane ->
            val bounds = bounds
            pane.remove(this)
            pane.repaint(bounds)
        }
        anchor = null
        layeredPane = null
    }

    private fun reposition() {
        val target = anchor ?: return
        val pane = layeredPane ?: return
        if (!target.isShowing) {
            hideBalloon()
            return
        }
        val size = preferredSize
        val location = SwingUtilities.convertPoint(target.parent, target.location, pane)
        val x = location.x + target.width + GAP
        val y = (location.y + target.height / 2 - size.height / 2)
            .coerceAtMost(pane.height - size.height)
            .coerceAtLeast(0)
        arrowCenterY = location.y + target.height / 2 - y
        setBounds(x, y, size.width, size.height)
        revalidate()
        pane.repaint()
    }

    // Vertical position of the arrow tip in the balloon, so that the arrow points at the anchor center
    private var arrowCenterY = 0

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val body = RoundRectangle2D.Float(
                ARROW_WIDTH.toFloat(), 0.5f,
                (width - ARROW_WIDTH - 1).toFloat(), (height - 1).toFloat(),
                ARC, ARC,
            )
            val tipY = arrowCenterY.coerceIn(ARC.toInt(), height - ARC.toInt()).toDouble()
            val arrow = Path2D.Double().apply {
                moveTo(ARROW_WIDTH + 1.0, tipY - ARROW_HALF_HEIGHT)
                lineTo(0.5, tipY)
                lineTo(ARROW_WIDTH + 1.0, tipY + ARROW_HALF_HEIGHT)
                closePath()
            }
            val shape = Area(body).apply { add(Area(arrow)) }
            g2.color = BACKGROUND
            g2.fill(shape)
            g2.color = UiStyles.LIME
            g2.stroke = BasicStroke(1f)
            g2.draw(shape)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val GAP = 4
        const val PADDING = 10
        const val TEXT_WIDTH = 210
        const val ARROW_WIDTH = 10
        const val ARROW_HALF_HEIGHT = 8.0
        const val ARC = 12f
        val BACKGROUND = Color(0x26, 0x26, 0x26)
    }
}
