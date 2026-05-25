package org.litvin.ui.tabs.scoring

/**
 * T1 (E-SC-001): Draft contracts for Scoring UI componentization.
 *
 * These interfaces decouple leaf Swing components from domain services.
 * The container (`SwingScoringPanel`) will implement/wire `ScoringActions` and
 * provide `ScoringViewState` snapshots to components.
 */

/** Sides used across scoring UI without leaking domain types. */
enum class Side { P1, P2 }

/**
 * View-to-domain (or container) commands used by leaf UI components.
 * Only method names/semantics, no implementation here. No side effects expected in EDT beyond UI triggers.
 */
interface ScoringActions {
    // Scoring inputs
    fun pointWon(side: Side)
    fun undo()
    fun redo()
    fun finalizeGame(winner: Side)
    fun finalizeSet(winner: Side)
    fun toggleServe()

    // Persistence / project
    fun saveScore()
    fun autosave()
}

interface VideoPlayerActions {
    // Video / timecode controls (subset used in Scoring tab)
    fun playPause()
    fun seekBy(milliseconds: Long)
    fun setSpeedMultiplier(multiplier: Float)
    /** Toggle whether Left/Right step by a single frame when paused. */
    fun setFrameStepEnabled(enabled: Boolean)
}

interface NavigationActions {
    // Timeline / selection
    fun navigateToPoint(index: Int)
    fun advanceToNextPoint()
}

/** Immutable snapshot of the Scoring tab state consumed by components. */
data class ScoringViewState(
    // Header basics
    val player1Name: String,
    val player2Name: String,
    val serving: Side?,

    // Current in-progress game
    val p1Points: Int,
    val p2Points: Int,
    val isTiebreak: Boolean,

    // Completed sets (left-to-right as played)
    val sets: List<SetScoreDto>,

    // Current set in progress score (games)
    val gamesP1: Int,
    val gamesP2: Int,

    // Timeline/selection
    val selectedPointIndex: Int?,
    val totalPoints: Int,

    // Video/player status (subset relevant to the tab)
    val isPlaying: Boolean,
    val speedMultiplier: Float,
)

/** Compact representation of a tennis set score. */
data class SetScoreDto(
    val p1Games: Int,
    val p2Games: Int,
    val tiebreak: Boolean = false,
)
