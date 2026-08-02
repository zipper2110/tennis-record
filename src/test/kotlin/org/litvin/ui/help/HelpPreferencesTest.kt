package org.litvin.ui.help

import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HelpPreferencesTest {
    @Test
    fun firstLaunchOverviewIsClaimedOnlyOnce() {
        val parent = Preferences.userNodeForPackage(HelpPreferencesTest::class.java)
        val nodeName = "help-test-${UUID.randomUUID()}"
        val preferences = parent.node(nodeName)
        try {
            assertTrue(HelpPreferences.claimFirstLaunchOverview(preferences))
            assertFalse(HelpPreferences.claimFirstLaunchOverview(preferences))
        } finally {
            preferences.removeNode()
        }
    }
}
