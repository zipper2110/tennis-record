package org.litvin

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.stage.Stage

class MainApp : Application() {
    override fun start(primaryStage: Stage) {
        val dispatcher = ProjectsDispatcher()
        val projectsTab = ProjectsTab(dispatcher)
        val markupTab = MarkupTab(MarkupDispatcher())

        val root = BorderPane().apply {
            center = projectsTab.view
        }

        dispatcher.onNavigate = { route ->
            when (route) {
                ProjectsDispatcher.Route.PROJECTS -> {
                    root.center = projectsTab.view
                    primaryStage.title = "Tennis Record — Projects"
                }
                ProjectsDispatcher.Route.Step1SelectSource -> {
                    // For now, keep showing Projects tab; selecting source not implemented yet
                    root.center = projectsTab.view
                    primaryStage.title = "Tennis Record — Projects"
                }
                ProjectsDispatcher.Route.Step2Trim -> {
                    root.center = markupTab.view
                    primaryStage.title = "Tennis Record — Markup"
                }
            }
        }

        val scene = Scene(root, 1200.0, 800.0)
        scene.stylesheets.add(this::class.java.getResource("/styles/app.css")?.toExternalForm())

        primaryStage.title = "Tennis Record — Projects"
        primaryStage.scene = scene
        primaryStage.show()
    }
}
