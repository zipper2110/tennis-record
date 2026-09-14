package org.litvin.analytics

import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsBufferTest {
    private val mapper = ObjectMapper()

    @Test
    fun `keeps a bounded in-memory queue and returns batches without removing retry data`() {
        val buffer = AnalyticsBuffer(capacity = 3)
        (0..3).forEach { buffer.add(mapper.readTree("{\"sequence_number\":$it}")) }

        assertEquals(3, buffer.size())
        assertEquals(listOf(1, 2), buffer.takeBatch(2).map { it.path("sequence_number").asInt() })
        assertEquals(listOf(1, 2, 3), buffer.takeBatch().map { it.path("sequence_number").asInt() })
    }
}
