package org.litvin.ui.flow.driver

import java.awt.Component
import java.awt.Container
import java.awt.Dialog
import java.awt.EventQueue
import java.awt.Point
import java.awt.Robot
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.time.Duration
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JSlider
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

class RobotSwingDriver : SwingUiDriver {
    private val robot = Robot().apply {
        autoDelay = EVENT_DELAY_MILLIS
        isAutoWaitForIdle = true
    }

    override fun click(name: String) {
        val component = requireComponent(name)
        requireState(component.isShowing, "component '$name' to be showing")
        requireState(component.isEnabled, "component '$name' to be enabled")
        click(component)
    }

    override fun setText(name: String, value: String) {
        val component = requireComponent(name, JTextComponent::class.java)
        focus(component, name)
        pressAndRelease(KeyEvent.VK_A, intArrayOf(KeyEvent.VK_CONTROL))
        value.forEach(::typeCharacter)
        waitUntil("text component '$name' to contain '$value'") { textOf(component) == value }
    }

    override fun setSlider(name: String, value: Int) {
        val slider = requireComponent(name, JSlider::class.java)
        val minimum = onEdt { slider.minimum }
        val maximum = onEdt { slider.maximum }
        require(value in minimum..maximum) {
            "slider '$name' value $value is outside $minimum..$maximum"
        }
        focus(slider, name)
        pressAndRelease(KeyEvent.VK_HOME)
        repeat(value - minimum) { pressAndRelease(KeyEvent.VK_RIGHT) }
        waitUntil("slider '$name' to have value $value") { onEdt { slider.value } == value }
    }

    override fun select(name: String, value: String) {
        val comboBox = requireComponent(name, JComboBox::class.java)
        val itemIndex = onEdt {
            (0 until comboBox.itemCount).firstOrNull { index ->
                comboBox.getItemAt(index)?.toString() == value
            }
        } ?: throw AssertionError("combo box '$name' has no item '$value'")

        focus(comboBox, name)
        pressAndRelease(KeyEvent.VK_HOME)
        repeat(itemIndex) { pressAndRelease(KeyEvent.VK_DOWN) }
        pressAndRelease(KeyEvent.VK_ENTER)
        waitUntil("combo box '$name' to select '$value'") { textOf(comboBox) == value }
    }

    override fun press(keyStroke: KeyStroke) {
        val keyCode = keyStroke.keyCode.takeUnless { it == KeyEvent.VK_UNDEFINED }
            ?: KeyEvent.getExtendedKeyCodeForChar(keyStroke.keyChar.code)
        require(keyCode != KeyEvent.VK_UNDEFINED) { "KeyStroke must expose a key code: $keyStroke" }
        pressAndRelease(keyCode, modifierKeyCodes(keyStroke.modifiers))
    }

    override fun requireShowing(name: String) {
        waitUntil("component '$name' to be showing") {
            findByName(name)?.isShowing == true
        }
    }

    override fun requireEnabled(name: String, enabled: Boolean) {
        waitUntil("component '$name' enabled state to be $enabled") {
            findByName(name)?.isEnabled == enabled
        }
    }

    override fun requireText(name: String, expected: String) {
        waitUntil("component '$name' to have text '$expected'") {
            findByName(name)?.let(::textOf) == expected
        }
    }

    override fun dismissDialog(title: String, buttonText: String) {
        var dialog: Dialog? = null
        var button: AbstractButton? = null
        waitUntil("dialog '$title' with button '$buttonText' to be showing") {
            dialog = findDialog(title)
            button = dialog?.let { showingDialog ->
                descendants(showingDialog)
                    .filterIsInstance<AbstractButton>()
                    .firstOrNull { candidate -> candidate.isShowing && candidate.text == buttonText }
            }
            button != null
        }
        click(button ?: throw AssertionError("dialog '$title' has no button '$buttonText'"))
        waitUntil("dialog '$title' to close") { dialog?.isShowing == false }
    }

