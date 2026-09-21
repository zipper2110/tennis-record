package org.litvin.ui.flow.screens

import org.litvin.ui.flow.fakes.DialogOutcome
import org.litvin.ui.flow.fakes.FilePickerOutcome
import java.nio.file.Path

internal class ExportScreen(application: ApplicationScreen) : UserFlowScreen(application) {
    fun open(): ExportScreen = apply { open("nav-export", "export-initialize") }

    fun configure(
        preset: String,
        resolution: String,
        idleTrim: Boolean,
        favoritesOnly: Boolean,
        scoreboard: Boolean,
        comments: Boolean,
    ): ExportScreen = apply {
        context.driver.select("export-preset", preset)
        context.driver.select("export-resolution", resolution)
        context.driver.setSelected("export-idle-trim", idleTrim)
        context.driver.setSelected("export-favorites-only", favoritesOnly)
        context.driver.setSelected("export-scoreboard", scoreboard)
        context.driver.setSelected("export-comments", comments)
    }

    fun setComments(enabled: Boolean): ExportScreen = apply {
        context.driver.setSelected("export-comments", enabled)
    }

    fun assertComments(enabled: Boolean): ExportScreen = apply {
        application.eventually("export comments selection to be $enabled") {
            context.driver.requireSelected("export-comments", enabled)
        }
    }

    fun setIdleTrim(enabled: Boolean): ExportScreen = apply {
        context.driver.setSelected("export-idle-trim", enabled)
    }

    fun selectResolution(resolution: String): ExportScreen = apply {
        context.driver.select("export-resolution", resolution)
    }

    fun selectBitrateQuality(quality: String): ExportScreen = apply {
        context.driver.select("export-preset", quality)
    }

    fun enableFavoritesOnlyAndDismissUnavailable(): ExportScreen = apply {
        context.dialogs.showNextAsRealModal()
        context.driver.click("export-favorites-only")
        application.eventually("favorite-only validation dialog to be dismissed") {
            context.driver.dismissDialog("Favorite export unavailable", "OK")
        }
    }

    fun initialize(destination: Path): ExportScreen = apply {
        context.filePicker.scriptExport(FilePickerOutcome.Selected(destination.toFile()))
        context.dialogs.script(DialogOutcome.RealModal)
        context.driver.click("export-initialize")
        application.eventually("render initialized dialog to be dismissed") {
            context.driver.dismissDialog("Info", "OK")
        }
    }

    fun assertInitializeEnabled(enabled: Boolean) {
        application.eventually("export initialization enabled state to become $enabled") {
            context.driver.requireEnabled("export-initialize", enabled)
        }
    }

    fun assertInitializeExplainsBlockedRender(explanation: String): ExportScreen = apply {
        assertInitializeEnabled(true)
        context.driver.requireAccessibleDescription("export-initialize", explanation)
        context.dialogs.showNextAsRealModal()
        context.driver.click("export-initialize")
        application.eventually("blocked render dialog to be dismissed") {
            context.driver.dismissDialog("Cannot start render", "OK")
        }
        val last = context.dialogs.calls.last()
        if (last.title != "Cannot start render" || !last.message.contains(explanation)) {
            throw AssertionError("Blocked render dialog was $last")
        }
    }

    fun assertExactlyOneRenderQueued(): ExportScreen = apply {
        application.eventually("exactly one render job to be queued") {
            if (context.renderService.jobs.size != 1) {
                throw AssertionError("Queued render jobs were ${context.renderService.jobs}")
            }
        }
    }

    fun assertResponsive(): ExportScreen = apply {
        assertVisible("export-initialize")
        assertInitializeEnabled(true)
    }

    fun assertRenderQueued(destination: Path) {
        val expected = destination.toAbsolutePath().normalize().toString()
        application.eventually("render to be queued for $expected") {
            val outputs = context.renderService.jobs.map { job -> Path.of(job.outputPath).toAbsolutePath().normalize().toString() }
            if (expected !in outputs) throw AssertionError("Queued render outputs were $outputs")
        }
    }
}
