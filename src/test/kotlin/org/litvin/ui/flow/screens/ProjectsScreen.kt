package org.litvin.ui.flow.screens

import org.litvin.ui.flow.fakes.FilePickerOutcome

internal class ProjectsScreen(application: ApplicationScreen) : UserFlowScreen(application) {
    fun open(): ProjectsScreen = apply { open("nav-projects", "projects-import-match") }

    fun importMatch(): ProjectsScreen = apply {
        context.filePicker.scriptSource(FilePickerOutcome.Selected(context.fixtures.sourceVideo.toFile()))
        context.driver.click("projects-import-match")
    }

    fun openRecent(id: String): ProjectsScreen = apply {
        require(id.isNotBlank()) { "project id must not be blank" }
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
}
