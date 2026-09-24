package org.litvin.ui

import org.assertj.swing.edt.GuiActionRunner
import org.junit.jupiter.api.Test
import org.litvin.ActiveQueueSnapshot
import org.litvin.CompletedRender
import org.litvin.RenderJob
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.app.AppDataPaths
import org.litvin.app.AppServices
import org.litvin.app.ExecutorProvider
import org.litvin.app.PreferencesProvider
import org.litvin.app.SwingApplicationFactory
import org.litvin.app.SwingApplicationHandle
import org.litvin.export.CompletedRendersRepository
import org.litvin.export.RenderService
import org.litvin.media.MediaPlayerFactory
import org.litvin.media.MediaScreen
import org.litvin.media.PlayerStatus
import org.litvin.media.SwingMediaPlayer
import org.litvin.media.VideoOverlay
import org.litvin.projects.ProjectManifestV1
import org.litvin.projects.ProjectSummary
import org.litvin.projects.ProjectsRepository
import org.litvin.ui.commons.FilePicker
import org.litvin.ui.commons.UserDialogService
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.prefs.AbstractPreferences
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComponentNameContractTest {
    @Test
    fun assembledApplicationExposesUniqueStableNamesForUiFlows() {
        val fixture = TestServices()
        val handle = GuiActionRunner.execute<SwingApplicationHandle> {
            SwingApplicationFactory.create(fixture.services, show = false).also { it.frame.addNotify() }
        }

        try {
            val namedComponents = GuiActionRunner.execute<List<Pair<String, Component>>> {
                handle.frame.descendants()
                    .mapNotNull { component -> component.name?.let { it to component } }
            }
            val names = namedComponents.map(Pair<String, Component>::first)
            val duplicates = names.groupingBy { it }.eachCount().filterValues { it > 1 }

            assertEquals(emptyMap(), duplicates, "Every non-null Swing component name must be unique")
            assertTrue(
                names.containsAll(REQUIRED_NAMES),
                "Missing stable component names: ${REQUIRED_NAMES - names.toSet()}",
            )
        } finally {
            handle.close()
        }
    }

    private fun Component.descendants(): List<Component> {
        val children = if (this is Container) components.flatMap { it.descendants() } else emptyList()
        return listOf(this) + children
    }

    private class TestServices {
        private val root = kotlin.io.path.createTempDirectory("component-names-").toFile()
        private val executors = TestExecutorProvider()
        private val preferences = MemoryPreferences()

        val services = AppServices(
            paths = AppDataPaths(root),
            preferences = PreferencesProvider { preferences },
            executors = executors,
            mediaPlayers = FakeMediaPlayerFactory,
            renderService = NoOpRenderService,
            filePicker = NoOpFilePicker,
            dialogs = NoOpDialogs,
            projectsRepository = OneProjectRepository,
            completedRenders = EmptyCompletedRendersRepository,
            adjustments = AdjustmentsSession(executors.createScheduledExecutor("adjustments"), 60_000),
        )
    }

    private class TestExecutorProvider : ExecutorProvider {
        private val executors = mutableListOf<ExecutorService>()

        override fun createExecutor(name: String): ExecutorService =
            DirectExecutorService().also(executors::add)

        override fun createScheduledExecutor(name: String): ScheduledExecutorService =
            ScheduledThreadPoolExecutor(1).also(executors::add)

        override fun close() {
            executors.forEach(ExecutorService::shutdownNow)
        }
    }

    private class DirectExecutorService : AbstractExecutorService() {
        private var shutdown = false

        override fun execute(command: Runnable) = command.run()
        override fun shutdown() { shutdown = true }
        override fun shutdownNow(): MutableList<Runnable> { shutdown = true; return mutableListOf() }
        override fun isShutdown(): Boolean = shutdown
        override fun isTerminated(): Boolean = shutdown
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = shutdown
    }

    private object FakeMediaPlayerFactory : MediaPlayerFactory {
        override fun create(screen: MediaScreen): SwingMediaPlayer = FakeMediaPlayer()
        override fun close() = Unit
    }

    private class FakeMediaPlayer : SwingMediaPlayer {
        override val component: Component = JPanel()
        override var onReady: (() -> Unit)? = null
        override var onStatusChanged: ((PlayerStatus) -> Unit)? = null
        override var onTimeChanged: ((Long) -> Unit)? = null
        override fun load(file: File) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seek(ms: Long) = Unit
        override fun setRate(rate: Float) = Unit
        override fun status() = PlayerStatus.UNKNOWN
        override fun currentTimeMs() = 0L
        override fun totalDurationMs() = 0L
        override fun isAdjustSupported() = true
        override fun applyColorAdjustments(adj: AdjustmentsV1) = Unit
        override fun applyGeometryAdjustments(adj: AdjustmentsV1) = true
        override fun applyPreviewAdjustments(adj: AdjustmentsV1) = Unit
        override fun applyPreviewRotation(rotationDeg: Float, reason: String) = Unit
        override fun setPreviewOverlay(overlay: VideoOverlay?) = Unit
        override fun stepFrameForward(maximumTimeMs: Long) = 0L
        override fun stepFrameBackward(minimumTimeMs: Long) = 0L
        override fun nextFrame() = Unit
        override fun setSubtitleFile(file: File) = true
        override fun activatePreview(reason: String) = Unit
        override fun deactivatePreview(reason: String) = Unit
        override fun close() = Unit
    }

    private object NoOpRenderService : RenderService {
        override fun enqueue(job: RenderJob) = Unit
        override fun cancelCurrent() = Unit
        override fun cancelQueued(jobId: String) = false
        override fun observe(observer: (ActiveQueueSnapshot) -> Unit) = AutoCloseable { }
        override fun close() = Unit
    }

    private object OneProjectRepository : ProjectsRepository {
        private const val PATH = "C:\\fixtures\\match.trproj"

        override fun projectsRootPath() = "C:\\fixtures"
        override fun getRecents() = listOf(ProjectSummary(PATH, "Match", "match.mp4", PROJECT_ID))
        override fun readManifest(path: String) = PROJECT_MANIFEST
        override fun summarize(path: String) = ProjectSummary(path, "Match", "match.mp4", PROJECT_ID)
        override fun createProject(sourceVideoPath: String, name: String) = error("unused")
        override fun openProject(path: String, sourceVideoPath: String?) = error("unused")
    }

    private class MemoryPreferences : AbstractPreferences(null, "") {
        private val values = mutableMapOf<String, String>()
        override fun putSpi(key: String, value: String) { values[key] = value }
        override fun getSpi(key: String): String? = values[key]
        override fun removeSpi(key: String) { values.remove(key) }
        override fun removeNodeSpi() = Unit
        override fun keysSpi(): Array<String> = values.keys.toTypedArray()
        override fun childrenNamesSpi(): Array<String> = emptyArray()
        override fun childSpi(name: String) = MemoryPreferences()
        override fun syncSpi() = Unit
        override fun flushSpi() = Unit
    }

    private object NoOpFilePicker : FilePicker {
        override fun chooseSourceVideo(parent: Component?, title: String, initialDirectory: File?, suggestedFile: File?) = null
        override fun chooseExportDestination(parent: Component?, title: String, initialDirectory: File?, suggestedFile: File?) = null
    }

    private object NoOpDialogs : UserDialogService {
        override fun showInfo(parent: Component?, message: String, title: String) = Unit
        override fun showError(parent: Component?, message: String, title: String) = Unit
        override fun confirm(parent: Component?, message: String, title: String) = false
    }

    private object EmptyCompletedRendersRepository : CompletedRendersRepository {
        override fun loadAll() = emptyList<CompletedRender>()
        override fun append(job: RenderJob) = Unit
        override fun clear() = Unit
    }

    private companion object {
        private const val PROJECT_ID = "123e4567-e89b-42d3-a456-426614174000"
        private val PROJECT_MANIFEST = ProjectManifestV1(
            id = PROJECT_ID,
            name = "Match",
            createdAt = "2026-08-23T00:00:00Z",
            lastOpenedAt = "2026-08-23T00:00:00Z",
            sourceVideo = "match.mp4",
        )

        private val REQUIRED_NAMES = setOf(
            "app-frame",
            "nav-projects",
            "nav-points",
            "nav-colors",
            "nav-crop",
            "nav-scoring",
            "nav-export",
            "nav-help",
            "projects-import-match",
            "projects-current-name",
            "projects-open-$PROJECT_ID",
            "points-video",
            "points-play-pause",
            "points-seek",
            "points-current-time",
            "points-point-start",
            "points-point-end",
            "points-point-count",
            "points-favorite-count",
            "points-comment-count",
            "colors-brightness",
            "colors-contrast",
            "colors-saturation",
            "colors-shadows",
            "colors-highlights",
            "colors-temperature",
            "colors-play-pause",
            "colors-reset",
            "crop-zoom",
            "crop-pan-x",
            "crop-pan-y",
            "crop-rotation",
            "crop-rotation-fine",
            "crop-reset",
            "crop-seek",
            "scoring-player-1-point",
            "scoring-player-2-point",
            "scoring-player-1-game-won",
            "scoring-player-2-set-won",
            "scoring-player-1-serve",
            "scoring-player-2-serve",
            "current-point-label",
            "current-point-favorite",
            "score-settings",
            "scoreboard-style",
            "scoring-score-summary",
            "export-preset",
            "export-resolution",
            "export-idle-trim",
            "export-favorites-only",
            "export-scoreboard",
            "export-initialize",
            "export-cancel",
            "export-progress",
        )
    }
}
