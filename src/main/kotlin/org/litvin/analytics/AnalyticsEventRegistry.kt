package org.litvin.analytics

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.UUID

/** Canonical v1 event schema and defensive wire validator. */
object AnalyticsEventRegistry {
    const val SCHEMA_VERSION = 1
    const val NOTICE_VERSION = 1
    const val MAX_SEQUENCE_NUMBER = 1_000_000
    const val MAX_ELAPSED_MS = 604_800_000
    private const val MAX_EVENTS = 20
    private const val MAX_PROPERTIES_BYTES = 2_048
    private val mapper = ObjectMapper()
    private val envelopeKeys = setOf("schema_version", "notice_version", "session_id", "app_version", "os_family", "events")
    private val eventKeys = setOf("sequence_number", "name", "elapsed_ms", "properties")
    private val appVersion = Regex("[0-9A-Za-z.+-]{1,32}")
    private val uuidV4 = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private val osFamilies = setOf("windows", "macos", "linux", "other")

    internal fun serializeEvent(sequenceNumber: Int, event: AnalyticsEvent, elapsedMs: Int): JsonNode {
        require(sequenceNumber in 0..MAX_SEQUENCE_NUMBER)
        require(isValidDuration(elapsedMs))
        val node = mapper.createObjectNode()
        node.put("sequence_number", sequenceNumber)
        node.put("name", eventName(event))
        node.put("elapsed_ms", elapsedMs)
        val properties = node.putObject("properties")
        when (event) {
            is AnalyticsEvent.SourceVideoOpened -> properties.put("result", event.result.wireValue)
            is AnalyticsEvent.AdjustmentChanged -> properties.put("adjustment_category", event.category.wireValue)
            is AnalyticsEvent.ExportStarted -> {
                properties.put("container", event.container.wireValue)
                properties.put("encoder_family", event.encoderFamily.wireValue)
            }
            is AnalyticsEvent.ExportCompleted -> {
                properties.put("container", event.container.wireValue)
                properties.put("encoder_family", event.encoderFamily.wireValue)
                properties.put("duration_ms", event.duration.value)
            }
            is AnalyticsEvent.ExportFailed -> {
                properties.put("failure_category", event.category.wireValue)
                properties.put("duration_ms", event.duration.value)
            }
            is AnalyticsEvent.ExportCancelled -> properties.put("duration_ms", event.duration.value)
            else -> Unit
        }
        return node
    }

    internal fun createEnvelope(
        sessionId: UUID,
        appVersion: String,
        osFamily: String,
        events: List<JsonNode>
    ): AnalyticsEnvelope {
        val envelope = AnalyticsEnvelope(SCHEMA_VERSION, NOTICE_VERSION, sessionId.toString(), appVersion, osFamily, events)
        require(isValidEnvelopeJson(toJson(envelope)))
        return envelope
    }

    internal fun toJson(envelope: AnalyticsEnvelope): JsonNode = mapper.createObjectNode().apply {
        put("schema_version", envelope.schemaVersion)
        put("notice_version", envelope.noticeVersion)
        put("session_id", envelope.sessionId)
        put("app_version", envelope.appVersion)
        put("os_family", envelope.osFamily)
        set<JsonNode>("events", mapper.createArrayNode().addAll(envelope.events))
    }

    internal fun isValidDuration(value: Int): Boolean = value in 0..MAX_ELAPSED_MS

    internal fun isValidEnvelopeJson(json: String): Boolean = runCatching { mapper.readTree(json) }.getOrNull()?.let(::isValidEnvelopeJson) == true

    internal fun isValidEnvelopeJson(envelope: JsonNode): Boolean {
        if (!envelope.isObject || envelope.fieldNames().asSequence().toSet() != envelopeKeys) return false
        if (envelope.path("schema_version").intValue() != SCHEMA_VERSION || !envelope.path("schema_version").isInt) return false
        if (envelope.path("notice_version").intValue() != NOTICE_VERSION || !envelope.path("notice_version").isInt) return false
        val sessionId = envelope.path("session_id")
        if (!sessionId.isTextual || !uuidV4.matches(sessionId.textValue())) return false
        val version = envelope.path("app_version")
        if (!version.isTextual || !appVersion.matches(version.textValue())) return false
        val osFamily = envelope.path("os_family")
        if (!osFamily.isTextual || osFamily.textValue() !in osFamilies) return false
        val events = envelope.path("events")
        if (!events.isArray || events.size() !in 1..MAX_EVENTS) return false
        val sequences = HashSet<Int>()
        return events.all { event -> validateEvent(event, sequences) }
    }

