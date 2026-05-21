package org.litvin.ui.commons

import java.awt.Dimension
import java.awt.LayoutManager
import javax.swing.JPanel

/**
 * Simple panel that preserves the provided aspect ratio by letterboxing.
 *
 * Children layout rules:
 * - The child named `"video"` is resized to fit the computed target rectangle preserving the aspect.
 * - Other children keep their own bounds (intended for overlays) and are revalidated on resize.
 */
class AspectPanel(private val aspect: Double) : JPanel() {
    override fun getPreferredSize(): Dimension {
        val w = super.getPreferredSize().width.takeIf { it > 0 } ?: 1280
        val h = (w / aspect).toInt()
        return Dimension(w, h)
    }

    override fun doLayout() {
        val w = width
        val h = height
        val targetW: Int
        val targetH: Int
        if (w / aspect < h) {
            targetW = w
            targetH = (w / aspect).toInt()
        } else {
            targetH = h
            targetW = (h * aspect).toInt()
        }
        val x = (w - targetW) / 2
        val y = (h - targetH) / 2
        for (i in 0 until componentCount) {
            val c = getComponent(i)
            if (!c.isVisible) continue
            if ("video" == c.name) {
                c.setBounds(x, y, targetW, targetH)
            } else {
                // Keep overlay absolute coordinates relative to this panel
                c.revalidate()
            }
        }
    }
}
