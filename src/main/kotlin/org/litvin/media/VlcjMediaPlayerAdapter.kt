package org.litvin.media

import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.image.ImageView
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.io.File

/**
 * VLCJ-backed media player that renders video into a JavaFX ImageView via PixelBuffer.
 */
class VlcjMediaPlayerAdapter : AppMediaPlayer {
    private val imageView = ImageView().apply {
        isPreserveRatio = true
        isSmooth = false
    }
    // Use conservative VLC options to improve stability on some Windows systems where
    // hardware decoding or aggressive frame dropping may cause flicker/gray frames.
    // - Disable hardware acceleration (avcodec-hw=none)
    // - Prefer D3D11 video output
    // - Slightly increase caching to reduce stutter
    // - Disable frame skipping/dropping
    // - Hide the big yellow title banner
    private val factory = MediaPlayerFactory(
        "--no-video-title-show",
        "--avcodec-hw=d3d11va",
        "--vout=direct3d11",
//        "--file-caching=300",
//        "--live-caching=300",
//        "--verbose=2",
//        "--file-logging",
//        "--logfile=vlcj.log",
        "--no-direct3d11-zerocopy",
        "--no-sub-autodetect-file"
    )
    private val mediaPlayer: EmbeddedMediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer()

    // Wrap the ImageView in a resizable container and bind fit size to it so video scales with layout
    private val container = javafx.scene.layout.StackPane(imageView).apply {
        minWidth = 0.0; minHeight = 0.0
        prefWidth = 0.0; prefHeight = 0.0
        maxWidth = Double.MAX_VALUE; maxHeight = Double.MAX_VALUE
        widthProperty().addListener { _, _, w -> imageView.fitWidth = (w?.toDouble() ?: 0.0) }
        heightProperty().addListener { _, _, h -> imageView.fitHeight = (h?.toDouble() ?: 0.0) }
    }

    override val viewNode: Node = container

    private var durationMs: Long = 0L

    override var onReady: (() -> Unit)? = null
    override var onStatusChanged: ((PlayerStatus) -> Unit)? = null
    override var onTimeChanged: ((Long) -> Unit)? = null

    init {
        val vs = ImageViewVideoSurface(imageView)
        mediaPlayer.videoSurface().set(vs)

        mediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                Platform.runLater { onStatusChanged?.invoke(PlayerStatus.PLAYING) }
            }
            override fun paused(mediaPlayer: MediaPlayer) {
                Platform.runLater { onStatusChanged?.invoke(PlayerStatus.PAUSED) }
            }
            override fun stopped(mediaPlayer: MediaPlayer) {
                Platform.runLater { onStatusChanged?.invoke(PlayerStatus.STOPPED) }
            }
//            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
//                Platform.runLater { onTimeChanged?.invoke(newTime) }
//            }
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                durationMs = newLength
                // Consider media ready once we know length
                Platform.runLater { onReady?.invoke() }
                Platform.runLater { onStatusChanged?.invoke(PlayerStatus.READY) }
            }
            override fun error(mediaPlayer: MediaPlayer) {
                Platform.runLater { onStatusChanged?.invoke(PlayerStatus.ERROR) }
            }
        })
    }

    override fun load(file: File) {
        durationMs = 0L
        // Start the media in paused state to avoid rapid play/pause cycles that can
        // cause flicker on some drivers; VLC will decode first frames and fire length event.
        mediaPlayer.media().play(file.absolutePath, ":start-paused")
        mediaPlayer.controls().setTime(0L)
    }

    override fun play() {
        mediaPlayer.controls().play()
    }

    override fun pause() {
        mediaPlayer.controls().pause()
    }

    override fun stop() {
        mediaPlayer.controls().stop()
    }

    override fun seek(ms: Long) {
        mediaPlayer.controls().setTime(ms.coerceAtLeast(0L))
    }

    override fun currentTimeMs(): Long = mediaPlayer.status().time()

    override fun totalDurationMs(): Long = if (durationMs > 0) durationMs else mediaPlayer.status().length()

    override fun status(): PlayerStatus {
        return when (mediaPlayer.status().state()) {
            uk.co.caprica.vlcj.player.base.State.PLAYING -> PlayerStatus.PLAYING
            uk.co.caprica.vlcj.player.base.State.PAUSED -> PlayerStatus.PAUSED
            uk.co.caprica.vlcj.player.base.State.STOPPED -> PlayerStatus.STOPPED
            uk.co.caprica.vlcj.player.base.State.OPENING, uk.co.caprica.vlcj.player.base.State.BUFFERING -> PlayerStatus.READY
            uk.co.caprica.vlcj.player.base.State.ENDED -> PlayerStatus.STOPPED
            uk.co.caprica.vlcj.player.base.State.ERROR -> PlayerStatus.ERROR
            else -> PlayerStatus.UNKNOWN
        }
    }

    override fun dispose() {
        try { mediaPlayer.release() } catch (_: Throwable) {}
        try { factory.release() } catch (_: Throwable) {}
    }
}
