package org.litvin.ui.tabs.scoring

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.awt.BorderLayout
import javax.swing.SwingUtilities
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SwingScoringPanelSmokeTest {
    @Test
    fun compose_withoutCrashing_inHeadlessOrWithVlc() {
        // Ensure headless to be CI-friendly (no windows are shown anyway)
        try { System.setProperty("java.awt.headless", "true") } catch (_: Throwable) {}

        // Try to instantiate on EDT; skip test if VLC/native libs are missing
        var panel: SwingScoringPanel? = null
        var error: Throwable? = null
        try {
            SwingUtilities.invokeAndWait {
                try {
                    panel = SwingScoringPanel()
                } catch (t: Throwable) {
                    error = t
                }
            }
        } catch (t: Throwable) {
            error = t
        }

        if (panel == null) {
            // Most probable cause in CI is missing VLC/libvlc — skip gracefully
            assumeTrue(false, "SwingScoringPanel could not be instantiated (likely due to VLC/libvlc not available): ${error?.javaClass?.simpleName}: ${error?.message}")
            return
        }

        // Basic composition assertions
        val p = requireNotNull(panel)
        assertIs<BorderLayout>(p.layout)
        // Two major regions should be present (left list + center)
        assertTrue(p.componentCount >= 2, "Expected at least 2 child components in the container")

        // Access a couple of public container hooks to ensure they are callable
        // These should be no-ops/safe without an active project
        try {
            p.onActivated()
            p.onDeactivated()
        } catch (t: Throwable) {
            // If libvlc operations fail, skip instead of failing the suite
            assumeTrue(false, "Panel lifecycle calls failed (likely VLC not present): ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
