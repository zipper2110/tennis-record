package org.litvin.app

import org.litvin.SwingMainApp
import org.litvin.ui.tabs.adjustments.SwingColorAdjustmentsPanel
import org.litvin.ui.tabs.export.SwingExportPanel
import org.litvin.ui.tabs.scoring.SwingScoringPanel
import org.litvin.ui.tabs.projects.presenter.DefaultProjectsPresenter
import java.util.prefs.Preferences

fun interface PreferencesProvider {
    fun node(key: String): Preferences

    companion object {
        const val APPLICATION = "application"
        const val PROJECTS = "projects"
        const val COLOR_ADJUSTMENTS = "color-adjustments"
        const val EXPORT = "export"
        const val SCORING = "scoring"
        const val ANALYTICS = "analytics"

        fun production(): PreferencesProvider = ProductionPreferencesProvider
    }
}

private object ProductionPreferencesProvider : PreferencesProvider {
    private val packageClasses = mapOf(
        PreferencesProvider.APPLICATION to SwingMainApp::class.java,
        PreferencesProvider.PROJECTS to DefaultProjectsPresenter::class.java,
        PreferencesProvider.COLOR_ADJUSTMENTS to SwingColorAdjustmentsPanel::class.java,
        PreferencesProvider.EXPORT to SwingExportPanel::class.java,
        PreferencesProvider.SCORING to SwingScoringPanel::class.java,
        PreferencesProvider.ANALYTICS to SwingMainApp::class.java,
    )

    override fun node(key: String): Preferences {
        val packageClass = packageClasses[key] ?: throw IllegalArgumentException("Unknown preferences key: $key")
        return Preferences.userNodeForPackage(packageClass)
    }
}
