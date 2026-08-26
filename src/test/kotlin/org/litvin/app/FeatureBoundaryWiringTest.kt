package org.litvin.app

import org.junit.jupiter.api.Test
import org.litvin.ActiveQueueSnapshot
import org.litvin.CompletedRender
import org.litvin.RenderJob
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.export.CompletedRendersRepository
import org.litvin.export.EncoderCapabilities
import org.litvin.export.RenderService
import org.litvin.media.PlayerStatus
import org.litvin.media.StillFrameCaptureService
import org.litvin.media.StillFrameMediaInfo
import org.litvin.media.SwingMediaPlayer
import org.litvin.projects.ProjectManifestV1
import org.litvin.projects.ProjectSummary
import org.litvin.projects.ProjectsRepository
import org.litvin.ui.commons.FilePicker
import org.litvin.ui.commons.UserDialogService
import org.litvin.ui.tabs.adjustments.SwingColorAdjustmentsPanel
import org.litvin.ui.tabs.crop.presenter.DefaultCropRotatePresenter
import org.litvin.ui.tabs.export.ExportSettingsPreferences
import org.litvin.ui.tabs.export.SwingExportPanel
import org.litvin.ui.tabs.markup.SwingMarkupPanel
import org.litvin.ui.tabs.projects.SwingProjectsPanel
import org.litvin.ui.tabs.projects.presenter.DefaultProjectsPresenter
import org.litvin.ui.tabs.scoring.SwingScoringPanel
import java.awt.Component
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.awt.image.RenderedImage
import java.io.File
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.prefs.AbstractPreferences
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureBoundaryWiringTest {
    @Test
    fun injectedFeatureConstructionUsesNoNativeOrGlobalEffectsAndClosesOwnedResources() {
        val scheduler = ScheduledThreadPoolExecutor(1)
        val autosaveExecutor = DirectExecutorService()
        val adjustments = AdjustmentsSession(scheduler, 60_000)
        val dialogs = RecordingDialogs()
        val picker = RecordingFilePicker()
        val render = RecordingRenderService()
        val completed = EmptyCompletedRendersRepository
        val preferences = MemoryPreferences()
        val players = List(3) { FakeSwingMediaPlayer() }
        val capture = FakeStillFrameCaptureService()

        var markup: SwingMarkupPanel? = null
        var colors: SwingColorAdjustmentsPanel? = null
        var scoring: SwingScoringPanel? = null
        var export: SwingExportPanel? = null
        var crop: DefaultCropRotatePresenter? = null
        SwingUtilities.invokeAndWait {
            val presenter = DefaultProjectsPresenter(
                repository = EmptyProjectsRepository,
                preferences = preferences,
                ioExecutor = autosaveExecutor,
            )
            SwingProjectsPanel(presenter, picker, dialogs)
            markup = SwingMarkupPanel(players[0], adjustments, autosaveExecutor, dialogs)
            colors = SwingColorAdjustmentsPanel(players[1], adjustments, preferences)
            scoring = SwingScoringPanel(players[2], adjustments, dialogs)
            crop = DefaultCropRotatePresenter(capture, adjustments, scheduler)
            export = SwingExportPanel(
                ExportSettingsPreferences(preferences),
                render,
                completed,
                picker,
                dialogs,
                EncoderCapabilities.NONE,
            )
        }

        assertEquals(0, picker.calls)
        assertEquals(0, dialogs.calls)
        assertEquals(1, render.observerRegistrations)

        SwingUtilities.invokeAndWait {
            markup!!.close()
            markup!!.close()
            colors!!.close()
            colors!!.close()
            scoring!!.close()
            scoring!!.close()
            crop!!.dispose()
            crop!!.dispose()
            export!!.close()
            export!!.close()
        }

        assertTrue(players.all { it.closeCalls == 1 })
        assertEquals(1, capture.closeCalls)
        assertEquals(1, render.subscriptionCloseCalls)
        adjustments.close()
        scheduler.shutdownNow()
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

    private class RecordingFilePicker : FilePicker {
        var calls = 0
        override fun chooseSourceVideo(parent: Component?, title: String, initialDirectory: File?, suggestedFile: File?): File? {
            calls++
            return null
        }
        override fun chooseExportDestination(parent: Component?, title: String, initialDirectory: File?, suggestedFile: File?): File? {
            calls++
            return null
        }
    }

    private class RecordingDialogs : UserDialogService {
        var calls = 0
        override fun showInfo(parent: Component?, message: String, title: String) { calls++ }
        override fun showError(parent: Component?, message: String, title: String) { calls++ }
        override fun confirm(parent: Component?, message: String, title: String): Boolean { calls++; return false }
    }

    private class RecordingRenderService : RenderService {
        var observerRegistrations = 0
        var subscriptionCloseCalls = 0
        override fun enqueue(job: RenderJob) = Unit
        override fun cancelCurrent() = Unit
        override fun cancelQueued(jobId: String) = false
        override fun observe(observer: (ActiveQueueSnapshot) -> Unit): AutoCloseable {
            observerRegistrations++
            return AutoCloseable { subscriptionCloseCalls++ }
        }
        override fun close() = Unit
    }

    private class FakeStillFrameCaptureService : StillFrameCaptureService {
        var closeCalls = 0
        override fun load(file: File) = StillFrameMediaInfo(0, Dimension(16, 9))
        override fun captureAt(ms: Long): BufferedImage? = null
        override fun durationMs() = 0L
        override fun close() { closeCalls++ }
    }

    private class FakeSwingMediaPlayer : SwingMediaPlayer {
        override val component: Component = JPanel()
        override var onReady: (() -> Unit)? = null
        override var onStatusChanged: ((PlayerStatus) -> Unit)? = null
        override var onTimeChanged: ((Long) -> Unit)? = null
        var closeCalls = 0
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
}
