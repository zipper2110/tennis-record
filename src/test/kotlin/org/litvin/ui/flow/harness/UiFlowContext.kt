package org.litvin.ui.flow.harness

import org.litvin.app.AppDataPaths
import org.litvin.app.AppServices
import org.litvin.app.SwingApplicationHandle
import org.litvin.ui.flow.driver.SwingUiDriver
import org.litvin.ui.flow.fakes.FakeMediaPlayerFactory
import org.litvin.ui.flow.fakes.FakeRenderService
import org.litvin.ui.flow.fakes.InMemoryPreferencesProvider
import org.litvin.ui.flow.fakes.ScriptedDialogService
import org.litvin.ui.flow.fakes.ScriptedFilePicker
import org.litvin.ui.flow.fixtures.UiFlowFixtureBuilder
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

class UiFlowContext internal constructor(
    val workspace: Path,
    val artifactDirectory: Path,
    val paths: AppDataPaths,
    val preferences: InMemoryPreferencesProvider,
    val mediaPlayers: FakeMediaPlayerFactory,
    val renderService: FakeRenderService,
    val filePicker: ScriptedFilePicker,
    val dialogs: ScriptedDialogService,
    val fixtures: UiFlowFixtureBuilder,
    val services: AppServices,
    val application: SwingApplicationHandle,
    val driver: SwingUiDriver,
    internal val threadPrefix: String,
    internal val asynchronousFailures: CopyOnWriteArrayList<Throwable>,
) {
    private var restartAction: (() -> UiFlowContext)? = null

    fun restartApplication(): UiFlowContext =
        checkNotNull(restartAction) { "UI flow restart is not available" }.invoke()

    internal fun onRestart(action: () -> UiFlowContext) {
        check(restartAction == null) { "UI flow restart is already configured" }
        restartAction = action
    }
}
