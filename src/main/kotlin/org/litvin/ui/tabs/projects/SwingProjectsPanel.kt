package org.litvin.ui.tabs.projects

import org.litvin.ui.UiStyles
import org.litvin.ui.UiStyles.GREEN
import org.litvin.ui.commons.Dialogs
import org.litvin.ui.commons.applyDarkScrollbar
import org.litvin.ui.tabs.projects.components.ProjectCard
import org.litvin.ui.tabs.projects.components.ProjectsEmptyListCard
import org.litvin.ui.tabs.projects.components.ProjectsHeader
import org.litvin.ui.tabs.projects.components.ProjectsPaginationBar
import org.litvin.ui.tabs.projects.presenter.DefaultProjectsPresenter
import org.litvin.ui.tabs.projects.presenter.ProjectCardState
import org.litvin.ui.tabs.projects.presenter.ProjectsIntent
import org.litvin.ui.tabs.projects.presenter.ProjectsPresenter
import org.litvin.ui.tabs.projects.presenter.ProjectsView
import org.litvin.ui.tabs.projects.presenter.ProjectsViewEffect
import org.litvin.ui.tabs.projects.presenter.ProjectsViewState
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Passive Swing view for the Projects tab.
 *
 * The presenter owns project IO, pagination decisions, and navigation effects.
 * This panel only forwards user intents and renders immutable ProjectsViewState.
 */
class SwingProjectsPanel(
    private val presenter: ProjectsPresenter = DefaultProjectsPresenter(),
) : JPanel(BorderLayout()), ProjectsView {
    private val currentProjectContainer = JPanel(BorderLayout()).apply {
        isOpaque = false
        alignmentX = 0f
        minimumSize = Dimension(200, 48)
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    }
    private val listContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = 0f
    }
    private val paginationBar = ProjectsPaginationBar(
        onPrevious = { presenter.onIntent(ProjectsIntent.GoToPage(lastState.currentPage - 1)) },
        onNext = { presenter.onIntent(ProjectsIntent.GoToPage(lastState.currentPage + 1)) },
    )
    private var lastState = ProjectsViewState()

    var onProjectOpened: ((String) -> Unit)? = null

    init {
        isOpaque = true
        background = UiStyles.DARK_BG
        border = BorderFactory.createEmptyBorder(16, 16, 16, 16)

        add(ProjectsHeader { presenter.onIntent(ProjectsIntent.ImportNewMatch) }, BorderLayout.NORTH)
        add(buildCenterPanel(), BorderLayout.CENTER)
    }

    override fun addNotify() {
        super.addNotify()
        presenter.attach(this)
        presenter.onActivated()
    }

    override fun removeNotify() {
        presenter.onDeactivated()
        presenter.detach()
        super.removeNotify()
    }

    fun onActivated() {
        presenter.onActivated()
    }

    fun onDeactivated() {
        presenter.onDeactivated()
    }

    override fun render(state: ProjectsViewState) {
        lastState = state
        renderCurrentProject(state.currentProject)
        renderProjectList(state)
        renderPagination(state)
    }

    override fun renderEffect(effect: ProjectsViewEffect) {
        when (effect) {
            is ProjectsViewEffect.ChooseSourceVideo -> {
                chooseSourceVideo("Select Source Video", effect.initialDirectory)?.let { path ->
                    presenter.onIntent(ProjectsIntent.SourceVideoSelected(path))
                }
            }
            is ProjectsViewEffect.ChooseMissingSourceVideo -> {
                chooseSourceVideo(
                    "Select Source Video for Project: ${effect.projectName}",
                    effect.initialDirectory,
                )?.let { path ->
                    presenter.onIntent(ProjectsIntent.MissingSourceVideoSelected(effect.manifestPath, path))
                }
            }
            is ProjectsViewEffect.ProjectOpened -> onProjectOpened?.invoke(effect.manifestPath)
            is ProjectsViewEffect.ShowError -> Dialogs.showError(this, RuntimeException(effect.message), effect.title)
        }
    }

    private fun buildCenterPanel(): JComponent {
        val topPanel = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(12, 0, 0, 0)
            add(Box.createVerticalStrut(30))
            add(sectionLabel("Current Project"))
            add(Box.createVerticalStrut(16))
            add(currentProjectContainer)
            add(Box.createVerticalStrut(30))
            add(sectionLabel("Recent Match Projects"))
            add(Box.createVerticalStrut(16))
        }

        val listScroll = JScrollPane(listContainer).apply {
            border = BorderFactory.createEmptyBorder()
            isOpaque = false
            viewport.isOpaque = false
            verticalScrollBar.unitIncrement = 18
            applyDarkScrollbar(this, background)
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(topPanel, BorderLayout.NORTH)
            add(listScroll, BorderLayout.CENTER)
            add(paginationBar, BorderLayout.SOUTH)
        }
    }

    private fun renderCurrentProject(project: ProjectCardState?) {
        currentProjectContainer.removeAll()
        currentProjectContainer.add(
            if (project == null) emptyCurrentProjectCard() else ProjectCard(project.name, project.secondary),
            BorderLayout.CENTER,
        )
        refresh(currentProjectContainer)
    }

    private fun renderProjectList(state: ProjectsViewState) {
        listContainer.removeAll()
        if (state.visibleProjects.isEmpty()) {
            listContainer.add(ProjectsEmptyListCard(state.emptyListMessage))
        } else {
            state.visibleProjects.forEach { project ->
                listContainer.add(buildProjectCard(project))
                listContainer.add(Box.createVerticalStrut(8))
            }
        }
        refresh(listContainer)
    }

    private fun renderPagination(state: ProjectsViewState) {
        paginationBar.render(
            currentPage = state.currentPage,
            totalPages = state.totalPages,
            canGoPrevious = state.canGoPrevious,
            canGoNext = state.canGoNext,
            isVisible = state.isPaginationVisible,
        )
        refresh(paginationBar)
    }

    private fun buildProjectCard(project: ProjectCardState): JComponent {
        return ProjectCard(project.name, project.secondary) {
            presenter.onIntent(ProjectsIntent.OpenProject(project.path))
        }
    }

    private fun emptyCurrentProjectCard(): JComponent {
        return ProjectCard("No open project", "Use \"IMPORT NEW MATCH\" or open from Existing Projects")
    }

    private fun chooseSourceVideo(title: String, initialDirectory: String?): String? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = false
            fileFilter = FileNameExtensionFilter("Video Files", "mp4", "mov", "mkv", "avi", "m4v", "wmv")
            initialDirectory?.let { path ->
                val dir = File(path)
                if (dir.exists()) currentDirectory = dir
            }
        }
        return if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.absolutePath
        } else {
            null
        }
    }

    private fun sectionLabel(text: String): JComponent = JLabel(text).apply {
        font = font.deriveFont(Font.BOLD, font.size2D + 2f)
        foreground = GREEN
        alignmentX = 0f
    }

    private fun refresh(container: Container) {
        container.revalidate()
        container.repaint()
    }
}
