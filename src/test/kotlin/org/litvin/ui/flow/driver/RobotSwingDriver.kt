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
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.plaf.basic.BasicSliderUI
import javax.swing.text.JTextComponent
import kotlin.math.abs

class RobotSwingDriver : SwingUiDriver {
    private val robot = Robot().apply {
        autoDelay = EVENT_DELAY_MILLIS
        isAutoWaitForIdle = true
    }

    override fun click(name: String) {
        val snapshot = requireComponentSnapshot(name)
        requireState(snapshot.showing, "component '$name' to be showing")
        requireState(snapshot.enabled, "component '$name' to be enabled")
        activate(snapshot.component, name)
        click(snapshot.component)
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
        val snapshot = onEdt {
            SliderSnapshot(slider.minimum, slider.maximum, slider.isShowing, slider.isEnabled)
        }
        val minimum = snapshot.minimum
        val maximum = snapshot.maximum
        require(value in minimum..maximum) {
            "slider '$name' value $value is outside $minimum..$maximum"
        }
        requireState(snapshot.showing, "slider '$name' to be showing")
        requireState(snapshot.enabled, "slider '$name' to be enabled")
        activate(slider)
        SliderMouseController(
            dragTo = { target -> dragSlider(slider, target) },
            readValue = { onEdt { slider.value } },
            pressKey = ::pressAndRelease,
            awaitValue = { expected ->
                waitUntil("slider '$name' to have value $expected") { onEdt { slider.value } == expected }
            },
        ).setValue(value, minimum, maximum)
    }

