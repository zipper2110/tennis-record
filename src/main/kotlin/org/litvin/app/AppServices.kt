package org.litvin.app

import org.litvin.adjustments.AdjustmentsSession
import org.litvin.export.CompletedRendersRepository
import org.litvin.export.EncoderCapabilities
import org.litvin.export.FileCompletedRendersRepository
import org.litvin.export.ProductionRenderService
import org.litvin.export.RenderService
import org.litvin.media.MediaPlayerFactory
import org.litvin.media.productionMediaPlayerFactory
import org.litvin.projects.FileProjectsRepository
import org.litvin.projects.ProjectsRepository
import org.litvin.ui.commons.FilePicker
import org.litvin.ui.commons.SwingFilePicker
import org.litvin.ui.commons.SwingUserDialogService
import org.litvin.ui.commons.UserDialogService
import org.litvin.analytics.AnalyticsBuildConfig
import org.litvin.analytics.AnalyticsController
import org.litvin.analytics.AnalyticsPreferences
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

internal data class AppServicesProductionFactory(
    val paths: () -> AppDataPaths = AppDataPaths::production,
    val preferences: () -> PreferencesProvider = PreferencesProvider::production,
    val executors: () -> ExecutorProvider = { TrackedExecutorProvider() },
    val mediaPlayers: () -> MediaPlayerFactory = { productionMediaPlayerFactory() },
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
    // The detection runs test encodes and takes some seconds, so the export panel waits for it in the background.
    val encoderCapabilities: CompletableFuture<EncoderCapabilities> = CompletableFuture.completedFuture(EncoderCapabilities.NONE),
    val analyticsConfig: AnalyticsBuildConfig = AnalyticsBuildConfig.Disabled("not_configured"),
    val analyticsPreferences: AnalyticsPreferences? = null,
    val analyticsController: AnalyticsController? = null,
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
            analyticsController,
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
                val encoderCapabilities = CompletableFuture.supplyAsync { factory.encoderCapabilities() }
                val analyticsConfig = AnalyticsBuildConfig.fromSystemProperties()
                val analyticsPreferences = if (analyticsConfig is AnalyticsBuildConfig.Enabled) {
                    AnalyticsPreferences(preferences.node(PreferencesProvider.ANALYTICS))
                } else null
                val analyticsController = analyticsPreferences?.let { preferencesForAnalytics ->
                    construct { AnalyticsController(analyticsConfig, preferencesForAnalytics) }.also { it.startIfConsented() }
                }
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
                    analyticsConfig = analyticsConfig,
                    analyticsPreferences = analyticsPreferences,
                    analyticsController = analyticsController,
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
