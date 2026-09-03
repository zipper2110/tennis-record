package org.litvin.ui.flow.screens

import org.litvin.adjustments.AdjustmentsIO
import org.litvin.ui.commons.AppShortcuts
import java.nio.file.Path
import javax.swing.KeyStroke
import kotlin.math.abs

internal class ColorsScreen(application: ApplicationScreen) : UserFlowScreen(application) {
    fun open(): ColorsScreen = apply { open("nav-colors", "colors-brightness") }

    fun setBrightness(value: Int): ColorsScreen = apply {
        application.eventually("brightness slider to become $value") {
            context.driver.setSlider("colors-brightness", value)
        }
    }

    fun togglePlayback(): ColorsScreen = apply {
        context.driver.press(KeyStroke.getKeyStroke(AppShortcuts.PLAY_PAUSE.keyStroke))
    }

    fun assertColorEditPersisted(projectDirectory: Path, brightness: Float) {
        application.eventually("brightness $brightness with default transform to be persisted") {
            val actual = AdjustmentsIO.readForProjectDir(projectDirectory.toString())
            val mismatches = buildList {
                if (!close(actual.brightness, brightness)) add("brightness=${actual.brightness}")
                if (!close(actual.zoom, 1.0f)) add("zoom=${actual.zoom}")
                if (!close(actual.panX, 0.0f)) add("panX=${actual.panX}")
                if (!close(actual.panY, 0.0f)) add("panY=${actual.panY}")
                if (!close(actual.rotationDeg, 0.0f)) add("rotationDeg=${actual.rotationDeg}")
            }
            if (mismatches.isNotEmpty()) throw AssertionError("Persisted adjustments mismatched: $mismatches")
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

    private fun close(actual: Float, expected: Float): Boolean = abs(actual - expected) <= 0.0001f
}
