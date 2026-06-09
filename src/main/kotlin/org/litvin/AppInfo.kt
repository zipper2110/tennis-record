package org.litvin

object AppInfo {
    const val NAME = "Tennis Record"

    val version: String by lazy {
        System.getProperty("tennis.record.version")?.takeIf { it.isNotBlank() }
            ?: AppInfo::class.java.`package`?.implementationVersion?.takeIf { it.isNotBlank() }
            ?: "development"
    }

    val displayName: String
        get() = if (version == "development") NAME else "$NAME $version"
}
