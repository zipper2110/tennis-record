package org.litvin.ui.flow.screens

import org.litvin.ui.flow.fakes.DialogOutcome
import org.litvin.ui.flow.fakes.FilePickerOutcome
import java.nio.file.Path

internal class ExportScreen(application: ApplicationScreen) : UserFlowScreen(application) {
    fun open(): ExportScreen = apply { open("nav-export", "export-initialize") }

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

    fun assertRenderQueued(destination: Path) {
        val expected = destination.toAbsolutePath().normalize().toString()
        application.eventually("render to be queued for $expected") {
            val outputs = context.renderService.jobs.map { job -> Path.of(job.outputPath).toAbsolutePath().normalize().toString() }
            if (expected !in outputs) throw AssertionError("Queued render outputs were $outputs")
        }
    }
}
