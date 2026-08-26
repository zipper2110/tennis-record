package org.litvin.app

import org.litvin.adjustments.AdjustmentsSession
import org.litvin.export.CompletedRendersRepository
import org.litvin.export.EncoderCapabilities
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

internal data class AppServicesProductionFactory(
    val paths: () -> AppDataPaths = AppDataPaths::production,
    val preferences: () -> PreferencesProvider = PreferencesProvider::production,
    val executors: () -> ExecutorProvider = { TrackedExecutorProvider() },
    val mediaPlayers: () -> MediaPlayerFactory = { VlcjMediaPlayerFactory() },
    val filePicker: () -> FilePicker = { SwingFilePicker() },
    val dialogs: () -> UserDialogService = { SwingUserDialogService() },
    val projectsRepository: (AppDataPaths) -> ProjectsRepository = { FileProjectsRepository(it.projects) },
    val completedRenders: (AppDataPaths) -> CompletedRendersRepository = { FileCompletedRendersRepository(it.completedRenders) },
    val adjustments: (ExecutorProvider) -> AdjustmentsSession = {
        AdjustmentsSession(it.createScheduledExecutor("adjustments-autosave"))
    },
    val renderService: (AdjustmentsSession, CompletedRendersRepository) -> RenderService = { adjustments, completed ->
        ProductionRenderService(adjustments, completed)
    },
    val encoderCapabilities: () -> EncoderCapabilities = EncoderCapabilities::production,
    val afterConstruction: (AppServices) -> Unit = { },
)

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
    val encoderCapabilities: EncoderCapabilities = EncoderCapabilities.NONE,
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
        fun production(): AppServices = production(AppServicesProductionFactory())

        internal fun production(factory: AppServicesProductionFactory): AppServices {
            val constructedResources = mutableListOf<AutoCloseable>()

            fun <T> construct(create: () -> T): T = create().also { resource ->
                if (resource is AutoCloseable) constructedResources += resource
            }

            try {
                val paths = construct(factory.paths)
                val preferences = construct(factory.preferences)
                val executors = construct(factory.executors)
                val mediaPlayers = construct(factory.mediaPlayers)
                val filePicker = construct(factory.filePicker)
                val dialogs = construct(factory.dialogs)
                val projectsRepository = construct { factory.projectsRepository(paths) }
                val completedRenders = construct { factory.completedRenders(paths) }
                val adjustments = construct { factory.adjustments(executors) }
                val renderService = construct { factory.renderService(adjustments, completedRenders) }
                val encoderCapabilities = factory.encoderCapabilities()
                val services = AppServices(
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
                    encoderCapabilities = encoderCapabilities,
                )
                factory.afterConstruction(services)
                return services
            } catch (failure: Throwable) {
                constructedResources.asReversed().forEach { resource ->
                    try {
                        resource.close()
                    } catch (cleanupFailure: Throwable) {
                        failure.addSuppressed(cleanupFailure)
                    }
                }
                throw failure
            }
        }
    }
}
