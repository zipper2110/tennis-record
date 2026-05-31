package org.litvin.ui.tabs.adjustments

import org.litvin.ui.UiStyles
import java.awt.BorderLayout
import java.awt.Dimension
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.prefs.Preferences
import javax.swing.JSplitPane
import javax.swing.JPanel

/**
 * Crop/Rotate Tab — T1 (Layout scaffolding per spec v0.3.0)
 *
 * Requirements:
 * - Split layout: Left (preview stack) and Right (controls column)
 * - Resizable; remember divider location between sessions
 * - Minimum sizes: Left ≥ 640×360; Right ≥ 280 px width
 * - Component IDs: "adj-cr-root", "adj-cr-left", "adj-cr-right"
 */
class SwingCropRotatePanel : JPanel(BorderLayout()) {

    private val prefs: Preferences = Preferences.userNodeForPackage(SwingCropRotatePanel::class.java)
    private val dividerPrefKey = "adj.cr.split.divider"

    private val leftPanel = JPanel(BorderLayout()).apply {
        name = "adj-cr-left"
        minimumSize = Dimension(640, 360)
        isOpaque = true
        background = UiStyles.DARK_BG
    }

    // T2 — Left preview stack: header + canvas + seek bar
    private val headerPanel = JPanel(BorderLayout()).apply {
        name = "adj-cr-left-header"
        isOpaque = true
        background = UiStyles.DARK_BG
        val title = javax.swing.JLabel("Crop")
        title.foreground = java.awt.Color.WHITE
        val resetBtn = javax.swing.JButton("Reset")
        // Action will be wired by later tasks; for now it's a placeholder per T2
        add(title, BorderLayout.WEST)
        add(resetBtn, BorderLayout.EAST)
    }

    private val canvasPanel = JPanel(BorderLayout()).apply {
        name = "adj-cr-canvas"
        isOpaque = true
        background = java.awt.Color.BLACK
        minimumSize = Dimension(640, 360)
    }

    private val seekSlider = javax.swing.JSlider(0, 1000, 0).apply {
        name = "adj-cr-seek"
        toolTipText = "Seek"
        paintLabels = false
        paintTicks = false
        snapToTicks = false
    }

    private val seekRow = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = UiStyles.DARK_BG
        add(seekSlider, BorderLayout.CENTER)
    }

    private val rightPanel = JPanel(BorderLayout()).apply {
        name = "adj-cr-right"
        minimumSize = Dimension(280, 360)
        preferredSize = Dimension(320, 600)
        isOpaque = true
        background = UiStyles.DARK_BG
    }

    private val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel).apply {
        name = "adj-cr-root"
        // Let the left grow with the window, keep right at fixed width as much as possible
        resizeWeight = 1.0
        isContinuousLayout = true
        dividerSize = 8
        background = UiStyles.DARK_BG
    }

    private val dividerListener = PropertyChangeListener { evt: PropertyChangeEvent ->
        if (evt.propertyName == JSplitPane.DIVIDER_LOCATION_PROPERTY) {
            val loc = (evt.newValue as? Int) ?: return@PropertyChangeListener
            prefs.putInt(dividerPrefKey, loc)
        }
    }

    init {
        // Compose left preview stack per T2
        leftPanel.add(headerPanel, BorderLayout.NORTH)
        leftPanel.add(canvasPanel, BorderLayout.CENTER)
        leftPanel.add(seekRow, BorderLayout.SOUTH)

        add(split, BorderLayout.CENTER)
        // Restore divider location if we have one; otherwise set ~68% on first layout
        val saved = prefs.getInt(dividerPrefKey, -1)
        if (saved >= 0) {
            split.dividerLocation = saved
        } else {
            // Will set proportionally in addNotify after we know the size
        }
        split.addPropertyChangeListener(dividerListener)
    }

    override fun addNotify() {
        super.addNotify()
        // If no saved divider location, initialize to ~68/32 of current width
        val saved = prefs.getInt(dividerPrefKey, -1)
        if (saved < 0) {
            val w = width.takeIf { it > 0 } ?: 1000 // fallback
            split.dividerLocation = (w * 0.68).toInt()
        }
    }
}
