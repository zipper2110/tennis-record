package org.litvin.media

import org.litvin.VlcBootstrap
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.State
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class VlcjStillFrameCaptureService : StillFrameCaptureService {
    private val frameLock = Any()
    @Volatile private var loadedFile: File? = null
    @Volatile private var cachedDurationMs: Long = 0L
    @Volatile private var frameLatch = CountDownLatch(0)
    @Volatile private var frameWidth = 0
    @Volatile private var frameHeight = 0
    @Volatile private var latestPixels: IntArray? = null

    private val renderCallback = object : RenderCallbackAdapter() {
        override fun onDisplay(mediaPlayer: MediaPlayer, buffer: IntArray) {
            synchronized(frameLock) {
                latestPixels = buffer.copyOf()
            }
            frameLatch.countDown()
        }
    }

    private val bufferFormatCallback = object : BufferFormatCallbackAdapter() {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }

        override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) {
            frameWidth = displayWidth.coerceAtLeast(1)
            frameHeight = displayHeight.coerceAtLeast(1)
            renderCallback.setBuffer(IntArray(frameWidth * frameHeight))
        }
    }

    private val component = CallbackMediaPlayerComponent(
        MediaPlayerFactory(*VlcBootstrap.factoryArguments()),
        null,
        null,
        true,
        renderCallback,
        bufferFormatCallback,
        null,
    )
    private val mediaPlayer = component.mediaPlayer()

    @Synchronized
    override fun load(file: File): StillFrameMediaInfo {
        if (loadedFile?.absolutePath != file.absolutePath) {
            loadedFile = file
            cachedDurationMs = 0L
            synchronized(frameLock) {
                latestPixels = null
            }
            frameLatch = CountDownLatch(1)
            mediaPlayer.media().play(file.absolutePath, ":no-audio", ":no-video-title-show")
            waitForOpen()
            mediaPlayer.controls().setTime(0L)
            waitForFrame(1_500L)
            mediaPlayer.controls().setPause(true)
            cachedDurationMs = mediaPlayer.status().length().coerceAtLeast(0L)
        }
        return StillFrameMediaInfo(durationMs(), videoDimension())
    }

    @Synchronized
    override fun captureAt(ms: Long): BufferedImage? {
        loadedFile ?: return null
        val target = ms.coerceAtLeast(0L)
        frameLatch = CountDownLatch(1)
        mediaPlayer.controls().setTime(target)
        mediaPlayer.controls().setPause(false)
        waitForFrame(900L)
        mediaPlayer.controls().setPause(true)
        return latestImage()
    }

    override fun durationMs(): Long {
        val length = try {
            mediaPlayer.status().length()
        } catch (_: Throwable) {
            0L
        }
        if (length > 0L) cachedDurationMs = length
        return cachedDurationMs
    }

    override fun close() {
        try {
            mediaPlayer.controls().stop()
        } catch (_: Throwable) {
        }
        try {
            component.release()
        } catch (_: Throwable) {
        }
    }

    private fun latestImage(): BufferedImage? {
        val width = frameWidth
        val height = frameHeight
        val pixels = synchronized(frameLock) { latestPixels?.copyOf() } ?: return null
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        return BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply {
            setRGB(0, 0, width, height, pixels, 0, width)
        }
    }

    private fun videoDimension(): Dimension? {
        val width = frameWidth
        val height = frameHeight
        return if (width > 0 && height > 0) Dimension(width, height) else null
    }

    private fun waitForOpen() {
        val deadline = System.currentTimeMillis() + 1_500L
        while (System.currentTimeMillis() < deadline) {
            val state = try {
                mediaPlayer.status().state()
            } catch (_: Throwable) {
                State.ERROR
            }
            if (state == State.PAUSED || state == State.PLAYING || durationMs() > 0L) return
            Thread.sleep(25L)
        }
    }

    private fun waitForFrame(timeoutMs: Long) {
        try {
            frameLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
