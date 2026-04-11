package org.litvin

/**
 * Dispatcher and simple state holder for Markup tab (task 2.4: auto-create point from Start/End).
 * - Maintains an in-memory list of created points (completed intervals)
 * - Maintains a single pending Start until End is set or replaced
 * - Rounds incoming timestamps to nearest 10 ms
 * - Validates duration >= 200 ms and start < end on finalize
 */
class MarkupDispatcher {
    data class UiRow(val title: String, val startMs: Int?, val endMs: Int?, val isPending: Boolean)

    private val points = mutableListOf<PointV1>()
    private var pendingStartMs: Int? = null

    /** Rounds milliseconds to nearest 10 ms to stabilize timestamps. */
    private fun round10(ms: Long): Int = (((ms + 5) / 10) * 10).toInt()

    /** Called when user presses I or clicks Point Start. */
    fun onPointStart(timeMs: Long) {
        pendingStartMs = round10(timeMs)
        println("[MARKUP] Pending Start set at $pendingStartMs ms")
    }

    /** Called when user presses O or clicks Point End. */
    fun onPointEnd(timeMs: Long) {
        val s = pendingStartMs
        if (s == null) {
            // No-op per spec (gentle hint)
            println("[MARKUP] Hint: Press I to set a Start before setting End")
            return
        }
        val e = round10(timeMs)
        val duration = e - s
        if (e <= s || duration < 200) {
            // Invalid; keep pending and show inline-ish hint in logs for now
            println("[MARKUP] Invalid point (start=$s, end=$e). Need end>start and duration>=200ms")
            return
        }
        val id = "PT_${points.size + 1}"
        points.add(PointV1(id = id, startMs = s, endMs = e))
        // Clear pending after successful creation
        pendingStartMs = null
        println("[MARKUP] Point created: $id [$s, $e]")
    }

    fun getCompletedPoints(): List<PointV1> = points.toList()

    fun getPendingStart(): Int? = pendingStartMs

    fun clearAll() {
        points.clear()
        pendingStartMs = null
    }

    // Transport telemetry (optional logging)
    fun onPlayPauseClicked() { println("[MARKUP] Play/Pause clicked") }
    fun onJumpBackClicked() { println("[MARKUP] Jump Back clicked") }
    fun onJumpForwardClicked() { println("[MARKUP] Jump Forward clicked") }
}