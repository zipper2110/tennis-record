package org.litvin.media

import javafx.scene.Node
import javafx.scene.image.ImageView
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import java.io.File

/**
 * Adapter over JavaFX MediaPlayer to satisfy AppMediaPlayer interface.
 * Used as a fallback while migrating to VLCJ or when VLC is unavailable.
 */
class JavaFxMediaPlayerAdapter : AppMediaPlayer {
    private val imageView = ImageView().apply {
        isPreserveRatio = true
        // ImageView is just a placeholder to keep layout bindings compatible (fitWidth/fitHeight props),
        // actual video is rendered by MediaView inside, but we avoid exposing MediaView to callers.
    }
    private var mediaView: javafx.scene.media.MediaView = javafx.scene.media.MediaView()
    private var player: MediaPlayer? = null

    override val viewNode: Node = javafx.scene.layout.StackPane(mediaView).apply {
        // keep a structure we can size via fit* properties
        imageView.fitWidthProperty().bind(widthProperty())
        imageView.fitHeightProperty().bind(heightProperty())
        // bind MediaView to same size behavior
        mediaView.fitWidthProperty().bind(widthProperty())
        mediaView.fitHeightProperty().bind(heightProperty())
        mediaView.isPreserveRatio = true
    }

    override var onReady: (() -> Unit)? = null
    override var onStatusChanged: ((PlayerStatus) -> Unit)? = null
    override var onTimeChanged: ((Long) -> Unit)? = null

    override fun load(file: File) {
        dispose()
        val media = Media(file.toURI().toString())
        val p = MediaPlayer(media)
        player = p
        mediaView.mediaPlayer = p
        p.setOnReady {
            onReady?.invoke()
            onStatusChanged?.invoke(PlayerStatus.READY)
        }
        p.statusProperty().addListener { _, _, new ->
            onStatusChanged?.invoke(new.toAppStatus())
        }
//        p.currentTimeProperty().addListener { _, _, v ->
//            onTimeChanged?.invoke(v?.toMillis()?.toLong() ?: 0L)
//        }
    }

    override fun play() { player?.play() }
    override fun pause() { player?.pause() }
    override fun stop() { player?.stop() }

    override fun seek(ms: Long) { player?.seek(Duration.millis(ms.coerceAtLeast(0L).toDouble())) }

    override fun currentTimeMs(): Long = player?.currentTime?.toMillis()?.toLong() ?: 0L

    override fun totalDurationMs(): Long = player?.totalDuration?.toMillis()?.toLong() ?: 0L

    override fun status(): PlayerStatus = player?.status?.toAppStatus() ?: PlayerStatus.UNKNOWN

    override fun dispose() {
        try { player?.dispose() } catch (_: Throwable) {}
        mediaView.mediaPlayer = null
        player = null
    }
}

private fun MediaPlayer.Status.toAppStatus(): PlayerStatus = when (this) {
    MediaPlayer.Status.PLAYING -> PlayerStatus.PLAYING
    MediaPlayer.Status.PAUSED -> PlayerStatus.PAUSED
    MediaPlayer.Status.STOPPED -> PlayerStatus.STOPPED
    MediaPlayer.Status.READY -> PlayerStatus.READY
    MediaPlayer.Status.STALLED -> PlayerStatus.UNKNOWN
    MediaPlayer.Status.HALTED -> PlayerStatus.UNKNOWN
    MediaPlayer.Status.DISPOSED -> PlayerStatus.STOPPED
    MediaPlayer.Status.UNKNOWN -> PlayerStatus.UNKNOWN
}
