package org.litvin.projects

import org.litvin.ApplicationLayout
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Figures about one project for the Projects list.
 * A null [durationMs] or [fileSizeBytes] means that the source video is missing or that ffprobe cannot read it.
 */
data class ProjectStats(
    /** The manifest has a source video path, but no file is at that path. */
    val videoMissing: Boolean = false,
    val durationMs: Long? = null,
    val fileSizeBytes: Long? = null,
    val pointCount: Int = 0,
    val scoredCount: Int = 0,
    val favoriteCount: Int = 0,
)

/** Reads the duration of a video file. Returns null when the duration is not available. */
fun interface VideoDurationProbe {
    fun durationMs(videoPath: String): Long?
}

/** Reads the container duration with ffprobe. */
class FfprobeVideoDurationProbe(
    private val ffprobeExecutable: () -> String = { ApplicationLayout.current().ffprobeExecutable },
) : VideoDurationProbe {
    override fun durationMs(videoPath: String): Long? {
        return try {
            val process = ProcessBuilder(
                ffprobeExecutable(),
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=nk=1:nw=1",
                videoPath,
            ).redirectErrorStream(true).start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) return null
            val seconds = process.inputStream.bufferedReader().use { it.readText().trim() }.toDoubleOrNull()
            seconds?.takeIf { it.isFinite() && it > 0 }?.let { (it * 1000).toLong() }
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * Keeps each probed duration while the file keeps its size and its modification time.
 * The Projects list shows the same videos many times, and one ffprobe run takes too much time to repeat.
 */
class CachingVideoDurationProbe(private val delegate: VideoDurationProbe) : VideoDurationProbe {
    private data class Key(val path: String, val length: Long, val lastModified: Long)

    private val cache = ConcurrentHashMap<Key, Long>()

    override fun durationMs(videoPath: String): Long? {
        val file = File(videoPath)
        val key = Key(file.absolutePath, file.length(), file.lastModified())
        cache[key]?.let { return it }
        return delegate.durationMs(videoPath)?.also { cache[key] = it }
    }
}
