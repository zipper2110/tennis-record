package org.litvin.ui.tabs.scoring

import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreboardSettingsV1
import java.util.prefs.Preferences

/**
 * The user's default scoreboard style. Each saved scoreboard style change updates it.
 * A new project starts with this style, so the last used style carries over to the next project.
 */
interface ScoreboardStyleDefaults {
    /** Returns the default style, or null if the user did not save a style yet. */
    fun load(): ScoreboardSettingsV1?

    fun save(settings: ScoreboardSettingsV1)

    companion object {
        /** Keeps no default. Tests and the standalone panel use it. */
        val NONE: ScoreboardStyleDefaults = object : ScoreboardStyleDefaults {
            override fun load(): ScoreboardSettingsV1? = null
            override fun save(settings: ScoreboardSettingsV1) = Unit
        }
    }
}

/** Keeps the default scoreboard style as JSON in the user preferences. */
class PreferencesScoreboardStyleDefaults(private val preferences: Preferences) : ScoreboardStyleDefaults {
    override fun load(): ScoreboardSettingsV1? =
        preferences.get(KEY_SCOREBOARD, null)?.let(ScoreIO::scoreboardFromJson)

    override fun save(settings: ScoreboardSettingsV1) {
        preferences.put(KEY_SCOREBOARD, ScoreIO.scoreboardToJson(settings.normalized()))
    }

    private companion object {
        const val KEY_SCOREBOARD = "default-scoreboard-style"
    }
}
