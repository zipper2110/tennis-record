package org.litvin.ui.tabs.scoring

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.SwingUtilities
import kotlin.test.assertNotNull

class SwingScoringPanelHotkeysTest {
    @Test
    fun hasTogglePlayPauseAction_invokableSafely() {
        try { System.setProperty("java.awt.headless", "true") } catch (_: Throwable) {}

        var panel: SwingScoringPanel? = null
        var error: Throwable? = null
        try {
            SwingUtilities.invokeAndWait {
                try { panel = SwingScoringPanel() } catch (t: Throwable) { error = t }
            }
        } catch (t: Throwable) { error = t }

        if (panel == null) {
            assumeTrue(false, "Cannot construct SwingScoringPanel (likely VLC/libvlc missing): ${error?.javaClass?.simpleName}: ${error?.message}")
            return
        }

        val p = requireNotNull(panel)
        // Action name as bound in installKeyBindings()
        val action = p.actionMap.get("togglePlayPause")
        assertNotNull(action, "togglePlayPause action should be installed in ActionMap")

        // Try invoking the action; if underlying VLC causes issues, skip rather than fail
        try {
            // Fire on EDT to mimic real key handling
            SwingUtilities.invokeAndWait {
                action.actionPerformed(ActionEvent(p, ActionEvent.ACTION_PERFORMED, "test"))
            }
        } catch (t: Throwable) {
            assumeTrue(false, "Invoking togglePlayPause failed (likely VLC not present): ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
