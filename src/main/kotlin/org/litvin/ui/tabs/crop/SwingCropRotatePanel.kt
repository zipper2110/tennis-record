package org.litvin.ui.tabs.crop

import org.litvin.shared.util.Timecode
import org.litvin.ui.UiStyles
import org.litvin.ui.tabs.crop.presenter.CropRotateIntent
import org.litvin.ui.tabs.crop.presenter.CropRotatePresenter
import org.litvin.ui.tabs.crop.presenter.CropRotateView
import org.litvin.ui.tabs.crop.presenter.CropRotateViewEffect
import org.litvin.ui.tabs.crop.presenter.CropRotateViewState
import org.litvin.ui.tabs.crop.presenter.DefaultCropRotatePresenter
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JSplitPane
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

class SwingCropRotatePanel(
    private val presenter: CropRotatePresenter = DefaultCropRotatePresenter(),
) : JPanel(BorderLayout()), CropRotateView {
    private val rightPanelWidth = 400
    private var updatingFromState = false
    private var currentState = CropRotateViewState()

    private val canvas = CropRotateCanvas { adjustments ->
        presenter.onIntent(CropRotateIntent.ChangeTransform(adjustments))
    }

    private val seekSlider = JSlider(0, 10000, 0).apply {
        name = "adj-cr-seek"
        toolTipText = "Seek"
        paintLabels = false
        paintTicks = false
        snapToTicks = false
    }

    private val transformControls = CropTransformControls(
        onChanged = { adjustments -> presenter.onIntent(CropRotateIntent.ChangeTransform(adjustments)) },
        onResetTransform = { presenter.onIntent(CropRotateIntent.ResetTransform) },
    )

    private val leftPanel = JPanel(BorderLayout()).apply {
        name = "adj-cr-left"
        minimumSize = Dimension(640, 360)
        isOpaque = true
        background = UiStyles.DARK_BG
    }

    private val rightPanel = JPanel(BorderLayout()).apply {
        name = "adj-cr-right"
        minimumSize = Dimension(rightPanelWidth, 360)
        preferredSize = Dimension(rightPanelWidth, 600)
        maximumSize = Dimension(rightPanelWidth, Int.MAX_VALUE)
        isOpaque = true
        background = UiStyles.DARK_BG
        add(transformControls, BorderLayout.CENTER)
    }

    private val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel).apply {
        name = "adj-cr-root"
        resizeWeight = 1.0
        isContinuousLayout = true
        isEnabled = false
        dividerSize = 0
        background = UiStyles.DARK_BG
    }

    private val seekListener = ChangeListener { _: ChangeEvent ->
        if (updatingFromState) return@ChangeListener
        val duration = currentState.durationMs
        if (duration <= 0L) return@ChangeListener
        val target = ((seekSlider.value / seekSlider.maximum.toDouble()) * duration).toLong()
            .coerceIn(0L, duration)
        seekSlider.toolTipText = Timecode.format(target)
        presenter.onIntent(CropRotateIntent.SeekTo(target, immediate = !seekSlider.valueIsAdjusting))
    }

    init {
        leftPanel.add(buildHeader(), BorderLayout.NORTH)
        leftPanel.add(canvas, BorderLayout.CENTER)
        leftPanel.add(buildSeekRow(), BorderLayout.SOUTH)
        add(split, BorderLayout.CENTER)

        seekSlider.addChangeListener(seekListener)
        SwingUtilities.invokeLater { fixDivider() }
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                fixDivider()
            }
        })
    }

    override fun addNotify() {
        super.addNotify()
        presenter.attach(this)
        SwingUtilities.invokeLater { fixDivider() }
    }

    override fun removeNotify() {
        presenter.detach()
        super.removeNotify()
    }

    fun setProjectManifest(path: String) {
        presenter.onIntent(CropRotateIntent.LoadProject(path))
    }

    fun onActivated() {
        presenter.onActivated()
    }

    fun onDeactivated() {
        presenter.onDeactivated()
    }

    fun dispose() {
        if (presenter is DefaultCropRotatePresenter) presenter.dispose()
    }

    override fun render(state: CropRotateViewState) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { render(state) }
            return
        }
        currentState = state
        updatingFromState = true
        try {
            val duration = state.durationMs
            seekSlider.isEnabled = duration > 0L
            seekSlider.value = if (duration > 0L) {
                ((state.seekMs.toDouble() / duration.toDouble()) * seekSlider.maximum).toInt()
                    .coerceIn(seekSlider.minimum, seekSlider.maximum)
            } else {
                0
            }
            seekSlider.toolTipText = Timecode.format(state.seekMs.coerceAtLeast(0L))
            transformControls.render(state.adjustments)
            canvas.render(state.frame, state.adjustments, state.outputAspect, state.frameLoading, state.banner)
        } finally {
            updatingFromState = false
        }
    }

    override fun renderEffect(effect: CropRotateViewEffect) {
        when (effect) {
            is CropRotateViewEffect.ShowError -> JOptionPane.showMessageDialog(
                this,
                effect.message,
                "Crop & Rotate",
                JOptionPane.WARNING_MESSAGE,
            )
        }
    }

    private fun buildHeader(): JPanel {
        return JPanel(BorderLayout()).apply {
            name = "adj-cr-left-header"
            isOpaque = true
            background = UiStyles.DARK_BG
            border = BorderFactory.createEmptyBorder(10, 14, 10, 14)
            val title = JLabel("Crop & Rotate").apply {
                foreground = Color.WHITE
                font = font.deriveFont(font.style, font.size2D + 2.0f)
            }
            val resetBtn = JButton("Reset All").apply {
                toolTipText = "Reset all adjustments"
                addActionListener { presenter.onIntent(CropRotateIntent.ResetAll) }
            }
            add(title, BorderLayout.WEST)
            add(resetBtn, BorderLayout.EAST)
        }
    }

    private fun buildSeekRow(): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UiStyles.DARK_BG
            border = BorderFactory.createEmptyBorder(8, 14, 10, 14)
            add(seekSlider, BorderLayout.CENTER)
        }
    }

    private fun fixDivider() {
        val total = split.size.width
        if (total > 0) {
            split.setDividerLocation((total - rightPanelWidth).coerceAtLeast(0))
        }
    }
}
