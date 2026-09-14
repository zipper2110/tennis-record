package org.litvin.analytics

import com.fasterxml.jackson.databind.JsonNode

/** Internal transport DTO; product code cannot create its own property maps. */
internal data class AnalyticsEnvelope(
    val schemaVersion: Int,
    val noticeVersion: Int,
    val sessionId: String,
    val appVersion: String,
    val osFamily: String,
    val events: List<JsonNode>
)
