package org.litvin.ui.tabs.crop

import org.litvin.media.PlayerStatus
import org.litvin.media.SwingMediaPlayer
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.AppShortcuts
import org.litvin.ui.commons.ScrubBar
import org.litvin.ui.tabs.crop.presenter.CropRotateIntent
import org.litvin.ui.tabs.crop.presenter.CropRotatePresenter
import org.litvin.ui.tabs.crop.presenter.CropRotateView
import org.litvin.ui.tabs.crop.presenter.CropRotateViewEffect
import org.litvin.ui.tabs.crop.presenter.CropRotateViewState
import org.litvin.ui.tabs.crop.presenter.DefaultCropRotatePresenter
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/**
 * Transform tab with a live video preview, like the Colors tab.
 *
 * The player shows the rotated full frame. [CropEditorOverlay] draws the crop rectangle and the handles
 * over the video. The right panel keeps the numeric transform controls.
 */
class SwingCropRotatePanel(
    private val player: SwingMediaPlayer,
    private val presenter: CropRotatePresenter = DefaultCropRotatePresenter(),
    private val onHelp: () -> Unit = {},
) : JPanel(BorderLayout()), CropRotateView {
    private val rightPanelWidth = 400
    private val closed = AtomicBoolean(false)
    private var pendingMediaFile: File? = null
    private var loadedMediaFile: File? = null

    private val editor = CropEditorOverlay(player) { adjustments ->
        presenter.onIntent(CropRotateIntent.ChangeTransform(adjustments))
    }

    private val transformControls = CropTransformControls(
        onChanged = { adjustments -> presenter.onIntent(CropRotateIntent.ChangeTransform(adjustments)) },
        onResetTransform = { presenter.onIntent(CropRotateIntent.ResetTransform) },
        onHelp = { onHelp() },
    )

    private val playPauseBtn: JButton = UiStyles.squarePrimaryButton(UiStyles.playIcon(28)) { togglePlayPause() }.apply {
        name = "crop-play-pause"
        accessibleContext.accessibleName = "Play or Pause"
        toolTipText = "SPACE - Play"
    }

    private val scrubBar = ScrubBar(
        onUserScrub = { target -> player.seek(target) },
        tooltip = "Seek",
        sliderComponentName = "crop-seek",
    )

    private val viewportPanel = JPanel(BorderLayout()).apply {
        name = "adj-cr-viewport"
        isOpaque = true
        background = Color.BLACK
        minimumSize = Dimension(640, 360)
        add(player.component, BorderLayout.CENTER)
    }

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

    init {
        leftPanel.add(viewportPanel, BorderLayout.CENTER)
        leftPanel.add(buildTransport(), BorderLayout.SOUTH)
        add(split, BorderLayout.CENTER)

        player.setCropEditing(true)
        installPlayerCallbacks()
        installKeyBindings()

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
        SwingUtilities.invokeLater {
            fixDivider()
            ensurePlayerLoaded()
        }
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
        ensurePlayerLoaded()
        player.activatePreview("crop/rotate activated")
    }

    fun onDeactivated() {
        player.pause()
        player.deactivatePreview("crop/rotate deactivated")
        presenter.onDeactivated()
    }

    fun dispose() {
        if (!closed.compareAndSet(false, true)) return
        onDeactivated()
        if (presenter is DefaultCropRotatePresenter) presenter.dispose() else presenter.detach()
        player.onTimeChanged = null
        player.onStatusChanged = null
        player.onReady = null
        player.onVideoBoundsChanged = null
        player.close()
    }

    override fun render(state: CropRotateViewState) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { render(state) }
            return
        }
        transformControls.render(state.adjustments)
        player.applyPreviewAdjustments(state.adjustments)
        editor.render(state.adjustments)
        val source = state.sourceVideo
        if (source != null && source != loadedMediaFile && source != pendingMediaFile) {
            pendingMediaFile = source
            ensurePlayerLoaded()
        }
    }

    override fun renderEffect(effect: CropRotateViewEffect) {
        when (effect) {
            is CropRotateViewEffect.ShowError -> JOptionPane.showMessageDialog(
                this,
                effect.message,
                "Transform",
                JOptionPane.WARNING_MESSAGE,
            )
        }
    }

    private fun ensurePlayerLoaded() {
        val file = pendingMediaFile ?: return
        val window = SwingUtilities.getWindowAncestor(player.component)
        if (!player.component.isDisplayable || window == null || !window.isShowing) return
        player.load(file)
        player.pause()
        pendingMediaFile = null
        loadedMediaFile = file
    }

    private var lastTimeUiUpdateAt = 0L

    private fun installPlayerCallbacks() {
        player.onTimeChanged = { ms ->
            EventQueue.invokeLater {
                val duration = player.totalDurationMs()
                if (duration > 0) {
                    val now = System.currentTimeMillis()
                    if (now - lastTimeUiUpdateAt >= 80L) {
                        scrubBar.setRange(0L, duration)
                        scrubBar.setPosition(ms)
                        lastTimeUiUpdateAt = now
                    }
                } else {
                    scrubBar.reset()
                }
                updatePlayPauseUi()
            }
        }
        player.onStatusChanged = { EventQueue.invokeLater { updatePlayPauseUi() } }
        player.onReady = {
            EventQueue.invokeLater {
                val duration = player.totalDurationMs()
                if (duration > 0) {
                    scrubBar.setRange(0L, duration)
                    scrubBar.setPosition(player.currentTimeMs())
                }
                updatePlayPauseUi()
            }
        }
    }

    private fun togglePlayPause() {
        if (player.status() == PlayerStatus.PLAYING) player.pause() else player.play()
        updatePlayPauseUi()
        player.component.requestFocusInWindow()
    }

    private fun updatePlayPauseUi() {
        val playing = player.status() == PlayerStatus.PLAYING
        playPauseBtn.icon = if (playing) UiStyles.pauseIcon(28) else UiStyles.playIcon(28)
        playPauseBtn.toolTipText = if (playing) "SPACE - Pause" else "SPACE - Play"
    }

    private fun installKeyBindings() {
        fun bind(target: JComponent, keyStroke: String, actionName: String, conditions: IntArray, action: () -> Unit) {
            conditions.forEach { condition ->
                target.getInputMap(condition).put(KeyStroke.getKeyStroke(keyStroke), actionName)
            }
            target.actionMap.put(actionName, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) = action()
            })
        }
        val anywhere = intArrayOf(JComponent.WHEN_IN_FOCUSED_WINDOW, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        bind(this, AppShortcuts.PLAY_PAUSE.keyStroke, "cropTogglePlayPause", anywhere) { togglePlayPause() }

        // Arrow keys move the crop rectangle only when the video has the focus (a click on the video gives it).
        val video = player.component as? JComponent ?: return
        val focused = intArrayOf(JComponent.WHEN_FOCUSED)
        bind(video, AppShortcuts.LEFT.keyStroke, "cropNudgeLeft", focused) { editor.nudge(-1, 0) }
        bind(video, AppShortcuts.RIGHT.keyStroke, "cropNudgeRight", focused) { editor.nudge(1, 0) }
        bind(video, AppShortcuts.UP.keyStroke, "cropNudgeUp", focused) { editor.nudge(0, -1) }
        bind(video, AppShortcuts.DOWN.keyStroke, "cropNudgeDown", focused) { editor.nudge(0, 1) }
        bind(video, AppShortcuts.SHIFT_LEFT.keyStroke, "cropNudgeLeftFast", focused) { editor.nudge(-10, 0) }
        bind(video, AppShortcuts.SHIFT_RIGHT.keyStroke, "cropNudgeRightFast", focused) { editor.nudge(10, 0) }
        bind(video, AppShortcuts.SHIFT_UP.keyStroke, "cropNudgeUpFast", focused) { editor.nudge(0, -10) }
        bind(video, AppShortcuts.SHIFT_DOWN.keyStroke, "cropNudgeDownFast", focused) { editor.nudge(0, 10) }
    }


    private fun buildTransport(): JPanel {
        val playRow = JPanel(FlowLayout(FlowLayout.CENTER, 0, 6)).apply {
            isOpaque = true
            background = UiStyles.SURFACE_HIGH
            add(playPauseBtn)
        }
        val scrubRow = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = Color(0x11, 0x11, 0x11)
            border = BorderFactory.createEmptyBorder(8, 16, 8, 16)
            scrubBar.setBarBackground(background)
            add(scrubBar, BorderLayout.CENTER)
        }
        return JPanel(BorderLayout()).apply {
            name = "adj-cr-transport"
            isOpaque = true
            background = UiStyles.DARK_BG
            add(playRow, BorderLayout.NORTH)
            add(scrubRow, BorderLayout.CENTER)
        }
    }

    private fun fixDivider() {
        val total = split.size.width
        if (total > 0) split.setDividerLocation((total - rightPanelWidth).coerceAtLeast(0))
    }
}
