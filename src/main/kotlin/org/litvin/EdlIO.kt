package org.litvin.points

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.JsonFileIO
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * EDL (Edit Decision List) schema v1 and JSON read/write helpers for points-only scope (task 2.3).
 * Now includes ID backfill and duplicate repair per task 2.16.
 *
 * Storage:
 * - File name: edl.json placed in the project directory (next to <projectName>.trproj)
 * - JSON library: Jackson Kotlin module (same configuration as ManifestIO)
 */

// Data model

data class PointV1(
    val id: String = "", // Backfill for legacy entries without id (2.16)
    val startMs: Int,
    val endMs: Int,
    val label: String? = null,
    val notes: String? = null,
    val favorite: Boolean = false,
)

data class CommentV1(
    val id: Int,
    val startMs: Int,
    val durationMs: Int,
    val text: String,
    val colorHex: String = "#FFFFFF",
)

data class CommentDefaultsV1(
    val colorHex: String = "#FFFFFF",
)

/**
 * EDL v1 container. Version is fixed to 1 for this schema.
 */
data class EdlV1(
    val points: List<PointV1> = emptyList(),
    val version: Int = 1,
    val comments: List<CommentV1> = emptyList(),
    val commentDefaults: CommentDefaultsV1 = CommentDefaultsV1(),
    val nextCommentId: Int = 1,
)

/**
 * JSON read/write for edl.json with Jackson Kotlin module.
 * - Pretty prints and omits nulls.
 * - Ignores unknown fields on read.
 */
object EdlIO {
    private val logger = KotlinLogging.logger {}

    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)

    /** Generate a new unique lowercase UUIDv4 string (2.16). */
    fun generateId(): String = UUID.randomUUID().toString().lowercase()

    /** Ensure all points have unique, non-blank ids. Repairs missing/duplicate ids deterministically. */
    fun ensureUniqueIds(points: List<PointV1>): Pair<List<PointV1>, Boolean> {
        val seen = HashSet<String>()
        var changed = false
        val repaired = points.map { p ->
            var pid = p.id
            if (pid.isBlank() || seen.contains(pid)) {
                var newId: String
                do { newId = generateId() } while (seen.contains(newId))
                pid = newId
                changed = true
            }
            seen.add(pid)
            if (pid == p.id) p else p.copy(id = pid)
        }
        return repaired to changed
    }

    fun normalizeColorHex(colorHex: String?): String? {
        val match = colorHex?.trim()?.let(COLOR_HEX_PATTERN::matchEntire) ?: return null
        return "#${match.groupValues[1].uppercase(Locale.ROOT)}"
    }

    /** Remove invalid comments and repair duplicate/non-positive numeric IDs deterministically. */
    fun normalizeComments(edl: EdlV1): EdlV1 {
        val validComments = edl.comments.filter { comment ->
            comment.startMs >= 0 && comment.durationMs > 0 && comment.text.isNotBlank()
        }
        val retainedIds = HashSet<Int>()
        val firstValidIds = validComments.map { comment ->
            comment.id.takeIf { it > 0 && retainedIds.add(it) }
        }
        var replacementId = (retainedIds.maxOrNull() ?: 0) + 1
        val repaired = validComments.mapIndexed { index, comment ->
            val id = firstValidIds[index] ?: replacementId++
            val color = normalizeColorHex(comment.colorHex) ?: DEFAULT_COMMENT_COLOR
            comment.copy(id = id, colorHex = color)
        }
        val maxCommentId = repaired.maxOfOrNull { it.id } ?: 0
        val normalizedDefault = normalizeColorHex(edl.commentDefaults.colorHex) ?: DEFAULT_COMMENT_COLOR
        val nextCommentId = maxOf(edl.nextCommentId, maxCommentId + 1, replacementId, 1)
        return edl.copy(
            comments = repaired,
            commentDefaults = CommentDefaultsV1(normalizedDefault),
            nextCommentId = nextCommentId,
        )
    }

    /** Returns absolute path to edl.json for a given project directory. */
    fun edlFilePath(projectDir: String): String = File(projectDir, "edl.json").absolutePath

    /** Returns the project directory given a manifest file path. */
    fun projectDirFromManifest(manifestFilePath: String): String = File(manifestFilePath).parentFile.absolutePath

    /** Write EDL to the given file path. Verifies/repairs unique ids before persisting. */
    fun write(edlFilePath: String, edl: EdlV1) {
        val (fixedPoints, changed) = ensureUniqueIds(edl.points)
        if (changed) {
            logger.info { "Repaired missing/duplicate point ids before save; changes will be persisted" }
        }
        JsonFileIO.writeAtomically(mapper, edlFilePath, normalizeComments(edl.copy(points = fixedPoints)))
    }

    /** Read EDL from the given file path; returns empty EdlV1 if file does not exist. Repairs ids if needed. */
    fun read(edlFilePath: String): EdlV1 {
        val f = File(edlFilePath)
        if (!f.exists()) return EdlV1(emptyList(), 1)
        val raw: EdlV1 = JsonFileIO.read(mapper, f.path, EdlV1::class.java)
        val (fixedPoints, changed) = ensureUniqueIds(raw.points)
        if (changed) {
            logger.info { "Backfilled/repaired point ids while loading; will persist on next autosave" }
        }
        return normalizeComments(raw.copy(points = fixedPoints))
    }

    /** Convenience: read EDL for a given project directory. */
    fun readForProjectDir(projectDir: String): EdlV1 = read(edlFilePath(projectDir))

    /** Convenience: write EDL for a given project directory. */
    fun writeForProjectDir(projectDir: String, edl: EdlV1) = write(edlFilePath(projectDir), edl)

    private val COLOR_HEX_PATTERN = Regex("^#?([0-9A-Fa-f]{6})$")
    private const val DEFAULT_COMMENT_COLOR = "#FFFFFF"
}
