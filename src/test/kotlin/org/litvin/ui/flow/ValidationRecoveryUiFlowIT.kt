package org.litvin.ui.flow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.litvin.points.EdlIO
import org.litvin.projects.ManifestIO
import org.litvin.ui.flow.harness.SwingUiFlowExtension
import org.litvin.ui.flow.harness.UiFlowContext
import org.litvin.ui.flow.screens.ApplicationScreen
import java.nio.file.Path
import kotlin.test.assertEquals

class ValidationRecoveryUiFlowIT {
    @Test
    fun `canceling match import leaves Projects active and usable`(context: UiFlowContext) {
        ApplicationScreen(context).projects.open()
            .cancelImport()
            .assertUsable()
    }

    @Test
    @Disabled("Deferred to packaged/native smoke: Windows foreground focus can swallow the Robot project-card click")
    fun `missing source can be relinked and opens Points`(context: UiFlowContext) {
        val project = context.fixtures.missingSourceProject()
        val application = ApplicationScreen(context)

        application.projects.open()
            .relinkMissingSource(project.manifest.id, context.fixtures.sourceVideo)
        application.points.assertReady()

        application.eventually("project manifest source to be repaired") {
            val repaired = ManifestIO.read(project.manifestFile.toString())
            assertEquals(
                context.fixtures.sourceVideo.toAbsolutePath().normalize(),
                Path.of(requireNotNull(repaired.sourceVideo)).toAbsolutePath().normalize(),
            )
        }
    }

    @Test
    fun `empty EDL explains blocked idle-trim export and full render recovers`(context: UiFlowContext) {
        val project = context.fixtures.emptyProject()
        val application = ApplicationScreen(context)

        application.projects.open().openRecent(project.manifest.id)
        application.export.open()
            .setIdleTrim(true)
            .assertInitializeExplainsBlockedRender("EDL is empty/invalid")
            .setIdleTrim(false)
            .assertInitializeEnabled(true)
    }

    @Test
    fun `favorite-only validation dialog can be dismissed and export stays responsive`(context: UiFlowContext) {
        val project = context.fixtures.exportReadyProject("No Favorites Match")
        val seededEdl = EdlIO.readForProjectDir(project.directory.toString())
        val withoutFavorites = seededEdl.copy(
            points = seededEdl.points.map { it.copy(favorite = false) },
        )
        EdlIO.writeForProjectDir(project.directory.toString(), withoutFavorites)
        val application = ApplicationScreen(context)

        application.projects.open().openRecent(project.manifest.id)
        application.export.open()
            .setIdleTrim(true)
            .enableFavoritesOnlyAndDismissUnavailable()
            .assertResponsive()
    }

    companion object {
        @JvmField
        @RegisterExtension
        val uiFlow = SwingUiFlowExtension()
    }
}
