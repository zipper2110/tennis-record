package org.litvin.shared.util

/**
 * Time formatting/parsing helpers for consistent display and editing.
 * - Formats milliseconds to hh:mm:ss.mmm
 * - Parses hh:mm:ss.mmm (or mm:ss.mmm or ss.mmm) into milliseconds
 * - Provides rounding to nearest 10 ms to align with Markup rules
 */
object Timecode {
    /** Format milliseconds to hh:mm:ss.mmm (zero-padded). */
    fun format(totalMs: Long): String {
        // For v0.1.0 we expect non-negative times; keep simple integer divisions
        val ms = (totalMs % 1000).toInt()
        val totalSeconds = totalMs / 1000
        val s = (totalSeconds % 60).toInt()
        val totalMinutes = totalSeconds / 60
        val m = (totalMinutes % 60).toInt()
        val h = (totalMinutes / 60).toInt()
        return String.format("%02d:%02d:%02d.%03d", h, m, s, ms)
    }

    /** Round to the nearest 10 milliseconds. */
    fun roundTo10ms(ms: Long): Int = (((ms + 5) / 10) * 10).toInt()

    /**
     * Parse time text to milliseconds. Accepts:
     * - hh:mm:ss.mmm
     * - mm:ss.mmm
     * - ss.mmm
     * - hh:mm:ss (no decimals)
     * - mm:ss
     * - ss
     */
    fun parse(text: String): Long {
        val t = text.trim()
        if (t.isEmpty()) throw IllegalArgumentException("Empty time string")
        val parts = t.split(":")
        val (h, m, sPart) = when (parts.size) {
            3 -> Triple(parts[0], parts[1], parts[2])
            2 -> Triple("0", parts[0], parts[1])
            1 -> Triple("0", "0", parts[0])
            else -> throw IllegalArgumentException("Invalid time format: $text")
        }
        val (secStr, msStr) = if (sPart.contains('.')) {
            val sp = sPart.split('.')
            Pair(sp[0], sp.getOrElse(1) { "0" })
        } else Pair(sPart, "0")
        val hours = h.toInt()
        val minutes = m.toInt()
        val seconds = secStr.toInt()
        val millis = when (msStr.length) {
            0 -> 0
            1 -> (msStr + "00").substring(0, 3).toInt()
            2 -> (msStr + "0").substring(0, 3).toInt()
            else -> msStr.substring(0, 3).toInt()
        }
        if (minutes !in 0..59 || seconds !in 0..59) throw IllegalArgumentException("Minutes/seconds out of range")
        val totalMs = (((hours * 60L + minutes) * 60L + seconds) * 1000L) + millis
        return totalMs
    }

}
