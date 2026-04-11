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
    private var userMessage: String? = null

    /** Callback invoked whenever completed points collection changes (create/delete/replace/clear). */
    var onPointsChanged: (() -> Unit)? = null

    private fun notifyUser(msg: String) {
        userMessage = msg
        // Also log to console for dev visibility
        println("[MARKUP][HINT] $msg")
    }

    fun consumeUserMessage(): String? {
        val m = userMessage
        userMessage = null
        return m
    }


    /** Called when user presses C or clicks Point Start. */
    fun onPointStart(timeMs: Long) {
        val t = Timecode.roundTo10ms(timeMs)
        // Block placing a Start inside an existing interval [start, end)
        val insideExisting = points.any { p -> t >= p.startMs && t < p.endMs }
        if (insideExisting) {
            notifyUser("Cannot place Start at ${t} ms: it's inside an existing point interval")
            return
        }
        pendingStartMs = t
        println("[MARKUP] Pending Start set at $pendingStartMs ms")
    }

    /** Half-open overlap check against existing points: [s, e) intersects [p.start, p.end) */
    private fun overlapsExisting(newStart: Int, newEnd: Int): Boolean {
        return points.any { p ->
            val ps = p.startMs
            val pe = p.endMs
            // Overlap if starts before existing end and ends after existing start
            newStart < pe && ps < newEnd
        }
    }

    /** Called when user presses V or clicks Point End. */
    fun onPointEnd(timeMs: Long) {
        val s = pendingStartMs
        if (s == null) {
            // No-op per spec (gentle hint)
            notifyUser("Set a Start first (press C) before setting End")
            return
        }
        val e = Timecode.roundTo10ms(timeMs)
        val duration = e - s
        if (e <= s || duration < 200) {
            // Invalid; keep pending and show hint
            notifyUser("Invalid point: End must be > Start and duration ≥ 200 ms")
            return
        }
        // Validation 2.7: reject overlaps with existing points; adjacent allowed via half-open rule
        if (overlapsExisting(s, e)) {
            notifyUser("Overlap blocked for [$s, $e). Adjust boundaries to avoid overlaps.")
            return
        }
        val id = "PT_${points.size + 1}"
        points.add(PointV1(id = id, startMs = s, endMs = e))
        // Clear pending after successful creation
        pendingStartMs = null
        println("[MARKUP] Point created: $id [$s, $e]")
        onPointsChanged?.invoke()
    }

    fun getCompletedPoints(): List<PointV1> = points.toList()

    fun setPoints(newPoints: List<PointV1>) {
        points.clear()
        points.addAll(newPoints.sortedBy { it.startMs })
        onPointsChanged?.invoke()
    }

    fun getPendingStart(): Int? = pendingStartMs

    /** Delete a point by id. Returns true if removed. */
    fun deletePoint(id: String): Boolean {
        val idx = points.indexOfFirst { it.id == id }
        if (idx >= 0) {
            points.removeAt(idx)
            onPointsChanged?.invoke()
            return true
        }
        return false
    }

    fun clearAll() {
        points.clear()
        pendingStartMs = null
        onPointsChanged?.invoke()
    }

    // Transport telemetry (optional logging)
    fun onPlayPauseClicked() { println("[MARKUP] Play/Pause clicked") }
    fun onJumpBackClicked() { println("[MARKUP] Jump Back clicked") }
    fun onJumpForwardClicked() { println("[MARKUP] Jump Forward clicked") }
}