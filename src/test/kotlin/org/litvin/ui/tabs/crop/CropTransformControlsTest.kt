package org.litvin.ui.tabs.crop

import org.junit.jupiter.api.Test
import org.litvin.adjustments.AdjustmentsV1
import java.awt.Component
import java.awt.Container
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.math.abs
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

    @Test
    fun rotationSlidersCoverOneEightyAndFiveDegrees() {
        headless()

        SwingUtilities.invokeAndWait {
            val controls = CropTransformControls(onChanged = {}, onResetTransform = {})
            val coarse = controls.slider("crop-rotation")
            val fine = controls.slider("crop-rotation-fine")

            assertEquals(-360 to 360, coarse.minimum to coarse.maximum, "Rotation spans -180..180 in half degrees")
            assertEquals(-50 to 50, fine.minimum to fine.maximum, "Fine rotation spans -5..5 in tenths of a degree")
        }
    }

    @Test
    fun fineRotationNudgesTheCoarseAngle() {
        headless()

        SwingUtilities.invokeAndWait {
            val changes = mutableListOf<AdjustmentsV1>()
            val controls = CropTransformControls(onChanged = { changes.add(it) }, onResetTransform = {})

            controls.slider("crop-rotation").value = 30
            controls.slider("crop-rotation-fine").value = 3

            assertClose(15.3f, changes.last().rotationDeg)
        }
    }

    @Test
    fun renderSplitsAnExternalAngleAcrossBothSliders() {
        headless()

        SwingUtilities.invokeAndWait {
            val changes = mutableListOf<AdjustmentsV1>()
            val controls = CropTransformControls(onChanged = { changes.add(it) }, onResetTransform = {})

            controls.render(AdjustmentsV1(rotationDeg = 12.3f))

            assertEquals(25, controls.slider("crop-rotation").value, "Coarse slider takes the nearest half degree")
            assertEquals(-2, controls.slider("crop-rotation-fine").value, "Fine slider takes the remainder")
            assertTrue(changes.isEmpty(), "Rendering an incoming angle must not publish a change")
        }
    }

    private fun headless() {
        try {
            System.setProperty("java.awt.headless", "true")
        } catch (_: Throwable) {
        }
    }

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) <= 0.0001f, "Expected $expected but was $actual")
    }

    private fun Component.slider(name: String): JSlider =
        sliders().firstOrNull { it.name == name } ?: throw AssertionError("No slider named $name")

    private fun Component.sliders(): List<JSlider> {
        val own = if (this is JSlider) listOf(this) else emptyList()
        val childMatches = if (this is Container) components.flatMap { it.sliders() } else emptyList()
        return own + childMatches
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
