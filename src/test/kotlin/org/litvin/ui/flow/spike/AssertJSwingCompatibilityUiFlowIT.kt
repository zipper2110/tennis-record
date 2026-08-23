package org.litvin.ui.flow.spike

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.litvin.ui.flow.driver.RobotSwingDriver
import org.litvin.ui.flow.harness.SwingTestDiagnostics
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.FutureTask
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.UIManager

class AssertJSwingCompatibilityUiFlowIT {

    @Test
    fun `driver ignores a disposed duplicate hierarchy and drives the current FlatLaf window`() {
        assertEquals("true", System.getProperty("tennis.record.uiFlow"))
        assertEquals(17, Runtime.version().feature(), "compatibility spike must run on JDK 17")

        lateinit var frame: SpikeWindow
        RobotSwingDriver().use { driver ->
            val priorFrame = SpikeWindow.show()
            val priorFrameDisposed = onEdt {
                priorFrame.dispose()
                !priorFrame.isDisplayable
            }
            assertTrue(priorFrameDisposed, "first lifecycle must be disposed before the second opens")

            frame = SpikeWindow.show()
            assertEquals(
                "com.formdev.flatlaf.FlatDarkLaf",
                onEdt { UIManager.getLookAndFeel().javaClass.name },
            )
            val artifactDir = Files.createTempDirectory(
                Files.createDirectories(
                    Path.of("target", "ui-test-artifacts", javaClass.simpleName),
                ),
                "compatibility-",
            )

            try {
                driver.requireShowing(SpikeWindow.INPUT_NAME)
                driver.requireEnabled(SpikeWindow.SUBMIT_NAME, true)
                driver.setSlider(SpikeWindow.SLIDER_NAME, 7)
                driver.requireText(SpikeWindow.RESULT_NAME, "slider=7")
                driver.select(SpikeWindow.CHOICE_NAME, "beta")
                driver.requireText(SpikeWindow.RESULT_NAME, "choice=beta")
                driver.setText(SpikeWindow.INPUT_NAME, "JDK17 FlatLaf")
                driver.requireText(SpikeWindow.INPUT_NAME, "JDK17 FlatLaf")
                driver.click(SpikeWindow.SUBMIT_NAME)

                driver.requireText(SpikeWindow.RESULT_NAME, "JDK17 FlatLaf")
                driver.dismissDialog(SpikeWindow.DIALOG_TITLE, SpikeWindow.DIALOG_BUTTON_TEXT)

                driver.press(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK))
                driver.requireText(SpikeWindow.RESULT_NAME, SpikeWindow.SHORTCUT_RESULT)

                val failure = assertThrows(AssertionError::class.java) {
                    driver.requireText(SpikeWindow.RESULT_NAME, "deliberate diagnostic probe")
                }
                SwingTestDiagnostics.capture(frame, failure, artifactDir)

                assertArtifactContains(artifactDir.resolve("failure.txt"), "deliberate diagnostic probe")
                assertArtifactContains(artifactDir.resolve("component-tree.txt"), "name=${SpikeWindow.RESULT_NAME}")
                assertArtifactContains(artifactDir.resolve("component-tree.txt"), "text=${SpikeWindow.SHORTCUT_RESULT}")
                assertArtifactContains(artifactDir.resolve("windows.txt"), "title=${SpikeWindow.TITLE}")
                assertTrue(
                    Files.list(artifactDir).use { files ->
                        files.anyMatch { path ->
                            path.fileName.toString().endsWith(".png") && Files.size(path) > 0
                        }
                    },
                    "diagnostics must include a non-empty PNG for the showing spike window",
                )
            } catch (failure: Throwable) {
                try {
                    SwingTestDiagnostics.capture(frame, failure, artifactDir)
                } catch (diagnosticFailure: Throwable) {
                    failure.addSuppressed(diagnosticFailure)
                }
                throw failure
            }
        }

        val cleanup = onEdt {
            CleanupSnapshot(
                frameDisposed = !frame.isDisplayable,
                noShowingWindows = Window.getWindows().none(Window::isShowing),
            )
        }
        assertTrue(cleanup.frameDisposed, "driver cleanup must dispose the spike frame")
        assertTrue(cleanup.noShowingWindows, "driver cleanup must leave no showing AWT windows")
    }

    private fun assertArtifactContains(path: Path, expected: String) {
        assertTrue(Files.isRegularFile(path), "expected diagnostic artifact $path")
        assertTrue(Files.size(path) > 0, "expected non-empty diagnostic artifact $path")
        assertTrue(
            Files.readString(path).contains(expected),
            "expected $path to contain '$expected'",
        )
    }

    private fun <T> onEdt(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return action()
        val task = FutureTask(action)
        SwingUtilities.invokeAndWait(task)
        return task.get()
    }

    private data class CleanupSnapshot(
        val frameDisposed: Boolean,
        val noShowingWindows: Boolean,
    )
}
