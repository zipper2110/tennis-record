package org.litvin.ui.flow.driver

import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JSlider
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SliderMouseControllerTest {
    @Test
    fun `semantic drag geometry reaches brightness and crop targets`() {
        assertSemanticDrag(
            minimum = -80,
            maximum = 74,
            initial = 0,
            target = 20,
        )
        assertSemanticDrag(
            minimum = 10,
            maximum = 400,
            initial = 100,
            target = 125,
        )
    }

    @Test
    fun `dense rotation geometry reaches exact values through bounded real key correction`() {
        assertSemanticControllerDrag(initial = 30, target = 0)
        assertSemanticControllerDrag(initial = 0, target = 15)
        assertSemanticControllerDrag(initial = 0, target = 30)
    }

    @Test
    fun `controller uses one small directional correction then settles`() {
        var value = 100
        val pressed = mutableListOf<Int>()
        var settles = 0
        val controller = SliderMouseController(
            dragTo = { value = 124 },
            readValue = { value },
            pressKey = { key ->
                pressed += key
                value += if (key == KeyEvent.VK_RIGHT) 1 else -1
            },
            awaitValue = { expected ->
                settles += 1
                assertEquals(expected, value)
            },
        )

        controller.setValue(target = 125, minimum = 10, maximum = 400)

        assertEquals(listOf(KeyEvent.VK_RIGHT), pressed)
        assertEquals(1, settles)
    }

    @Test
    fun `controller refuses a long keyboard fallback after drag`() {
        val pressed = mutableListOf<Int>()
        val controller = SliderMouseController(
            dragTo = {},
            readValue = { 53 },
            pressKey = pressed::add,
            awaitValue = {},
        )

        val failure = assertFailsWith<AssertionError> {
            controller.setValue(target = 125, minimum = 10, maximum = 400)
        }

        assertEquals(emptyList(), pressed)
        assertEquals(
            "Slider drag landed at 53, too far from target 125 for a bounded keyboard correction",
            failure.message,
        )
    }

    private fun assertSemanticDrag(minimum: Int, maximum: Int, initial: Int, target: Int) {
        var actual = Int.MIN_VALUE
        SwingUtilities.invokeAndWait {
            val slider = JSlider(minimum, maximum, initial).apply {
                setSize(360, 30)
            }
            val image = BufferedImage(slider.width, slider.height, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            try {
                slider.paint(graphics)
            } finally {
                graphics.dispose()
            }
            val gesture = SliderDragGeometry.gesture(slider, target)
            dispatchDrag(slider, gesture)
            actual = slider.value
        }

        assertEquals(target, actual)
    }

    private fun assertSemanticControllerDrag(initial: Int, target: Int) {
        var actual = Int.MIN_VALUE
        SwingUtilities.invokeAndWait {
            val slider = JSlider(-360, 360, initial).apply {
                setSize(369, 30)
            }
            val image = BufferedImage(slider.width, slider.height, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            try {
                slider.paint(graphics)
            } finally {
                graphics.dispose()
            }
            val controller = SliderMouseController(
                dragTo = { requested -> dispatchDrag(slider, SliderDragGeometry.gesture(slider, requested)) },
                readValue = { slider.value },
                pressKey = { key -> invokeBoundAction(slider, KeyStroke.getKeyStroke(key, 0)) },
                awaitValue = { expected -> assertEquals(expected, slider.value) },
            )

            controller.setValue(target, slider.minimum, slider.maximum)
            actual = slider.value
        }

        assertEquals(target, actual)
    }

    private fun dispatchDrag(slider: JSlider, gesture: SliderDragGesture) {
        slider.dispatchEvent(mouseEvent(slider, MouseEvent.MOUSE_PRESSED, gesture.start.x, gesture.start.y))
        slider.dispatchEvent(
            mouseEvent(
                slider,
                MouseEvent.MOUSE_DRAGGED,
                gesture.target.x,
                gesture.target.y,
                InputEvent.BUTTON1_DOWN_MASK,
                MouseEvent.NOBUTTON,
            )
        )
        slider.dispatchEvent(mouseEvent(slider, MouseEvent.MOUSE_RELEASED, gesture.target.x, gesture.target.y))
    }

    private fun invokeBoundAction(slider: JSlider, keyStroke: KeyStroke) {
        val actionKey = slider.getInputMap(JComponent.WHEN_FOCUSED).get(keyStroke)
        val action = slider.actionMap.get(actionKey)
        checkNotNull(action) { "No slider action for $keyStroke (mapped key: $actionKey)" }
        action.actionPerformed(ActionEvent(slider, ActionEvent.ACTION_PERFORMED, actionKey.toString()))
    }

    private fun mouseEvent(
        slider: JSlider,
        id: Int,
        x: Int,
        y: Int,
        modifiers: Int = 0,
        button: Int = MouseEvent.BUTTON1,
    ): MouseEvent = MouseEvent(
        slider,
        id,
        System.currentTimeMillis(),
        modifiers,
        x,
        y,
        1,
        false,
        button,
    )
}
