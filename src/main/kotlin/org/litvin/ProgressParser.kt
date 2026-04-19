package org.litvin

/**
 * FFmpeg stderr progress parsing helpers (Task 3.14 — unit tested).
 */
object ProgressParser {
    private val durRegex = Regex("Duration: (\\d{2}):(\\d{2}):(\\d{2}\\.\\d{2})")
    private val timeRegex = Regex("time=\\s*(\\d{2}):(\\d{2}):(\\d{2}\\.\\d{2})")
    private val sizeRegex = Regex("size=\\s*([0-9.]+)\\s*([kKmMgG]i?[bB])")
    private val speedRegex = Regex("speed=\\s*([0-9.]+)x")

    fun parseDurationMs(line: String): Long? = durRegex.find(line)?.destructured?.let { (h, m, s) ->
        hmsToMs(h, m, s)
    }

    fun parseTimeMs(line: String): Long? = timeRegex.find(line)?.destructured?.let { (h, m, s) ->
        hmsToMs(h, m, s)
    }

    fun parseSizeBytes(line: String): Long? = sizeRegex.find(line)?.destructured?.let { (num, unit) ->
        parseSizeToBytes(num.toDoubleOrNull() ?: return null, unit)
    }

    fun parseSpeed(line: String): Double? = speedRegex.find(line)?.destructured?.component1()?.toDoubleOrNull()

    fun hmsToMs(h: String, m: String, s: String): Long {
        val hh = h.toLong()
        val mm = m.toLong()
        val ss = s.toDouble()
        return ((hh * 3600 + mm * 60) * 1000L) + kotlin.math.round(ss * 1000.0).toLong()
    }

    fun parseSizeToBytes(num: Double, unit: String): Long {
        val u = unit.lowercase()
        return when {
            u.startsWith("g") -> (num * 1_000_000_000L).toLong()
            u.startsWith("m") -> (num * 1_000_000L).toLong()
            u.startsWith("k") -> (num * 1_000L).toLong()
            else -> num.toLong()
        }
    }
}
