package org.litvin

import java.awt.*
import javax.swing.*

/**
 * Shared Swing UI styles to match the mock (projects.html):
 * - Primary CTA button: rounded, lime gradient background with olive text and a leading plus icon (optional).
 * - Primary small button: solid neon green pill.
 * - Secondary button: subtle dark surface with thin rounded border.
 */
object UiStyles {
    // Palette inspired by design/projects.html dark theme
    val DARK_BG: Color = Color(0x0E, 0x0E, 0x0E)            // background / surface-dim
    val SURFACE_HIGH: Color = Color(0x20, 0x20, 0x1F)       // surface-container-high
    val CARD_BG: Color = Color(0x1A, 0x1A, 0x1A)            // surface-container
    val CARD_BORDER: Color = Color(0x26, 0x26, 0x26)        // surface-variant border
    val FG_PRIMARY: Color = Color(0xFF, 0xFF, 0xFF)         // on-surface
    val FG_SECONDARY: Color = Color(0xAD, 0xAA, 0xAA)       // on-surface-variant
    val GREEN: Color = Color(0xA1, 0xFE, 0x00)              // primary-fixed

    // Sidebar specific palette (from mock)
    val SIDEBAR_BG: Color = Color(0x12, 0x12, 0x12)
    val SIDEBAR_FG: Color = Color(0xD8, 0xD8, 0xD8)
    val SIDEBAR_FG_MUTED: Color = Color(0x9A, 0x9A, 0x9A)
    val SIDEBAR_HOVER_BG: Color = Color(0x1C, 0x1C, 0x1C)
    val SIDEBAR_ACTIVE_BG: Color = Color(0x18, 0x18, 0x18)
    val LIME: Color = Color(0xA1, 0xFE, 0x00)

    // CTA gradient (mock: light lime to bright neon green)
    private val GRADIENT_START = Color(0xDD, 0xFF, 0xB0)     // #ddffb0
    private val GRADIENT_END = Color(0xA1, 0xFE, 0x00)       // #a1fe00
    private val GRADIENT_START_HOVER = GRADIENT_START.brighter()
    private val GRADIENT_END_HOVER = GRADIENT_END.brighter()

    // CTA content colors to match mock
    private val TEXT_ON_PRIMARY = Color(0x2B, 0x49, 0x00)    // dark olive text
    private val ICON_CIRCLE_DARK = Color(0x3C, 0x43, 0x00)   // deep olive circle
    private val ICON_PLUS_LIGHT = Color(0xED, 0xFF, 0xC8)    // pale lime for plus

