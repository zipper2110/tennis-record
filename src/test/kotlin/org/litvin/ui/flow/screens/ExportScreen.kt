package org.litvin.ui.flow.screens

import org.litvin.ui.flow.fakes.DialogOutcome
import org.litvin.ui.flow.fakes.FilePickerOutcome
import java.nio.file.Path

internal class ExportScreen(application: ApplicationScreen) : UserFlowScreen(application) {
    fun open(): ExportScreen = apply { open("nav-export", "export-initialize") }

    /** [content] is "full", "points" or "favorites". */
    fun configure(
        content: String,
        scoreboard: Boolean,
        comments: Boolean,
    ): ExportScreen = apply {
        selectContent(content)
        context.driver.setSelected("export-scoreboard", scoreboard)
        context.driver.setSelected("export-comments", comments)
    }

    fun selectContent(content: String): ExportScreen = apply {
        context.driver.setSelected("export-content-$content", true)
    }

    fun setComments(enabled: Boolean): ExportScreen = apply {
        context.driver.setSelected("export-comments", enabled)
    }

    fun assertComments(enabled: Boolean): ExportScreen = apply {
        application.eventually("export comments selection to be $enabled") {
            context.driver.requireSelected("export-comments", enabled)
        }
    }

    fun setIdleTrim(enabled: Boolean): ExportScreen = selectContent(if (enabled) "points" else "full")

    /** [preset] is "best", "balanced" or "fast". */
    fun selectSimpleQuality(preset: String): ExportScreen = apply {
        context.driver.setSelected("export-mode-simple", true)
        context.driver.setSelected("export-quality-$preset", true)
    }

    /** [card] is the end of the card name, for example "export-fps-24" takes "24". */
    fun selectAdvancedFrameRate(card: String): ExportScreen = apply {
        context.driver.setSelected("export-mode-advanced", true)
        context.driver.setSelected("export-fps-$card", true)
    }

    fun assertAdvancedCardEnabled(cardName: String, enabled: Boolean): ExportScreen = apply {
        context.driver.setSelected("export-mode-advanced", true)
        application.eventually("$cardName enabled state to be $enabled") {
            context.driver.requireEnabled(cardName, enabled)
        }
    }

    fun enableFavoritesOnlyAndDismissUnavailable(): ExportScreen = apply {
        context.dialogs.showNextAsRealModal()
        context.driver.click("export-content-favorites")
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
            context.driver.dismissDialog("Cannot start export", "OK")
        }
        val last = context.dialogs.calls.last()
        if (last.title != "Cannot start export" || !last.message.contains(explanation)) {
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
