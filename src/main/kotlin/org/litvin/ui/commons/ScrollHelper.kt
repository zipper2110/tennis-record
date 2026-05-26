package org.litvin.ui.commons

import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.plaf.basic.BasicScrollBarUI

fun JComponent.scrollIntoView() = uiSafe {
    val rec = this.bounds

    var c0: Container? = null
    var c1 = this.getParent()
    while ((c1 != null) && c1 !is JScrollPane) {
        val r2: Rectangle = c1.bounds
        rec.x += r2.x
        rec.y += r2.y

        c0 = c1
        c1 = c1.getParent()
    }
    c0 as JComponent
    c0.scrollRectToVisible(rec)
}

/**
 * Apply the app-wide dark scrollbar style to the given scroll pane.
 * - Track #202020, thumb #333, hover/drag #444
 * - Rounded thumb (8px), width 10
 * - No arrow buttons
 * - Non-opaque, unitIncrement=16
 */
fun applyDarkScrollbar(scroll: JScrollPane, background: Color? = null) {
    val trackClr = Color(0x20, 0x20, 0x20)
    val thumbClr = Color(0x33, 0x33, 0x33)
    val thumbHover = Color(0x44, 0x44, 0x44)

    // Transparency to blend with parents
    scroll.isOpaque = false
    scroll.viewport.isOpaque = false
    background?.let {
        scroll.background = it
        scroll.viewport.background = it
    }
    // Sane increments for wheel/keyboard
    scroll.verticalScrollBar.unitIncrement = 16

    fun styleBar(bar: JScrollBar) {
        bar.isOpaque = false
        bar.background = background ?: trackClr
        bar.foreground = thumbClr
        if (bar.orientation == JScrollBar.VERTICAL) bar.preferredSize = Dimension(10, 10) else bar.preferredSize = Dimension(10, 10)
        bar.border = BorderFactory.createEmptyBorder()
        bar.ui = object : BasicScrollBarUI() {
            override fun configureScrollBarColors() {
                thumbColor = thumbClr
                thumbDarkShadowColor = thumbClr
                thumbHighlightColor = thumbClr
                thumbLightShadowColor = thumbClr
                trackColor = trackClr
                trackHighlightColor = trackClr
            }
            override fun paintThumb(g: Graphics, c: JComponent, thumbBounds: Rectangle) {
                if (!c.isEnabled) return
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val arc = 8
                val color = if (isDragging || isThumbRollover) thumbHover else thumbClr
                g2.color = color
                g2.fillRoundRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height, arc, arc)
                g2.dispose()
            }
            override fun paintTrack(g: Graphics, c: JComponent, trackBounds: Rectangle) {
                val g2 = g.create() as Graphics2D
                g2.color = trackClr
                g2.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height)
                g2.dispose()
            }
            override fun createDecreaseButton(orientation: Int): JButton = JButton().apply {
                preferredSize = Dimension(0, 0)
                isOpaque = false
                isContentAreaFilled = false
                border = null
                isFocusable = false
            }
            override fun createIncreaseButton(orientation: Int): JButton = createDecreaseButton(orientation)
        }
    }

    styleBar(scroll.verticalScrollBar)
    scroll.horizontalScrollBar?.let { styleBar(it) }
}

/**
 * Recursively apply dark scrollbar styling to all JScrollPanes inside the root container.
 */
fun applyDarkScrollbars(root: Container) {
    fun visit(c: Component) {
        when (c) {
            is JScrollPane -> applyDarkScrollbar(c)
            is Container -> c.components?.forEach { visit(it) }
        }
    }
    visit(root)
}