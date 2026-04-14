package org.litvin.media

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.State
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.awt.Component
import java.io.File

/**
 * Swing variant of VLCJ-backed media player using EmbeddedMediaPlayerComponent (AWT Canvas).
 *
 * Note: We intentionally do NOT implement AppMediaPlayer here because that interface is bound to JavaFX Node.
 * This class mirrors the API and can be adapted by a thin layer later in migration.
 */
class VlcjSwingMediaPlayerAdapter {
    // For Phase 0 we stick to defaults; options can be tuned later if needed.
    private val embeddedComponent = EmbeddedMediaPlayerComponent()
    private val mediaPlayer: EmbeddedMediaPlayer = embeddedComponent.mediaPlayer()

    // Expose AWT component for embedding into Swing containers
    val component: Component get() = embeddedComponent

    private var durationMs: Long = 0L

    var onReady: (() -> Unit)? = null
    var onStatusChanged: ((PlayerStatus) -> Unit)? = null
    var onTimeChanged: ((Long) -> Unit)? = null

    fun setRate(rate: Float) {
        try { mediaPlayer.controls().setRate(rate) } catch (_: Throwable) { }
    }

    init {
        mediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                onStatusChanged?.invoke(PlayerStatus.PLAYING)
            }
            override fun paused(mediaPlayer: MediaPlayer) {
                onStatusChanged?.invoke(PlayerStatus.PAUSED)
            }
            override fun stopped(mediaPlayer: MediaPlayer) {
                onStatusChanged?.invoke(PlayerStatus.STOPPED)
            }
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                onTimeChanged?.invoke(newTime)
            }
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                durationMs = newLength
                onReady?.invoke()
                onStatusChanged?.invoke(PlayerStatus.READY)
            }
            override fun error(mediaPlayer: MediaPlayer) {
                onStatusChanged?.invoke(PlayerStatus.ERROR)
            }
        })
    }

    fun load(file: File) {
        durationMs = 0L
        mediaPlayer.media().play(file.absolutePath, ":start-paused")
        mediaPlayer.controls().setTime(0L)
    }

    fun play() = mediaPlayer.controls().play()
    fun pause() = mediaPlayer.controls().setPause(true)
    fun stop() = mediaPlayer.controls().stop()
    fun seek(ms: Long) = mediaPlayer.controls().setTime(ms.coerceAtLeast(0L))

    fun currentTimeMs(): Long = mediaPlayer.status().time()
    fun totalDurationMs(): Long = if (durationMs > 0) durationMs else mediaPlayer.status().length()

    fun status(): PlayerStatus = when (mediaPlayer.status().state()) {
        State.PLAYING -> PlayerStatus.PLAYING
        State.PAUSED -> PlayerStatus.PAUSED
        State.STOPPED, State.ENDED -> PlayerStatus.STOPPED
        State.OPENING, State.BUFFERING -> PlayerStatus.READY
        State.ERROR -> PlayerStatus.ERROR
        else -> PlayerStatus.UNKNOWN
    }

    fun dispose() {
        try { mediaPlayer.release() } catch (_: Throwable) {}
        try { embeddedComponent.release() } catch (_: Throwable) {}
    }
}