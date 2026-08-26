package org.litvin.ui.flow.harness

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.app.AppDataPaths
import org.litvin.app.AppServices
import org.litvin.app.PreferencesProvider
import org.litvin.app.SwingApplicationFactory
import org.litvin.app.SwingApplicationHandle
import org.litvin.app.TrackedExecutorProvider
import org.litvin.export.FileCompletedRendersRepository
import org.litvin.export.EncoderCapabilities
import org.litvin.projects.FileProjectsRepository
import org.litvin.ui.flow.driver.RobotSwingDriver
import org.litvin.ui.flow.driver.SwingUiDriver
import org.litvin.ui.flow.fakes.FakeMediaPlayerFactory
import org.litvin.ui.flow.fakes.FakeRenderService
import org.litvin.ui.flow.fakes.InMemoryPreferencesProvider
import org.litvin.ui.flow.fakes.ScriptedDialogService
import org.litvin.ui.flow.fakes.ScriptedFilePicker
import org.litvin.ui.flow.fixtures.UiFlowFixtureBuilder
import java.awt.EventQueue
import java.awt.Window
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import javax.swing.JComponent
import javax.swing.RepaintManager

class SwingUiFlowExtension(
    private val workspaceParent: Path = Path.of("target", "ui-test-workspaces"),
    private val artifactsRoot: Path = Path.of("target", "ui-test-artifacts"),
    private val driverFactory: () -> SwingUiDriver = ::RobotSwingDriver,
) : BeforeEachCallback, AfterTestExecutionCallback, AfterEachCallback, ParameterResolver {
    private var current: UiFlowContext? = null
    private var testFailure: Throwable? = null
    private var previousUncaughtHandler: Thread.UncaughtExceptionHandler? = null
    private var previousRepaintManager: RepaintManager? = null
    private var lifecycleOrdinal = 0

    override fun beforeEach(context: ExtensionContext) {
        check(current == null) { "SwingUiFlowExtension cannot run tests concurrently" }
        testFailure = null
        val className = safeSegment(context.requiredTestClass.simpleName)
        val methodName = safeSegment(context.requiredTestMethod.name)
        val artifactDirectory = artifactsRoot.toAbsolutePath().normalize().resolve(className).resolve(methodName)
        deleteTree(artifactDirectory, artifactsRoot.toAbsolutePath().normalize())

        val workspaceBase = workspaceParent.toAbsolutePath().normalize()
        Files.createDirectories(workspaceBase)
        val workspace = Files.createTempDirectory(workspaceBase, "$className-$methodName-")
        val failures = CopyOnWriteArrayList<Throwable>()
        val preferences = InMemoryPreferencesProvider().apply {
            node(PreferencesProvider.APPLICATION).putBoolean("help.overviewShown", true)
        }
        lifecycleOrdinal = 0

        previousUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
        val delegatedUncaughtHandler = previousUncaughtHandler
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            failures += IllegalStateException("Uncaught exception on thread '${thread.name}'", failure)
            delegatedUncaughtHandler?.uncaughtException(thread, failure)
        }
        previousRepaintManager = RepaintManager.currentManager(null as JComponent?)
        RepaintManager.setCurrentManager(CapturingRepaintManager(failures))

        try {
            current = createApplicationContext(
                workspace = workspace,
                artifactDirectory = artifactDirectory,
                preferences = preferences,
                failures = failures,
                threadPrefixBase = "ui-flow-$className-$methodName",
            )
        } catch (failure: Throwable) {
            runCatching { disposeAllWindows() }.exceptionOrNull()?.let(failure::addSuppressed)
            restoreCaptureHooks()
            deleteTree(workspace, workspaceBase)
            throw failure
        }
    }

    override fun afterTestExecution(context: ExtensionContext) {
        testFailure = context.executionException.orElse(null)
            ?: current?.asynchronousFailures?.firstOrNull()
    }

    override fun afterEach(context: ExtensionContext) {
        val flow = current ?: return
        var failure = testFailure ?: flow.asynchronousFailures.firstOrNull()
        if (failure != null) capture(flow, failure)

        failure = cleanupFailure(failure) { flow.application.close() }
        failure = cleanupFailure(failure) { flow.driver.close() }
        failure = cleanupFailure(failure) { flow.services.close() }
        failure = cleanupFailure(failure) { disposeAllWindows() }
        failure = cleanupFailure(failure) { requireOwnedThreadsStopped(flow.threadPrefix) }
        failure = failure ?: flow.asynchronousFailures.firstOrNull()
        restoreCaptureHooks()

        if (failure != null) {
            if (testFailure == null) capture(flow, failure)
            failure = cleanupFailure(failure) { UiFlowArtifacts.retainWorkspace(flow) }
            deleteTree(flow.workspace, workspaceParent.toAbsolutePath().normalize())
        } else {
            deleteTree(flow.workspace, workspaceParent.toAbsolutePath().normalize())
        }
        current = null

        if (testFailure == null && failure != null) throw failure
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
        parameterContext.parameter.type == UiFlowContext::class.java

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any =
        activeContext()

    internal fun activeContext(): UiFlowContext = checkNotNull(current) { "UI flow context is not active" }

    private fun restartApplication(previous: UiFlowContext): UiFlowContext {
        check(current === previous) { "Only the active UI flow context can be restarted" }
        var failure: Throwable? = null
        failure = cleanupFailure(failure) { previous.application.close() }
        failure = cleanupFailure(failure) { previous.driver.close() }
        failure = cleanupFailure(failure) { previous.services.close() }
        failure = cleanupFailure(failure) { disposeAllWindows() }
        failure = cleanupFailure(failure) { requireOwnedThreadsStopped(previous.threadPrefix) }
        failure?.let { throw it }

        return createApplicationContext(
            workspace = previous.workspace,
            artifactDirectory = previous.artifactDirectory,
            preferences = previous.preferences,
            failures = previous.asynchronousFailures,
            threadPrefixBase = previous.threadPrefix.substringBeforeLast("-lifecycle-"),
        ).also { restarted -> current = restarted }
    }

    private fun createApplicationContext(
        workspace: Path,
        artifactDirectory: Path,
        preferences: InMemoryPreferencesProvider,
        failures: CopyOnWriteArrayList<Throwable>,
        threadPrefixBase: String,
    ): UiFlowContext {
        val paths = AppDataPaths(workspace.toFile())
        Files.createDirectories(paths.projects.toPath())
        Files.createDirectories(paths.logs.toPath())
        Files.createDirectories(paths.temporary.toPath())
        val threadPrefix = "$threadPrefixBase-lifecycle-${++lifecycleOrdinal}"
        val executors = TrackedExecutorProvider(threadPrefix, Duration.ofSeconds(3))
        val mediaPlayers = FakeMediaPlayerFactory()
        val renderService = FakeRenderService()
        val filePicker = ScriptedFilePicker()
        val dialogs = ScriptedDialogService()
        val adjustments = AdjustmentsSession(executors.createScheduledExecutor("adjustments"), 25L)
        val services = AppServices(
            paths = paths,
            preferences = preferences,
            executors = executors,
            mediaPlayers = mediaPlayers,
            renderService = renderService,
            filePicker = filePicker,
            dialogs = dialogs,
            projectsRepository = FileProjectsRepository(paths.projects),
            completedRenders = FileCompletedRendersRepository(paths.completedRenders),
            adjustments = adjustments,
            encoderCapabilities = EncoderCapabilities.NONE,
        )

        var application: SwingApplicationHandle? = null
        var driver: SwingUiDriver? = null
        try {
            val builtApplication = onEdt { SwingApplicationFactory.create(services, show = true) }
            application = builtApplication
            val builtDriver = driverFactory()
            driver = builtDriver
            return UiFlowContext(
                workspace = workspace,
                artifactDirectory = artifactDirectory,
                paths = paths,
                preferences = preferences,
                mediaPlayers = mediaPlayers,
                renderService = renderService,
                filePicker = filePicker,
                dialogs = dialogs,
                fixtures = UiFlowFixtureBuilder(paths.projects.toPath()),
                services = services,
                application = builtApplication,
                driver = builtDriver,
                threadPrefix = threadPrefix,
                asynchronousFailures = failures,
            ).apply {
                onRestart { restartApplication(this) }
            }
        } catch (failure: Throwable) {
            runCatching { driver?.close() }.exceptionOrNull()?.let(failure::addSuppressed)
            runCatching { application?.close() }.exceptionOrNull()?.let(failure::addSuppressed)
            runCatching { services.close() }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    private fun capture(flow: UiFlowContext, failure: Throwable) {
        try {
            UiFlowArtifacts.capture(flow, failure)
        } catch (captureFailure: Throwable) {
            failure.addSuppressed(captureFailure)
        }
    }

    private fun cleanupFailure(previous: Throwable?, cleanup: () -> Unit): Throwable? = try {
        cleanup()
        previous
    } catch (failure: Throwable) {
        if (previous == null) failure else previous.apply { addSuppressed(failure) }
    }

    private fun disposeAllWindows() {
        onEdt { Window.getWindows().filter(Window::isDisplayable).forEach(Window::dispose) }
    }

    private fun requireOwnedThreadsStopped(prefix: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        var alive: List<Thread>
        do {
            alive = Thread.getAllStackTraces().keys.filter { it.isAlive && !it.isDaemon && it.name.startsWith(prefix) }
            if (alive.isEmpty()) return
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20))
        } while (System.nanoTime() < deadline)
        throw AssertionError("Application-owned threads still alive: ${alive.map(Thread::getName)}")
    }

    private fun restoreCaptureHooks() {
        previousRepaintManager?.let(RepaintManager::setCurrentManager)
        Thread.setDefaultUncaughtExceptionHandler(previousUncaughtHandler)
        previousRepaintManager = null
        previousUncaughtHandler = null
    }

    private fun deleteTree(path: Path, allowedRoot: Path) {
        val normalized = path.toAbsolutePath().normalize()
        val root = allowedRoot.toAbsolutePath().normalize()
        if (!Files.exists(normalized)) return
        require(normalized != root && normalized.startsWith(root)) {
            "Refusing to delete outside the configured UI-flow root: $normalized"
        }
        Files.walk(normalized).use { entries ->
            entries.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun safeSegment(value: String): String =
        value.replace(Regex("[<>:\"/\\\\|?*\\p{Cntrl}]"), "_").trim().ifBlank { "unnamed" }

    private fun <T> onEdt(action: () -> T): T {
        if (EventQueue.isDispatchThread()) return action()
        val task = FutureTask(action)
        EventQueue.invokeAndWait(task)
        return task.get()
    }

    private class CapturingRepaintManager(
        private val failures: MutableList<Throwable>,
    ) : RepaintManager() {
        override fun addInvalidComponent(invalidComponent: JComponent) {
            recordViolation(invalidComponent)
            super.addInvalidComponent(invalidComponent)
        }

        override fun addDirtyRegion(component: JComponent, x: Int, y: Int, width: Int, height: Int) {
            recordViolation(component)
            super.addDirtyRegion(component, x, y, width, height)
        }

        private fun recordViolation(component: JComponent) {
            if (!EventQueue.isDispatchThread() && component.isShowing) {
                failures += AssertionError(
                    "Swing component ${component.javaClass.name} '${component.name}' was mutated off the EDT",
                )
            }
        }
    }
}
