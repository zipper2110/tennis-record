package org.litvin.ui.tabs.crop

import org.junit.jupiter.api.Test
import org.litvin.adjustments.AdjustmentsV1
import java.awt.Component
import java.awt.Container
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CropTransformControlsTest {
    @Test
    fun editingFocusedReadoutDoesNotMutateDocumentDuringNotification() {
        try {
            System.setProperty("java.awt.headless", "true")
        } catch (_: Throwable) {
        }

        SwingUtilities.invokeAndWait {
            val previousFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            val focusManager = TestFocusManager()
            KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager)

            try {
                val changes = mutableListOf<AdjustmentsV1>()
                val controls = CropTransformControls(
                    onChanged = { changes.add(it) },
                    onResetTransform = {},
                )
                val zoomReadout = controls.textFields().first()

                focusManager.focus(zoomReadout)
                try {
                    zoomReadout.text = "12"
                } finally {
                    focusManager.focus(null)
                }

                assertEquals("12", zoomReadout.text)
                assertTrue(changes.any { it.zoom == 0.12f }, "Expected typed zoom value to publish")
            } finally {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previousFocusManager)
            }
        }
    }

    private class TestFocusManager : DefaultKeyboardFocusManager() {
        fun focus(component: Component?) {
            setGlobalFocusOwner(component)
        }
    }

    private fun Component.textFields(): List<JTextField> {
        val own = if (this is JTextField) listOf(this) else emptyList()
        val childMatches = if (this is Container) {
            components.flatMap { it.textFields() }
        } else {
            emptyList()
        }
        return own + childMatches
    }
}
