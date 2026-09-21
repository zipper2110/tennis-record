package org.litvin.analytics

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsEventRegistryTest {
    private val mapper = ObjectMapper()

    @Test
    fun `serializes every approved event with exact bounded properties`() {
        val cases = listOf(
            AnalyticsEvent.SessionStarted to "{\"sequence_number\":7,\"name\":\"session_started\",\"elapsed_ms\":11,\"properties\":{}}",
            AnalyticsEvent.SessionHeartbeat to "{\"sequence_number\":7,\"name\":\"session_heartbeat\",\"elapsed_ms\":11,\"properties\":{}}",
            AnalyticsEvent.SessionEnded to "{\"sequence_number\":7,\"name\":\"session_ended\",\"elapsed_ms\":11,\"properties\":{}}",
            AnalyticsEvent.ProjectCreated to "{\"sequence_number\":7,\"name\":\"project_created\",\"elapsed_ms\":11,\"properties\":{}}",
            AnalyticsEvent.ProjectOpened to "{\"sequence_number\":7,\"name\":\"project_opened\",\"elapsed_ms\":11,\"properties\":{}}",
            AnalyticsEvent.SourceVideoOpened(SourceVideoResult.SUCCESS) to "{\"sequence_number\":7,\"name\":\"source_video_opened\",\"elapsed_ms\":11,\"properties\":{\"result\":\"success\"}}",
            AnalyticsEvent.PointAdded to "{\"sequence_number\":7,\"name\":\"point_added\",\"elapsed_ms\":11,\"properties\":{}}",
            AnalyticsEvent.PointRemoved to "{\"sequence_number\":7,\"name\":\"point_removed\",\"elapsed_ms\":11,\"properties\":{}}",
            AnalyticsEvent.ScorePointRecorded to "{\"sequence_number\":7,\"name\":\"score_point_recorded\",\"elapsed_ms\":11,\"properties\":{}}",
            AnalyticsEvent.AdjustmentChanged(AdjustmentCategory.COLOR) to "{\"sequence_number\":7,\"name\":\"adjustment_changed\",\"elapsed_ms\":11,\"properties\":{\"adjustment_category\":\"color\"}}",
            AnalyticsEvent.ExportStarted(ExportContainer.MP4, EncoderFamily.SOFTWARE) to "{\"sequence_number\":7,\"name\":\"export_started\",\"elapsed_ms\":11,\"properties\":{\"container\":\"mp4\",\"encoder_family\":\"software\"}}",
            AnalyticsEvent.ExportCompleted(ExportContainer.MOV, EncoderFamily.NVIDIA, ExportDurationMs(12)) to "{\"sequence_number\":7,\"name\":\"export_completed\",\"elapsed_ms\":11,\"properties\":{\"container\":\"mov\",\"encoder_family\":\"nvidia\",\"duration_ms\":12}}",
            AnalyticsEvent.ExportFailed(ExportFailureCategory.PROCESSING, ExportDurationMs(13)) to "{\"sequence_number\":7,\"name\":\"export_failed\",\"elapsed_ms\":11,\"properties\":{\"failure_category\":\"processing\",\"duration_ms\":13}}",
            AnalyticsEvent.ExportCancelled(ExportDurationMs(14)) to "{\"sequence_number\":7,\"name\":\"export_cancelled\",\"elapsed_ms\":11,\"properties\":{\"duration_ms\":14}}"
        )

        cases.forEach { (event, expectedJson) ->
            assertEquals(mapper.readTree(expectedJson), AnalyticsEventRegistry.serializeEvent(7, event, 11))
        }
    }

    @Test
    fun `shared valid fixtures pass independent defensive validation`() {
        validBatches().forEach { batch ->
            assertTrue(AnalyticsEventRegistry.isValidEnvelopeJson(batch))
        }
    }

    @Test
    fun `shared invalid fixtures are rejected without accepting free-form values`() {
        invalidBatches().forEach { invalid ->
            val payload = invalid.path("payload")
            if (!payload.isMissingNode) {
                assertFalse(AnalyticsEventRegistry.isValidEnvelopeJson(payload), invalid.path("name").asText())
            } else {
                assertFalse(AnalyticsEventRegistry.isValidEnvelopeJson(invalid.path("raw").asText()), invalid.path("name").asText())
            }
        }
    }

    @Test
    fun `duration is bounded and no serialized event can contain forbidden keys`() {
        assertFalse(AnalyticsEventRegistry.isValidDuration(-1))
        assertFalse(AnalyticsEventRegistry.isValidDuration(604_800_001))
        assertTrue(AnalyticsEventRegistry.isValidDuration(604_800_000))

        val text = AnalyticsEventRegistry.serializeEvent(
            0,
            AnalyticsEvent.ExportFailed(ExportFailureCategory.OTHER, ExportDurationMs(0)),
            0
        ).toString()
        listOf("path", "filename", "email", "exception", "stack", "metadata", "project", "player").forEach { forbidden ->
            assertFalse(text.contains(forbidden, ignoreCase = true))
        }
    }

    private fun validBatches(): List<JsonNode> = mapper.readTree(Path("analytics-contract/v1/valid-batches.json").readText()).path("validBatches").toList()

    private fun invalidBatches(): List<JsonNode> = mapper.readTree(Path("analytics-contract/v1/invalid-batches.json").readText()).path("invalidBatches").toList()
}
