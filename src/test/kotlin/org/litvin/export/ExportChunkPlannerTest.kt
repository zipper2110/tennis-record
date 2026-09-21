package org.litvin.export

import org.litvin.points.PointV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportChunkPlannerTest {
    private fun point(id: String, startMs: Int, endMs: Int) = PointV1(id = id, startMs = startMs, endMs = endMs)

    @Test
    fun plan_empty_isEmpty() {
        assertTrue(ExportChunkPlanner.plan(emptyList()).isEmpty())
    }

    @Test
    fun plan_fitsInOneChunk_keepsSinglePass() {
        val keeps = listOf(point("A", 0, 1000), point("B", 5000, 6000))
        val chunks = ExportChunkPlanner.plan(keeps, maxSegmentsPerChunk = 8)
        assertEquals(1, chunks.size)
        assertEquals(keeps, chunks[0].keeps)
        assertEquals(0L, chunks[0].outputOffsetMs)
    }

    @Test
    fun plan_splitsAndAccumulatesOutputOffsets() {
        // Five one-second keeps scattered across the source, three segments per chunk.
        val keeps = (0 until 5).map { point("P$it", it * 10_000, it * 10_000 + 1000) }
        val chunks = ExportChunkPlanner.plan(keeps, maxSegmentsPerChunk = 3)

        assertEquals(2, chunks.size)
        assertEquals(listOf("P0", "P1", "P2"), chunks[0].keeps.map { it.id })
        assertEquals(listOf("P3", "P4"), chunks[1].keeps.map { it.id })
        // The second chunk starts where the first chunk's kept material ends, not where it sits in the source.
        assertEquals(0L, chunks[0].outputOffsetMs)
        assertEquals(3000L, chunks[1].outputOffsetMs)
    }

    @Test
    fun plan_everyChunkIsWithinTheLimit() {
        val keeps = (0 until 128).map { point("P$it", it * 10_000, it * 10_000 + 1000) }
        val chunks = ExportChunkPlanner.plan(keeps, maxSegmentsPerChunk = 8)

        assertEquals(16, chunks.size)
        assertTrue(chunks.all { it.keeps.size <= 8 }, "No chunk may exceed the segment limit")
        // Nothing is dropped or duplicated by the split.
        assertEquals(keeps.map { it.id }, chunks.flatMap { chunk -> chunk.keeps.map { it.id } })
        // Offsets tile the output timeline end to end.
        assertEquals(ExportChunkPlanner.keptDurationMs(keeps), chunks.last().let {
            it.outputOffsetMs + ExportChunkPlanner.keptDurationMs(it.keeps)
        })
    }

    @Test
    fun keptDurationMs_sumsSegmentsAndIgnoresInverted() {
        val keeps = listOf(point("A", 0, 1000), point("B", 5000, 5500), point("C", 9000, 8000))
        assertEquals(1500L, ExportChunkPlanner.keptDurationMs(keeps))
    }
}
