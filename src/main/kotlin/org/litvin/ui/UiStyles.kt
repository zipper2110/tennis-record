package org.litvin.ui

import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.AbstractButton
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.DefaultListCellRenderer
import javax.swing.UIManager
import javax.swing.JCheckBox
// Ikonli (icon packs)
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.material2.Material2RoundMZ
import org.kordamp.ikonli.swing.FontIcon

/**
 * Shared Swing UI styles to match the mock (projects.html):
 * - Primary CTA button: rounded, lime gradient background with olive text and a leading plus icon (optional).
 * - Primary small button: solid neon green pill.
 * - Secondary button: subtle dark surface with thin rounded border.
 */
object UiStyles {
    // Small action icons for cards
    fun targetIcon(size: Int = 18) = ikon(Material2AL.ASSIGNMENT_TURNED_IN, size, LIME)

    fun plusCircleIcon(size: Int = 18) = ikon(Material2AL.ADD_CIRCLE, size, ICON_CIRCLE_DARK)

    fun pencilIcon(size: Int = 18): Icon = ikon(Material2AL.EDIT, size, LIME)

    fun crossIcon(size: Int = 18): Icon = ikon(Material2AL.BACKSPACE, size, Color(0xCC, 0x46, 0x46))

    fun smallIconButton(icon: Icon, tooltip: String? = null, onClick: () -> Unit): JButton = JButton().apply {
        this.icon = icon
        toolTipText = tooltip
        isFocusPainted = false
        isBorderPainted = false
        isContentAreaFilled = true
        background = SURFACE_HIGH
        foreground = FG_PRIMARY
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER, 1, true),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)
        )
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addActionListener { onClick() }
    }
    // Palette inspired by design/projects.html dark theme
    val DARK_BG: Color = Color(0x0E, 0x0E, 0x0E)            // background / surface-dim
    val SURFACE_HIGH: Color = Color(0x30, 0x30, 0x30)       // surface-container-high
    val CARD_BG: Color = Color(0x1A, 0x1A, 0x1A)            // surface-container
    val CARD_BORDER: Color = Color(0x26, 0x26, 0x26)        // surface-variant border
    val FG_PRIMARY: Color = Color(0xD8, 0xD8, 0xD8)         // on-surface
    val FG_SECONDARY: Color = Color(0xAD, 0xAA, 0xAA)       // on-surface-variant
    val GREEN: Color = Color(0xAF, 0xF6, 0x25)              // primary-fixed
    val YELLOW: Color = Color(0xFF, 0xD5, 0x4A)            // warning/emphasis

    // Sidebar specific palette (from mock)
    val SIDEBAR_BG: Color = Color(0x12, 0x12, 0x12)
    val SIDEBAR_FG: Color = Color(0xD8, 0xD8, 0xD8)
    val SIDEBAR_FG_MUTED: Color = Color(0x9A, 0x9A, 0x9A)
    val SIDEBAR_HOVER_BG: Color = Color(0x2C, 0x2C, 0x2C)
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

    // Transport icons (prefer Ikonli Feather pack when available)
    private fun ikon(ik: Ikon, size: Int, color: Color): Icon {
        return try {
            FontIcon.of(ik, size).also { it.iconColor = color }
        } catch (_: Throwable) {
            // Fallback: empty label icon of requested size; callers usually pair with background styling
            object : Icon {
                override fun getIconWidth() = size
                override fun getIconHeight() = size
                override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {}
            }
        }
    }

    fun playIcon(size: Int = 28) = ikon(Material2RoundMZ.PLAY_ARROW, size, Color(0x1A, 0x2E, 0x00))

    fun pauseIcon(size: Int = 28) = ikon(Material2RoundMZ.PAUSE, size, Color(0x1A, 0x2E, 0x00))

    fun seekRightIcon(size: Int = 18): Icon = ikon(Feather.CHEVRON_RIGHT, size, LIME)
    fun seekLeftIcon(size: Int = 18): Icon = ikon(Feather.CHEVRON_LEFT, size, LIME)

    fun forward5Icon(size: Int = 18): Icon = ikon(Feather.CHEVRONS_RIGHT, size, LIME)
    fun backward5Icon(size: Int = 18): Icon = ikon(Feather.CHEVRONS_LEFT, size, LIME)

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
                g2.paint = if (model.isRollover) GradientPaint(
                    0f,
                    0f,
                    GRADIENT_START_HOVER,
                    0f,
                    h.toFloat(),
                    GRADIENT_END_HOVER
                ) else grad
                g2.fillRoundRect(0,0,w,h,r,r)
                // icon
                this.icon.paintIcon(this, g2, (w - this.icon.iconWidth)/2, (h - this.icon.iconHeight)/2)
                // subtle inner shadow
                g2.color = Color(0, 0, 0, 40)
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
            // Center icon and place text under the icon
            horizontalAlignment = CENTER
            horizontalTextPosition = CENTER
            verticalTextPosition = BOTTOM
            iconTextGap = 6
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            foreground = SIDEBAR_FG
            border = BorderFactory.createEmptyBorder(8, 6, 8, 6)
            preferredSize = Dimension(180, 56)
            minimumSize = Dimension(0, 56)
            maximumSize = Dimension(Int.MAX_VALUE, 56)
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
    fun folderIcon(size: Int = 20): Icon = ikon(Material2MZ.SOURCE, size, LIME)

    fun colorsIcon(size: Int = 20): Icon = ikon(Material2AL.COLOR_LENS, size, LIME)

    fun exportIcon(size: Int = 20): Icon = ikon(Feather.FILM, size, LIME)

    // Tab icon: Crop (prefer Ikonli Feather.CROP with fallback)
    fun cropRotateIcon(size: Int = 20): Icon = ikon(Material2AL.CROP_ROTATE, size, LIME)

    // Tab icon: Crop (prefer Ikonli Feather.CROP with fallback)
    fun rallyIcon(size: Int = 20): Icon = ikon(Material2MZ.SPORTS_TENNIS, size, LIME)

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
        icon = plusCircleIcon(size = 22)
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
        foreground = TEXT_ON_PRIMARY
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        border = BorderFactory.createEmptyBorder(4, 10, 4, 10)
        font = font.deriveFont(Font.BOLD)
        isFocusPainted = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
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

    /**
     * Apply secondary/tertiary text style for labels and helper texts on card-like surfaces.
     * - Foreground: FG_SECONDARY
     * - Background: CARD_BG
     * - Slightly reduce font size to de-emphasize
     */
    fun styleHelper(c: JComponent) {
        c.foreground = FG_SECONDARY
        c.background = CARD_BG
        try {
            val f = c.font
            if (f != null) c.font = f.deriveFont((f.size2D - 1f).coerceAtLeast(11f))
        } catch (_: Throwable) { }
    }

    /** Primary text style on card-like surfaces: FG_PRIMARY on CARD_BG. */
    fun stylePrimary(c: JComponent) {
        c.foreground = FG_PRIMARY
        c.background = CARD_BG
    }

    /** Monospace variant of [styleHelper] using Consolas when available. */
    fun styleMono(c: JComponent) {
        styleHelper(c)
        try {
            c.font = Font("Consolas", Font.PLAIN, c.font.size)
        } catch (_: Throwable) { }
    }

    /** Apply dark theme styling for JComboBox controls (editor + popup items). */
    fun <T> styleComboBox(cb: JComboBox<T>) {
        try {
            cb.isOpaque = true
            cb.background = SURFACE_HIGH
            cb.foreground = FG_PRIMARY
            cb.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER, 1, true),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)
            )
            cb.isFocusable = true

            // Dark arrow/button area on the right
            try {
                cb.ui = object : javax.swing.plaf.basic.BasicComboBoxUI() {
                    override fun createArrowButton(): JButton {
                        val icon = object : Icon {
                            override fun getIconWidth() = 10
                            override fun getIconHeight() = 6
                            override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
                                val g2 = g as Graphics2D
                                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                                g2.color = FG_SECONDARY
                                val w = iconWidth; val h = iconHeight
                                val px = intArrayOf(x, x + w / 2, x + w)
                                val py = intArrayOf(y, y + h, y)
                                g2.fillPolygon(px, py, 3)
                            }
                        }
                        return object : JButton(icon) {
                            init {
                                isContentAreaFilled = false
                                isOpaque = true
                                background = SURFACE_HIGH
                                foreground = FG_PRIMARY
                                border = BorderFactory.createMatteBorder(0, 1, 0, 0, CARD_BORDER)
                                isFocusPainted = false
                                isBorderPainted = true
                                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                            }
                            override fun paintComponent(g: Graphics) {
                                val g2 = g as Graphics2D
                                g2.color = SURFACE_HIGH
                                g2.fillRect(0, 0, width, height)
                                super.paintComponent(g)
                            }
                        }
                    }
                }
            } catch (_: Throwable) { }

            // Renderer for both the selected value (index == -1) and dropdown items
            cb.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
                ): Component {
                    val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                    // Apply list-level colors too (helps popup background)
                    try {
                        list?.background = CARD_BG
                        list?.foreground = FG_PRIMARY
                        list?.selectionBackground = SIDEBAR_HOVER_BG
                        list?.selectionForeground = FG_PRIMARY
                    } catch (_: Throwable) { }
                    c.isOpaque = true
                    c.background = if (isSelected) SIDEBAR_HOVER_BG else CARD_BG
                    c.foreground = FG_PRIMARY
                    c.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
                    return c
                }
            }

            // Ensure the popup menu itself is dark and has dark border — best-effort via UIManager keys
            // (Listener approach is avoided for cross‑LAF reliability.)

            // Hint UI defaults to keep popup consistent when LAF reads UIManager
            try {
                UIManager.put("ComboBox.background", SURFACE_HIGH)
                UIManager.put("ComboBox.foreground", FG_PRIMARY)
                UIManager.put("ComboBox.selectionBackground", SIDEBAR_HOVER_BG)
                UIManager.put("ComboBox.selectionForeground", FG_PRIMARY)
                UIManager.put("ComboBox.border", BorderFactory.createLineBorder(CARD_BORDER))
                UIManager.put("ComboBox.popupBackground", CARD_BG)
                UIManager.put("ComboBox.disabledForeground", FG_SECONDARY)
                UIManager.put("ComboBox.buttonBackground", SURFACE_HIGH)
                UIManager.put("ComboBox.buttonHoverBackground", SURFACE_HIGH)
                UIManager.put("ComboBox.buttonPressedBackground", SURFACE_HIGH)
                UIManager.put("ComboBox.buttonArrowColor", FG_SECONDARY)
                UIManager.put("ComboBox.borderColor", CARD_BORDER)
                // Popup menu fallbacks (some LAFs read these keys for combo popups)
                UIManager.put("PopupMenu.background", CARD_BG)
                UIManager.put("PopupMenu.foreground", FG_PRIMARY)
                UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(CARD_BORDER, 1, true))
            } catch (_: Throwable) { }
        } catch (_: Throwable) { }
    }

    /**
     * Simple card container with title header and body.
     * - Background: CARD_BG, Foreground: FG_PRIMARY
     * - Thin rounded border with CARD_BORDER
     * - 12px internal padding
     */
    fun card(title: String, body: JComponent): JPanel {
        val container = JPanel(BorderLayout())
        container.background = CARD_BG
        container.foreground = FG_PRIMARY
        container.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)
        )
        val header = JLabel(title)
        header.font = header.font.deriveFont(Font.BOLD)
        header.foreground = FG_PRIMARY
        container.add(header, BorderLayout.NORTH)
        container.add(body, BorderLayout.CENTER)
        return container
    }

    /** Small badge label with custom background/foreground and compact padding. */
    fun smallBadge(text: String, bg: Color, fg: Color): JLabel = JLabel(text).apply {
        isOpaque = true
        background = bg
        foreground = fg
        border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
        font = font.deriveFont(10f)
    }

    /** Apply dark theme styling to JCheckBox with custom minimalist box and checkmark. */
    fun styleCheckBox(cb: JCheckBox) {
        try {
            cb.isOpaque = false
            cb.foreground = FG_PRIMARY
            cb.background = CARD_BG
            cb.border = BorderFactory.createEmptyBorder(2, 2, 2, 2)
            cb.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

            fun boxIcon(selected: Boolean, disabled: Boolean = false): Icon = object : Icon {
                private val size = 16
                override fun getIconWidth() = size
                override fun getIconHeight() = size
                override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    val w = size; val h = size
                    val r = 4
                    // Box background
                    val bgCol = if (disabled) Color(0x1F,0x1F,0x1F) else SURFACE_HIGH
                    g2.color = bgCol
                    g2.fillRoundRect(x, y, w, h, r, r)
                    // Border
                    g2.color = if (disabled) CARD_BORDER.darker() else CARD_BORDER
                    g2.drawRoundRect(x, y, w - 1, h - 1, r, r)
                    if (selected) {
                        // Fill with accent tint and draw check
                        val fill = if (disabled) Color(0x3A,0x3A,0x2F) else Color(0x22, 0x2F, 0x16)
                        g2.color = fill
                        g2.fillRoundRect(x + 1, y + 1, w - 2, h - 2, r, r)
                        // Check mark
                        g2.color = if (disabled) FG_SECONDARY else LIME
                        g2.stroke = BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                        val p = java.awt.geom.Path2D.Float()
                        p.moveTo((x + w*0.26f), (y + h*0.54f))
                        p.lineTo((x + w*0.44f), (y + h*0.72f))
                        p.lineTo((x + w*0.78f), (y + h*0.30f))
                        g2.draw(p)
                    }
                }
            }
            cb.icon = boxIcon(false, disabled = false)
            cb.selectedIcon = boxIcon(true, disabled = false)
            cb.disabledIcon = boxIcon(false, disabled = true)
            cb.disabledSelectedIcon = boxIcon(true, disabled = true)
            // Keep text spacing pleasant
            cb.iconTextGap = 8
        } catch (_: Throwable) { }
    }

    /** Green circle with white checkmark icon for scored points. */
    fun scoredIcon(size: Int = 18): Icon = object : Icon {
        override fun getIconWidth(): Int = size
        override fun getIconHeight(): Int = size
        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val d = size
            val green = Color(0x71, 0xB4, 0x00)
            // Draw filled green circle
            g2.color = green
            g2.fillOval(x, y, d, d)
            // Draw white checkmark
            val s = d.toDouble()
            val p = java.awt.geom.Path2D.Double()
            p.moveTo(x + 0.28 * s, y + 0.55 * s)
            p.lineTo(x + 0.45 * s, y + 0.72 * s)
            p.lineTo(x + 0.75 * s, y + 0.35 * s)
            g2.color = Color.WHITE
            g2.stroke = BasicStroke((d * 0.12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g2.draw(p)
        }
    }
}
