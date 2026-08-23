package org.litvin.ui.flow.harness

import java.awt.Component
import java.awt.Container
import java.awt.Dialog
import java.awt.Frame
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JProgressBar
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

object SwingTestDiagnostics {

    fun capture(frame: Frame, failure: Throwable, artifactDir: Path) {
        Files.createDirectories(artifactDir)
        val snapshot = onEdt {
            DiagnosticSnapshot(
                componentTree = buildString { appendComponent(frame, 0) },
                windows = buildString {
                    Window.getWindows().forEachIndexed { index, window ->
                        appendWindow(index, window)
                    }
                },
                screenshots = Window.getWindows()
                    .filter(Window::isShowing)
                    .mapIndexed { index, window -> screenshot(index, window) },
            )
        }

        Files.writeString(artifactDir.resolve("failure.txt"), stackTrace(failure))
        Files.writeString(artifactDir.resolve("component-tree.txt"), snapshot.componentTree)
        Files.writeString(artifactDir.resolve("windows.txt"), snapshot.windows)
        snapshot.screenshots.forEach { screenshot ->
            check(ImageIO.write(screenshot.image, "png", artifactDir.resolve(screenshot.fileName).toFile())) {
                "No PNG writer available for ${screenshot.fileName}"
            }
        }
    }

    private fun StringBuilder.appendComponent(component: Component, depth: Int) {
        append("  ".repeat(depth))
        append("class=").append(component.javaClass.name)
        append(" name=").append(component.name)
        append(" visible=").append(component.isVisible)
        append(" showing=").append(component.isShowing)
        append(" enabled=").append(component.isEnabled)
        componentText(component)?.let { text ->
            append(" text=").append(singleLine(text))
        }
        appendLine()

        if (component is Container) {
            component.components.forEach { child -> appendComponent(child, depth + 1) }
        }
    }

    private fun StringBuilder.appendWindow(index: Int, window: Window) {
        append("index=").append(index)
        append(" class=").append(window.javaClass.name)
        append(" name=").append(window.name)
        when (window) {
            is Frame -> append(" title=").append(singleLine(window.title))
            is Dialog -> {
                append(" title=").append(singleLine(window.title))
                append(" modal=").append(window.isModal)
            }
        }
        append(" visible=").append(window.isVisible)
        append(" showing=").append(window.isShowing)
        append(" displayable=").append(window.isDisplayable)
        append(" active=").append(window.isActive)
        append(" focused=").append(window.isFocused)
        append(" enabled=").append(window.isEnabled)
        appendLine()
    }

    private fun screenshot(index: Int, window: Window): WindowScreenshot {
        val width = window.width.coerceAtLeast(1)
        val height = window.height.coerceAtLeast(1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            window.paintAll(graphics)
        } finally {
            graphics.dispose()
        }
        val type = window.javaClass.simpleName.ifBlank { "Window" }
        return WindowScreenshot("window-%02d-%s.png".format(index, type), image)
    }

    private fun componentText(component: Component): String? =
        when (component) {
            is JTextComponent -> component.text
            is JLabel -> component.text
            is AbstractButton -> component.text
            is JComboBox<*> -> component.selectedItem?.toString()
            is JProgressBar -> component.string.takeIf { component.isStringPainted }
            else -> null
        }

    private fun singleLine(value: String): String =
        value.replace("\r", "\\r").replace("\n", "\\n")

    private fun stackTrace(failure: Throwable): String =
        StringWriter().also { writer -> failure.printStackTrace(PrintWriter(writer)) }.toString()

    private fun <T> onEdt(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return action()
        var value: T? = null
        var thrown: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                value = action()
            } catch (failure: Throwable) {
                thrown = failure
            }
        }
        thrown?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private data class DiagnosticSnapshot(
        val componentTree: String,
        val windows: String,
        val screenshots: List<WindowScreenshot>,
    )

    private data class WindowScreenshot(
        val fileName: String,
        val image: BufferedImage,
    )
}
