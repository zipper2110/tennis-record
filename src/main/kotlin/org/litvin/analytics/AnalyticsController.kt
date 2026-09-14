package org.litvin.analytics

import java.util.concurrent.atomic.AtomicReference

interface ManagedAnalytics : Analytics, AutoCloseable

/** Stable feature-facing facade that atomically swaps delivery on consent changes. */
class AnalyticsController(
    private val config: AnalyticsBuildConfig,
    private val preferences: AnalyticsPreferences,
    private val enabledFactory: (AnalyticsBuildConfig.Enabled) -> ManagedAnalytics = { EnabledAnalytics(it) }
) : Analytics, AutoCloseable {
    private val delegate = AtomicReference<Analytics>(DisabledAnalytics)
    private val managed = AtomicReference<ManagedAnalytics?>(null)

    override fun record(event: AnalyticsEvent) {
        runCatching { delegate.get().record(event) }
    }

    fun startIfConsented() {
        if (preferences.resolve().isEnabled) enable(recordConsent = false)
    }

    fun enable() = enable(recordConsent = true)

    private fun enable(recordConsent: Boolean) {
        val enabled = config as? AnalyticsBuildConfig.Enabled ?: return
        if (managed.get() != null) return
        val created = runCatching { enabledFactory(enabled) }.getOrNull() ?: return
        if (!managed.compareAndSet(null, created)) {
            created.close()
            return
        }
        if (recordConsent) preferences.record(AnalyticsPreferences.Choice.ENABLED)
        delegate.set(created)
        created.record(AnalyticsEvent.SessionStarted)
    }

    fun disable() {
        delegate.set(DisabledAnalytics)
        val active = managed.getAndSet(null)
        runCatching { active?.close() }
        preferences.record(AnalyticsPreferences.Choice.DISABLED)
    }

    override fun close() = disable()
}
