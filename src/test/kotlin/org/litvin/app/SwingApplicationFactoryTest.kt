package org.litvin.app

import org.assertj.swing.edt.GuiActionRunner
import org.junit.jupiter.api.Test
import org.litvin.ActiveQueueSnapshot
import org.litvin.CompletedRender
import org.litvin.RenderJob
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.export.CompletedRendersRepository
import org.litvin.export.EncoderCapabilities
import org.litvin.export.RenderService
import org.litvin.media.MediaPlayerFactory
import org.litvin.media.MediaScreen
import org.litvin.media.PlayerStatus
import org.litvin.media.SwingMediaPlayer
import org.litvin.projects.ProjectManifestV1
import org.litvin.projects.ProjectSummary
import org.litvin.projects.FileProjectsRepository
import org.litvin.projects.ProjectsRepository
import org.litvin.ui.commons.FilePicker
import org.litvin.ui.commons.UserDialogService
import org.litvin.ui.tabs.projects.SwingProjectsPanel
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Window
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.awt.image.RenderedImage
import java.io.File
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.prefs.AbstractPreferences
import javax.swing.AbstractButton
import javax.swing.JFrame
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SwingApplicationFactoryTest {
    @Test
    fun `test mode suppresses the GPU restart notification`() {
        assertTrue(
            SwingApplicationFactory.shouldShowGpuRestartNotification(
                show = true,
                testEnabled = false,
                gpuPreferenceChanged = true,
            )
        )
        assertFalse(
            SwingApplicationFactory.shouldShowGpuRestartNotification(
                show = true,
                testEnabled = true,
                gpuPreferenceChanged = true,
            )
        )
    }

    @Test
    fun `a project can return to Projects after opening`() {
        val projectFixture = OpenProjectFixture()
        val fixture = TestServices(projectsRepository = projectFixture.repository)
        val windowsBefore = Window.getWindows().toSet()
        var handle: SwingApplicationHandle? = null

        try {
            val opened = GuiActionRunner.execute<SwingApplicationHandle> {
                SwingApplicationFactory.create(fixture.services, show = true)
            }
            handle = opened

            GuiActionRunner.execute {
                checkNotNull(findComponent<AbstractButton>(opened.frame) { it.name == "projects-open-${projectFixture.project.id}" })
                    .doClick()
            }
            assertTrue(checkNotNull(findComponent(opened.frame) { it.name == "rallies-point-start" }).isShowing)

            GuiActionRunner.execute {
                checkNotNull(findComponent<AbstractButton>(opened.frame) { it.name == "nav-projects" }).doClick()
            }
            assertEquals("Tennis Record — Projects", opened.frame.title)
            assertTrue(checkNotNull(findComponent(opened.frame) { it.name == "projects-import-match" }).isShowing)
            assertEquals(
                projectFixture.project.name,
                checkNotNull(findComponent<JLabel>(opened.frame) { it.name == "projects-current-name" }).text,
            )
        } finally {
            handle?.close()
            Window.getWindows().filterNot(windowsBefore::contains).forEach(Window::dispose)
        }
    }

    @Test
    fun `window close cleans resources before it requests process exit`() {
        val fixture = TestServices()
        val windowsBefore = Window.getWindows().toSet()
        var exitRequests = 0
        var handle: SwingApplicationHandle? = null

        try {
            val opened = GuiActionRunner.execute<SwingApplicationHandle> {
                SwingApplicationFactory.create(
                    services = fixture.services,
                    show = true,
                    onWindowClosed = {
                        assertTrue(fixture.mediaPlayers.players.all { it.closeCalls == 1 })
                        assertEquals(1, fixture.renderService.closeCalls)
                        assertEquals(1, fixture.executors.closeCalls)
                        exitRequests++
                    },
                )
            }
            handle = opened

            GuiActionRunner.execute {
                opened.frame.dispatchEvent(WindowEvent(opened.frame, WindowEvent.WINDOW_CLOSING))
            }

            assertEquals(1, exitRequests)
            assertTrue(Window.getWindows().filterNot(windowsBefore::contains).none(Window::isDisplayable))
        } finally {
            handle?.close()
            Window.getWindows().filterNot(windowsBefore::contains).forEach(Window::dispose)
        }
    }

    @Test
    fun visibleApplicationUsesInjectedEncoderCapabilitiesWithoutNativeProbe() {
        val fixture = TestServices(EncoderCapabilities(setOf("h264_nvenc"), "h264_nvenc"))
        val windowsBefore = Window.getWindows().toSet()
        val previousFfmpeg = System.getProperty("tr.ffmpeg.path")
        System.setProperty("tr.ffmpeg.path", fixture.root.resolve("must-not-run-ffmpeg.exe").absolutePath)
        org.litvin.ApplicationLayout.resetForTests()
        org.litvin.FFmpegCapabilities.refresh()
        var handle: SwingApplicationHandle? = null

        try {
            val opened = GuiActionRunner.execute<SwingApplicationHandle> {
                SwingApplicationFactory.create(fixture.services, show = true)
            }
            handle = opened

            val encoderLabels = findComponents(opened.frame, JComboBox::class.java)
                .flatMap { combo -> (0 until combo.itemCount).map { combo.getItemAt(it).toString() } }
            assertTrue(encoderLabels.any { "NVENC" in it }, "Injected encoder options were $encoderLabels")
        } finally {
            handle?.close()
            Window.getWindows().filterNot(windowsBefore::contains).forEach(Window::dispose)
            if (previousFfmpeg == null) System.clearProperty("tr.ffmpeg.path")
            else System.setProperty("tr.ffmpeg.path", previousFfmpeg)
            org.litvin.ApplicationLayout.resetForTests()
            org.litvin.FFmpegCapabilities.refresh()
        }
    }

    @Test
    fun invisibleApplicationStartsOnProjectsAndClosesEveryOwnedResourceOnce() {
        val fixture = TestServices()
        val windowsBefore = Window.getWindows().toSet()

        val handle = GuiActionRunner.execute<SwingApplicationHandle> {
            SwingApplicationFactory.create(fixture.services, show = false)
        }

        assertFalse(handle.frame.isVisible)
        assertEquals(JFrame.DISPOSE_ON_CLOSE, handle.frame.defaultCloseOperation)
        assertEquals("Tennis Record — Projects", handle.frame.title)
        assertNotNull(findComponent<SwingProjectsPanel>(handle.frame))

        handle.close()
        handle.close()

        assertTrue(fixture.mediaPlayers.players.all { it.closeCalls == 1 })
        assertEquals(1, fixture.mediaPlayers.closeCalls)
        assertEquals(1, fixture.renderService.subscriptionCloseCalls)
        assertEquals(1, fixture.renderService.closeCalls)
        assertEquals(1, fixture.executors.closeCalls)
        assertTrue(Window.getWindows().filterNot(windowsBefore::contains).none(Window::isDisplayable))
    }

    private inline fun <reified T : Component> findComponent(root: Component): T? =
        findComponent(root, T::class.java)

    private inline fun <reified T : Component> findComponent(
        root: Component,
        noinline predicate: (T) -> Boolean,
    ): T? = findComponents(root, T::class.java).firstOrNull(predicate)

    private fun <T : Component> findComponent(root: Component, type: Class<T>): T? {
        if (type.isInstance(root)) return type.cast(root)
        if (root !is Container) return null
        return root.components.firstNotNullOfOrNull { findComponent(it, type) }
    }

    private fun <T : Component> findComponents(root: Component, type: Class<T>): List<T> = buildList {
        if (type.isInstance(root)) add(type.cast(root))
        if (root is Container) root.components.forEach { addAll(findComponents(it, type)) }
    }

    private class TestServices(
        encoderCapabilities: EncoderCapabilities = EncoderCapabilities.NONE,
        projectsRepository: ProjectsRepository = EmptyProjectsRepository,
    ) {
        val root = kotlin.io.path.createTempDirectory("swing-application-").toFile()
        val executors = RecordingExecutorProvider()
        val mediaPlayers = RecordingMediaPlayerFactory()
        val renderService = RecordingRenderService()
        private val adjustmentsExecutor = executors.createScheduledExecutor("adjustments")
        private val preferences = MemoryPreferences()

        val services = AppServices(
            paths = AppDataPaths(root),
            preferences = PreferencesProvider { preferences },
            executors = executors,
            mediaPlayers = mediaPlayers,
            renderService = renderService,
            filePicker = NoOpFilePicker,
            dialogs = NoOpDialogs,
            projectsRepository = projectsRepository,
            completedRenders = EmptyCompletedRendersRepository,
            adjustments = AdjustmentsSession(adjustmentsExecutor, 60_000),
            encoderCapabilities = encoderCapabilities,
        )
    }

    private class OpenProjectFixture {
        val root = kotlin.io.path.createTempDirectory("swing-project-navigation-").toFile()
        val source = root.resolve("source.mp4").apply { writeText("") }
        val repository = FileProjectsRepository(root.resolve("projects"))
        val project = repository.createProject(source.absolutePath)
    }

    private class RecordingExecutorProvider : ExecutorProvider {
        private val executors = CopyOnWriteArrayList<ExecutorService>()
        var closeCalls = 0
            private set

        override fun createExecutor(name: String): ExecutorService =
            DirectExecutorService().also(executors::add)

        override fun createScheduledExecutor(name: String): ScheduledExecutorService =
            ScheduledThreadPoolExecutor(1).also(executors::add)

        override fun close() {
            closeCalls++
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

    private class RecordingMediaPlayerFactory : MediaPlayerFactory {
        val players = mutableListOf<FakeSwingMediaPlayer>()
        var closeCalls = 0
            private set

        override fun create(screen: MediaScreen): SwingMediaPlayer =
            FakeSwingMediaPlayer().also(players::add)

        override fun close() {
            closeCalls++
        }
    }

    private class FakeSwingMediaPlayer : SwingMediaPlayer {
        override val component: Component = JPanel()
        override var onReady: (() -> Unit)? = null
        override var onStatusChanged: ((PlayerStatus) -> Unit)? = null
        override var onTimeChanged: ((Long) -> Unit)? = null
        var closeCalls = 0
            private set
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
        override fun setPreviewOverlayImage(image: RenderedImage?) = Unit
        override fun stepFrameForward(maximumTimeMs: Long) = 0L
        override fun stepFrameBackward(minimumTimeMs: Long) = 0L
        override fun nextFrame() = Unit
        override fun setSubtitleFile(file: File) = true
        override fun activatePreview(reason: String) = Unit
        override fun deactivatePreview(reason: String) = Unit
        override fun close() { closeCalls++ }
    }

    private class RecordingRenderService : RenderService {
        var closeCalls = 0
            private set
        var subscriptionCloseCalls = 0
            private set
        override fun enqueue(job: RenderJob) = Unit
        override fun cancelCurrent() = Unit
        override fun cancelQueued(jobId: String) = false
        override fun observe(observer: (ActiveQueueSnapshot) -> Unit): AutoCloseable =
            AutoCloseable { subscriptionCloseCalls++ }
        override fun close() { closeCalls++ }
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

    private object EmptyProjectsRepository : ProjectsRepository {
        override fun projectsRootPath() = ""
        override fun getRecents() = emptyList<ProjectSummary>()
        override fun readManifest(path: String): ProjectManifestV1 = error("unused")
        override fun summarize(path: String): ProjectSummary = error("unused")
        override fun createProject(sourceVideoPath: String): ProjectSummary = error("unused")
        override fun openProject(path: String, sourceVideoPath: String?): ProjectSummary = error("unused")
    }

    private object EmptyCompletedRendersRepository : CompletedRendersRepository {
        override fun loadAll() = emptyList<CompletedRender>()
        override fun append(job: RenderJob) = Unit
        override fun clear() = Unit
    }
}
