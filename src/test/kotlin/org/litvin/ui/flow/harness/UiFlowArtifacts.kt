package org.litvin.ui.flow.harness

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object UiFlowArtifacts {
    fun capture(context: UiFlowContext, failure: Throwable) {
        val artifactDir = context.artifactDirectory
        Files.createDirectories(artifactDir)
        SwingTestDiagnostics.capture(context.application.frame, failure, artifactDir)
        Files.writeString(
            artifactDir.resolve("fake-calls.txt"),
            buildString {
                appendLine("media:")
                context.mediaPlayers.calls.forEach { appendLine(it) }
                appendLine("render:")
                context.renderService.calls.forEach { appendLine(it) }
                appendLine("picker:")
                context.filePicker.calls.forEach { appendLine(it) }
                appendLine("dialogs:")
                context.dialogs.calls.forEach { appendLine(it) }
            },
        )
        Files.writeString(artifactDir.resolve("threads.txt"), threadDump())
        copyDirectoryIfPresent(context.paths.logs.toPath(), artifactDir.resolve("logs"))
    }

    fun retainWorkspace(context: UiFlowContext) {
        copyDirectory(context.workspace, context.artifactDirectory.resolve("workspace"))
    }

    private fun threadDump(): String = buildString {
        Thread.getAllStackTraces()
            .entries
            .sortedBy { it.key.name }
            .forEach { (thread, stack) ->
                append('"').append(thread.name).append('"')
                append(" state=").append(thread.state)
                append(" daemon=").append(thread.isDaemon)
                appendLine()
                stack.forEach { frame -> append("    at ").appendLine(frame) }
                appendLine()
            }
    }

    private fun copyDirectoryIfPresent(source: Path, target: Path) {
        if (Files.exists(source)) copyDirectory(source, target)
    }

    private fun copyDirectory(source: Path, target: Path) {
        Files.walk(source).use { entries ->
            entries.forEach { entry ->
                val destination = target.resolve(source.relativize(entry).toString())
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(destination)
                } else {
                    Files.createDirectories(destination.parent)
                    Files.copy(entry, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                }
            }
        }
    }
}
