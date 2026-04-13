package org.litvin.media

import javafx.scene.Node
import java.io.File

/**
 * Simple media player abstraction to decouple UI from a specific backend (JavaFX MediaPlayer or VLCJ).
 */
interface AppMediaPlayer {
    val viewNode: Node

    fun load(file: File)
    fun play()
    fun pause()
    fun stop()
    fun seek(ms: Long)

    fun currentTimeMs(): Long
    fun totalDurationMs(): Long
    fun status(): PlayerStatus

    fun dispose()

    var onReady: (() -> Unit)?
    var onStatusChanged: ((PlayerStatus) -> Unit)?
    var onTimeChanged: ((Long) -> Unit)?
}

enum class PlayerStatus { READY, PLAYING, PAUSED, STOPPED, UNKNOWN, ERROR }
