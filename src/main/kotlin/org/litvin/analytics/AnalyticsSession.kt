package org.litvin.analytics

import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

internal class AnalyticsSession(private val clock: AnalyticsClock = SystemAnalyticsClock()) {
    private val random = SecureRandom()
    private val sequence = AtomicInteger(0)
    val id: UUID = UUID(random.nextLong(), random.nextLong()).let { UUID(it.mostSignificantBits and -0x1001L or 0x4000L, it.leastSignificantBits and 0x3fff_ffff_ffff_ffffL or Long.MIN_VALUE) }

    fun nextSequence(): Int = sequence.getAndIncrement().coerceAtMost(AnalyticsEventRegistry.MAX_SEQUENCE_NUMBER)
    fun elapsedMs(): Int = clock.elapsedMs().coerceIn(0, AnalyticsEventRegistry.MAX_ELAPSED_MS)
}
