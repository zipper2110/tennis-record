package org.litvin

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

/**
 * EDL (Edit Decision List) schema v1 and JSON read/write helpers for points-only scope (task 2.3).
 *
 * Storage:
 * - File name: edl.json placed in the project directory (next to <projectName>.trproj)
 * - JSON library: Jackson Kotlin module (same configuration as ManifestIO)
 */

// Data model

data class PointV1(
    val id: String,
    val startMs: Int,
    val endMs: Int,
    val label: String? = null,
    val notes: String? = null,
)

/**
 * EDL v1 container. Version is fixed to 1 for this schema.
 */
data class EdlV1(
    val points: List<PointV1> = emptyList(),
    val version: Int = 1,
)

/**
 * JSON read/write for edl.json with Jackson Kotlin module.
 * - Pretty prints and omits nulls.
 * - Ignores unknown fields on read.
 */
object EdlIO {
    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)

    /** Returns absolute path to edl.json for a given project directory. */
    fun edlFilePath(projectDir: String): String = File(projectDir, "edl.json").absolutePath

    /** Returns the project directory given a manifest file path. */
    fun projectDirFromManifest(manifestFilePath: String): String = File(manifestFilePath).parentFile.absolutePath

    /** Write EDL to the given file path. */
    fun write(edlFilePath: String, edl: EdlV1) {
        mapper.writeValue(File(edlFilePath), edl)
    }

    /** Read EDL from the given file path; returns empty EdlV1 if file does not exist. */
    fun read(edlFilePath: String): EdlV1 {
        val f = File(edlFilePath)
        if (!f.exists()) return EdlV1(emptyList(), 1)
        return mapper.readValue(f)
    }

    /** Convenience: read EDL for a given project directory. */
    fun readForProjectDir(projectDir: String): EdlV1 = read(edlFilePath(projectDir))

    /** Convenience: write EDL for a given project directory. */
    fun writeForProjectDir(projectDir: String, edl: EdlV1) = write(edlFilePath(projectDir), edl)
}
