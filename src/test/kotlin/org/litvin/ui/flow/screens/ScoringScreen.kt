package org.litvin.ui.flow.screens

import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.ui.commons.AppShortcuts
import java.nio.file.Path
import javax.swing.KeyStroke

internal class ScoringScreen(application: ApplicationScreen) : UserFlowScreen(application) {
    fun open(): ScoringScreen = apply { open("nav-scoring", "scoring-player-1-point") }

    fun awardPointToPlayer1(): ScoringScreen = apply {
        context.driver.press(KeyStroke.getKeyStroke(AppShortcuts.SCORE_PLAYER_1.keyStroke))
    }

    fun awardPointToPlayer2(): ScoringScreen = apply {
        context.driver.click("scoring-player-2-point")
    }

    fun assertVisibleScore(expected: String) {
        application.eventually("visible score to become '$expected'") {
            context.driver.requireText("scoring-score-summary", expected)
        }
    }

    fun assertOutcomePersisted(projectDirectory: Path, pointId: String, expected: Outcome) {
        application.eventually("outcome $expected for $pointId to be persisted") {
            val actual = ScoreIO.readForProjectDir(projectDirectory.toString()).outcomes[pointId]
            if (actual != expected) throw AssertionError("Persisted outcome for $pointId was $actual, expected $expected")
        }
    }
}
