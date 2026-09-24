package org.litvin.ui.flow.screens

import org.litvin.ui.flow.fakes.FilePickerOutcome

internal class ProjectsScreen(application: ApplicationScreen) : UserFlowScreen(application) {
    fun open(): ProjectsScreen = apply { open("nav-projects", "projects-import-match") }

    /** Imports the fixture video. A [projectName] replaces the suggested name in the New project dialog. */
    fun importMatch(projectName: String? = null): ProjectsScreen = apply {
        startImport()
        projectName?.let { context.driver.setText("new-project-name", it) }
        context.driver.click("new-project-create")
    }

    fun cancelNewProjectDialog(): ProjectsScreen = apply {
        startImport()
        context.driver.click("new-project-cancel")
    }

    private fun startImport() {
        context.filePicker.scriptSource(FilePickerOutcome.Selected(context.fixtures.sourceVideo.toFile()))
        context.driver.click("projects-import-match")
        assertVisible("new-project-create")
    }

    fun cancelImport(): ProjectsScreen = apply {
        context.filePicker.scriptSource(FilePickerOutcome.Cancelled)
        context.driver.click("projects-import-match")
    }

    fun relinkMissingSource(id: String, replacement: java.nio.file.Path): ProjectsScreen = apply {
        require(id.isNotBlank()) { "project id must not be blank" }
        assertRecent(id)
        context.filePicker.scriptSource(FilePickerOutcome.Selected(replacement.toFile()))
        context.driver.click("projects-open-$id")
        application.eventually("source-video relink picker to be requested") {
            if (context.filePicker.calls.none { it.kind == "source" }) {
                throw AssertionError("Source-video picker calls were ${context.filePicker.calls}")
            }
        }
    }

    fun openRecent(id: String): ProjectsScreen = apply {
        require(id.isNotBlank()) { "project id must not be blank" }
        assertRecent(id)
        context.driver.click("projects-open-$id")
    }

    fun assertRecent(id: String): ProjectsScreen = apply {
        require(id.isNotBlank()) { "project id must not be blank" }
        assertVisible("projects-open-$id")
    }

    fun assertCurrentProject(name: String) {
        application.eventually("current project name to be '$name'") {
            context.driver.requireText("projects-current-name", name)
        }
    }

    fun assertReady() = assertVisible("projects-import-match")

    fun assertUsable(): ProjectsScreen = apply {
        assertVisible("projects-import-match")
        context.driver.requireEnabled("projects-import-match", true)
    }
}
