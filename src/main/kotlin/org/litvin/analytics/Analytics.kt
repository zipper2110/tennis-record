package org.litvin.analytics

/** The only analytics API available to product features. */
interface Analytics {
    fun record(event: AnalyticsEvent)
}

/** Safe default until a complete configuration and current-version consent exist. */
object DisabledAnalytics : Analytics {
    override fun record(event: AnalyticsEvent) = Unit
}
