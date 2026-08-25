package org.litvin.ui.flow.driver

import java.time.Duration
import javax.swing.KeyStroke

interface SwingUiDriver : AutoCloseable {
    fun click(name: String)
    fun setText(name: String, value: String)
    fun setSlider(name: String, value: Int)
    fun select(name: String, value: String)
    fun press(keyStroke: KeyStroke)
    fun requireShowing(name: String, showing: Boolean = true)
    fun requireEnabled(name: String, enabled: Boolean)
    fun requireText(name: String, expected: String)
    fun dismissDialog(title: String, buttonText: String)
    fun waitUntil(
        description: String,
        timeout: Duration = Duration.ofSeconds(5),
        condition: () -> Boolean,
    )
}
