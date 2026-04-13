package org.litvin

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.stage.Stage
import javafx.stage.FileChooser
import java.io.File

class MainApp : Application() {
    override fun start(primaryStage: Stage) {
        // Try to hint Windows to use the High Performance GPU for Java/this app
        try {
            WindowsGpuPreference.ensureHighPerformancePreference()
        } catch (_: Throwable) { }

        val dispatcher = ProjectsDispatcher()
        val projectsTab = ProjectsTab(dispatcher)
        val markupTab = MarkupTab(MarkupDispatcher())
        val videoTestTab = VideoTestTab()

        // Allow Markup to request re-selection of source video
        markupTab.onRequestSelectSource = {
            dispatcher.onNavigate?.invoke(ProjectsDispatcher.Route.Step1SelectSource)
        }
        // Allow Markup to navigate back to Projects when user clicks the sidebar item
        markupTab.onRequestNavigateProjects = {
            dispatcher.onNavigate?.invoke(ProjectsDispatcher.Route.PROJECTS)
        }
        // Allow Markup to navigate to Export
        markupTab.onRequestNavigateExport = {
            dispatcher.onNavigate?.invoke(ProjectsDispatcher.Route.EXPORT)
        }
        // Allow Markup to navigate to Video Test
        markupTab.onRequestNavigateVideoTest = {
            dispatcher.onNavigate?.invoke(ProjectsDispatcher.Route.VIDEO_TEST)
        }

        val exportTab = ExportTab()

        val root = BorderPane().apply {
            center = projectsTab.view
        }

        // Wire ExportTab navigation callbacks
        exportTab.onRequestNavigateProjects = { dispatcher.onNavigate?.invoke(ProjectsDispatcher.Route.PROJECTS) }
        exportTab.onRequestNavigateMarkup = { dispatcher.onNavigate?.invoke(ProjectsDispatcher.Route.MARKUP) }
        exportTab.onRequestNavigateVideoTest = { dispatcher.onNavigate?.invoke(ProjectsDispatcher.Route.VIDEO_TEST) }
        // Wire VideoTestTab navigation callbacks
        videoTestTab.onRequestNavigateProjects = { dispatcher.onNavigate?.invoke(ProjectsDispatcher.Route.PROJECTS) }
        videoTestTab.onRequestNavigateMarkup = { dispatcher.onNavigate?.invoke(ProjectsDispatcher.Route.MARKUP) }
        videoTestTab.onRequestNavigateExport = { dispatcher.onNavigate?.invoke(ProjectsDispatcher.Route.EXPORT) }

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
                                dispatcher.onNavigate?.invoke(ProjectsDispatcher.Route.MARKUP)
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
                ProjectsDispatcher.Route.MARKUP -> {
                    root.center = markupTab.view
                    primaryStage.title = "Tennis Record — Markup"
                    // Ensure the viewer loads the project's video when entering Markup
                    markupTab.onEnter()
                }
                ProjectsDispatcher.Route.EXPORT -> {
                    root.center = exportTab.view
                    primaryStage.title = "Tennis Record — Export"
                    try { (exportTab as ExportTab).onEnter() } catch (_: Throwable) {}
                }
                ProjectsDispatcher.Route.VIDEO_TEST -> {
                    root.center = videoTestTab.view
                    primaryStage.title = "Tennis Record — Video test"
                }
            }
        }

        val scene = Scene(root, 1200.0, 800.0)
        scene.stylesheets.add(this::class.java.getResource("/styles/app.css")?.toExternalForm())

        primaryStage.title = "Tennis Record — Projects"
        primaryStage.scene = scene
        primaryStage.show()

        // If GPU preference was just applied, let user know a restart may be needed
        try {
            if (WindowsGpuPreference.wasChangeApplied()) {
                DialogUtils.info(
                    title = "GPU preference set",
                    header = "High‑performance GPU preference saved",
                    content = "We set a Windows preference for this app to use the dedicated/external GPU on future launches.\n\n" +
                             "Please restart the application now. If it still uses the integrated GPU, open Windows Graphics Settings → Graphics performance preference, or NVIDIA/AMD control panel, and force the high‑performance GPU for javaw.exe (or your packaged EXE)."
                )
            }
        } catch (_: Throwable) { }
    }
}
