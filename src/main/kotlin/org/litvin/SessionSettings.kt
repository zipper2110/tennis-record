package org.litvin

/**
 * Session-scoped settings shared across panels while the app is open.
 * Resets on app restart.
 */
object SessionSettings {
    /**
     * Index into [speedPresets] used for playback rate.
     * Default = 1 (1×) per spec 4.5.
     */
    @JvmStatic
    var playbackSpeedIndex: Int = 1

    /** Ordered to match UI labels (higher speeds first). */
    @JvmStatic
    val speedPresets: FloatArray = floatArrayOf(2.0f, 1.0f, 0.5f, 0.25f, 0.1f)

    /**
     * When enabled, and the player is paused, Left/Right arrows step by exactly one frame.
     * Default is false (original behavior: ±1s regardless of paused state).
     */
    @JvmStatic
    var frameStepWhenPaused: Boolean = false

    @JvmStatic
    fun clampIndex(i: Int): Int = when {
        i < 0 -> 0
        i >= speedPresets.size -> speedPresets.lastIndex
        else -> i
    }

    @JvmStatic
    fun toRate(i: Int): Float = speedPresets[clampIndex(i)]
}