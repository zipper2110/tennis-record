package org.litvin.ui.tabs.projects.presenter

import org.litvin.app.PreferencesProvider
import org.litvin.projects.ProjectManifestV1
import org.litvin.projects.ProjectSummary
import org.litvin.projects.ProjectsRepository
import org.litvin.projects.FileProjectsRepository
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executor
import java.util.prefs.Preferences
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultProjectsPresenterTest {
    @TempDir
    lateinit var tempDir: Path

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

    @Test
    fun selectedVideoAsksToConfirmTheSuggestedProjectName() {
        val view = RecordingProjectsView()
        val presenter = presenter(FakeProjectsRepository())
        val video = tempDir.resolve("Club final.mp4").toString()

        presenter.attach(view)
        presenter.onIntent(ProjectsIntent.SourceVideoSelected(video))
        drainEdt()

        assertEquals(ProjectsViewEffect.ConfirmNewProject("Club final", video), view.effects.last())
    }

    @Test
    fun confirmedProjectIsCreatedWithTheTypedNameAndOpened() {
        val repository = FakeProjectsRepository()
        val view = RecordingProjectsView()
        val presenter = presenter(repository)
        val video = tempDir.resolve("match.mp4").toFile().apply { writeText("video") }

        presenter.attach(view)
        presenter.onIntent(ProjectsIntent.CreateProject("  Semi final  ", video.absolutePath))
        drainEdt()

        assertEquals(listOf("Semi final"), repository.createdNames)
        assertEquals(ProjectsViewEffect.ProjectOpened("created.trproj"), view.effects.last())
        assertEquals("Semi final", view.states.last().currentProject?.name)
    }

    @Test
    fun incorrectNameOrVideoShowsAnErrorAndCreatesNoProject() {
        val repository = FakeProjectsRepository()
        val view = RecordingProjectsView()
        val presenter = presenter(repository)
        val video = tempDir.resolve("match.mp4").toFile().apply { writeText("video") }

        presenter.attach(view)
        presenter.onIntent(ProjectsIntent.CreateProject(" ", video.absolutePath))
        presenter.onIntent(ProjectsIntent.CreateProject("Match", tempDir.resolve("missing.mp4").toString()))
        drainEdt()

        assertEquals(emptyList(), repository.createdNames)
        assertEquals(2, view.effects.filterIsInstance<ProjectsViewEffect.ShowError>().size)
    }

    @Test
    fun fileRepositoryUsesTheProjectNameAndMakesItUnique() {
        val repository = FileProjectsRepository(tempDir.resolve("projects").toFile())
        val video = tempDir.resolve("match.mp4").toFile().apply { writeText("video") }

        val first = repository.createProject(video.absolutePath, "Club final")
        val second = repository.createProject(video.absolutePath, "Club final")

        assertEquals("Club final", first.name)
        assertEquals("Club final (2)", second.name)
        assertEquals("Club final (2).trproj", java.io.File(second.path).name)
    }

    @Test
    fun fileRepositoriesKeepProjectsAndRecentsInsideTheirOwnRoots() {
        val firstRoot = tempDir.resolve("first/projects").toFile()
        val secondRoot = tempDir.resolve("second/projects").toFile()
        val firstSource = tempDir.resolve("first-match.mp4").toFile().apply { writeText("first") }
        val secondSource = tempDir.resolve("second-match.mp4").toFile().apply { writeText("second") }
        val firstRepository = FileProjectsRepository(firstRoot)
        val secondRepository = FileProjectsRepository(secondRoot)

        val firstProject = firstRepository.createProject(firstSource.absolutePath, "First")

        assertEquals(firstRoot.absolutePath, firstRepository.projectsRootPath())
        assertEquals(listOf(firstProject.path), firstRepository.getRecents().map { it.path })
        assertEquals(emptyList(), secondRepository.getRecents())

        val secondProject = secondRepository.createProject(secondSource.absolutePath, "Second")

        assertEquals(secondRoot.absolutePath, secondRepository.projectsRootPath())
        assertEquals(listOf(firstProject.path), firstRepository.getRecents().map { it.path })
        assertEquals(listOf(secondProject.path), secondRepository.getRecents().map { it.path })
    }

    @Test
    fun productionPreferencesKeepTheExistingProjectsPackageNode() {
        val expected = Preferences.userNodeForPackage(DefaultProjectsPresenter::class.java).absolutePath()

        val actual = PreferencesProvider.production().node(PreferencesProvider.PROJECTS).absolutePath()

        assertEquals(expected, actual)
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

        val createdNames = mutableListOf<String>()

        override fun createProject(sourceVideoPath: String, name: String): ProjectSummary {
            createdNames += name
            val summary = ProjectSummary("created.trproj", name, sourceVideoPath)
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
