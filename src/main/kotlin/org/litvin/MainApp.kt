package org.litvin

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.stage.Stage

class MainApp : Application() {
    override fun start(primaryStage: Stage) {
        val dispatcher = ProjectsDispatcher()
        val projectsTab = ProjectsTab(dispatcher)

        val root = BorderPane().apply {
            center = projectsTab.view
        }

        val scene = Scene(root, 1200.0, 800.0)
        scene.stylesheets.add(this::class.java.getResource("/styles/app.css")?.toExternalForm())

        primaryStage.title = "Tennis Record — Projects"
        primaryStage.scene = scene
        primaryStage.show()
    }
}
