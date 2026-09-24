package org.litvin.ui.tabs.projects.presenter

import org.litvin.export.RenderFormatting
import org.litvin.projects.FileProjectsRepository
import org.litvin.projects.NewProjectRules
import org.litvin.projects.ProjectStats
import org.litvin.projects.ProjectSummary
import org.litvin.projects.ProjectsRepository
import org.litvin.app.PreferencesProvider
import java.awt.EventQueue
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.prefs.Preferences

class DefaultProjectsPresenter(
    private val repository: ProjectsRepository = FileProjectsRepository(),
    private val preferences: Preferences = PreferencesProvider.production().node(PreferencesProvider.PROJECTS),
    private val ioExecutor: Executor = Executors.newSingleThreadExecutor(),
) : ProjectsPresenter {
    private val pageSize = 10
    private val stateLock = Any()

    private var view: ProjectsView? = null
    private var currentProject: ProjectCardState? = null
    private var recents: List<ProjectCardState> = emptyList()
    private var currentPage: Int = 1

    // The presenter keeps old figures during a reload. Thus the table does not flash empty cells.
    private val stats = mutableMapOf<String, ProjectStatsState>()

    override fun attach(view: ProjectsView) {
        this.view = view
        renderState()
    }

    override fun detach() {
        view = null
    }

    override fun onActivated() {
        refreshRecents()
    }

    override fun onDeactivated() {
        // No media or timers to pause for the Projects tab.
    }

    override fun onIntent(intent: ProjectsIntent) {
        when (intent) {
            ProjectsIntent.ImportNewMatch -> emitEffect(ProjectsViewEffect.ChooseSourceVideo(lastVideoDir()))
            is ProjectsIntent.SourceVideoSelected -> confirmNewProject(intent.path)
            is ProjectsIntent.CreateProject -> createProject(intent.name, intent.sourceVideoPath)
            is ProjectsIntent.OpenProject -> openProject(intent.manifestPath)
            is ProjectsIntent.RenameProject -> renameProject(intent.manifestPath, intent.name)
            is ProjectsIntent.DeleteProject -> deleteProject(intent.manifestPath)
            is ProjectsIntent.MissingSourceVideoSelected -> openProject(intent.manifestPath, intent.sourceVideoPath)
            is ProjectsIntent.GoToPage -> goToPage(intent.page)
        }
    }

    private fun refreshRecents() {
        runIo("Failed to load projects") {
            val refreshed = repository.getRecents().map { it.toCardState() }
            synchronized(stateLock) {
                recents = refreshed
                currentPage = 1
            }
            renderState()
            loadVisibleStats()
        }
    }

    private fun confirmNewProject(sourceVideoPath: String) {
        if (sourceVideoPath.isBlank()) return
        rememberVideoDir(sourceVideoPath)
        emitEffect(ProjectsViewEffect.ConfirmNewProject(NewProjectRules.suggestedName(sourceVideoPath), sourceVideoPath))
    }

    private fun createProject(name: String, sourceVideoPath: String) {
        runIo("Failed to create project") {
            // The dialog also validates. This check stops a request that skips the dialog.
            val error = NewProjectRules.nameError(name) ?: NewProjectRules.sourceVideoError(sourceVideoPath)
            if (error != null) {
                emitEffect(ProjectsViewEffect.ShowError("Failed to create project", error))
                return@runIo
            }
            rememberVideoDir(sourceVideoPath)
            val created = repository.createProject(sourceVideoPath.trim(), name.trim()).toCardState()
            val refreshed = repository.getRecents().map { it.toCardState() }
            synchronized(stateLock) {
                currentProject = created
                recents = refreshed
                currentPage = 1
            }
            renderState()
            emitEffect(ProjectsViewEffect.ProjectOpened(created.path))
            loadVisibleStats()
        }
    }

    private fun openProject(manifestPath: String, sourceVideoPath: String? = null) {
        if (manifestPath.isBlank()) return
        sourceVideoPath?.let(::rememberVideoDir)
        runIo("Failed to open project") {
            val manifest = repository.readManifest(manifestPath)
            if (sourceVideoPath.isNullOrBlank() && manifest.sourceVideo.isNullOrBlank()) {
                emitEffect(
                    ProjectsViewEffect.ChooseMissingSourceVideo(
                        manifestPath = manifestPath,
                        projectName = manifest.name,
                        initialDirectory = lastVideoDir(),
                    )
                )
                return@runIo
            }
            val video = sourceVideoPath?.takeIf { it.isNotBlank() } ?: manifest.sourceVideo.orEmpty()
            if (!File(video).isFile) {
                // The other tabs cannot load the project without the video. Thus the project stays closed.
                emitEffect(ProjectsViewEffect.ShowError("Cannot open project", videoMissingMessage(video)))
                loadVisibleStats()
                return@runIo
            }

            val opened = repository.openProject(manifestPath, sourceVideoPath).toCardState()
            val refreshed = repository.getRecents().map { it.toCardState() }
            synchronized(stateLock) {
                currentProject = opened
                recents = refreshed
                currentPage = 1
            }
            renderState()
            emitEffect(ProjectsViewEffect.ProjectOpened(opened.path))
            loadVisibleStats()
        }
    }

    private fun renameProject(manifestPath: String, name: String) {
        if (manifestPath.isBlank()) return
        runIo("Failed to rename project") {
            NewProjectRules.nameError(name)?.let { error ->
                emitEffect(ProjectsViewEffect.ShowError("Failed to rename project", error))
                return@runIo
            }
            val renamed = repository.renameProject(manifestPath, name.trim()).toCardState()
            val refreshed = repository.getRecents().map { it.toCardState() }
            synchronized(stateLock) {
                if (currentProject?.path == manifestPath) currentProject = renamed
                recents = refreshed
            }
            renderState()
            loadVisibleStats()
        }
    }

    private fun deleteProject(manifestPath: String) {
        if (manifestPath.isBlank()) return
        runIo("Failed to delete project") {
            if (synchronized(stateLock) { currentProject?.path == manifestPath }) {
                emitEffect(ProjectsViewEffect.ShowError("Cannot delete project", "You cannot delete the open project."))
                return@runIo
            }
            repository.deleteProject(manifestPath)
            val refreshed = repository.getRecents().map { it.toCardState() }
            synchronized(stateLock) {
                recents = refreshed
                stats.remove(manifestPath)
            }
            renderState()
            loadVisibleStats()
        }
    }

    private fun goToPage(page: Int) {
        synchronized(stateLock) {
            currentPage = page.coerceIn(1, totalPages(recents.size))
        }
        renderState()
        runIo("Failed to load projects") { loadVisibleStats() }
    }

    /** Reads the figures of the projects on the current page. Each project that changes causes one render. */
    private fun loadVisibleStats() {
        val paths = synchronized(stateLock) { visibleRange().map { recents[it].path } }
        paths.forEach { path ->
            val loaded = try {
                repository.stats(path)
            } catch (_: Throwable) {
                ProjectStats()
            }.toState()
            val changed = synchronized(stateLock) { stats.put(path, loaded) != loaded }
            if (changed) renderState()
        }
    }

    private fun renderState() {
        val snapshot = synchronized(stateLock) {
            val totalPages = totalPages(recents.size)
            currentPage = currentPage.coerceIn(1, totalPages)
            ProjectsViewState(
                currentProject = currentProject,
                visibleProjects = visibleRange().map { recents[it].let { card -> card.copy(stats = stats[card.path]) } },
                currentPage = currentPage,
                totalPages = totalPages,
                canGoPrevious = currentPage > 1,
                canGoNext = currentPage < totalPages,
                isPaginationVisible = totalPages > 1,
            )
        }
        onEventDispatchThread { view?.render(snapshot) }
    }

    /** Returns the indexes in [recents] of the current page. Call it only with [stateLock]. */
    private fun visibleRange(): IntRange {
        val start = (currentPage.coerceIn(1, totalPages(recents.size)) - 1) * pageSize
        return start until (start + pageSize).coerceAtMost(recents.size)
    }

    private fun emitEffect(effect: ProjectsViewEffect) {
        onEventDispatchThread { view?.renderEffect(effect) }
    }

    private fun runIo(errorTitle: String, block: () -> Unit) {
        ioExecutor.execute {
            try {
                block()
            } catch (t: Throwable) {
                emitEffect(ProjectsViewEffect.ShowError(errorTitle, t.message ?: t.toString()))
            }
        }
    }

    private fun rememberVideoDir(path: String) {
        val parent = File(path).parentFile?.absolutePath ?: return
        preferences.put(LAST_VIDEO_DIR_KEY, parent)
    }

    private fun lastVideoDir(): String? = preferences.get(LAST_VIDEO_DIR_KEY, null)

    private fun totalPages(itemCount: Int): Int {
        return if (itemCount == 0) 1 else (itemCount + pageSize - 1) / pageSize
    }

    private fun ProjectSummary.toCardState(): ProjectCardState {
        return ProjectCardState(path = path, name = name, secondary = secondary, id = id)
    }

    private fun ProjectStats.toState(): ProjectStatsState {
        return ProjectStatsState(
            duration = durationMs?.let(RenderFormatting::formatDuration) ?: UNKNOWN,
            fileSize = fileSizeBytes?.let(RenderFormatting::formatSize) ?: UNKNOWN,
            scoredPoints = "$scoredCount/$pointCount",
            favoritePoints = favoriteCount.toString(),
            videoMissingMessage = if (videoMissing) "The video is not on the disk anymore." else null,
        )
    }

    private fun videoMissingMessage(videoPath: String): String =
        "The video of the project is not on the disk anymore:\n$videoPath\n\n" +
            "Put the video back at this location to open the project."

    private fun onEventDispatchThread(action: () -> Unit) {
        if (EventQueue.isDispatchThread()) {
            action()
        } else {
            EventQueue.invokeLater(action)
        }
    }

    private companion object {
        private const val LAST_VIDEO_DIR_KEY = "lastVideoDir"
        private const val UNKNOWN = "—"
    }
}
