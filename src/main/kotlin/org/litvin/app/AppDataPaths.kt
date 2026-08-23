package org.litvin.app

import org.litvin.ApplicationLayout
import java.io.File

data class AppDataPaths(val root: File) {
    val projects: File = root.resolve("projects")
    val completedRenders: File = root.resolve("completed-renders.json")
    val logs: File = root.resolve("logs")
    val temporary: File = root.resolve("tmp")

    companion object {
        fun production(): AppDataPaths = AppDataPaths(ApplicationLayout.current().appDataDirectory)
    }
}
