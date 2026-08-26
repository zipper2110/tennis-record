package org.litvin.ui.flow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.litvin.scoring.Outcome
import org.litvin.ui.flow.harness.SwingUiFlowExtension
import org.litvin.ui.flow.harness.UiFlowContext
import org.litvin.ui.flow.screens.ApplicationScreen

class EditingAcrossTabsUiFlowIT {
    @Test
    fun `editing a point across tabs persists without native media or processes`(context: UiFlowContext) {
        val project = context.fixtures.emptyProject()
        val application = ApplicationScreen(context)

        application.projects.open()
            .assertRecent(project.manifest.id)
            .openRecent(project.manifest.id)

        application.rallies.assertReady()
        application.rallies.markPoint(1_000, 2_000)
        val pointId = application.rallies.assertSinglePointPersisted(project.directory, 1_000, 2_000)

        application.colors.open()
            .setBrightness(20)
            .togglePlayback()
            .assertColorEditPersisted(project.directory, brightness = 1.2f)

        application.crop.open()
            .setZoomPercent(125)
            .setRotationDegrees(15f)

        application.scoring.open()
        application.crop.assertTransformPersisted(
            project.directory,
            brightness = 1.2f,
            zoom = 1.25f,
            rotationDegrees = 15f,
        )
        application.scoring.awardPointToPlayer1()
            .assertOutcomePersisted(project.directory, pointId, Outcome.P1)
        application.scoring.assertVisibleScore("15")

        context.mediaPlayers.assertEditingFlowEvents()
    }

    companion object {
        @JvmField
        @RegisterExtension
        val uiFlow = SwingUiFlowExtension()
    }
}
