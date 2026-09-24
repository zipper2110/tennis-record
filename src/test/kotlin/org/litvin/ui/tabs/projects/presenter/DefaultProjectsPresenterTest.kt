package org.litvin.ui.tabs.projects.presenter

import org.litvin.app.PreferencesProvider
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.projects.CachingVideoDurationProbe
import org.litvin.projects.ProjectManifestV1
import org.litvin.projects.ProjectStats
import org.litvin.projects.ProjectSummary
import org.litvin.projects.ProjectsRepository
import org.litvin.projects.FileProjectsRepository
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executor
import java.util.prefs.Preferences
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun activationShowsTheFormattedFiguresOfTheVisibleProjectsOnly() {
        val repository = FakeProjectsRepository(
            recents = (1..12).map { ProjectSummary("project-$it.trproj", "Project $it", "video-$it.mp4") },
            stats = mapOf(
                "project-1.trproj" to ProjectStats(
                    durationMs = 5_025_000,
                    fileSizeBytes = 2_500_000_000,
                    pointCount = 40,
                    scoredCount = 12,
                    favoriteCount = 3,
                ),
            ),
        )
        val view = RecordingProjectsView()
        val presenter = presenter(repository)

        presenter.attach(view)
        presenter.onActivated()
        drainEdt()

        val projects = view.states.last().visibleProjects
        assertEquals(ProjectStatsState("1:23:45", "2.50 GB", "12/40", "3"), projects.first().stats)
        assertEquals(ProjectStatsState("—", "—", "0/0", "0"), projects[1].stats)
        assertEquals((1..10).map { "project-$it.trproj" }, repository.statsRequests)

        repository.statsRequests.clear()
        presenter.onIntent(ProjectsIntent.GoToPage(2))
        drainEdt()

        assertEquals(listOf("project-11.trproj", "project-12.trproj"), repository.statsRequests)
        assertEquals(true, view.states.last().visibleProjects.all { it.stats != null })
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

        val video = videoFile()
        presenter.onIntent(ProjectsIntent.MissingSourceVideoSelected("match.trproj", video))
        drainEdt()

        assertIs<ProjectsViewEffect.ProjectOpened>(view.effects.last())
        assertEquals(video, repository.manifests.getValue("match.trproj").sourceVideo)
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
    fun openingProjectWithAMissingVideoShowsOneErrorAndKeepsTheProjectClosed() {
        val missing = tempDir.resolve("gone.mp4").toString()
        val repository = FakeProjectsRepository(
            recents = listOf(ProjectSummary("match.trproj", "Match", missing)),
            manifests = mutableMapOf("match.trproj" to manifest("Match", sourceVideo = missing)),
            stats = mapOf("match.trproj" to ProjectStats(videoMissing = true)),
        )
        val view = RecordingProjectsView()
        val presenter = presenter(repository)

        presenter.attach(view)
        presenter.onActivated()
        presenter.onIntent(ProjectsIntent.OpenProject("match.trproj"))
        drainEdt()

        val error = assertIs<ProjectsViewEffect.ShowError>(view.effects.single())
        assertEquals("Cannot open project", error.title)
        assertEquals(true, missing in error.message)
        assertEquals(emptyList(), repository.openedPaths)
        assertEquals(null, view.states.last().currentProject)
        assertEquals(
            "The video is not on the disk anymore.",
            view.states.last().visibleProjects.single().stats?.videoMissingMessage,
        )
    }

    @Test
    fun deletingAProjectRemovesItFromTheList() {
        val repository = FakeProjectsRepository(
            recents = listOf(
                ProjectSummary("first.trproj", "First", "first.mp4"),
                ProjectSummary("second.trproj", "Second", "second.mp4"),
            ),
        )
        val view = RecordingProjectsView()
        val presenter = presenter(repository)

        presenter.attach(view)
        presenter.onActivated()
        presenter.onIntent(ProjectsIntent.DeleteProject("first.trproj"))
        drainEdt()

        assertEquals(listOf("first.trproj"), repository.deletedPaths)
        assertEquals(listOf("Second"), view.states.last().visibleProjects.map { it.name })
    }

    @Test
    fun deletingTheOpenProjectShowsAnErrorAndKeepsTheProject() {
        val repository = FakeProjectsRepository(
            manifests = mutableMapOf("match.trproj" to manifest("Match", sourceVideo = videoFile())),
        )
        val view = RecordingProjectsView()
        val presenter = presenter(repository)

        presenter.attach(view)
        presenter.onIntent(ProjectsIntent.OpenProject("match.trproj"))
        presenter.onIntent(ProjectsIntent.DeleteProject("match.trproj"))
        drainEdt()

        assertEquals("Cannot delete project", assertIs<ProjectsViewEffect.ShowError>(view.effects.last()).title)
        assertEquals(emptyList(), repository.deletedPaths)
        assertEquals("match.trproj", view.states.last().currentProject?.path)
    }

    @Test
    fun renamingTheOpenProjectUpdatesTheCurrentCardAndTheRecents() {
        val repository = FakeProjectsRepository(
            manifests = mutableMapOf("match.trproj" to manifest("Match", sourceVideo = videoFile())),
        )
        val view = RecordingProjectsView()
        val presenter = presenter(repository)

        presenter.attach(view)
        presenter.onIntent(ProjectsIntent.OpenProject("match.trproj"))
        presenter.onIntent(ProjectsIntent.RenameProject("match.trproj", "  Club final  "))
        drainEdt()

        val state = view.states.last()
        assertEquals("Club final", state.currentProject?.name)
        assertEquals(listOf("Club final"), state.visibleProjects.map { it.name })
        assertEquals("Club final", repository.manifests.getValue("match.trproj").name)
    }

    @Test
    fun renamingToAnIncorrectNameShowsAnErrorAndKeepsTheName() {
        val repository = FakeProjectsRepository(
            manifests = mutableMapOf("match.trproj" to manifest("Match", sourceVideo = "match.mp4")),
        )
        val view = RecordingProjectsView()
        val presenter = presenter(repository)

        presenter.attach(view)
        presenter.onIntent(ProjectsIntent.RenameProject("match.trproj", "  "))
        drainEdt()

        assertIs<ProjectsViewEffect.ShowError>(view.effects.last())
        assertEquals("Match", repository.manifests.getValue("match.trproj").name)
    }

    @Test
    fun fileRepositoryRenameKeepsTheFilesAndTheLastOpenedTime() {
        val repository = FileProjectsRepository(tempDir.resolve("projects").toFile())
        val video = tempDir.resolve("match.mp4").toFile().apply { writeText("video") }
        val created = repository.createProject(video.absolutePath, "Match")
        val before = repository.readManifest(created.path)

        val renamed = repository.renameProject(created.path, "Club final")

        assertEquals(created.path, renamed.path)
        assertEquals("Club final", renamed.name)
        assertEquals(before.copy(name = "Club final"), repository.readManifest(created.path))
        assertEquals(listOf("Club final"), repository.getRecents().map { it.name })
    }

    @Test
    fun fileRepositoryStatsReadTheVideoThePointsAndTheScore() {
        val probedPaths = mutableListOf<String>()
        val repository = FileProjectsRepository(tempDir.resolve("projects").toFile(), durationProbe = { path ->
            probedPaths += path
            83_000L
        })
        val video = tempDir.resolve("match.mp4").toFile().apply { writeText("video") }
        val project = repository.createProject(video.absolutePath, "Match")
        val projectDir = EdlIO.projectDirFromManifest(project.path)
        EdlIO.writeForProjectDir(
            projectDir,
            EdlV1(
                points = listOf(
                    PointV1("a", 0, 1_000, favorite = true),
                    PointV1("b", 2_000, 3_000),
                    PointV1("c", 4_000, 5_000, favorite = true),
                ),
            ),
        )
        ScoreIO.writeForProjectDir(projectDir, ScoreV1(outcomes = mapOf("a" to Outcome.P1, "c" to Outcome.NONE, "gone" to Outcome.P2)))

        assertEquals(
            ProjectStats(durationMs = 83_000, fileSizeBytes = 5, pointCount = 3, scoredCount = 2, favoriteCount = 2),
            repository.stats(project.path),
        )
        assertEquals(listOf(video.absolutePath), probedPaths)

        video.delete()

        assertEquals(
            ProjectStats(videoMissing = true, pointCount = 3, scoredCount = 2, favoriteCount = 2),
            repository.stats(project.path),
        )
    }

    @Test
    fun fileRepositoryDeletesOnlyTheProjectFolder() {
        val root = tempDir.resolve("projects").toFile()
        val repository = FileProjectsRepository(root, removeFolder = { it.deleteRecursively() })
        val video = tempDir.resolve("match.mp4").toFile().apply { writeText("video") }
        val first = repository.createProject(video.absolutePath, "First")
        val second = repository.createProject(video.absolutePath, "Second")

        repository.deleteProject(first.path)

        assertEquals(false, java.io.File(first.path).parentFile.exists())
        assertEquals(true, video.isFile)
        assertEquals(listOf(second.path), repository.getRecents().map { it.path })
        assertFailsWith<IllegalArgumentException> {
            repository.deleteProject(tempDir.resolve("outside/outside.trproj").toString())
        }
    }

    @Test
    fun cachedDurationProbeRunsAgainOnlyAfterTheVideoChanges() {
        var probes = 0
        val probe = CachingVideoDurationProbe { probes++; 1_000L }
        val video = tempDir.resolve("match.mp4").toFile().apply { writeText("video") }

        probe.durationMs(video.absolutePath)
        probe.durationMs(video.absolutePath)
        assertEquals(1, probes)

        video.writeText("longer video")
        probe.durationMs(video.absolutePath)
        assertEquals(2, probes)
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

    private fun videoFile(): String =
        tempDir.resolve("video-${UUID.randomUUID()}.mp4").toFile().apply { writeText("video") }.absolutePath

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
        private val stats: Map<String, ProjectStats> = emptyMap(),
    ) : ProjectsRepository {
        val statsRequests = mutableListOf<String>()
        val openedPaths = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()

        override fun deleteProject(path: String) {
            deletedPaths += path
            recents = recents.filterNot { it.path == path }
        }

        override fun projectsRootPath(): String = "projects"

        override fun stats(path: String): ProjectStats {
            statsRequests += path
            return stats[path] ?: ProjectStats()
        }

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

        override fun renameProject(path: String, name: String): ProjectSummary {
            manifests[path] = manifests.getValue(path).copy(name = name)
            val summary = ProjectSummary(path, name, manifests.getValue(path).sourceVideo ?: path)
            recents = recents.map { if (it.path == path) summary else it }
            return summary
        }

        override fun openProject(path: String, sourceVideoPath: String?): ProjectSummary {
            openedPaths += path
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