    override fun waitUntil(description: String, timeout: Duration, condition: () -> Boolean) {
        require(!timeout.isNegative && !timeout.isZero) { "timeout must be positive" }
        val deadline = System.nanoTime() + timeout.toNanos()
        while (true) {
            if (condition()) return
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) throw AssertionError("Timed out waiting for $description")
            LockSupport.parkNanos(minOf(POLL_INTERVAL_NANOS, remaining))
        }
    }

    override fun close() {
        onEdt {
            Window.getWindows()
                .filter(Window::isDisplayable)
                .forEach(Window::dispose)
        }
        waitUntil("all AWT windows to close") { Window.getWindows().none(Window::isShowing) }
    }

    private fun click(component: Component) {
        val center = onEdt {
            val location = component.locationOnScreen
            Point(location.x + component.width / 2, location.y + component.height / 2)
        }
        robot.mouseMove(center.x, center.y)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        Toolkit.getDefaultToolkit().sync()
        robot.waitForIdle()
    }

    private fun focus(component: Component, name: String) {
        onEdt {
            SwingUtilities.getWindowAncestor(component)?.let { window ->
                window.toFront()
                window.requestFocus()
            }
        }
        click(component)
        robot.waitForIdle()
        waitUntil("component '$name' to gain focus") { onEdt { component.isFocusOwner } }
    }

    private fun typeCharacter(character: Char) {
        val stroke = characterStroke(character)
        pressAndRelease(stroke.keyCode, if (stroke.shift) intArrayOf(KeyEvent.VK_SHIFT) else intArrayOf())
    }

    private fun pressAndRelease(keyCode: Int, modifiers: IntArray = intArrayOf()) {
        modifiers.forEach(robot::keyPress)
        robot.keyPress(keyCode)
        robot.keyRelease(keyCode)
        modifiers.reversedArray().forEach(robot::keyRelease)
        Toolkit.getDefaultToolkit().sync()
        robot.waitForIdle()
    }

    private fun characterStroke(character: Char): CharacterStroke {
        if (character.isLetter()) {
            return CharacterStroke(
                KeyEvent.getExtendedKeyCodeForChar(character.uppercaseChar().code),
                character.isUpperCase(),
            )
        }
        if (character.isDigit()) {
            return CharacterStroke(KeyEvent.getExtendedKeyCodeForChar(character.code), false)
        }
        return when (character) {
            ' ' -> CharacterStroke(KeyEvent.VK_SPACE, false)
            '-' -> CharacterStroke(KeyEvent.VK_MINUS, false)
            '_' -> CharacterStroke(KeyEvent.VK_MINUS, true)
            '=' -> CharacterStroke(KeyEvent.VK_EQUALS, false)
            '+' -> CharacterStroke(KeyEvent.VK_EQUALS, true)
            '.' -> CharacterStroke(KeyEvent.VK_PERIOD, false)
            ',' -> CharacterStroke(KeyEvent.VK_COMMA, false)
            '/' -> CharacterStroke(KeyEvent.VK_SLASH, false)
            '?' -> CharacterStroke(KeyEvent.VK_SLASH, true)
            '\\' -> CharacterStroke(KeyEvent.VK_BACK_SLASH, false)
            ':' -> CharacterStroke(KeyEvent.VK_SEMICOLON, true)
            ';' -> CharacterStroke(KeyEvent.VK_SEMICOLON, false)
            else -> throw IllegalArgumentException("Robot text entry does not support character '$character'")
        }
    }

    private fun modifierKeyCodes(modifiers: Int): IntArray = buildList {
        if (modifiers and InputEvent.SHIFT_DOWN_MASK != 0) add(KeyEvent.VK_SHIFT)
        if (modifiers and InputEvent.CTRL_DOWN_MASK != 0) add(KeyEvent.VK_CONTROL)
        if (modifiers and InputEvent.ALT_DOWN_MASK != 0) add(KeyEvent.VK_ALT)
        if (modifiers and InputEvent.META_DOWN_MASK != 0) add(KeyEvent.VK_META)
        if (modifiers and InputEvent.ALT_GRAPH_DOWN_MASK != 0) add(KeyEvent.VK_ALT_GRAPH)
    }.toIntArray()

    private fun requireComponent(name: String): Component =
        findByName(name) ?: throw AssertionError("No component named '$name'")

    private fun <T : Component> requireComponent(name: String, type: Class<T>): T {
        val component = requireComponent(name)
        if (!type.isInstance(component)) {
            throw AssertionError("Component '$name' is ${component.javaClass.name}, expected ${type.name}")
        }
        return type.cast(component)
    }

    private fun findByName(name: String): Component? = onEdt {
        Window.getWindows()
            .asSequence()
            .flatMap(::descendants)
            .firstOrNull { component -> component.name == name }
    }

    private fun findDialog(title: String): Dialog? = onEdt {
        Window.getWindows()
            .filterIsInstance<Dialog>()
            .firstOrNull { dialog -> dialog.isShowing && dialog.title == title }
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) {
            component.components.forEach { child -> yieldAll(descendants(child)) }
        }
    }

    private fun textOf(component: Component): String? = onEdt {
        when (component) {
            is JTextComponent -> component.text
            is JLabel -> component.text
            is AbstractButton -> component.text
            is JComboBox<*> -> component.selectedItem?.toString()
            else -> null
        }
    }

    private fun requireState(actual: Boolean, description: String) {
        if (!actual) throw AssertionError("Expected $description")
    }

    private fun <T> onEdt(action: () -> T): T {
        if (EventQueue.isDispatchThread()) return action()
        val task = FutureTask(action)
        EventQueue.invokeAndWait(task)
        return task.get()
    }

    private data class CharacterStroke(val keyCode: Int, val shift: Boolean)

    private companion object {
        const val EVENT_DELAY_MILLIS = 20
        val POLL_INTERVAL_NANOS: Long = TimeUnit.MILLISECONDS.toNanos(20)
    }
}
