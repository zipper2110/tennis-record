package org.litvin.ui.help

import java.util.prefs.Preferences

object HelpPreferences {
    private const val OVERVIEW_SHOWN_KEY = "help.overviewShown"

    fun claimFirstLaunchOverview(preferences: Preferences): Boolean {
        if (preferences.getBoolean(OVERVIEW_SHOWN_KEY, false)) return false
        preferences.putBoolean(OVERVIEW_SHOWN_KEY, true)
        return true
    }
}
