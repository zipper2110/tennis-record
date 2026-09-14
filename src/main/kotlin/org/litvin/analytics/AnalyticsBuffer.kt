package org.litvin.analytics

import com.fasterxml.jackson.databind.JsonNode
import java.util.ArrayDeque

/** Bounded volatile queue. Taking a batch deliberately preserves it for retry. */
internal class AnalyticsBuffer(private val capacity: Int = 100) {
    private val events = ArrayDeque<JsonNode>()

    @Synchronized
    fun add(event: JsonNode) {
        if (events.size == capacity) events.removeFirst()
        events.addLast(event)
    }

    @Synchronized
    fun takeBatch(limit: Int = 20): List<JsonNode> = events.take(limit)

    @Synchronized
    fun clear() = events.clear()

    @Synchronized
    fun size(): Int = events.size
}
