package org.litvin

import org.litvin.media.VlcjSwingMediaPlayerAdapter
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseWheelEvent
import java.io.File
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 1 — Media preview MVP in Swing.
 *
 * Features:
 * - Open file via JFileChooser
 * - Play/Pause/Stop/Seek controls and position slider
 * - Time display (current/total)
 * - Keyboard controls: J/K/L, Space, Left/Right small seeks
 * - Mouse wheel seek (with modifiers for step sizes)
 * - Proper disposal of VLCJ resources
 */
class SwingVideoTestPanel : JPanel(BorderLayout()), ChangeListener {

    private val player = VlcjSwingMediaPlayerAdapter()

    private val btnOpen = JButton("Open…")
    private val btnPlay = JButton("Play")
    private val btnPause = JButton("Pause")
    private val btnStop = JButton("Stop")

    private val timeLabel = JLabel("00:00.000 / 00:00.000")

    private val slider = JSlider(0, 1000, 0) // 0..1000 as percentage; we'll map to duration
    private var suppressSliderEvents = false

    private var loadedFile: File? = null

    init {
        // Top toolbar
        val toolbar = JPanel()
        toolbar.layout = BoxLayout(toolbar, BoxLayout.X_AXIS)
        toolbar.border = BorderFactory.createEmptyBorder(6, 6, 6, 6)

        listOf(btnOpen, btnPlay, btnPause, btnStop).forEach { b ->
            toolbar.add(b)
            toolbar.add(Box.createRigidArea(Dimension(8, 0)))
        }

        toolbar.add(Box.createHorizontalStrut(12))
        toolbar.add(JLabel("Position:"))
        toolbar.add(Box.createRigidArea(Dimension(6, 0)))
        slider.preferredSize = Dimension(300, 24)
        toolbar.add(slider)
        toolbar.add(Box.createRigidArea(Dimension(12, 0)))
        toolbar.add(timeLabel)

        add(toolbar, BorderLayout.NORTH)
        add(player.component, BorderLayout.CENTER)

        // Wire actions
        btnOpen.addActionListener { onOpen() }
        btnPlay.addActionListener { player.play() }
        btnPause.addActionListener { player.pause() }
        btnStop.addActionListener {
            player.stop()
            updateTime(0L, player.totalDurationMs())
            setSliderFromTime(0L)
        }

        slider.addChangeListener(this)

        // Callbacks from player
        player.onTimeChanged = { t ->
            EventQueue.invokeLater {
                updateTime(t, player.totalDurationMs())
                setSliderFromTime(t)
            }
        }
        player.onReady = {
            EventQueue.invokeLater {
                // Update total duration in label
                updateTime(player.currentTimeMs(), player.totalDurationMs())
            }
        }

        // Keyboard shortcuts
        installKeyBindings()

        // Mouse wheel seek over the video surface
        player.component.addMouseWheelListener { e -> onMouseWheel(e) }
    }

    private fun onOpen() {
        val chooser = JFileChooser(loadedFile?.parentFile)
        val ret = chooser.showOpenDialog(this)
        if (ret == JFileChooser.APPROVE_OPTION) {
            val f = chooser.selectedFile
            try {
                loadedFile = f
                player.load(f)
            } catch (t: Throwable) {
                t.printStackTrace()
                SwingDialogUtils.showError(this, t, "Failed to open file")
            }
        }
    }

    private fun installKeyBindings() {
        fun bind(key: String, actionName: String, runnable: () -> Unit) {
            val am = this.actionMap
            val im = this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            im.put(KeyStroke.getKeyStroke(key), actionName)
            am.put(actionName, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) { runnable() }
            })
        }
        // Space toggles play/pause
        bind("SPACE", "toggle") {
            when (player.status()) {
                org.litvin.media.PlayerStatus.PLAYING -> player.pause()
                else -> player.play()
            }
        }
        // K pause, L play
        bind("K", "pause") { player.pause() }
        bind("L", "play") { player.play() }
        // J small back seek (200 ms)
        bind("J", "backSmall") { seekBy(-200) }
        // Left/Right small seeks (1s)
        bind("LEFT", "seekLeft") { seekBy(-1000) }
        bind("RIGHT", "seekRight") { seekBy(1000) }
    }

    private fun onMouseWheel(e: MouseWheelEvent) {
        val notches = e.wheelRotation
        val step = when {
            e.isShiftDown -> 100 // fine
            e.isControlDown -> 5000 // big
            else -> 1000 // normal
        }
        seekBy(step * -notches)
    }

    private fun seekBy(deltaMs: Int) {
        val cur = player.currentTimeMs()
        val target = max(0L, cur + deltaMs)
        player.seek(target)
    }

    override fun stateChanged(e: ChangeEvent) {
        if (suppressSliderEvents) return
        if (!slider.valueIsAdjusting) {
            val percent = slider.value / 1000.0
            val total = max(1L, player.totalDurationMs())
            val target = (total * percent).toLong()
            player.seek(target)
        }
    }

    private fun setSliderFromTime(currentMs: Long) {
        val total = max(1L, player.totalDurationMs())
        val pct = (currentMs.toDouble() / total).coerceIn(0.0, 1.0)
        suppressSliderEvents = true
        try {
            slider.value = (pct * 1000).toInt()
        } finally {
            suppressSliderEvents = false
        }
    }

    private fun updateTime(currentMs: Long, totalMs: Long) {
        timeLabel.text = "${formatTime(currentMs)} / ${formatTime(totalMs)}"
    }

    fun dispose() {
        try { player.dispose() } catch (_: Throwable) {}
    }

    companion object {
        private fun formatTime(ms: Long): String {
            val m = ms.coerceAtLeast(0L)
            val hours = m / 3_600_000
            val minutes = (m % 3_600_000) / 60_000
            val seconds = (m % 60_000) / 1000
            val millis = m % 1000
            return if (hours > 0)
                String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, millis)
            else
                String.format("%02d:%02d.%03d", minutes, seconds, millis)
        }
    }
}