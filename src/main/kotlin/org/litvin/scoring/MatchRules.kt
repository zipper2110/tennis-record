package org.litvin.scoring

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue

/** The unit that the match is played in. */
enum class MatchStructure {
    /** Games make sets, and sets make the match. */
    @JsonEnumDefaultValue
    SETS,

    /** Games are counted without sets. The match has no end. Use it for practice games. */
    GAMES_ONLY,

    /** One tiebreak is the full match. */
    SINGLE_TIEBREAK,

    /** Points are counted 1, 2, 3 like in a tiebreak, without a points limit. The match has no end. */
    PLAIN_POINTS,
}

/** What happens when the game score is 40–40. */
enum class DeuceRule {
    /** A player must win two points in a row after deuce. */
    @JsonEnumDefaultValue
    ADVANTAGE,

    /** At 40–40, the next point wins the game (deciding point). */
    NO_AD,
}

/** How the deciding set is played when the sets are equal. */
enum class FinalSetRule {
    @JsonEnumDefaultValue
    FULL_SET,

    /** A tiebreak to [MatchRulesV1.MATCH_TIEBREAK_POINTS] points is played in place of the deciding set. */
    MATCH_TIEBREAK,
}

/**
 * Point counting rules of a match. They are stored in score.json.
 *
 * When [manualScoring] is true, the engine counts points only. The user marks each game and set win.
 */
data class MatchRulesV1(
    val manualScoring: Boolean = false,
    val structure: MatchStructure = MatchStructure.SETS,
    /** 1, 3 or 5. */
    val bestOfSets: Int = 3,
    /** The number of games that wins a set (with a two-game lead). */
    val gamesPerSet: Int = 6,
    /** True: a tiebreak is played when the games are equal at [gamesPerSet]. False: the set continues until a two-game lead. */
    val setTiebreak: Boolean = true,
    /** The points that win a set tiebreak or a [MatchStructure.SINGLE_TIEBREAK] (with a two-point lead). */
    val tiebreakPoints: Int = 7,
    val finalSet: FinalSetRule = FinalSetRule.FULL_SET,
    val deuce: DeuceRule = DeuceRule.ADVANTAGE,
) {
    /** The number of sets that wins the match. */
    fun setsToWin(): Int = bestOfSets / 2 + 1

    /** True when the deciding set is a match tiebreak. A one-set match has no deciding set. */
    fun hasMatchTiebreakDecider(): Boolean = finalSet == FinalSetRule.MATCH_TIEBREAK && bestOfSets > 1

    /** Returns a copy with all values in their allowed ranges. */
    fun normalized(): MatchRulesV1 = copy(
        bestOfSets = bestOfSets.takeIf { it in BEST_OF_OPTIONS } ?: 3,
        gamesPerSet = gamesPerSet.coerceIn(MIN_GAMES_PER_SET, MAX_GAMES_PER_SET),
        tiebreakPoints = tiebreakPoints.coerceIn(MIN_TIEBREAK_POINTS, MAX_TIEBREAK_POINTS),
    )

    /**
     * Returns a copy where the values that [structure] does not use have their defaults.
     * Two rules with the same scoring behavior have the same canonical form.
     */
    fun canonical(): MatchRulesV1 {
        val rules = normalized()
        val defaults = MatchRulesV1()
        return when (rules.structure) {
            MatchStructure.SETS -> rules.copy(
                tiebreakPoints = if (rules.setTiebreak) rules.tiebreakPoints else defaults.tiebreakPoints,
                finalSet = if (rules.bestOfSets > 1) rules.finalSet else defaults.finalSet,
            )
            MatchStructure.GAMES_ONLY -> defaults.copy(
                manualScoring = rules.manualScoring,
                structure = rules.structure,
                deuce = rules.deuce,
            )
            MatchStructure.SINGLE_TIEBREAK -> defaults.copy(
                manualScoring = rules.manualScoring,
                structure = rules.structure,
                tiebreakPoints = rules.tiebreakPoints,
            )
            MatchStructure.PLAIN_POINTS -> defaults.copy(
                manualScoring = rules.manualScoring,
                structure = rules.structure,
            )
        }
    }

    companion object {
        const val MATCH_TIEBREAK_POINTS = 10
        val BEST_OF_OPTIONS = listOf(1, 3, 5)
        val GAMES_PER_SET_OPTIONS = listOf(4, 6, 8)
        val TIEBREAK_POINTS_OPTIONS = listOf(7, 10)
        const val MIN_GAMES_PER_SET = 1
        const val MAX_GAMES_PER_SET = 12
        const val MIN_TIEBREAK_POINTS = 3
        const val MAX_TIEBREAK_POINTS = 21
    }
}

