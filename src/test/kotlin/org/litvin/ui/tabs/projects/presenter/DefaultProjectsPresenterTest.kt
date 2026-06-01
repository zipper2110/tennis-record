package org.litvin.ui.tabs.projects.presenter

import org.litvin.projects.ProjectManifestV1
import org.litvin.projects.ProjectSummary
import org.litvin.projects.ProjectsRepository
import java.util.UUID
import java.util.concurrent.Executor
import java.util.prefs.Preferences
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultProjectsPresenterTest {
    @Test
    fun activationRendersPagedRecentsFromRepository() {
        val repository = FakeProjectsRepository(
            recents = (1..12).map { ProjectSummary("project-$it.trproj", "Project $it", "video-$it.mp4") },
        )
        val view = RecordingProjectsView()
        val presenter = presenter(repository)

        presenter.attach(view)
        presenter.onActivated()
        drainEdt()

        val state = view.states.last()
        assertEquals(10, state.visibleProjects.size)
        assertEquals(1, state.currentPage)
        assertEquals(2, state.totalPages)
        assertEquals(true, state.canGoNext)
        assertEquals(true, state.isPaginationVisible)
    }

    @Test
    fun openingProjectWithoutSourceVideoRequestsVideoBeforeNavigation() {
        val repository = FakeProjectsRepository(
            manifests = mutableMapOf(
                "match.trproj" to manifest("Match", sourceVideo = null),
            ),
        )
        val view = RecordingProjectsView()
        val presenter = presenter(repository)

        presenter.attach(view)
        presenter.onIntent(ProjectsIntent.OpenProject("match.trproj"))
        drainEdt()

        assertIs<ProjectsViewEffect.ChooseMissingSourceVideo>(view.effects.last())

        presenter.onIntent(ProjectsIntent.MissingSourceVideoSelected("match.trproj", "match.mp4"))
        drainEdt()

        assertIs<ProjectsViewEffect.ProjectOpened>(view.effects.last())
        assertEquals("match.mp4", repository.manifests.getValue("match.trproj").sourceVideo)
        assertEquals("match.trproj", view.states.last().currentProject?.path)
    }

    private fun presenter(repository: ProjectsRepository): DefaultProjectsPresenter {
        val prefs = Preferences.userNodeForPackage(DefaultProjectsPresenterTest::class.java)
            .node("test-${UUID.randomUUID()}")
        return DefaultProjectsPresenter(
            repository = repository,
            preferences = prefs,
            ioExecutor = Executor { it.run() },
        )
    }

    private fun drainEdt() {
        SwingUtilities.invokeAndWait { }
    }

    private fun manifest(name: String, sourceVideo: String?): ProjectManifestV1 {
        return ProjectManifestV1(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAt = "2026-06-01T00:00:00Z",
            lastOpenedAt = "2026-06-01T00:00:00Z",
            sourceVideo = sourceVideo,
        )
    }

    private class RecordingProjectsView : ProjectsView {
        val states = mutableListOf<ProjectsViewState>()
        val effects = mutableListOf<ProjectsViewEffect>()

        override fun render(state: ProjectsViewState) {
            states += state
        }

        override fun renderEffect(effect: ProjectsViewEffect) {
            effects += effect
        }
    }

    private class FakeProjectsRepository(
        private var recents: List<ProjectSummary> = emptyList(),
        val manifests: MutableMap<String, ProjectManifestV1> = mutableMapOf(),
    ) : ProjectsRepository {
        override fun projectsRootPath(): String = "projects"

        override fun getRecents(): List<ProjectSummary> = recents

        override fun readManifest(path: String): ProjectManifestV1 = manifests.getValue(path)

        override fun summarize(path: String): ProjectSummary {
            val manifest = manifests.getValue(path)
            return ProjectSummary(path, manifest.name, manifest.sourceVideo ?: path)
        }

        override fun createProject(sourceVideoPath: String): ProjectSummary {
            val summary = ProjectSummary("created.trproj", "created", sourceVideoPath)
            recents = listOf(summary)
            return summary
        }

        override fun openProject(path: String, sourceVideoPath: String?): ProjectSummary {
            val updated = manifests.getValue(path).copy(
                lastOpenedAt = "2026-06-01T01:00:00Z",
                sourceVideo = sourceVideoPath ?: manifests.getValue(path).sourceVideo,
            )
            manifests[path] = updated
            val summary = ProjectSummary(path, updated.name, updated.sourceVideo ?: path)
            recents = listOf(summary)
            return summary
        }
    }
}
