package org.litvin.app

import org.junit.jupiter.api.Test
import org.litvin.ActiveQueueSnapshot
import org.litvin.RenderJob
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.export.CompletedRendersRepository
import org.litvin.export.RenderService
import org.litvin.media.MediaPlayerFactory
import org.litvin.media.MediaScreen
import org.litvin.media.StillFrameCaptureService
import org.litvin.media.SwingMediaPlayer
import org.litvin.projects.ProjectManifestV1
import org.litvin.projects.ProjectSummary
import org.litvin.projects.ProjectsRepository
import org.litvin.ui.commons.FilePicker
import org.litvin.ui.commons.UserDialogService
import java.awt.Component
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppServicesTest {
    @Test
    fun closeStopsRenderBeforeFlushingItsDependenciesThenClosesRemainingResourcesOnce() {
        val root = kotlin.io.path.createTempDirectory("app-services-").toFile()
        val events = mutableListOf<String>()
        val executors = RecordingExecutorProvider(events)
        val adjustments = AdjustmentsSession(executors.createScheduledExecutor("adjustments"), 60_000)
        adjustments.load(root.absolutePath)
        adjustments.set { it.copy(saturation = 1.5f) }
        val render = RecordingRenderService(events)
        val media = RecordingMediaPlayerFactory(events) {
            assertTrue(File(root, "adjustments.json").exists(), "Adjustments were not flushed after render shutdown")
        }
        val services = AppServices(
            paths = AppDataPaths(root),
            preferences = PreferencesProvider { throw UnsupportedOperationException() },
            executors = executors,
            mediaPlayers = media,
            renderService = render,
            filePicker = NoOpFilePicker,
            dialogs = NoOpDialogs,
            projectsRepository = NoOpProjectsRepository,
            completedRenders = NoOpCompletedRendersRepository,
            adjustments = adjustments,
        )

        services.close()
        services.close()

        assertEquals(listOf("render", "media", "executors"), events)
        root.deleteRecursively()
    }

    @Test
    fun productionFailureClosesEveryConstructedResourceInReverseOrderDespiteCleanupFailures() {
        val root = kotlin.io.path.createTempDirectory("app-services-construction-").toFile()
        val events = mutableListOf<String>()
        val executors = RecordingExecutorProvider(events)
        val media = FailingMediaPlayerFactory(events)
        val render = FailingRenderService(events)
        try {
            val failure = assertFailsWith<IllegalStateException> {
                AppServices.production(
                    AppServicesProductionFactory(
                        paths = { AppDataPaths(root) },
                        preferences = { PreferencesProvider { throw UnsupportedOperationException() } },
                        executors = { executors },
                        mediaPlayers = { media },
                        filePicker = { NoOpFilePicker },
                        dialogs = { NoOpDialogs },
                        projectsRepository = { _ -> NoOpProjectsRepository },
                        completedRenders = { _ -> NoOpCompletedRendersRepository },
                        adjustments = { provider ->
                            AdjustmentsSession(provider.createScheduledExecutor("construction-adjustments"))
                        },
                        renderService = { _, _ -> render },
                        afterConstruction = { throw IllegalStateException("construction failed") },
                    ),
                )
            }

            assertEquals("construction failed", failure.message)
            assertEquals(listOf("render", "media", "executors"), events)
            assertEquals(2, failure.suppressed.size)
        } finally {
            root.deleteRecursively()
        }
    }

    private class RecordingExecutorProvider(private val events: MutableList<String>) : ExecutorProvider {
        private val delegate = TrackedExecutorProvider("app-services-test")
        override fun createExecutor(name: String): ExecutorService = delegate.createExecutor(name)
        override fun createScheduledExecutor(name: String): ScheduledExecutorService =
            delegate.createScheduledExecutor(name)
        override fun close() {
            events += "executors"
            delegate.close()
        }
    }

    private class RecordingMediaPlayerFactory(
        private val events: MutableList<String>,
        private val beforeClose: () -> Unit,
    ) : MediaPlayerFactory {
        override fun create(screen: MediaScreen): SwingMediaPlayer = error("unused")
        override fun createFrameCapture(): StillFrameCaptureService = error("unused")
        override fun close() {
            beforeClose()
            events += "media"
        }
    }

    private class RecordingRenderService(private val events: MutableList<String>) : RenderService {
        override fun enqueue(job: RenderJob) = Unit
        override fun cancelCurrent() = Unit
        override fun cancelQueued(jobId: String): Boolean = false
        override fun observe(observer: (ActiveQueueSnapshot) -> Unit): AutoCloseable = AutoCloseable { }
        override fun close() {
            events += "render"
        }
    }

    private class FailingMediaPlayerFactory(private val events: MutableList<String>) : MediaPlayerFactory {
        override fun create(screen: MediaScreen): SwingMediaPlayer = error("unused")
        override fun createFrameCapture(): StillFrameCaptureService = error("unused")
        override fun close() {
            events += "media"
            throw IllegalArgumentException("media close failed")
        }
    }

    private class FailingRenderService(private val events: MutableList<String>) : RenderService {
        override fun enqueue(job: RenderJob) = Unit
        override fun cancelCurrent() = Unit
        override fun cancelQueued(jobId: String): Boolean = false
        override fun observe(observer: (ActiveQueueSnapshot) -> Unit): AutoCloseable = AutoCloseable { }
        override fun close() {
            events += "render"
            throw IllegalStateException("render close failed")
        }
    }

    private object NoOpFilePicker : FilePicker {
        override fun chooseSourceVideo(parent: Component?, title: String, initialDirectory: File?, suggestedFile: File?) = null
        override fun chooseExportDestination(parent: Component?, title: String, initialDirectory: File?, suggestedFile: File?) = null
    }

    private object NoOpDialogs : UserDialogService {
        override fun showInfo(parent: Component?, message: String, title: String) = Unit
        override fun showError(parent: Component?, message: String, title: String) = Unit
        override fun confirm(parent: Component?, message: String, title: String): Boolean = false
    }

    private object NoOpProjectsRepository : ProjectsRepository {
        override fun projectsRootPath(): String = ""
        override fun getRecents(): List<ProjectSummary> = emptyList()
        override fun readManifest(path: String): ProjectManifestV1 = error("unused")
        override fun summarize(path: String): ProjectSummary = error("unused")
        override fun createProject(sourceVideoPath: String): ProjectSummary = error("unused")
        override fun openProject(path: String, sourceVideoPath: String?): ProjectSummary = error("unused")
    }

    private object NoOpCompletedRendersRepository : CompletedRendersRepository {
        override fun loadAll() = emptyList<org.litvin.CompletedRender>()
        override fun append(job: RenderJob) = Unit
        override fun clear() = Unit
    }
}
