package org.litvin.scoring

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

/**
 * Score v1 schema and JSON read/write helpers for scoring outcomes (task 4.9).
 *
 * Storage:
 * - File name: score.json placed in the project directory (next to edl.json and project.trproj)
 * - JSON library: Jackson Kotlin module (same configuration as EdlIO/ManifestIO)
 * - Unknown fields are ignored on read; pretty-printed; nulls omitted
 */

enum class Outcome { P1, P2, NONE }

data class ScoreV1(
    val outcomes: Map<String, Outcome> = emptyMap(),
    val version: Int = 1,
    val player1Name: String = "Player 1",
    val player2Name: String = "Player 2",
    val player1ColorHex: String = "#4DA3FF",
    val player2ColorHex: String = "#FF6B6B",
)

object ScoreIO {
    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)

    /** Returns absolute path to score.json for a given project directory. */
    fun scoreFilePath(projectDir: String): String = File(projectDir, "score.json").absolutePath

    /** Write ScoreV1 to the given file path. */
    fun write(scoreFilePath: String, score: ScoreV1) {
        mapper.writeValue(File(scoreFilePath), score)
    }

    /** Read ScoreV1 from the given file path; returns empty ScoreV1 if file does not exist. */
    fun read(scoreFilePath: String): ScoreV1 {
        val f = File(scoreFilePath)
        if (!f.exists()) return ScoreV1()
        return mapper.readValue(f)
    }

    /** Convenience: read ScoreV1 for a given project directory. */
    fun readForProjectDir(projectDir: String): ScoreV1 = read(scoreFilePath(projectDir))

    /** Convenience: write ScoreV1 for a given project directory. */
    fun writeForProjectDir(projectDir: String, score: ScoreV1) = write(scoreFilePath(projectDir), score)
}
