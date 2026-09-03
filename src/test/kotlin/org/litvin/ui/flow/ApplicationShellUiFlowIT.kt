package org.litvin.ui.flow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.litvin.ui.flow.harness.SwingUiFlowExtension
import org.litvin.ui.flow.harness.UiFlowContext
import org.litvin.ui.flow.screens.ApplicationScreen
import java.nio.file.Path
import kotlin.test.assertEquals

class ApplicationShellUiFlowIT {
    @Test
    fun `a new match survives application restart and reopens from recents`(initial: UiFlowContext) {
        var application = ApplicationScreen(initial)
        application.assertProjectsOnlyNavigation()

        application.projects.importMatch()
        application.rallies.assertReady()
        application.assertProjectNavigation()

        val created = initial.fixtures.onlyProject()
        assertEquals(
            initial.fixtures.sourceVideo.toAbsolutePath().normalize(),
            Path.of(requireNotNull(created.manifest.sourceVideo)).toAbsolutePath().normalize(),
        )

        val restarted = initial.restartApplication()
        application = ApplicationScreen(restarted)
        application.assertProjectsOnlyNavigation()
        application.projects.assertRecent(created.manifest.id).openRecent(created.manifest.id)
        application.rallies.assertReady()
        application.projects.open().assertCurrentProject(created.manifest.name)
    }

    companion object {
        @JvmField
        @RegisterExtension
        val uiFlow = SwingUiFlowExtension()
    }
}
