package org.litvin.ui.tabs.projects.presenter

interface ProjectsView {
    fun render(state: ProjectsViewState)
    fun renderEffect(effect: ProjectsViewEffect) {}
}

interface ProjectsPresenter {
    fun attach(view: ProjectsView)
    fun detach()
    fun onActivated()
    fun onDeactivated()
    fun onIntent(intent: ProjectsIntent)
}

data class ProjectsViewState(
    val currentProject: ProjectCardState? = null,
    val visibleProjects: List<ProjectCardState> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
    val isPaginationVisible: Boolean = false,
    val emptyListMessage: String = "No projects yet. Click \"IMPORT NEW MATCH\" to create one.",
)

data class ProjectCardState(
    val path: String,
    val name: String,
    val secondary: String,
    val id: String = path,
    /** Null until the presenter loads the figures of the project. */
    val stats: ProjectStatsState? = null,
)

/** Display text for the columns of the projects table. */
data class ProjectStatsState(
    val duration: String,
    val fileSize: String,
    val scoredPoints: String,
    val favoritePoints: String,
    /** A message for the user when the video file is not on the disk. Null when the video is available. */
    val videoMissingMessage: String? = null,
)

sealed class ProjectsIntent {
    object ImportNewMatch : ProjectsIntent()
    data class SourceVideoSelected(val path: String) : ProjectsIntent()
    data class CreateProject(val name: String, val sourceVideoPath: String) : ProjectsIntent()
    data class OpenProject(val manifestPath: String) : ProjectsIntent()
    data class RenameProject(val manifestPath: String, val name: String) : ProjectsIntent()

    /** The user confirmed the deletion. */
    data class DeleteProject(val manifestPath: String) : ProjectsIntent()
    data class MissingSourceVideoSelected(val manifestPath: String, val sourceVideoPath: String) : ProjectsIntent()
    data class GoToPage(val page: Int) : ProjectsIntent()
}

sealed class ProjectsViewEffect {
    data class ChooseSourceVideo(val initialDirectory: String?) : ProjectsViewEffect()

    /** Asks the user to confirm or change the name and the video of the new project. */
    data class ConfirmNewProject(val name: String, val sourceVideoPath: String) : ProjectsViewEffect()
    data class ChooseMissingSourceVideo(
        val manifestPath: String,
        val projectName: String,
        val initialDirectory: String?,
    ) : ProjectsViewEffect()

    data class ProjectOpened(val manifestPath: String) : ProjectsViewEffect()
    data class ShowError(val title: String, val message: String) : ProjectsViewEffect()
}
