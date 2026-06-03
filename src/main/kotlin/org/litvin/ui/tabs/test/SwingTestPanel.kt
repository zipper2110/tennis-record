package org.litvin.ui.tabs.test

import org.litvin.media.PlayerStatus
import org.litvin.media.VlcjSwingMediaPlayerAdapter
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.ScrubBar
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.EventQueue
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.filechooser.FileNameExtensionFilter

class SwingTestPanel : JPanel(BorderLayout()) {
    private val player = VlcjSwingMediaPlayerAdapter()
    private var pendingVideoFile: File? = null
    private var projectManifestPath: String? = null

    private val playPauseButton: JButton = UiStyles.squarePrimaryButton(UiStyles.playIcon(26), size = 52) {
        togglePlayPause()
    }.apply {
        toolTipText = "Play"
    }

    private val fileLabel = JLabel("No video loaded").apply {
        foreground = UiStyles.FG_SECONDARY
    }

    private val projectLabel = JLabel("No project loaded").apply {
        foreground = UiStyles.FG_SECONDARY
    }
    private val rotationValueLabel = JLabel("0.0 deg").apply {
        foreground = UiStyles.FG_PRIMARY
    }
    private val rotationSlider = JSlider(-360, 360, 0).apply {
        toolTipText = "Rotation degrees"
        isOpaque = false
        putClientProperty("JSlider.isFilled", true)
    }

    private val scrubBar = ScrubBar(
        onUserScrub = { target ->
            player.seek(target)
            refreshScrub()
        },
        tooltip = "Seek test video"
    )

    private val idleUiTimer = Timer(100) {
        if (player.status() != PlayerStatus.PLAYING) {
            refreshScrub()
        }
    }.apply { isRepeats = true }

    init {
        isOpaque = true
        background = UiStyles.DARK_BG
        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)

        val videoStack = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = Color.BLACK
            minimumSize = Dimension(640, 360)
            add(player.component, BorderLayout.CENTER)
            add(scrubBar, BorderLayout.SOUTH)
        }

        val rightPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = true
            background = UiStyles.DARK_BG
            preferredSize = Dimension(300, 0)
            border = BorderFactory.createEmptyBorder(8, 16, 8, 8)
        }

        val title = JLabel("Test").apply {
            foreground = UiStyles.FG_PRIMARY
            font = font.deriveFont(font.style, font.size2D + 6.0f)
            alignmentX = LEFT_ALIGNMENT
        }
        val openButton = UiStyles.primarySmallButton("Open Video") { openVideo() }.apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 36)
        }
        val applyRotationButton = UiStyles.primarySmallButton("Apply Rotation") {
            applyRotation()
        }.apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 36)
        }

        playPauseButton.alignmentX = LEFT_ALIGNMENT
        fileLabel.alignmentX = LEFT_ALIGNMENT
        projectLabel.alignmentX = LEFT_ALIGNMENT
        rotationValueLabel.alignmentX = LEFT_ALIGNMENT
        rotationSlider.alignmentX = LEFT_ALIGNMENT
        rotationSlider.maximumSize = Dimension(Int.MAX_VALUE, 44)
        rotationSlider.addChangeListener {
            rotationValueLabel.text = String.format(java.util.Locale.US, "%.1f deg", currentRotationDeg())
        }

        rightPanel.add(title)
        rightPanel.add(Box.createRigidArea(Dimension(0, 18)))
        rightPanel.add(openButton)
        rightPanel.add(Box.createRigidArea(Dimension(0, 16)))
        rightPanel.add(playPauseButton)
        rightPanel.add(Box.createRigidArea(Dimension(0, 20)))
        rightPanel.add(JLabel("Rotation").apply {
            foreground = UiStyles.FG_SECONDARY
            alignmentX = LEFT_ALIGNMENT
        })
        rightPanel.add(rotationSlider)
        rightPanel.add(rotationValueLabel)
        rightPanel.add(Box.createRigidArea(Dimension(0, 10)))
        rightPanel.add(applyRotationButton)
        rightPanel.add(Box.createRigidArea(Dimension(0, 20)))
        rightPanel.add(fileLabel)
        rightPanel.add(Box.createRigidArea(Dimension(0, 8)))
        rightPanel.add(projectLabel)

        add(videoStack, BorderLayout.CENTER)
        add(rightPanel, BorderLayout.EAST)

        installPlayerCallbacks()
        idleUiTimer.start()
    }

    fun setProjectManifest(path: String) {
        projectManifestPath = path
        projectLabel.text = "Project: ${File(path).parentFile?.name ?: path}"
    }

    fun onActivated() {
        player.activatePreview("test tab activated")
        pendingVideoFile?.let {
            player.pause()
            refreshScrub()
        }
        EventQueue.invokeLater { player.component.requestFocusInWindow() }
    }

    fun onDeactivated() {
        player.pause()
        player.deactivatePreview("test tab deactivated")
    }

    private fun openVideo() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Open video"
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter(
                "Video files",
                "mp4", "mov", "mkv", "avi", "m4v", "webm", "mts", "m2ts"
            )
        }
        val result = chooser.showOpenDialog(SwingUtilities.getWindowAncestor(this))
        if (result != JFileChooser.APPROVE_OPTION) return

        val file = chooser.selectedFile ?: return
        pendingVideoFile = file
        fileLabel.text = file.name
        scrubBar.reset()
        player.load(file)
        player.pause()
        updatePlayPauseUi()
    }

    private fun applyRotation() {
        player.applyPreviewRotation(currentRotationDeg(), "test tab apply rotation")
        updatePlayPauseUi()
    }

    private fun currentRotationDeg(): Float = rotationSlider.value / 2.0f

    private fun togglePlayPause() {
        val wasPlaying = player.status() == PlayerStatus.PLAYING
        if (wasPlaying) {
            player.pause()
        } else {
            player.play()
        }
        updatePlayPauseUi()
    }

    private fun installPlayerCallbacks() {
        player.onReady = {
            EventQueue.invokeLater {
                refreshScrub()
                updatePlayPauseUi()
            }
        }
        player.onTimeChanged = {
            EventQueue.invokeLater {
                refreshScrub()
                updatePlayPauseUi()
            }
        }
        player.onStatusChanged = {
            EventQueue.invokeLater { updatePlayPauseUi() }
        }
    }

    private fun refreshScrub() {
        val duration = player.totalDurationMs()
        if (duration > 0) {
            scrubBar.setRange(0L, duration)
            scrubBar.setPosition(player.currentTimeMs())
        } else {
            scrubBar.reset()
        }
    }

    private fun updatePlayPauseUi() {
        val playing = player.status() == PlayerStatus.PLAYING
        playPauseButton.icon = if (playing) UiStyles.pauseIcon(26) else UiStyles.playIcon(26)
        playPauseButton.toolTipText = if (playing) "Pause" else "Play"
    }
}
