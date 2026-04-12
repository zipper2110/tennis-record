package org.litvin

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.stage.Stage
import javafx.stage.FileChooser
import java.io.File

class MainApp : Application() {
    override fun start(primaryStage: Stage) {
        val dispatcher = ProjectsDispatcher()
        val projectsTab = ProjectsTab(dispatcher)
        val markupTab = MarkupTab(MarkupDispatcher())

        // Allow Markup to request re-selection of source video
        markupTab.onRequestSelectSource = {
            dispatcher.onNavigate?.invoke(ProjectsDispatcher.Route.Step1SelectSource)
        }
        // Allow Markup to navigate back to Projects when user clicks the sidebar item
        markupTab.onRequestNavigateProjects = {
            dispatcher.onNavigate?.invoke(ProjectsDispatcher.Route.PROJECTS)
        }

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
                    // Implement Select Source step: pick video and update manifest, then go to Markup
                    root.center = projectsTab.view
                    primaryStage.title = "Tennis Record — Projects"
                    val manifestPath = ProjectsDispatcher.currentProjectPath
                    if (manifestPath != null) {
                        try {
                            val chooser = FileChooser().apply {
                                title = "Select Source Video"
                                extensionFilters.addAll(
                                    FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.mov", "*.mkv", "*.avi", "*.m4v", "*.wmv"),
                                    FileChooser.ExtensionFilter("All Files", "*.*")
                                )
                                val initial = File(manifestPath).parentFile
                                if (initial.exists()) initialDirectory = initial
                            }
                            val selected = chooser.showOpenDialog(primaryStage)
                            if (selected != null) {
                                // Update manifest with selected video and navigate to Trim
                                val mf = ManifestIO.read(manifestPath)
                                val updated = mf.copy(sourceVideo = selected.absolutePath, lastOpenedAt = ManifestIO.nowIsoUtc())
                                ManifestIO.write(manifestPath, updated)
                                dispatcher.onNavigate?.invoke(ProjectsDispatcher.Route.Step2Trim)
                            } else {
                                // Stay on Projects view if user cancels
                                println("[INFO] Select Source canceled by user")
                            }
                        } catch (t: Throwable) {
                            System.err.println("[ERROR] Select Source failed: ${t.message}")
                            t.printStackTrace()
                        }
                    }
                }
                ProjectsDispatcher.Route.Step2Trim -> {
                    root.center = markupTab.view
                    primaryStage.title = "Tennis Record — Markup"
                    // Ensure the viewer loads the project's video when entering Markup
                    (markupTab as MarkupTab).onEnter()
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
