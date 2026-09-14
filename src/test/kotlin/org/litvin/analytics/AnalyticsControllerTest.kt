package org.litvin.analytics

import java.util.Properties
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsControllerTest {
    @Test
    fun `does not create delivery until enabled and clears it immediately when disabled`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        val captured = mutableListOf<AnalyticsEvent>()
        val controller = AnalyticsController(
            enabledConfig(),
            preferences,
            enabledFactory = { RecordingEnabledAnalytics(captured) }
        )

        controller.record(AnalyticsEvent.ProjectCreated)
        assertTrue(captured.isEmpty())

        controller.enable()
        controller.record(AnalyticsEvent.ProjectCreated)
        assertEquals(listOf(AnalyticsEvent.SessionStarted, AnalyticsEvent.ProjectCreated), captured)
        assertTrue(preferences.resolve().isEnabled)

        controller.disable()
        controller.record(AnalyticsEvent.ProjectOpened)
        assertFalse(preferences.resolve().isEnabled)
        assertEquals(listOf(AnalyticsEvent.SessionStarted, AnalyticsEvent.ProjectCreated), captured)
    }

    private fun enabledConfig(): AnalyticsBuildConfig.Enabled = AnalyticsBuildConfig.fromProperties(Properties().apply {
        setProperty(AnalyticsBuildConfig.ENDPOINT_PROPERTY, "https://analytics.example.test/v1/events/batch")
        setProperty(AnalyticsBuildConfig.PRIVACY_URL_PROPERTY, "https://tennis.example.test/privacy/analytics/")
        setProperty(AnalyticsBuildConfig.NOTICE_VERSION_PROPERTY, "1")
    }) as AnalyticsBuildConfig.Enabled

    private fun withPreferences(block: (Preferences) -> Unit) {
        val node = Preferences.userRoot().node("/org/litvin/test/analytics/${UUID.randomUUID()}")
        try { block(node) } finally { node.removeNode() }
    }

    private class RecordingEnabledAnalytics(private val events: MutableList<AnalyticsEvent>) : ManagedAnalytics {
        override fun record(event: AnalyticsEvent) { events += event }
        override fun close() = Unit
    }
}
