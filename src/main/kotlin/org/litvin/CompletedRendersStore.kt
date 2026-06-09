package org.litvin

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Task 3.11 — Completed Renders list (persistent)
 */

data class CompletedRender(
    val id: String,
    val projectId: String? = null,
    val projectName: String? = null,
    val outputPath: String,
    val fileName: String,
    val encoderLabel: String,
    val outWidth: Int,
    val outHeight: Int,
    val bytesWritten: Long,
    val includeScoreboard: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

object CompletedRendersStore {
    private val logger = KotlinLogging.logger {}

    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)

    private val lock = ReentrantLock()

    private fun appDataDir(): File {
        val dir = ApplicationLayout.current().appDataDirectory
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun jsonFile(): File = File(appDataDir(), "completed-renders.json")

    fun loadAll(): List<CompletedRender> = lock.withLock {
        val f = jsonFile()
        if (!f.exists()) return emptyList()
        return try {
            mapper.readValue(f)
        } catch (t: Throwable) {
            logger.warn(t) { "Failed to read completed renders. Resetting store." }
            emptyList()
        }
    }

    private fun saveAll(items: List<CompletedRender>) = lock.withLock {
        val f = jsonFile()
        try {
            mapper.writeValue(f, items)
        } catch (t: Throwable) {
            logger.warn(t) { "Failed to write completed renders." }
        }
    }

    fun append(job: RenderJob) {
        val item = CompletedRender(
            id = job.id,
            projectId = job.projectId,
            projectName = job.projectName,
            outputPath = job.outputPath,
            fileName = File(job.outputPath).name,
            encoderLabel = job.encoderLabel,
            outWidth = job.outWidth,
            outHeight = job.outHeight,
            bytesWritten = job.bytesWritten,
            includeScoreboard = job.includeScoreboard,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        val items = loadAll().toMutableList()
        items.add(0, item) // newest first
        saveAll(items)
    }

    fun clear() {
        saveAll(emptyList())
    }
}
