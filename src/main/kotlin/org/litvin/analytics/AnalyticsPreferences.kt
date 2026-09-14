package org.litvin.analytics

import java.time.Instant
import java.util.prefs.Preferences

/** Stores consent only; session and event data never enter Preferences. */
class AnalyticsPreferences(private val preferences: Preferences) {
    enum class Choice { UNDECIDED, ENABLED, DISABLED }

    data class ResolvedChoice(val choice: Choice, val isEnabled: Boolean, val needsChoice: Boolean)

    fun resolve(): ResolvedChoice {
        val storedChoice = runCatching { Choice.valueOf(preferences.get(CHOICE_KEY, Choice.UNDECIDED.name)) }
            .getOrDefault(Choice.UNDECIDED)
        val storedVersion = preferences.getInt(NOTICE_VERSION_KEY, 0)
        val current = storedVersion == AnalyticsEventRegistry.NOTICE_VERSION
        if (!current) return ResolvedChoice(Choice.DISABLED, isEnabled = false, needsChoice = true)
        return ResolvedChoice(storedChoice, storedChoice == Choice.ENABLED, storedChoice == Choice.UNDECIDED)
    }

    fun record(choice: Choice) {
        require(choice != Choice.UNDECIDED)
        preferences.put(CHOICE_KEY, choice.name)
        preferences.putInt(NOTICE_VERSION_KEY, AnalyticsEventRegistry.NOTICE_VERSION)
        preferences.put(DECIDED_AT_KEY, Instant.now().toString())
    }

    fun dismiss() = record(Choice.DISABLED)

    private companion object {
        const val CHOICE_KEY = "analytics.choice"
        const val NOTICE_VERSION_KEY = "analytics.noticeVersion"
        const val DECIDED_AT_KEY = "analytics.decidedAt"
    }
}
