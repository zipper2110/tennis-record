package org.litvin.analytics

import java.net.URI
import java.util.Properties

sealed interface AnalyticsBuildConfig {
    data class Enabled(val endpoint: URI, val privacyUrl: URI, val noticeVersion: Int, val osFamily: String) : AnalyticsBuildConfig
    data class Disabled(val diagnosticCode: String) : AnalyticsBuildConfig

    companion object {
        const val ENDPOINT_PROPERTY = "tennis.record.analytics.endpoint"
        const val PRIVACY_URL_PROPERTY = "tennis.record.analytics.privacyUrl"
        const val NOTICE_VERSION_PROPERTY = "tennis.record.analytics.noticeVersion"

        fun fromSystemProperties(): AnalyticsBuildConfig = fromProperties(System.getProperties())

        fun fromProperties(properties: Properties, osName: String = System.getProperty("os.name", "")): AnalyticsBuildConfig {
            val endpoint = parseUrl(properties.getProperty(ENDPOINT_PROPERTY)) ?: return Disabled("invalid_endpoint")
            val privacyUrl = parseUrl(properties.getProperty(PRIVACY_URL_PROPERTY)) ?: return Disabled("invalid_privacy_url")
            val noticeVersion = properties.getProperty(NOTICE_VERSION_PROPERTY)?.toIntOrNull()
                ?: return Disabled("invalid_notice_version")
            if (noticeVersion != AnalyticsEventRegistry.NOTICE_VERSION) return Disabled("unsupported_notice_version")
            if (endpoint.rawPath != "/v1/events/batch") return Disabled("invalid_endpoint_path")
            return Enabled(endpoint, privacyUrl, noticeVersion, osFamily(osName))
        }

        private fun parseUrl(value: String?): URI? = runCatching { value?.let(::URI) }.getOrNull()?.takeIf {
            it.scheme == "https" && !it.isOpaque && it.host != null && it.userInfo == null && it.rawQuery == null && it.rawFragment == null
        }

        private fun osFamily(osName: String): String = when {
            osName.contains("win", ignoreCase = true) -> "windows"
            osName.contains("mac", ignoreCase = true) -> "macos"
            osName.contains("linux", ignoreCase = true) -> "linux"
            else -> "other"
        }
    }
}
