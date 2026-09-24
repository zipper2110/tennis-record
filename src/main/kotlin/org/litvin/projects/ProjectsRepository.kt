package org.litvin.projects

import org.litvin.app.AppDataPaths
import org.litvin.points.EdlIO
import org.litvin.scoring.ScoreIO
import java.awt.Desktop
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
    /** Creates a project with the [name] for the video. A suffix such as " (2)" makes the name unique. */
    fun createProject(sourceVideoPath: String, name: String): ProjectSummary
    fun openProject(path: String, sourceVideoPath: String? = null): ProjectSummary

    /** Changes the name in the manifest. The project folder and the manifest file keep their names. */
    fun renameProject(path: String, name: String): ProjectSummary

    /** Returns the video and point figures of the project. This call can start a slow video probe. */
    fun stats(path: String): ProjectStats = ProjectStats()

    /** Removes the project folder with the points, the scores, and the settings. The source video stays on the disk. */
    fun deleteProject(path: String)
}

class FileProjectsRepository(
    private val projectsRoot: File,
    private val durationProbe: VideoDurationProbe = CachingVideoDurationProbe(FfprobeVideoDurationProbe()),
    private val removeFolder: (File) -> Unit = ::moveToTrashOrDelete,
) : ProjectsRepository {
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

    override fun createProject(sourceVideoPath: String, name: String): ProjectSummary {
        NewProjectRules.nameError(name)?.let { throw IllegalArgumentException(it) }
        val selected = File(sourceVideoPath)
        val targetRoot = projectsRoot.apply {
            if (!exists()) mkdirs()
        }

        val baseName = name.trim()
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

    override fun renameProject(path: String, name: String): ProjectSummary {
        NewProjectRules.nameError(name)?.let { throw IllegalArgumentException(it) }
        val updated = ManifestIO.read(path).copy(name = name.trim())
        ManifestIO.write(path, updated)
        recentsProvider.refresh()
        return summaryFor(path, updated)
    }

    override fun stats(path: String): ProjectStats {
        val videoPath = ManifestIO.read(path).sourceVideo?.takeIf { it.isNotBlank() }
        val video = videoPath?.let(::File)?.takeIf { it.isFile }
        val projectDir = EdlIO.projectDirFromManifest(path)
        val points = EdlIO.readForProjectDir(projectDir).points
        val outcomes = ScoreIO.readForProjectDir(projectDir).outcomes
        return ProjectStats(
            videoMissing = videoPath != null && video == null,
            durationMs = video?.let { durationProbe.durationMs(it.absolutePath) },
            fileSizeBytes = video?.length(),
            pointCount = points.size,
            scoredCount = points.count { outcomes.containsKey(it.id) },
            favoriteCount = points.count { it.favorite },
        )
    }

    override fun deleteProject(path: String) {
        val projectDir = File(path).absoluteFile.parentFile
            ?: throw IllegalArgumentException("The project has no folder: $path")
        // Remove only a direct subfolder of the projects root. A bad path must not remove other folders.
        require(projectDir.parentFile?.canonicalFile == projectsRoot.canonicalFile) {
            "The project is not in the projects folder: $path"
        }
        removeFolder(projectDir)
        check(!projectDir.exists()) { "Could not delete the project folder: ${projectDir.absolutePath}" }
        recentsProvider.refresh()
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

/** Moves the folder to the Recycle Bin. When the system has no Recycle Bin, deletes the folder. */
private fun moveToTrashOrDelete(folder: File) {
    val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
    if (desktop?.isSupported(Desktop.Action.MOVE_TO_TRASH) == true && desktop.moveToTrash(folder)) return
    folder.deleteRecursively()
}
