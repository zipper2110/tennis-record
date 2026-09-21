package org.litvin

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Crash- and reader-safe persistence for the small JSON documents of a project.
 *
 * Writing straight to the destination truncates it before the new content is streamed, so a reader
 * on another thread can observe an empty file, and a crash mid-write loses the document. Two things
 * prevent that: a per-file lock, which keeps the readers and the writer inside this process off the
 * file at the same time, and a sibling temporary file moved into place, which leaves the previous
 * content intact if serializing fails or the process dies partway.
 *
 * The lock is also what makes the move reliable on Windows, where replacing a file that any reader
 * still holds open fails - Java opens files without granting deletion to others, so no timeout can
 * be relied on to outlast a reader.
 */
object JsonFileIO {
    /**
     * Reads through an NIO stream. Windows refuses to replace a file that a reader holds open via
     * [java.io.FileInputStream], which is what Jackson's `readValue(File)` uses; an NIO channel
     * permits the replacement and keeps serving the content the reader started with.
     */
    fun <T : Any> read(mapper: ObjectMapper, sourcePath: String, type: Class<T>): T {
        val lock = lockFor(sourcePath).readLock()
        lock.lock()
        try {
            return Files.newInputStream(File(sourcePath).toPath()).use { mapper.readValue(it, type) }
        } finally {
            lock.unlock()
        }
    }

    fun writeAtomically(mapper: ObjectMapper, targetPath: String, value: Any) {
        val target = File(targetPath)
        target.parentFile?.mkdirs()
        val temporary = File.createTempFile(".${target.name}", ".tmp", target.parentFile)
        val lock = lockFor(targetPath).writeLock()
        lock.lock()
        try {
            mapper.writeValue(temporary, value)
            moveIntoPlace(temporary, target)
        } finally {
            lock.unlock()
            temporary.delete()
        }
    }

    private fun lockFor(path: String): ReentrantReadWriteLock {
        val key = try {
            File(path).canonicalPath
        } catch (_: IOException) {
            File(path).absolutePath
        }
        return locks.getOrPut(key) { ReentrantReadWriteLock() }
    }

    private val locks = ConcurrentHashMap<String, ReentrantReadWriteLock>()

    private fun moveIntoPlace(temporary: File, target: File) {
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            // Filesystems without atomic replace still benefit from the fully written temporary file.
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
