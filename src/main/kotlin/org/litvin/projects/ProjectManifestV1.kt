package org.litvin.projects

/**
 * Project manifest schema v1 (stored as JSON in project.trproj).
 *
 * Fields:
 * - id: stable UUID v4 string uniquely identifying the project
 * - name: human-friendly project name
 * - createdAt: ISO-8601 UTC timestamp string (e.g., 2026-04-01T21:00:00Z)
 * - lastOpenedAt: ISO-8601 UTC timestamp string
 * - version: manifest schema version (always 1 for v1)
 * - sourceVideo: optional absolute path to the selected source video file
 */
data class ProjectManifestV1(
    val id: String,
    val name: String,
    val createdAt: String,
    val lastOpenedAt: String,
    val version: Int = 1,
    val sourceVideo: String? = null,
)
