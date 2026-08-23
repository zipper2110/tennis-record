package org.litvin.app

import org.litvin.adjustments.AdjustmentsSession
import org.litvin.export.CompletedRendersRepository
import org.litvin.export.FileCompletedRendersRepository
import org.litvin.export.ProductionRenderService
import org.litvin.export.RenderService
import org.litvin.media.MediaPlayerFactory
import org.litvin.media.VlcjMediaPlayerFactory
import org.litvin.projects.FileProjectsRepository
import org.litvin.projects.ProjectsRepository
import org.litvin.ui.commons.FilePicker
import org.litvin.ui.commons.SwingFilePicker
import org.litvin.ui.commons.SwingUserDialogService
import org.litvin.ui.commons.UserDialogService
import java.util.concurrent.atomic.AtomicBoolean

data class AppServices(
    val paths: AppDataPaths,
    val preferences: PreferencesProvider,
    val executors: ExecutorProvider,
    val mediaPlayers: MediaPlayerFactory,
    val renderService: RenderService,
    val filePicker: FilePicker,
    val dialogs: UserDialogService,
    val projectsRepository: ProjectsRepository,
    val completedRenders: CompletedRendersRepository,
    val adjustments: AdjustmentsSession,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val resourcesInConstructionOrder = listOf(
            paths,
            preferences,
            executors,
            mediaPlayers,
            filePicker,
            dialogs,
            projectsRepository,
            completedRenders,
            adjustments,
            renderService,
        )
        var firstFailure: Throwable? = null
        resourcesInConstructionOrder.asReversed().forEach { resource ->
            if (resource is AutoCloseable) {
                try {
                    resource.close()
                } catch (failure: Throwable) {
                    if (firstFailure == null) firstFailure = failure
                }
            }
        }
        firstFailure?.let { throw it }
    }

    companion object {
        fun production(): AppServices {
            val paths = AppDataPaths.production()
            val preferences = PreferencesProvider.production()
            val executors = TrackedExecutorProvider()
            try {
                val mediaPlayers = VlcjMediaPlayerFactory()
                val filePicker = SwingFilePicker()
                val dialogs = SwingUserDialogService()
                val projectsRepository = FileProjectsRepository(paths.projects)
                val completedRenders = FileCompletedRendersRepository(paths.completedRenders)
                val adjustments = AdjustmentsSession(executors.createScheduledExecutor("adjustments-autosave"))
                val renderService = ProductionRenderService(adjustments, completedRenders)
                return AppServices(
                    paths = paths,
                    preferences = preferences,
                    executors = executors,
                    mediaPlayers = mediaPlayers,
                    renderService = renderService,
                    filePicker = filePicker,
                    dialogs = dialogs,
                    projectsRepository = projectsRepository,
                    completedRenders = completedRenders,
                    adjustments = adjustments,
                )
            } catch (failure: Throwable) {
                executors.close()
                throw failure
            }
        }
    }
}
