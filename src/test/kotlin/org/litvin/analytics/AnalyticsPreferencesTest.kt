package org.litvin.analytics

import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsPreferencesTest {
    @Test
    fun `current enabled decision is effective and old decisions require a new choice`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        assertTrue(preferences.resolve().needsChoice)
        assertFalse(preferences.resolve().isEnabled)

        preferences.record(AnalyticsPreferences.Choice.ENABLED)
        assertTrue(preferences.resolve().isEnabled)
        assertFalse(preferences.resolve().needsChoice)

        node.putInt("analytics.noticeVersion", 0)
        assertFalse(preferences.resolve().isEnabled)
        assertTrue(preferences.resolve().needsChoice)
    }

    @Test
    fun `future notice decision fails closed and dismissal records disabled`() = withPreferences { node ->
        val preferences = AnalyticsPreferences(node)
        preferences.dismiss()
        assertEquals(AnalyticsPreferences.Choice.DISABLED, preferences.resolve().choice)
        assertFalse(preferences.resolve().needsChoice)

        node.putInt("analytics.noticeVersion", 99)
        assertEquals(AnalyticsPreferences.Choice.DISABLED, preferences.resolve().choice)
        assertTrue(preferences.resolve().needsChoice)
    }

    private fun withPreferences(block: (Preferences) -> Unit) {
        val node = Preferences.userRoot().node("/org/litvin/test/analytics/${UUID.randomUUID()}")
        try {
            block(node)
        } finally {
            node.removeNode()
        }
    }
}
