package org.litvin

import javafx.stage.FileChooser
import java.io.File

/**
 * Dispatcher for Projects tab primary actions.
 * In later steps, these will open dialogs and navigate to flows.
 */
class ProjectsDispatcher {
    companion object {
        private var lastVideoDir: File? = null
        var currentProjectPath: String? = null
            private set
    }

    fun onNewProjectClicked() {
        println("[INFO] New Project clicked")
        // Step 0.3: pick a source video, auto-create project folder under Documents, write <projectName>.trproj
        try {
            // 1) Ask user to pick a video file
            val chooser = FileChooser().apply {
                title = "Select Source Video"
                extensionFilters.addAll(
                    FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.mov", "*.mkv", "*.avi", "*.m4v", "*.wmv"),
                    FileChooser.ExtensionFilter("All Files", "*.*")
                )
                val initial = lastVideoDir ?: run {
                    val userHome = System.getProperty("user.home") ?: "."
                    val videos = File(userHome, "Videos")
                    val docs = File(userHome, "Documents")
                    when {
                        videos.exists() -> videos
                        docs.exists() -> docs
                        else -> File(userHome)
                    }
                }
                if (initial.exists()) initialDirectory = initial
            }
            val selected: File = chooser.showOpenDialog(null) ?: run {
                println("[INFO] New Project canceled (no file selected)")
                return
            }
            lastVideoDir = selected.parentFile

            // 2) Determine projects root under Documents
            val userHome = System.getProperty("user.home") ?: "."
            val projectsRoot = File(userHome, "Documents\\TennisRecord\\Projects").apply { if (!exists()) mkdirs() }

            // 3) Derive project name from selected video base name (without extension)
            val baseName = selected.name.substringBeforeLast('.')
            var projectName = baseName
            var projectDir = File(projectsRoot, projectName)
            var suffix = 2
            while (projectDir.exists()) {
                projectName = "$baseName (${suffix++})"
                projectDir = File(projectsRoot, projectName)
            }
            if (!projectDir.mkdirs()) {
                // might already exist if created in loop, ensure directory present
                projectDir.mkdirs()
            }

            // 4) Create manifest <projectName>.trproj inside the folder
            val now = ManifestIO.nowIsoUtc()
            val manifest = ProjectManifestV1(
                id = java.util.UUID.randomUUID().toString(),
                name = projectName,
                createdAt = now,
                lastOpenedAt = now,
                version = 1,
                sourceVideo = selected.absolutePath
            )
            val manifestPath = File(projectDir, "$projectName.trproj").absolutePath
            ManifestIO.write(manifestPath, manifest)
            println("[INFO] Created project at: ${projectDir.absolutePath}")
            println("[INFO] Created manifest: $manifestPath")
            currentProjectPath = manifestPath

            // 5) Open the project and navigate to Step 2 (Trim)
            // Navigator not implemented yet; log intent for now.
            println("[NAVIGATE] -> Step2Trim (project opened)")
        } catch (t: Throwable) {
            System.err.println("[ERROR] Failed to create project: ${t.message}")
            t.printStackTrace()
        }
    }

    fun openProject(manifestPath: String) {
        try {
            println("[INFO] Opening project manifest: $manifestPath")
            val manifest = ManifestIO.read(manifestPath)
            if (manifest.version != 1) {
                throw IllegalArgumentException("Unsupported manifest version: ${manifest.version}")
            }
            val now = ManifestIO.nowIsoUtc()
            val updated = manifest.copy(lastOpenedAt = now)
            ManifestIO.write(manifestPath, updated)
            currentProjectPath = manifestPath
            val hasVideo = !updated.sourceVideo.isNullOrBlank()
            println("[INFO] Project loaded: ${updated.name} (id=${updated.id})")
            println("[NAVIGATE] -> ${if (hasVideo) "Step2Trim" else "Step1SelectSource"}")
        } catch (t: Throwable) {
            System.err.println("[ERROR] Failed to open project '$manifestPath': ${t.message}")
            t.printStackTrace()
        }
    }

    fun onOpenProjectClicked() {
        // Adjusted per 0.4 per 0.3: opening via list click in ProjectsTab; keep for future file-chooser flow.
        println("[INFO] Use the projects list to open an existing project.")
    }
}
