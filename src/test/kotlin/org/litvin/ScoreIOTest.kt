package org.litvin

import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.ScoreboardPosition
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoreboardStyleId
import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Files
import java.nio.file.Path
import java.io.File

class ScoreIOTest {
    @Test
    fun roundTrip_scoreJson() {
        val tmpDir: Path = Files.createTempDirectory("score_rt_")
        val scorePath = tmpDir.resolve("score.json").toFile().absolutePath

        val score = ScoreV1(
            outcomes = linkedMapOf(
                "A" to Outcome.P1,
                "B" to Outcome.NONE,
                "C" to Outcome.P2
            ),
            version = 1
        )

        ScoreIO.write(scorePath, score)
        val readBack = ScoreIO.read(scorePath)

        assertEquals(1, readBack.version, "Version must be 1")
        assertEquals(score.outcomes, readBack.outcomes)
    }

    @Test
    fun read_missingFile_returnsEmptyScore() {
        val tmpDir: Path = Files.createTempDirectory("score_missing_")
        val scorePath = tmpDir.resolve("does_not_exist.json").toFile().absolutePath

        val readBack = ScoreIO.read(scorePath)
        assertEquals(1, readBack.version)
        assertEquals(0, readBack.outcomes.size)
    }

    @Test
    fun read_ignoresUnknownFields() {
        val tmpDir: Path = Files.createTempDirectory("score_unknown_")
        val f = tmpDir.resolve("score.json").toFile()
        f.writeText(
            """
            {
              "version": 1,
              "outcomes": {"X":"P1"},
              "unknown": {"foo": 1}
            }
            """.trimIndent()
        )
        val readBack = ScoreIO.read(f.absolutePath)
        assertEquals(1, readBack.version)
        assertEquals(mapOf("X" to Outcome.P1), readBack.outcomes)
    }

    @Test
    fun roundTrip_keepsScoreboardSettings() {
        val tmpDir: Path = Files.createTempDirectory("score_board_")
        val scorePath = tmpDir.resolve("score.json").toFile().absolutePath
        val settings = ScoreboardSettingsV1(
            style = ScoreboardStyleId.CENTER_COURT,
            title = "Batumi Raketo League",
            showTitle = false,
            position = ScoreboardPosition.BOTTOM_RIGHT,
            sizePercent = 120,
            backgroundOpacityPercent = 70,
            accentColorHex = "#FFCC00",
        )

        ScoreIO.write(scorePath, ScoreV1(scoreboard = settings))

        assertEquals(settings, ScoreIO.read(scorePath).scoreboard)
    }

    @Test
    fun read_oldScoreWithoutScoreboard_usesDefaultSettings() {
        val tmpDir: Path = Files.createTempDirectory("score_old_")
        val f = tmpDir.resolve("score.json").toFile()
        f.writeText("""{ "version": 1, "outcomes": {} }""")

        assertEquals(ScoreboardSettingsV1(), ScoreIO.read(f.absolutePath).scoreboard)
    }

    @Test
    fun read_unknownScoreboardStyle_fallsBackToDefaultStyle() {
        val tmpDir: Path = Files.createTempDirectory("score_future_")
        val f = tmpDir.resolve("score.json").toFile()
        f.writeText("""{ "version": 1, "scoreboard": { "style": "FUTURE_STYLE", "position": "TOP_RIGHT" } }""")

        val scoreboard = ScoreIO.read(f.absolutePath).scoreboard
        assertEquals(ScoreboardStyleId.BROADCAST, scoreboard.style)
        assertEquals(ScoreboardPosition.TOP_RIGHT, scoreboard.position)
    }
}
