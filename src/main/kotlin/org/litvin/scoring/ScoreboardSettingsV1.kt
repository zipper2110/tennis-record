package org.litvin.scoring

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue

/** The look of the scoreboard. The scoring preview and the export use the same style. */
enum class ScoreboardStyleId(val title: String) {
    @JsonEnumDefaultValue
    BROADCAST("Broadcast"),
    CLASSIC("Classic"),
    CENTER_COURT("Center Court"),
    COMPACT("Compact"),
}

/** The corner of the video that shows the scoreboard. */
enum class ScoreboardPosition(val title: String) {
    @JsonEnumDefaultValue
    TOP_LEFT("Top left"),
    TOP_RIGHT("Top right"),
    BOTTOM_LEFT("Bottom left"),
    BOTTOM_RIGHT("Bottom right"),
}

/**
 * Scoreboard settings of a project. They are stored in score.json.
 *
 * Null values use the default of the selected style.
 */
data class ScoreboardSettingsV1(
    val style: ScoreboardStyleId = ScoreboardStyleId.BROADCAST,
    val title: String = DEFAULT_TITLE,
    val showTitle: Boolean = true,
    /** Shows the [APP_CREDIT] line at the bottom of the scoreboard. */
    val showAppCredit: Boolean = true,
    val position: ScoreboardPosition = ScoreboardPosition.TOP_LEFT,
    val sizePercent: Int = 100,
    val backgroundOpacityPercent: Int? = null,
    val accentColorHex: String? = null,
) {
    /** Returns a copy with all values in their allowed ranges. */
    fun normalized(): ScoreboardSettingsV1 = copy(
        title = title.trim().take(MAX_TITLE_LENGTH),
        sizePercent = sizePercent.coerceIn(MIN_SIZE_PERCENT, MAX_SIZE_PERCENT),
        backgroundOpacityPercent = backgroundOpacityPercent?.coerceIn(MIN_OPACITY_PERCENT, 100),
        accentColorHex = accentColorHex?.let(::normalizeHex),
    )

    companion object {
        const val DEFAULT_TITLE = "Tournament or club name"
        const val APP_CREDIT = "TennisRecord app"
        const val MAX_TITLE_LENGTH = 40
        const val MIN_SIZE_PERCENT = 50
        const val MAX_SIZE_PERCENT = 200
        const val MIN_OPACITY_PERCENT = 10

        private fun normalizeHex(value: String): String? {
            val clean = value.trim().removePrefix("#")
            if (clean.length != 6 || clean.toIntOrNull(16) == null) return null
            return "#" + clean.uppercase()
        }
    }
}
