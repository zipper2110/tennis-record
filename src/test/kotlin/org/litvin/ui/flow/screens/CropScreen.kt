package org.litvin.ui.flow.screens

import org.litvin.adjustments.AdjustmentsIO
import java.nio.file.Path
import kotlin.math.abs

internal class CropScreen(application: ApplicationScreen) : UserFlowScreen(application) {
    fun open(): CropScreen = apply { open("nav-crop", "crop-rotation") }

    fun setRotationDegrees(value: Float): CropScreen = apply {
        val halfDegreeSteps = value * 2.0f
        require(abs(halfDegreeSteps - halfDegreeSteps.toInt()) < 0.0001f) {
            "rotation must be expressible in half-degree steps: $value"
        }
        application.eventually("rotation slider to become $value degrees") {
            context.driver.setSlider("crop-rotation", halfDegreeSteps.toInt())
        }
    }

    fun setZoomPercent(value: Int): CropScreen = apply {
        require(value in 10..400) { "zoom must be between 10 and 400 percent: $value" }
        application.eventually("zoom slider to become $value percent") {
            context.driver.setSlider("crop-zoom", value)
        }
    }

    fun assertTransformPersisted(
        projectDirectory: Path,
        brightness: Float,
        zoom: Float,
        rotationDegrees: Float,
    ) {
        application.eventually("crop transform and color edit to be persisted") {
            val actual = AdjustmentsIO.readForProjectDir(projectDirectory.toString())
            val mismatches = buildList {
                if (!close(actual.brightness, brightness)) add("brightness=${actual.brightness}")
                if (!close(actual.zoom, zoom)) add("zoom=${actual.zoom}")
                if (!close(actual.rotationDeg, rotationDegrees)) add("rotationDeg=${actual.rotationDeg}")
                if (!close(actual.panX, 0.0f)) add("panX=${actual.panX}")
                if (!close(actual.panY, 0.0f)) add("panY=${actual.panY}")
            }
            if (mismatches.isNotEmpty()) throw AssertionError("Persisted adjustments mismatched: $mismatches")
        }
    }

    fun assertRotationPersisted(projectDirectory: Path, degrees: Float) {
        application.eventually("rotation $degrees degrees to be persisted") {
            val actual = AdjustmentsIO.readForProjectDir(projectDirectory.toString()).rotationDeg
            if (abs(actual - degrees) > 0.0001f) {
                throw AssertionError("Persisted rotation was $actual, expected $degrees")
            }
        }
    }

    private fun close(actual: Float, expected: Float): Boolean = abs(actual - expected) <= 0.0001f
}
