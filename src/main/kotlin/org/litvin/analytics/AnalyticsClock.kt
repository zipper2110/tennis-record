package org.litvin.analytics

interface AnalyticsClock {
    fun elapsedMs(): Int
}

class SystemAnalyticsClock(private val startedAtNanos: Long = System.nanoTime()) : AnalyticsClock {
    override fun elapsedMs(): Int = ((System.nanoTime() - startedAtNanos) / 1_000_000L)
        .coerceIn(0, AnalyticsEventRegistry.MAX_ELAPSED_MS.toLong())
        .toInt()
}
