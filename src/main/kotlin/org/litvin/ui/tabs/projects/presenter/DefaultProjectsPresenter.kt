package org.litvin.ui.tabs.projects.presenter

import org.litvin.projects.FileProjectsRepository
import org.litvin.projects.ProjectSummary
import org.litvin.projects.ProjectsRepository
import java.awt.EventQueue
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.prefs.Preferences

class DefaultProjectsPresenter(
    private val repository: ProjectsRepository = FileProjectsRepository(),
    private val preferences: Preferences = Preferences.userNodeForPackage(DefaultProjectsPresenter::class.java),
    private val ioExecutor: Executor = Executors.newSingleThreadExecutor(),
) : ProjectsPresenter {
    private val pageSize = 10
    private val stateLock = Any()

    private var view: ProjectsView? = null
    private var currentProject: ProjectCardState? = null
    private var recents: List<ProjectCardState> = emptyList()
    private var currentPage: Int = 1

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
            is ProjectsIntent.SourceVideoSelected -> createProject(intent.path)
            is ProjectsIntent.OpenProject -> openProject(intent.manifestPath)
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
        }
    }

    private fun createProject(sourceVideoPath: String) {
        if (sourceVideoPath.isBlank()) return
        rememberVideoDir(sourceVideoPath)
        runIo("Failed to create project") {
            val created = repository.createProject(sourceVideoPath).toCardState()
            val refreshed = repository.getRecents().map { it.toCardState() }
            synchronized(stateLock) {
                currentProject = created
                recents = refreshed
                currentPage = 1
            }
            renderState()
            emitEffect(ProjectsViewEffect.ProjectOpened(created.path))
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

            val opened = repository.openProject(manifestPath, sourceVideoPath).toCardState()
            val refreshed = repository.getRecents().map { it.toCardState() }
            synchronized(stateLock) {
                currentProject = opened
                recents = refreshed
                currentPage = 1
            }
            renderState()
            emitEffect(ProjectsViewEffect.ProjectOpened(opened.path))
        }
    }

    private fun goToPage(page: Int) {
        synchronized(stateLock) {
            currentPage = page.coerceIn(1, totalPages(recents.size))
        }
        renderState()
    }

    private fun renderState() {
        val snapshot = synchronized(stateLock) {
            val totalPages = totalPages(recents.size)
            currentPage = currentPage.coerceIn(1, totalPages)
            val start = (currentPage - 1) * pageSize
            val end = (start + pageSize).coerceAtMost(recents.size)
            ProjectsViewState(
                currentProject = currentProject,
                visibleProjects = if (recents.isEmpty()) emptyList() else recents.subList(start, end),
                currentPage = currentPage,
                totalPages = totalPages,
                canGoPrevious = currentPage > 1,
                canGoNext = currentPage < totalPages,
                isPaginationVisible = totalPages > 1,
            )
        }
        onEventDispatchThread { view?.render(snapshot) }
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
        return ProjectCardState(path = path, name = name, secondary = secondary)
    }

    private fun onEventDispatchThread(action: () -> Unit) {
        if (EventQueue.isDispatchThread()) {
            action()
        } else {
            EventQueue.invokeLater(action)
        }
    }

    private companion object {
        private const val LAST_VIDEO_DIR_KEY = "lastVideoDir"
    }
}