/**
 * Popular match formats. Each preset sets the match structure. The deuce rule and manual scoring
 * are separate choices, so a preset keeps them.
 */
enum class MatchFormatPreset(val title: String, val description: String, private val rules: MatchRulesV1?) {
    BEST_OF_3(
        "Best of 3 sets",
        "Standard match. Sets to 6 games, tiebreak at 6–6.",
        MatchRulesV1(),
    ),
    BEST_OF_3_MATCH_TIEBREAK(
        "Best of 3 sets, match tiebreak",
        "Sets to 6 games. At one set all, a 10-point match tiebreak decides the match.",
        MatchRulesV1(finalSet = FinalSetRule.MATCH_TIEBREAK),
    ),
    BEST_OF_5(
        "Best of 5 sets",
        "Grand Slam format. Sets to 6 games, tiebreak at 6–6.",
        MatchRulesV1(bestOfSets = 5),
    ),
    ONE_SET(
        "One set",
        "One set to 6 games, tiebreak at 6–6.",
        MatchRulesV1(bestOfSets = 1),
    ),
    PRO_SET(
        "Pro set (8 games)",
        "One set to 8 games, tiebreak at 8–8.",
        MatchRulesV1(bestOfSets = 1, gamesPerSet = 8),
    ),
    SHORT_SETS(
        "Short sets (4 games)",
        "Best of 3 sets to 4 games, tiebreak at 4–4.",
        MatchRulesV1(gamesPerSet = 4),
    ),
    GAMES_ONLY(
        "Games only",
        "Count games without sets. Use it for practice games.",
        MatchRulesV1(structure = MatchStructure.GAMES_ONLY),
    ),
    TIEBREAK(
        "Tiebreak (7 points)",
        "One tiebreak to 7 points with a two-point lead.",
        MatchRulesV1(structure = MatchStructure.SINGLE_TIEBREAK, tiebreakPoints = 7),
    ),
    MATCH_TIEBREAK(
        "Match tiebreak (10 points)",
        "One tiebreak to 10 points with a two-point lead.",
        MatchRulesV1(structure = MatchStructure.SINGLE_TIEBREAK, tiebreakPoints = 10),
    ),
    PLAIN_POINTS(
        "Plain points (endless tiebreak)",
        "Count points 1, 2, 3 like in a tiebreak, without a points limit. The match has no end.",
        MatchRulesV1(structure = MatchStructure.PLAIN_POINTS),
    ),
    CUSTOM(
        "Custom",
        "Your own combination of the rules below.",
        null,
    ),
    ;

    /** Applies this preset to [current]. The deuce rule and manual scoring of [current] stay. [CUSTOM] returns [current]. */
    fun applyTo(current: MatchRulesV1): MatchRulesV1 =
        rules?.copy(manualScoring = current.manualScoring, deuce = current.deuce) ?: current

    companion object {
        /** Returns the preset that has the same structure as [rules], or [CUSTOM]. */
        fun of(rules: MatchRulesV1): MatchFormatPreset {
            val target = rules.canonical()
            return entries.firstOrNull { preset -> preset.rules != null && preset.applyTo(target).canonical() == target } ?: CUSTOM
        }
    }
}

/** Game and set wins that the user marked by hand in manual scoring. The maps use point ids as keys. */
data class ManualScoreMarks(
    val gameWins: Map<String, Outcome> = emptyMap(),
    val setWins: Map<String, Outcome> = emptyMap(),
)
