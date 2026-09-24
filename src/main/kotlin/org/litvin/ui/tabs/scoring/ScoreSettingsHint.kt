package org.litvin.ui.tabs.scoring

import java.util.prefs.Preferences

/**
 * The hint that points at the Scoring Settings button after the automatic score settings dialog closes.
 * The hint shows again after each automatic dialog until the user closes the hint. Then it never shows again.
 */
interface ScoreSettingsHint {
    /** True after the user closed the hint. */
    fun isDismissed(): Boolean

    fun dismiss()

    companion object {
        /** Never shows the hint. Tests and the standalone panel use it. */
        val NONE: ScoreSettingsHint = object : ScoreSettingsHint {
            override fun isDismissed() = true
            override fun dismiss() = Unit
        }
    }
}

/** Keeps the closed state of the hint in the user preferences, so that it applies to all projects. */
class PreferencesScoreSettingsHint(private val preferences: Preferences) : ScoreSettingsHint {
    override fun isDismissed(): Boolean = preferences.getBoolean(KEY_DISMISSED, false)

    override fun dismiss() {
        preferences.putBoolean(KEY_DISMISSED, true)
    }

    private companion object {
        const val KEY_DISMISSED = "score-settings-hint-dismissed"
    }
}
