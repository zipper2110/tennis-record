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
)

sealed class ProjectsIntent {
    object ImportNewMatch : ProjectsIntent()
    data class SourceVideoSelected(val path: String) : ProjectsIntent()
    data class OpenProject(val manifestPath: String) : ProjectsIntent()
    data class MissingSourceVideoSelected(val manifestPath: String, val sourceVideoPath: String) : ProjectsIntent()
    data class GoToPage(val page: Int) : ProjectsIntent()
}

sealed class ProjectsViewEffect {
    data class ChooseSourceVideo(val initialDirectory: String?) : ProjectsViewEffect()
    data class ChooseMissingSourceVideo(
        val manifestPath: String,
        val projectName: String,
        val initialDirectory: String?,
    ) : ProjectsViewEffect()

    data class ProjectOpened(val manifestPath: String) : ProjectsViewEffect()
    data class ShowError(val title: String, val message: String) : ProjectsViewEffect()
}
