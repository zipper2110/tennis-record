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

    fun assertRotationPersisted(projectDirectory: Path, degrees: Float) {
        application.eventually("rotation $degrees degrees to be persisted") {
            val actual = AdjustmentsIO.readForProjectDir(projectDirectory.toString()).rotationDeg
            if (abs(actual - degrees) > 0.0001f) {
                throw AssertionError("Persisted rotation was $actual, expected $degrees")
            }
        }
    }
}
