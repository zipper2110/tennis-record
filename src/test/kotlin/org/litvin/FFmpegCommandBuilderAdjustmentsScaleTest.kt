package org.litvin

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.WhiteBalanceV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FFmpegCommandBuilderAdjustmentsScaleTest {
    private val preset = ExportPresetsIO.load().let { presets ->
        presets[ExportPresetsIO.defaultBalancedIndex(presets)]
    }

    private fun buildVf(adj: AdjustmentsV1?): String {
        val res = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "in.mp4",
                outputPath = "out.mp4",
                preset = preset,
                outWidth = 1280,
                outHeight = 720,
                encoderLabel = "H.264 (libx264)",
                idleTrim = false,
                adjustments = adj,
            )
        )
        val vfIdx = res.args.indexOf("-vf")
        assertTrue(vfIdx >= 0, "-vf expected in simple path")
        return res.args[vfIdx + 1]
    }

    private fun parseEqMap(vf: String): Map<String, String>? {
        val eqStart = vf.indexOf("eq=")
        if (eqStart < 0) return null
        val after = vf.substring(eqStart + 3)
        val endIdx = after.indexOfAny(charArrayOf(',', ';'))
        val eqBody = if (endIdx >= 0) after.substring(0, endIdx) else after
        val parts = eqBody.split(":")
        return parts.mapNotNull {
            val kv = it.split("=")
            if (kv.size == 2) kv[0] to kv[1] else null
        }.toMap()
    }

    @Test
    fun identity_model_values_emit_no_eq_filter() {
        // Treat identity as brightness=1.0, contrast=1.0, saturation=1.0, wb=0/0 per UI mapping
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f, 0f)))
        // We accept either no eq at all or eq with identity values — prefer no extra filter when possible
        if (vf.contains("eq=")) {
            val eq = parseEqMap(vf)!!
            val b = eq["brightness"]?.toDoubleOrNull() ?: 999.0
            val c = eq["contrast"]?.toDoubleOrNull() ?: -1.0
            val s = eq["saturation"]?.toDoubleOrNull() ?: -1.0
            val g = eq["gamma"]?.toDoubleOrNull() ?: -1.0
            assertTrue(kotlin.math.abs(b) < 1e-3, "brightness should be ~0.0, was $b")
            assertTrue(kotlin.math.abs(c - 1.0) < 1e-3, "contrast should be ~1.0, was $c")
            assertTrue(kotlin.math.abs(s - 1.0) < 1e-3, "saturation should be ~1.0, was $s")
            assertTrue(kotlin.math.abs(g - 1.0) < 1e-3, "gamma should be ~1.0, was $g")
        }
    }

    @Test
    fun brightness_model_1_0_maps_to_eq_0_0() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f, 0f)))
        val eq = parseEqMap(vf)
        // If all identity, eq may be omitted entirely. Accept both: omitted or present with 0/1 values.
        if (eq == null) return
        val b = eq["brightness"]?.toDoubleOrNull()
        val c = eq["contrast"]?.toDoubleOrNull()
        val s = eq["saturation"]?.toDoubleOrNull()
        val g = eq["gamma"]?.toDoubleOrNull()
        assertTrue(b != null && kotlin.math.abs(b!!) < 1e-3, "brightness should be ~0.0, was $b")
        assertTrue(c != null && kotlin.math.abs(c!! - 1.0) < 1e-3, "contrast should be ~1.0, was $c")
        assertTrue(s != null && kotlin.math.abs(s!! - 1.0) < 1e-3, "saturation should be ~1.0, was $s")
        assertTrue(g != null && kotlin.math.abs(g!! - 1.0) < 1e-3, "gamma should be ~1.0, was $g")
    }

    @Test
    fun brightness_extremes_map_to_eq_bounds() {
        // Low end: model 0.0 -> eq -1.0
        var vf = buildVf(AdjustmentsV1(brightness = 0.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f, 0f)))
        var eq = parseEqMap(vf)!!
        val bLow = eq["brightness"]!!.toDouble()
        assertTrue(bLow <= -0.999 || kotlin.math.abs(bLow + 1.0) < 1e-3, "Expected ~-1.0, was $bLow (vf=$vf)")

        // High end: model 3.0 -> eq +1.0
        vf = buildVf(AdjustmentsV1(brightness = 3.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f, 0f)))
        eq = parseEqMap(vf)!!
        val bHigh = eq["brightness"]!!.toDouble()
        assertTrue(bHigh >= 0.999 || kotlin.math.abs(bHigh - 1.0) < 1e-3, "Expected ~+1.0, was $bHigh (vf=$vf)")
    }
}