    private fun validateEvent(event: JsonNode, sequences: MutableSet<Int>): Boolean {
        if (!event.isObject || event.fieldNames().asSequence().toSet() != eventKeys) return false
        val sequence = event.path("sequence_number")
        if (!sequence.isInt || sequence.intValue() !in 0..MAX_SEQUENCE_NUMBER || !sequences.add(sequence.intValue())) return false
        val elapsed = event.path("elapsed_ms")
        if (!elapsed.isInt || !isValidDuration(elapsed.intValue())) return false
        val name = event.path("name")
        val properties = event.path("properties")
        if (!name.isTextual || !properties.isObject || mapper.writeValueAsBytes(properties).size > MAX_PROPERTIES_BYTES) return false
        return validProperties(name.textValue(), properties as ObjectNode)
    }

    private fun validProperties(name: String, properties: ObjectNode): Boolean {
        fun noProperties() = properties.isEmpty
        fun exactKeys(vararg keys: String) = properties.fieldNames().asSequence().toSet() == keys.toSet()
        fun enum(key: String, values: Array<out Enum<*>>) = properties.path(key).isTextual && properties.path(key).textValue() in values.map { enumWireValue(it) }
        fun duration() = properties.path("duration_ms").isInt && isValidDuration(properties.path("duration_ms").intValue())
        return when (name) {
            "session_started", "session_heartbeat", "session_ended", "project_created", "project_opened", "markup_point_added", "markup_point_removed", "score_point_recorded" -> noProperties()
            "source_video_opened" -> exactKeys("result") && enum("result", SourceVideoResult.entries.toTypedArray())
            "adjustment_changed" -> exactKeys("adjustment_category") && enum("adjustment_category", AdjustmentCategory.entries.toTypedArray())
            "export_started" -> exactKeys("container", "encoder_family") && enum("container", ExportContainer.entries.toTypedArray()) && enum("encoder_family", EncoderFamily.entries.toTypedArray())
            "export_completed" -> exactKeys("container", "encoder_family", "duration_ms") && enum("container", ExportContainer.entries.toTypedArray()) && enum("encoder_family", EncoderFamily.entries.toTypedArray()) && duration()
            "export_failed" -> exactKeys("failure_category", "duration_ms") && enum("failure_category", ExportFailureCategory.entries.toTypedArray()) && duration()
            "export_cancelled" -> exactKeys("duration_ms") && duration()
            else -> false
        }
    }

    private fun enumWireValue(value: Enum<*>): String = when (value) {
        is SourceVideoResult -> value.wireValue
        is AdjustmentCategory -> value.wireValue
        is ExportContainer -> value.wireValue
        is EncoderFamily -> value.wireValue
        is ExportFailureCategory -> value.wireValue
        else -> error("unknown analytics enum")
    }

    private fun eventName(event: AnalyticsEvent): String = when (event) {
        AnalyticsEvent.SessionStarted -> "session_started"
        AnalyticsEvent.SessionHeartbeat -> "session_heartbeat"
        AnalyticsEvent.SessionEnded -> "session_ended"
        AnalyticsEvent.ProjectCreated -> "project_created"
        AnalyticsEvent.ProjectOpened -> "project_opened"
        is AnalyticsEvent.SourceVideoOpened -> "source_video_opened"
        AnalyticsEvent.MarkupPointAdded -> "markup_point_added"
        AnalyticsEvent.MarkupPointRemoved -> "markup_point_removed"
        AnalyticsEvent.ScorePointRecorded -> "score_point_recorded"
        is AnalyticsEvent.AdjustmentChanged -> "adjustment_changed"
        is AnalyticsEvent.ExportStarted -> "export_started"
        is AnalyticsEvent.ExportCompleted -> "export_completed"
        is AnalyticsEvent.ExportFailed -> "export_failed"
        is AnalyticsEvent.ExportCancelled -> "export_cancelled"
    }
}
