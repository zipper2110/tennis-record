package org.litvin.ui.flow.screens

import org.litvin.adjustments.AdjustmentsIO
import java.nio.file.Path
import kotlin.math.abs

internal class ColorsScreen(application: ApplicationScreen) : UserFlowScreen(application) {
    fun open(): ColorsScreen = apply { open("nav-colors", "colors-brightness") }

    fun setBrightness(value: Int): ColorsScreen = apply {
        application.eventually("brightness slider to become $value") {
            context.driver.setSlider("colors-brightness", value)
        }
    }

    fun assertBrightnessPersisted(projectDirectory: Path, sliderValue: Int) {
        val expected = 1.0f + sliderValue / 100.0f
        application.eventually("brightness $sliderValue to be persisted") {
            val actual = AdjustmentsIO.readForProjectDir(projectDirectory.toString()).brightness
            if (abs(actual - expected) > 0.0001f) {
                throw AssertionError("Persisted brightness was $actual, expected $expected")
            }
        }
    }
}