    // Transport icons
    fun playIcon(size: Int = 28): Icon = object : Icon {
        override fun getIconWidth() = size
        override fun getIconHeight() = size
        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = Color(0x1A, 0x2E, 0x00)
            val w = size; val h = size
            val px = x + (w * 0.28).toInt()
            val py = y + (h * 0.18).toInt()
            val p = Polygon()
            p.addPoint(px, py)
            p.addPoint(px, py + (h * 0.64).toInt())
            p.addPoint(px + (w * 0.54).toInt(), y + h / 2)
            g2.fillPolygon(p)
        }
    }

    fun pauseIcon(size: Int = 28): Icon = object : Icon {
        override fun getIconWidth() = size
        override fun getIconHeight() = size
        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = Color(0x1A, 0x2E, 0x00)
            val w = size; val h = size
            val barW = (w * 0.26).toInt()
            val gap = (w * 0.16).toInt()
            g2.fillRect(x + (w*0.16).toInt(), y + (h*0.16).toInt(), barW, (h*0.68).toInt())
            g2.fillRect(x + (w*0.16).toInt() + barW + gap, y + (h*0.16).toInt(), barW, (h*0.68).toInt())
        }
    }
    fun seekIcon(isRight: Boolean, size: Int = 20): Icon = object : Icon {
        override fun getIconWidth() = size
        override fun getIconHeight() = size
        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = LIME
            val w = size; val h = size
            // simple chevron arrow
            val p = Polygon()
            if (isRight) {
                p.addPoint(x + (w*0.25).toInt(), y + (h*0.15).toInt())
                p.addPoint(x + (w*0.75).toInt(), y + h/2)
                p.addPoint(x + (w*0.25).toInt(), y + (h*0.85).toInt())
            } else {
                p.addPoint(x + (w*0.75).toInt(), y + (h*0.15).toInt())
                p.addPoint(x + (w*0.25).toInt(), y + h/2)
                p.addPoint(x + (w*0.75).toInt(), y + (h*0.85).toInt())
            }
            g2.stroke = BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g2.drawPolyline(p.xpoints, p.ypoints, 3)
        }
    }

    fun squarePrimaryButton(icon: Icon, size: Int = 64, onClick: (() -> Unit)? = null): JButton {
        return object : JButton() {
            init {
                isFocusPainted = false
                isBorderPainted = false
                isContentAreaFilled = false
                cursor =  Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                preferredSize = Dimension(size, size)
                minimumSize = Dimension(size, size)
                maximumSize = Dimension(size, size)
                this.icon = icon
                addActionListener { onClick?.invoke() }
            }
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val w = width; val h = height
                val r = 18
                val grad = GradientPaint(0f, 0f, GRADIENT_START, 0f, h.toFloat(), GRADIENT_END)
                g2.paint = if (model.isRollover) GradientPaint(0f,0f,GRADIENT_START_HOVER,0f,h.toFloat(),GRADIENT_END_HOVER) else grad
                g2.fillRoundRect(0,0,w,h,r,r)
                // icon
                this.icon.paintIcon(this, g2, (w - this.icon.iconWidth)/2, (h - this.icon.iconHeight)/2)
                // subtle inner shadow
                g2.color = Color(0,0,0,40)
                g2.drawRoundRect(0,0,w-1,h-1,r,r)
            }
        }
    }

    // Sidebar container styling
    fun styleSidebarContainer(panel: JPanel) {
        panel.background = SIDEBAR_BG
        panel.isOpaque = true
        panel.border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    }

    /** Sidebar button with custom hover/active styling and an icon. */
    fun sidebarButton(text: String, icon: Icon, onClick: () -> Unit): SidebarButton = SidebarButton(text, icon).apply {
        addActionListener { onClick() }
    }

    class SidebarButton(text: String, icon: Icon) : JButton(text) {
        var active: Boolean = false
            set(value) { field = value; repaint() }
        init {
            this.icon = icon
            horizontalAlignment = SwingConstants.LEFT
            iconTextGap = 12
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            foreground = SIDEBAR_FG
            border = BorderFactory.createEmptyBorder(10, 8, 10, 8)
            preferredSize = Dimension(180, 44)
            minimumSize = Dimension(120, 44)
            maximumSize = Dimension(Int.MAX_VALUE, 44)
        }
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val w = width
            val h = height
            val hover = model.isRollover
            val r = 10
            // background
            when {
                active -> {
                    g2.color = SIDEBAR_ACTIVE_BG
                    g2.fillRoundRect(0, 0, w, h, r, r)
                    // lime accent bar on the left
                    g2.color = LIME
                    g2.fillRoundRect(0, 0, 4, h, 6, 6)
                }
                hover -> {
                    g2.color = SIDEBAR_HOVER_BG
                    g2.fillRoundRect(0, 0, w, h, r, r)
                }
            }
            // text color
            foreground = if (active) Color.WHITE else SIDEBAR_FG
            super.paintComponent(g)
        }
    }

    // Small, simple icons for sidebar
    fun folderIcon(size: Int = 20): Icon = object : Icon {
        override fun getIconWidth() = size
        override fun getIconHeight() = size
        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = LIME
            val w = size; val h = size
            g2.fillRoundRect(x + w/10, y + h/4, (w*0.8).toInt(), (h*0.6).toInt(), 4, 4)
            g2.fillRoundRect(x, y + h/5, (w*0.55).toInt(), (h*0.28).toInt(), 4, 4)
        }
    }
    fun slidersIcon(size: Int = 20): Icon = object : Icon {
        override fun getIconWidth() = size
        override fun getIconHeight() = size
        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g2.color = LIME
            g2.stroke = BasicStroke((size/8f))
            val w = size; val h = size
            g2.drawLine(x + w/5, y + h/4, x + w - w/5, y + h/4)
            g2.drawLine(x + w/5, y + h/2, x + w - w/5, y + h/2)
            g2.drawLine(x + w/5, y + h - h/4, x + w - w/5, y + h - h/4)
        }
    }
    fun exportIcon(size: Int = 20): Icon = object : Icon {
        override fun getIconWidth() = size
        override fun getIconHeight() = size
        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = LIME
            val w = size; val h = size
            // box
            g2.drawRoundRect(x + w/6, y + h/3, (w*0.66).toInt(), (h*0.5).toInt(), 4, 4)
            // arrow up-right
            g2.stroke = BasicStroke(2f)
            g2.drawLine(x + w/3, y + h/2, x + w - w/5, y + h/3)
            g2.drawLine(x + w - w/5, y + h/3, x + w - w/5 - w/6, y + h/3)
            g2.drawLine(x + w - w/5, y + h/3, x + w - w/5, y + h/3 + h/6)
        }
    }

    /** Primary CTA button with gradient; includes a leading circle-plus icon. */
    fun primaryButton(text: String, onClick: () -> Unit): JButton = object : JButton(text) {
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val w = width
            val h = height
            val r = 12
            val hover = model.isRollover
            val pressed = model.isArmed && model.isPressed
            val c1 = if (pressed) GRADIENT_START.darker() else if (hover) GRADIENT_START_HOVER else GRADIENT_START
            val c2 = if (pressed) GRADIENT_END.darker() else if (hover) GRADIENT_END_HOVER else GRADIENT_END
            val paint = GradientPaint(0f, 0f, c1, w.toFloat(), h.toFloat(), c2)
            g2.paint = paint
            g2.fillRoundRect(0, 0, w, h, r, r)
            super.paintComponent(g)
        }
    }.apply {
        addActionListener { onClick() }
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        isRolloverEnabled = true
        border = BorderFactory.createEmptyBorder(10, 20, 10, 20)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        icon = PlusInCircleIcon(18, ICON_CIRCLE_DARK, ICON_PLUS_LIGHT)
        iconTextGap = 10
        foreground = TEXT_ON_PRIMARY
        font = font.deriveFont(Font.BOLD, font.size2D + 1.5f)
        isFocusPainted = false
    }

    /** Smaller solid green primary button (used in per-card actions). Rounded corners = 5px. */
    fun primarySmallButton(text: String, onClick: () -> Unit): JButton = object : JButton(text) {
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val w = width
            val h = height
            val r = 5 // corner radius in pixels
            g2.color = background
            // arc width/height should be ~2x radius for Swing's round-rect
            g2.fillRoundRect(0, 0, w, h, r * 2, r * 2)
            super.paintComponent(g)
        }
    }.apply {
        addActionListener { onClick() }
        background = GREEN
        foreground = Color.BLACK
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        border = BorderFactory.createEmptyBorder(4, 10, 4, 10)
        font = font.deriveFont(Font.BOLD)
        isFocusPainted = false
    }

    /** Secondary button: dark surface with thin rounded border, bold label. */
    fun styleSecondary(btn: AbstractButton) {
        btn.isOpaque = true
        btn.background = SURFACE_HIGH
        btn.foreground = FG_PRIMARY
        btn.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER, 1, true),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)
        )
        btn.isFocusPainted = false
        btn.font = btn.font.deriveFont(Font.BOLD)
    }

    // Small painter for the leading plus-in-circle icon on the primary CTA
    private class PlusInCircleIcon(
        private val size: Int,
        private val circleColor: Color,
        private val plusColor: Color
    ) : Icon {
        override fun getIconWidth(): Int = size
        override fun getIconHeight(): Int = size
        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
            if (g == null) return
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val r = size
            g2.color = circleColor
            g2.fillOval(x, y, r, r)
            g2.color = plusColor
            val bar = (r * 0.16).toInt().coerceAtLeast(2)
            val len = (r * 0.52).toInt()
            val cx = x + r / 2
            val cy = y + r / 2
            g2.fillRoundRect(cx - len / 2, cy - bar / 2, len, bar, bar, bar)
            g2.fillRoundRect(cx - bar / 2, cy - len / 2, bar, len, bar, bar)
        }
    }
}
