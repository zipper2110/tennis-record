package org.litvin.export

import org.litvin.points.PointV1

/**
 * Splits an idle-trim keep list into chunks, each encoded by its own ffmpeg pass.
 *
 * Idle trim gives every kept segment its own `-ss`/`-t` input so that decoding costs the kept
 * duration instead of the whole source span. ffmpeg opens a demuxer and allocates a decoder for
 * every input before it starts working, though, and a full match easily holds a hundred points —
 * that many simultaneous 4K decoders would exhaust memory. So a bounded number of segments is
 * encoded at a time and the resulting chunks are joined without re-encoding.
 */
object ExportChunkPlanner {
    const val DEFAULT_MAX_SEGMENTS_PER_CHUNK = 8

    /** Override for troubleshooting; a larger value trades memory for fewer passes. */
    private const val MAX_SEGMENTS_PROPERTY = "tennisrecord.export.chunkSegments"

    data class Chunk(
        val keeps: List<PointV1>,
        /** Output time where this chunk starts, i.e. the kept duration of every earlier chunk. */
        val outputOffsetMs: Long,
    )

    fun maxSegmentsPerChunk(): Int =
        System.getProperty(MAX_SEGMENTS_PROPERTY)?.toIntOrNull()?.takeIf { it > 0 }
            ?: DEFAULT_MAX_SEGMENTS_PER_CHUNK

    fun plan(keeps: List<PointV1>, maxSegmentsPerChunk: Int = maxSegmentsPerChunk()): List<Chunk> {
        if (keeps.isEmpty()) return emptyList()
        var offset = 0L
        return keeps.chunked(maxSegmentsPerChunk.coerceAtLeast(1)).map { group ->
            Chunk(keeps = group, outputOffsetMs = offset).also { offset += keptDurationMs(group) }
        }
    }

    fun keptDurationMs(keeps: List<PointV1>): Long =
        keeps.sumOf { (it.endMs - it.startMs).toLong().coerceAtLeast(0L) }
}
