package org.litvin.ui.tabs.adjustments

import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Entry panel for the Adjustments tab (v0.3.0 T1).
 *
 * Minimal placeholder wired into the app; real UI will be migrated per
 * docs/tasks-tracker/v0.3.0/adjustments.md and design/adjustments.html.
 *
 * Contract mirrors other tabs to simplify app wiring:
 *  - setProjectManifest(path)
 *  - onActivated()/onDeactivated()
 *  - saveNow()
 */
class SwingAdjustmentsPanel : JPanel(BorderLayout()) {
    private var projectManifestPath: String? = null

    init {
        add(JLabel("Adjustments — WIP"), BorderLayout.CENTER)
    }

    fun setProjectManifest(path: String) {
        projectManifestPath = path
        // Future: load adjustments for project if needed
    }

    fun onActivated() {
        // Future: resume rendering/media if any
    }

    fun onDeactivated() {
        // Future: pause rendering/media if any
    }

    fun saveNow() {
        // Future: persist any pending state synchronously
    }
}
