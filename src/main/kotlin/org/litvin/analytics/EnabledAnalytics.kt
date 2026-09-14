package org.litvin.analytics

/** In-memory event collector. Delivery is added by the lifecycle transport in a later task step. */
internal class EnabledAnalytics(config: AnalyticsBuildConfig.Enabled) : ManagedAnalytics {
    private val session = AnalyticsSession()
    private val buffer = AnalyticsBuffer()
    private val appVersion = System.getProperty("tennis.record.version", "1.0.0")
    private val osFamily = config.osFamily
    @Volatile private var closed = false

    override fun record(event: AnalyticsEvent) {
        if (closed) return
        runCatching {
            buffer.add(AnalyticsEventRegistry.serializeEvent(session.nextSequence(), event, session.elapsedMs()))
        }
    }

    override fun close() {
        closed = true
        buffer.clear()
    }
}
