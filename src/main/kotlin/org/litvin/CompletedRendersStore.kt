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
    val outputFrameRate: String? = null,
    val bytesWritten: Long,
    val idleTrim: Boolean = false,
    val favoriteOnly: Boolean = false,
    val includeScoreboard: Boolean = false,
    val includeComments: Boolean = false,
    val createdAtEpochMs: Long = 0L,
)

object CompletedRendersStore {
    private val logger = KotlinLogging.logger {}

    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)

    private val fileLocks = CanonicalFileLockRegistry()

    internal val activeLockEntryCount: Int
        get() = fileLocks.activeEntryCount

    fun loadAll(file: File): List<CompletedRender> = withFileLock(file) {
        loadAllUnlocked(file)
    }

    private fun loadAllUnlocked(file: File): List<CompletedRender> {
        if (!file.exists()) return emptyList()
        return try {
            mapper.readValue(file)
        } catch (t: Throwable) {
            logger.warn(t) { "Failed to read completed renders. Resetting store." }
            emptyList()
        }
    }

    private fun saveAllUnlocked(file: File, items: List<CompletedRender>) {
        try {
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            mapper.writeValue(file, items)
        } catch (t: Throwable) {
            logger.warn(t) { "Failed to write completed renders." }
        }
    }

    fun append(file: File, item: CompletedRender) = withFileLock(file) {
        val items = loadAllUnlocked(file).toMutableList()
        items.add(0, item) // newest first
        saveAllUnlocked(file, items)
    }

    fun clear(file: File) = withFileLock(file) {
        saveAllUnlocked(file, emptyList())
    }

    private fun <T> withFileLock(file: File, action: () -> T): T {
        return fileLocks.withLock(file, action)
    }
}

internal class CanonicalFileLockRegistry {
    private data class Entry(
        val lock: ReentrantLock = ReentrantLock(),
        var references: Int = 0,
    )

    private val monitor = Any()
    private val entries = mutableMapOf<String, Entry>()

    val activeEntryCount: Int
        get() = synchronized(monitor) { entries.size }

    fun <T> withLock(file: File, action: () -> T): T {
        val key = try {
            file.canonicalPath
        } catch (_: Throwable) {
            file.absoluteFile.toPath().normalize().toString()
        }
        val entry = synchronized(monitor) {
            entries.getOrPut(key) { Entry() }.also { it.references++ }
        }
        try {
            return entry.lock.withLock(action)
        } finally {
            synchronized(monitor) {
                entry.references--
                if (entry.references == 0) entries.remove(key, entry)
            }
        }
    }
}
