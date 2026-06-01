package org.litvin.projects

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * JSON read/write for project manifest v1 using Jackson Kotlin module.
 * - Pretty prints and omits nulls.
 * - Ignores unknown fields on read.
 * - Times are ISO-8601 UTC strings with 'Z'.
 */
object ManifestIO {
    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)

    fun nowIsoUtc(): String = isoFormatter.format(Instant.now())

    /** Returns the manifest file path given a project directory path. */
    fun manifestFilePath(projectDir: String): String = File(projectDir, "project.trproj").absolutePath

    fun write(manifestFilePath: String, manifest: ProjectManifestV1) {
        mapper.writeValue(File(manifestFilePath), manifest)
    }

    fun read(manifestFilePath: String): ProjectManifestV1 {
        return mapper.readValue(File(manifestFilePath))
    }
}
