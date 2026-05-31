package org.litvin.ui.commons

import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.feather.Feather
import org.kordamp.ikonli.swing.FontIcon

/**
 * Simple in-app icon browser for Ikonli Feather pack.
 * Lets you search and preview icons and copy the enum name.
 */
class IconBrowserDialog(owner: Window?) : JDialog(owner, "Icon Browser — Feather", ModalityType.MODELESS) {
    private val txtSearch = JTextField()
    private val sizeSlider = JSlider(12, 64, 24)
    private val gridPanel = JPanel(WrapLayout(FlowLayout.LEFT, 10, 10))

    init {
        minimumSize = Dimension(720, 520)
        preferredSize = Dimension(900, 700)
        contentPane = JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }

        val topPanel = JPanel(BorderLayout(8, 8))
        val searchPanel = JPanel(BorderLayout(6, 6))
        searchPanel.add(JLabel("Search:"), BorderLayout.WEST)
        searchPanel.add(txtSearch, BorderLayout.CENTER)
        val sizePanel = JPanel(BorderLayout(6, 6))
        sizePanel.add(JLabel("Size:"), BorderLayout.WEST)
        sizeSlider.majorTickSpacing = 8
        sizeSlider.paintTicks = true
        sizePanel.add(sizeSlider, BorderLayout.CENTER)
        topPanel.add(searchPanel, BorderLayout.CENTER)
        topPanel.add(sizePanel, BorderLayout.EAST)
        (contentPane as JPanel).add(topPanel, BorderLayout.NORTH)

        val scroll = JScrollPane(gridPanel)
        scroll.border = BorderFactory.createEmptyBorder()
        (contentPane as JPanel).add(scroll, BorderLayout.CENTER)

        // Events
        val updater = {
            refreshGrid()
        }
        txtSearch.addKeyListener(object: KeyAdapter() {
            override fun keyReleased(e: KeyEvent) { updater.invoke() }
        })
        sizeSlider.addChangeListener { updater.invoke() }

        refreshGrid()
        pack()
        setLocationRelativeTo(owner)
    }

    private fun makeCard(name: String, ikon: Ikon, size: Int): JComponent {
        val icon = try { FontIcon.of(ikon, size).also { it.iconColor = Color.WHITE } } catch (_: Throwable) { null }
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0x33,0x33,0x33), 1, true),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        )
        panel.background = Color(0x1A,0x1A,0x1A)
        val iconLabel = JLabel(icon)
        iconLabel.horizontalAlignment = SwingConstants.CENTER
        iconLabel.preferredSize = Dimension(72, 48)
        val nameLabel = JLabel(name)
        nameLabel.foreground = Color(0xDD,0xDD,0xDD)
        nameLabel.font = nameLabel.font.deriveFont(nameLabel.font.size2D - 1f)
        panel.add(iconLabel, BorderLayout.CENTER)
        panel.add(nameLabel, BorderLayout.SOUTH)
        panel.toolTipText = name
        panel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        panel.addMouseListener(object: java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                val sel = name
                val cb = Toolkit.getDefaultToolkit().systemClipboard
                cb.setContents(java.awt.datatransfer.StringSelection(sel), null)
                JOptionPane.showMessageDialog(this@IconBrowserDialog, "Copied: $sel")
            }
        })
        return panel
    }

    private fun refreshGrid() {
        val query = txtSearch.text.trim().lowercase()
        val size = sizeSlider.value
        gridPanel.removeAll()
        val icons = Feather.values()
        var shown = 0
        for (ik in icons) {
            val name = ik.name
            if (query.isNotEmpty() && !name.lowercase().contains(query)) continue
            gridPanel.add(makeCard(name, ik, size))
            shown++
        }
        if (shown == 0) {
            gridPanel.add(JLabel("No icons match your search."))
        }
        gridPanel.revalidate()
        gridPanel.repaint()
    }
}

/**
 * A FlowLayout that supports wrapping nicely inside a scrollpane.
 * Source adapted from Rob Camick's WrapLayout (public domain-like).
 */
class WrapLayout(align: Int = FlowLayout.LEFT, hgap: Int = 5, vgap: Int = 5) : FlowLayout(align, hgap, vgap) {
    override fun preferredLayoutSize(target: Container): Dimension {
        return layoutSize(target, true)
    }
    override fun minimumLayoutSize(target: Container): Dimension {
        val minimum = layoutSize(target, false)
        minimum.width -= (hgap + 1)
        return minimum
    }
    private fun layoutSize(target: Container, preferred: Boolean): Dimension {
        synchronized(target.treeLock) {
            val targetWidth = target.width
            if (targetWidth == 0) return super.preferredLayoutSize(target)
            var hgap = hgap
            var vgap = vgap
            val insets = target.insets
            val maxWidth = targetWidth - (insets.left + insets.right + hgap * 2)
            var x = 0
            var y = insets.top + vgap
            var rowHeight = 0
            val nmembers = target.componentCount
            for (i in 0 until nmembers) {
                val m = target.getComponent(i)
                if (!m.isVisible) continue
                val d = if (preferred) m.preferredSize else m.minimumSize
                if (x == 0 || x + d.width <= maxWidth) {
                    if (x > 0) x += hgap
                    x += d.width
                    rowHeight = maxOf(rowHeight, d.height)
                } else {
                    x = d.width
                    y += vgap + rowHeight
                    rowHeight = d.height
                }
            }
            y += rowHeight
            y += insets.bottom
            return Dimension(maxWidth, y)
        }
    }
}
