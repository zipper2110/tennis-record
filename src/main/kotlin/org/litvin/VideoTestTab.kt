package org.litvin

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.layout.*
import javafx.stage.FileChooser
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import javafx.scene.image.ImageView
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface
import java.io.File

/**
 * Very minimal screen for testing VLCJ video performance.
 * Contains a bare player with no callbacks/observers and simple controls.
 */
class VideoTestTab {
    // Navigation callbacks so the left sidebar can navigate to existing screens
    var onRequestNavigateProjects: (() -> Unit)? = null
    var onRequestNavigateMarkup: (() -> Unit)? = null
    var onRequestNavigateExport: (() -> Unit)? = null

    private val root = BorderPane()

    // Minimal player objects (no listeners/callbacks)
    private val imageView = ImageView().apply {
        isPreserveRatio = true
//        isSmooth = false
//        fitWidth = 0.0
//        fitHeight = 0.0
    }
    private val factory = MediaPlayerFactory(
        "--no-video-title-show",
        "--avcodec-hw=d3d11va",
        "--vout=direct3d11",
//        "--no-direct3d11-zerocopy",
        "--no-sub-autodetect-file"
        )
    private val mediaPlayer = factory.mediaPlayers().newMediaPlayer()

    private val playerContainer = StackPane(imageView).apply {
        minWidth = 0.0; minHeight = 0.0
        prefWidth = 0.0; prefHeight = 0.0
        maxWidth = Double.MAX_VALUE; maxHeight = Double.MAX_VALUE
        widthProperty().addListener { _, _, w -> imageView.fitWidth = (w?.toDouble() ?: 0.0) }
        heightProperty().addListener { _, _, h -> imageView.fitHeight = (h?.toDouble() ?: 0.0) }
    }

    val view: Node get() = buildView()

    init {
        // Hook ImageView as video surface
//        mediaPlayer.videoSurface().set(ImageViewVideoSurface(imageView))
    }

    private fun buildSidebar(): Node {
        return org.litvin.markup.AppSidebar.build(
            active = org.litvin.markup.AppSidebar.Active.VIDEO_TEST,
            onProjects = { onRequestNavigateProjects?.invoke() },
            onMarkup = { onRequestNavigateMarkup?.invoke() },
            onExport = { onRequestNavigateExport?.invoke() },
            onVideoTest = { /* already here */ }
        )
    }

    private fun buildControls(): Node {
        val openBtn = Button("Open file…")
        val playBtn = Button("Play")
        val pauseBtn = Button("Pause")
        val stopBtn = Button("Stop")

        openBtn.setOnAction {
            try {
                val chooser = FileChooser().apply {
                    title = "Open Video"
                    extensionFilters.addAll(
                        FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.mov", "*.mkv", "*.avi", "*.m4v", "*.wmv"),
                        FileChooser.ExtensionFilter("All Files", "*.*")
                    )
                }
                val f: File? = chooser.showOpenDialog(null)
                if (f != null) {
                    mediaPlayer.media().play(f.absolutePath)
                }
            } catch (_: Throwable) { }
        }
        playBtn.setOnAction { try { mediaPlayer.controls().play() } catch (_: Throwable) {} }
        pauseBtn.setOnAction { try { mediaPlayer.controls().pause() } catch (_: Throwable) {} }
        stopBtn.setOnAction { try { mediaPlayer.controls().stop() } catch (_: Throwable) {} }

        return HBox(8.0, openBtn, playBtn, pauseBtn, stopBtn).apply {
            padding = Insets(8.0)
            alignment = Pos.CENTER_LEFT
        }
    }

    private fun buildView(): Node {
        val main = VBox(0.0,
            HBox().apply { minHeight = 8.0 }, // small spacer on top
            playerContainer,
            buildControls()
        ).apply {
            padding = Insets(0.0, 0.0, 0.0, 0.0)
            VBox.setVgrow(playerContainer, Priority.ALWAYS)
        }

        root.left = buildSidebar()
        root.center = main
        return root
    }

    fun dispose() {
        try { mediaPlayer.release() } catch (_: Throwable) {}
        try { factory.release() } catch (_: Throwable) {}
    }
}
