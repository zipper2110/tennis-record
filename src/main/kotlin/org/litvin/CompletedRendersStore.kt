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
    val createdAtEpochMs: Long = 0L,
)

object CompletedRendersStore {
    private val logger = KotlinLogging.logger {}

    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)

    private val lock = ReentrantLock()

    fun loadAll(file: File): List<CompletedRender> = lock.withLock {
        val f = file
        if (!f.exists()) return emptyList()
        return try {
            mapper.readValue(f)
        } catch (t: Throwable) {
            logger.warn(t) { "Failed to read completed renders. Resetting store." }
            emptyList()
        }
    }

    private fun saveAll(file: File, items: List<CompletedRender>) = lock.withLock {
        val f = file
        try {
            f.parentFile?.let { if (!it.exists()) it.mkdirs() }
            mapper.writeValue(f, items)
        } catch (t: Throwable) {
            logger.warn(t) { "Failed to write completed renders." }
        }
    }

    fun append(file: File, item: CompletedRender) {
        val items = loadAll(file).toMutableList()
        items.add(0, item) // newest first
        saveAll(file, items)
    }

    fun clear(file: File) {
        saveAll(file, emptyList())
    }
}
