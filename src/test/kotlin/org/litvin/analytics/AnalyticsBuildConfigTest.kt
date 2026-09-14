package org.litvin.analytics

import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AnalyticsBuildConfigTest {
    @Test
    fun `accepts only a complete HTTPS release configuration`() {
        val config = AnalyticsBuildConfig.fromProperties(properties(
            endpoint = "https://analytics.example.test/v1/events/batch",
            privacyUrl = "https://tennis.example.test/privacy/analytics/",
            noticeVersion = "1"
        ))

        val enabled = assertIs<AnalyticsBuildConfig.Enabled>(config)
        assertEquals("https://analytics.example.test/v1/events/batch", enabled.endpoint.toString())
        assertEquals("https://tennis.example.test/privacy/analytics/", enabled.privacyUrl.toString())
        assertEquals(1, enabled.noticeVersion)
    }

    @Test
    fun `fails closed for partial or unsafe configuration`() {
        listOf(
            properties(endpoint = "https://analytics.example.test/v1/events/batch", privacyUrl = null, noticeVersion = "1"),
            properties(endpoint = "http://analytics.example.test/v1/events/batch", privacyUrl = "https://tennis.example.test/privacy/analytics/", noticeVersion = "1"),
            properties(endpoint = "https://analytics.example.test/v1/events/batch?x=1", privacyUrl = "https://tennis.example.test/privacy/analytics/", noticeVersion = "1"),
            properties(endpoint = "https://analytics.example.test/v1/events/other", privacyUrl = "https://tennis.example.test/privacy/analytics/", noticeVersion = "1"),
            properties(endpoint = "https://user:password@analytics.example.test/v1/events/batch", privacyUrl = "https://tennis.example.test/privacy/analytics/", noticeVersion = "1"),
            properties(endpoint = "https://analytics.example.test/v1/events/batch", privacyUrl = "https://tennis.example.test/privacy/analytics/#notice", noticeVersion = "1"),
            properties(endpoint = "https://analytics.example.test/v1/events/batch", privacyUrl = "https://tennis.example.test/privacy/analytics/", noticeVersion = "2")
        ).forEach { properties ->
            assertIs<AnalyticsBuildConfig.Disabled>(AnalyticsBuildConfig.fromProperties(properties))
        }
    }

    private fun properties(endpoint: String?, privacyUrl: String?, noticeVersion: String?): Properties = Properties().apply {
        endpoint?.let { setProperty(AnalyticsBuildConfig.ENDPOINT_PROPERTY, it) }
        privacyUrl?.let { setProperty(AnalyticsBuildConfig.PRIVACY_URL_PROPERTY, it) }
        noticeVersion?.let { setProperty(AnalyticsBuildConfig.NOTICE_VERSION_PROPERTY, it) }
    }
}
