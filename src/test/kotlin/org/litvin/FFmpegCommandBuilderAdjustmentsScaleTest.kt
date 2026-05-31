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

    private fun parseHueMap(vf: String): Map<String, String>? {
        val hueStart = vf.indexOf("hue=")
        if (hueStart < 0) return null
        val after = vf.substring(hueStart + 4)
        val endIdx = after.indexOfAny(charArrayOf(',', ';'))
        val hueBody = if (endIdx >= 0) after.substring(0, endIdx) else after
        val parts = hueBody.split(":")
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
    fun brightness_extremes_are_damped_for_ffmpeg_preview_parity() {
        // Low end: model 0.0 -> eq about -0.39, not -1.0, because ffmpeg eq is much stronger than VLC preview.
        var vf = buildVf(AdjustmentsV1(brightness = 0.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f, 0f)))
        var eq = parseEqMap(vf)!!
        val bLow = eq["brightness"]!!.toDouble()
        assertTrue(kotlin.math.abs(bLow + 0.39) < 1e-3, "Expected ~-0.39, was $bLow (vf=$vf)")

        // High end: model 2.0 -> eq about +0.39
        vf = buildVf(AdjustmentsV1(brightness = 2.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f, 0f)))
        eq = parseEqMap(vf)!!
        val bHigh = eq["brightness"]!!.toDouble()
        assertTrue(kotlin.math.abs(bHigh - 0.39) < 1e-3, "Expected ~+0.39, was $bHigh (vf=$vf)")
    }

    @Test
    fun boosted_saturation_is_damped_for_ffmpeg_preview_parity() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 1.0f, saturation = 2.0f, whiteBalance = WhiteBalanceV1(0f, 0f)))
        val eq = parseEqMap(vf)!!
        val saturation = eq["saturation"]!!.toDouble()

        assertTrue(kotlin.math.abs(saturation - 1.5) < 1e-3, "Expected saturation boost to map to ~1.5, was $saturation (vf=$vf)")
    }

    @Test
    fun full_desaturation_stays_grayscale() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 1.0f, saturation = 0.0f, whiteBalance = WhiteBalanceV1(0f, 0f)))
        val eq = parseEqMap(vf)!!
        val saturation = eq["saturation"]!!.toDouble()

        assertTrue(kotlin.math.abs(saturation) < 1e-3, "Expected saturation 0.0 to stay grayscale, was $saturation (vf=$vf)")
    }

    @Test
    fun negative_contrast_export_supports_current_ui_floor() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 0.5f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f, 0f)))
        val eq = parseEqMap(vf)!!
        val contrast = eq["contrast"]!!.toDouble()
        assertTrue(kotlin.math.abs(contrast - 0.5) < 1e-3, "Expected -50 contrast to pass through as 0.5, was $contrast (vf=$vf)")
    }

    @Test
    fun positive_contrast_still_passes_through() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 2.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f, 0f)))
        val eq = parseEqMap(vf)!!
        val contrast = eq["contrast"]!!.toDouble()

        assertTrue(kotlin.math.abs(contrast - 2.0) < 1e-3, "Expected +100 contrast to pass through as 2.0, was $contrast (vf=$vf)")
    }

    @Test
    fun white_balance_temperature_adds_hue_filter_and_saturation_nudge() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0.5f, 0f)))
        val eq = parseEqMap(vf)!!
        val hue = parseHueMap(vf)!!

        assertTrue(vf.contains(",hue="), "Expected hue filter after eq, was: $vf")
        assertTrue(kotlin.math.abs(eq["saturation"]!!.toDouble() - 1.0125) < 1e-3, "Expected WB temp saturation nudge, vf=$vf")
        assertTrue(kotlin.math.abs(hue["h"]!!.toDouble() - 90.0) < 1e-3, "Expected WB temp hue 90 degrees, vf=$vf")
    }

    @Test
    fun white_balance_tint_adds_gamma_and_small_hue_offset() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f, 0.5f)))
        val eq = parseEqMap(vf)!!
        val hue = parseHueMap(vf)!!

        assertTrue(vf.contains(",hue="), "Expected hue filter after eq, was: $vf")
        assertTrue(kotlin.math.abs(eq["gamma"]!!.toDouble() - 1.1) < 1e-3, "Expected WB tint gamma 1.1, vf=$vf")
        assertTrue(kotlin.math.abs(hue["h"]!!.toDouble() - 6.0) < 1e-3, "Expected WB tint hue 6 degrees, vf=$vf")
    }
}