    override fun setSelected(name: String, selected: Boolean) {
        val button = requireComponent(name, AbstractButton::class.java)
        val snapshot = onEdt { SelectionSnapshot(button.isSelected, button.isShowing, button.isEnabled) }
        requireState(snapshot.showing, "component '$name' to be showing")
        requireState(snapshot.enabled, "component '$name' to be enabled")
        if (snapshot.selected != selected) click(button)
        waitUntil("component '$name' selected state to be $selected") { onEdt { button.isSelected } == selected }
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

    override fun requireShowing(name: String, showing: Boolean) {
        waitUntil("component '$name' showing state to be $showing") {
            componentSnapshotIncludingHidden(name)?.showing == showing
        }
    }

    override fun requireEnabled(name: String, enabled: Boolean) {
        waitUntil("component '$name' enabled state to be $enabled") {
            componentSnapshot(name)?.enabled == enabled
        }
    }

    override fun requireAccessibleDescription(name: String, expectedSubstring: String) {
        waitUntil("component '$name' accessible description to contain '$expectedSubstring'") {
            onEdt {
                val component = findByNameOnEdt(name) ?: return@onEdt false
                component.accessibleContext.accessibleDescription?.contains(expectedSubstring, ignoreCase = true) == true
            }
        }
    }

    override fun requireText(name: String, expected: String) {
        waitUntil("component '$name' to have text '$expected'") {
            textSnapshot(name) == expected
        }
    }

    override fun dismissDialog(title: String, buttonText: String) {
        var snapshot: DialogButtonSnapshot? = null
        waitUntil("dialog '$title' with button '$buttonText' to be showing") {
            snapshot = dialogButtonSnapshot(title, buttonText)
            snapshot != null
        }
        val target = snapshot ?: throw AssertionError("dialog '$title' has no button '$buttonText'")
        click(target.button)
        waitUntil("dialog '$title' to close") { onEdt { !target.dialog.isShowing } }
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
        waitUntil("all AWT windows to close") {
            onEdt { Window.getWindows().none(Window::isShowing) }
        }
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

    private fun activate(component: Component, componentName: String? = component.name) {
        val window = onEdt {
            SwingUtilities.getWindowAncestor(component)?.also { owner ->
                owner.toFront()
                owner.requestFocus()
            } ?: throw AssertionError("Showing component '${componentName ?: component.javaClass.simpleName}' has no owning window")
        }
        robot.waitForIdle()
        waitUntil("window containing '${componentName ?: component.javaClass.simpleName}' to become active") {
            onEdt { window.isActive || window.isFocused }
        }
    }

    private fun dragSlider(slider: JSlider, target: Int) {
        val gesture = onEdt {
            val componentGesture = SliderDragGeometry.gesture(slider, target)
            val location = slider.locationOnScreen
            SliderDragGesture(
                start = Point(location.x + componentGesture.start.x, location.y + componentGesture.start.y),
                target = Point(location.x + componentGesture.target.x, location.y + componentGesture.target.y),
            )
        }
        robot.mouseMove(gesture.start.x, gesture.start.y)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
        robot.mouseMove(gesture.target.x, gesture.target.y)
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

    private fun requireComponentSnapshot(name: String): ComponentSnapshot =
        componentSnapshot(name) ?: throw AssertionError("No component named '$name'")

    private fun <T : Component> requireComponent(name: String, type: Class<T>): T {
        val component = requireComponent(name)
        if (!type.isInstance(component)) {
            throw AssertionError("Component '$name' is ${component.javaClass.name}, expected ${type.name}")
        }
        return type.cast(component)
    }

    private fun componentSnapshot(name: String): ComponentSnapshot? = onEdt {
        findByNameOnEdt(name)?.let { component ->
            ComponentSnapshot(component, component.isShowing, component.isEnabled)
        }
    }

    private fun componentSnapshotIncludingHidden(name: String): ComponentSnapshot? = onEdt {
        findByNameIncludingHiddenOnEdt(name)?.let { component ->
            ComponentSnapshot(component, component.isShowing, component.isEnabled)
        }
    }

    private fun textSnapshot(name: String): String? = onEdt {
        findByNameOnEdt(name)?.let(::textOfOnEdt)
    }

    private fun findByName(name: String): Component? = onEdt { findByNameOnEdt(name) }

    private fun findByNameOnEdt(name: String): Component? {
        check(EventQueue.isDispatchThread()) { "component hierarchy must be traversed on the EDT" }
        return Window.getWindows()
            .filter(Window::isShowing)
            .asReversed()
            .asSequence()
            .flatMap(::descendants)
            .firstOrNull { component -> component.name == name }
    }

    private fun findByNameIncludingHiddenOnEdt(name: String): Component? {
        check(EventQueue.isDispatchThread()) { "component hierarchy must be traversed on the EDT" }
        return Window.getWindows()
            .filter(Window::isDisplayable)
            .asReversed()
            .asSequence()
            .flatMap(::descendants)
            .firstOrNull { component -> component.name == name }
    }

    private fun dialogButtonSnapshot(title: String, buttonText: String): DialogButtonSnapshot? = onEdt {
        val dialog = Window.getWindows()
            .filterIsInstance<Dialog>()
            .asReversed()
            .firstOrNull { candidate -> candidate.isShowing && candidate.title == title }
            ?: return@onEdt null
        val button = descendants(dialog)
            .filterIsInstance<AbstractButton>()
            .firstOrNull { candidate -> candidate.isShowing && candidate.text == buttonText }
            ?: return@onEdt null
        DialogButtonSnapshot(dialog, button)
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        check(EventQueue.isDispatchThread()) { "component hierarchy must be traversed on the EDT" }
        yield(component)
        if (component is Container) {
            component.components.forEach { child -> yieldAll(descendants(child)) }
        }
    }

    private fun textOf(component: Component): String? = onEdt { textOfOnEdt(component) }

    private fun textOfOnEdt(component: Component): String? {
        check(EventQueue.isDispatchThread()) { "component text must be read on the EDT" }
        return when (component) {
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

    private data class ComponentSnapshot(
        val component: Component,
        val showing: Boolean,
        val enabled: Boolean,
    )

    private data class DialogButtonSnapshot(
        val dialog: Dialog,
        val button: AbstractButton,
    )

    private data class SliderSnapshot(
        val minimum: Int,
        val maximum: Int,
        val showing: Boolean,
        val enabled: Boolean,
    )

    private data class SelectionSnapshot(
        val selected: Boolean,
        val showing: Boolean,
        val enabled: Boolean,
    )

    private companion object {
        const val EVENT_DELAY_MILLIS = 20
        val POLL_INTERVAL_NANOS: Long = TimeUnit.MILLISECONDS.toNanos(20)
    }
}

internal class SliderMouseController(
    private val dragTo: (Int) -> Unit,
    private val readValue: () -> Int,
    private val pressKey: (Int) -> Unit,
    private val awaitValue: (Int) -> Unit,
) {
    fun setValue(target: Int, minimum: Int, maximum: Int) {
        require(target in minimum..maximum) { "slider value $target is outside $minimum..$maximum" }
        dragTo(target)
        val observed = readValue()
        val correction = target - observed
        if (abs(correction) > MAX_DIRECTIONAL_CORRECTION) {
            throw AssertionError(
                "Slider drag landed at $observed, too far from target $target for a bounded keyboard correction"
            )
        }
        val correctionKey = if (correction >= 0) KeyEvent.VK_RIGHT else KeyEvent.VK_LEFT
        repeat(abs(correction)) { pressKey(correctionKey) }
        awaitValue(target)
    }

    private companion object {
        const val MAX_DIRECTIONAL_CORRECTION = 2
    }
}

internal object SliderDragGeometry {
    fun gesture(slider: JSlider, target: Int): SliderDragGesture {
        check(EventQueue.isDispatchThread()) { "slider drag geometry must be calculated on the EDT" }
        require(slider.orientation == SwingConstants.HORIZONTAL) { "only horizontal sliders are supported" }
        require(target in slider.minimum..slider.maximum) {
            "slider value $target is outside ${slider.minimum}..${slider.maximum}"
        }
        require(slider.width > 0 && slider.height > 0) { "slider must have live non-zero bounds" }
        val ui = slider.ui as? BasicSliderUI
            ?: throw AssertionError("Slider UI ${slider.ui.javaClass.name} does not expose semantic geometry")
        return SliderDragGesture(
            start = semanticPoint(slider, ui, slider.value),
            target = semanticPoint(slider, ui, target),
        )
    }

    private fun semanticPoint(slider: JSlider, ui: BasicSliderUI, value: Int): Point {
        var firstX = -1
        var lastX = -1
        var bestDistance = Int.MAX_VALUE
        for (x in 0 until slider.width) {
            val distance = abs(ui.valueForXPosition(x) - value)
            if (distance < bestDistance) {
                bestDistance = distance
                firstX = x
                lastX = x
            } else if (distance == bestDistance) {
                lastX = x
            }
        }
        return Point((firstX + lastX) / 2, slider.height / 2)
    }
}

internal data class SliderDragGesture(
    val start: Point,
    val target: Point,
)
