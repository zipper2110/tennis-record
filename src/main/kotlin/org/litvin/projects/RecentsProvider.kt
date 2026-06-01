package org.litvin.projects

import java.io.File
import java.time.Instant

/**
 * Provides Most-Recently-Used (MRU) projects list by scanning manifests on disk.
 * - Scans %USERPROFILE%\Documents\TennisRecord\Projects
 * - Reads <projectName>.trproj in each subfolder
 * - Keeps only valid manifests; sorts by lastOpenedAt desc
 * - Supports simple in-memory remove-from-view
 */
object RecentsProvider {
    data class RecentEntry(
        val path: String,
        val name: String,
        val lastOpenedAt: Instant?,
    )

    private var cache: List<RecentEntry> = emptyList()

    fun projectsRoot(): File {
        val userHome = System.getProperty("user.home") ?: "."
        return File(userHome, "Documents\\TennisRecord\\Projects")
    }

    private fun scan(): List<RecentEntry> {
        val root = projectsRoot()
        val items = mutableListOf<RecentEntry>()
        if (root.exists() && root.isDirectory) {
            root.listFiles { f -> f.isDirectory }?.forEach { dir ->
                val manifests = dir.listFiles { f ->
                    f.isFile && f.name.endsWith(".trproj", ignoreCase = true)
                }?.toList() ?: emptyList()
                val manifestFile = manifests.firstOrNull() ?: return@forEach
                try {
                    val mf = ManifestIO.read(manifestFile.absolutePath)
                    val ts = try {
                        Instant.parse(mf.lastOpenedAt)
                    } catch (_: Throwable) {
                        null
                    }
                    items.add(RecentEntry(manifestFile.absolutePath, mf.name.ifBlank { manifestFile.nameWithoutExtension }, ts))
                } catch (_: Throwable) {
                    // Ignore invalid manifest files while building the recents list.
                }
            }
        }
        cache = items.sortedWith(
            compareByDescending<RecentEntry> { it.lastOpenedAt ?: Instant.EPOCH }
                .thenBy { it.name.lowercase() }
        )
        return cache
    }

    fun refresh(): List<RecentEntry> = scan()

    fun current(): List<RecentEntry> = cache.ifEmpty { scan() }
}
