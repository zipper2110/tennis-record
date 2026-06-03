package org.litvin.media

import java.util.Locale
import kotlin.math.abs

object VlcPreviewMediaOptions {
    private const val ROTATION_EPSILON_DEG = 0.001f
    private const val CARDINAL_EPSILON_DEG = 0.5f

    fun startPaused(): Array<String> = arrayOf(":start-paused")

    fun factoryArguments(rotationDeg: Float): Array<String> {
        val normalized = -rotationDeg.coerceIn(-180.0f, 180.0f)
        if (abs(normalized) < ROTATION_EPSILON_DEG) return emptyArray()

        cardinalTransformType(normalized)?.let { transformType ->
            return arrayOf(
                "--video-filter=transform",
                "--transform-type=$transformType"
            )
        }
        return arrayOf(
            "--video-filter=rotate",
            "--rotate-angle=${formatDegrees(toVlcPositiveDegrees(normalized))}"
        )
    }

    fun isSameRotation(a: Float, b: Float): Boolean = abs(a - b) < ROTATION_EPSILON_DEG

    private fun cardinalTransformType(value: Float): String? {
        val normalized = toVlcPositiveDegrees(value)
        return when {
            abs(normalized - 90.0f) <= CARDINAL_EPSILON_DEG -> "90"
            abs(normalized - 180.0f) <= CARDINAL_EPSILON_DEG -> "180"
            abs(normalized - 270.0f) <= CARDINAL_EPSILON_DEG -> "270"
            else -> null
        }
    }

    private fun toVlcPositiveDegrees(value: Float): Float {
        val mod = value % 360.0f
        return if (mod < 0.0f) mod + 360.0f else mod
    }

    private fun formatDegrees(value: Float): String {
        return String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
    }
}
