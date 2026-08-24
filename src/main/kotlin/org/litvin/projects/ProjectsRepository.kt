package org.litvin.projects

import org.litvin.app.AppDataPaths
import java.io.File
import java.util.UUID

data class ProjectSummary(
    val path: String,
    val name: String,
    val secondary: String,
    val id: String = path,
)

interface ProjectsRepository {
    fun projectsRootPath(): String
    fun getRecents(): List<ProjectSummary>
    fun readManifest(path: String): ProjectManifestV1
    fun summarize(path: String): ProjectSummary
    fun createProject(sourceVideoPath: String): ProjectSummary
    fun openProject(path: String, sourceVideoPath: String? = null): ProjectSummary
}

class FileProjectsRepository(private val projectsRoot: File) : ProjectsRepository {
    constructor() : this(AppDataPaths.production().projects)

    private val recentsProvider = RecentsProvider(projectsRoot)

    override fun projectsRootPath(): String = projectsRoot.absolutePath

    override fun getRecents(): List<ProjectSummary> = recentsProvider.current().map(::summarize)

    override fun readManifest(path: String): ProjectManifestV1 = ManifestIO.read(path)

    override fun summarize(path: String): ProjectSummary {
        return try {
            val manifest = ManifestIO.read(path)
            summaryFor(path, manifest)
        } catch (_: Throwable) {
            ProjectSummary(path = path, name = File(path).nameWithoutExtension, secondary = path)
        }
    }

    override fun createProject(sourceVideoPath: String): ProjectSummary {
        val selected = File(sourceVideoPath)
        val targetRoot = projectsRoot.apply {
            if (!exists()) mkdirs()
        }

        val baseName = selected.name.substringBeforeLast('.').ifBlank { "Untitled Match" }
        var projectName = baseName
        var projectDir = File(targetRoot, projectName)
        var suffix = 2
        while (projectDir.exists()) {
            projectName = "$baseName (${suffix++})"
            projectDir = File(targetRoot, projectName)
        }
        if (!projectDir.mkdirs() && !projectDir.exists()) {
            error("Could not create project directory: ${projectDir.absolutePath}")
        }

        val now = ManifestIO.nowIsoUtc()
        val manifest = ProjectManifestV1(
            id = UUID.randomUUID().toString(),
            name = projectName,
            createdAt = now,
            lastOpenedAt = now,
            version = 1,
            sourceVideo = selected.absolutePath,
        )
        val manifestPath = File(projectDir, "$projectName.trproj").absolutePath
        ManifestIO.write(manifestPath, manifest)
        recentsProvider.refresh()
        return summaryFor(manifestPath, manifest)
    }

    override fun openProject(path: String, sourceVideoPath: String?): ProjectSummary {
        val manifest = ManifestIO.read(path)
        val updated = manifest.copy(
            lastOpenedAt = ManifestIO.nowIsoUtc(),
            sourceVideo = sourceVideoPath?.takeIf { it.isNotBlank() } ?: manifest.sourceVideo,
        )
        ManifestIO.write(path, updated)
        recentsProvider.refresh()
        return summaryFor(path, updated)
    }

    private fun summarize(entry: RecentsProvider.RecentEntry): ProjectSummary = summarize(entry.path)

    private fun summaryFor(path: String, manifest: ProjectManifestV1): ProjectSummary {
        return ProjectSummary(
            path = path,
            name = manifest.name.ifBlank { File(path).nameWithoutExtension },
            secondary = manifest.sourceVideo?.takeIf { it.isNotBlank() } ?: path,
            id = manifest.id,
        )
    }
}
