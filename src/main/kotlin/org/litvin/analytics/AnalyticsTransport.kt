package org.litvin.analytics

interface AnalyticsTransport : AutoCloseable {
    /** Queues a prevalidated request. Implementations must return immediately. */
    fun send(payload: String)
}
