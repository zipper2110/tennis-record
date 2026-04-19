package org.litvin

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
}
