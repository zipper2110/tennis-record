package org.litvin

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.WhiteBalanceV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f)))
        // We accept either no eq at all or eq with identity values — prefer no extra filter when possible
        if (vf.contains("eq=")) {
            val eq = parseEqMap(vf)!!
            val b = eq["brightness"]?.toDoubleOrNull() ?: 999.0
            val c = eq["contrast"]?.toDoubleOrNull() ?: -1.0
            val s = eq["saturation"]?.toDoubleOrNull() ?: -1.0
            assertTrue(kotlin.math.abs(b) < 1e-3, "brightness should be ~0.0, was $b")
            assertTrue(kotlin.math.abs(c - 1.0) < 1e-3, "contrast should be ~1.0, was $c")
            assertTrue(kotlin.math.abs(s - 1.0) < 1e-3, "saturation should be ~1.0, was $s")
        }
    }

    @Test
    fun brightness_model_1_0_maps_to_eq_0_0() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f)))
        val eq = parseEqMap(vf)
        // If all identity, eq may be omitted entirely. Accept both: omitted or present with 0/1 values.
        if (eq == null) return
        val b = eq["brightness"]?.toDoubleOrNull()
        val c = eq["contrast"]?.toDoubleOrNull()
        val s = eq["saturation"]?.toDoubleOrNull()
        assertTrue(b != null && kotlin.math.abs(b!!) < 1e-3, "brightness should be ~0.0, was $b")
        assertTrue(c != null && kotlin.math.abs(c!! - 1.0) < 1e-3, "contrast should be ~1.0, was $c")
        assertTrue(s != null && kotlin.math.abs(s!! - 1.0) < 1e-3, "saturation should be ~1.0, was $s")
    }

    @Test
    fun brightness_extremes_span_the_whole_eq_brightness_range() {
        // Low end: slider -100 -> model 0.0 -> eq -1.0, the lowest value the eq filter accepts.
        var vf = buildVf(AdjustmentsV1(brightness = 0.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f)))
        var eq = parseEqMap(vf)!!
        val bLow = eq["brightness"]!!.toDouble()
        assertTrue(kotlin.math.abs(bLow + 1.0) < 1e-3, "Expected -1.0, was $bLow (vf=$vf)")

        // High end: slider +100 -> model 2.0 -> eq +1.0
        vf = buildVf(AdjustmentsV1(brightness = 2.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f)))
        eq = parseEqMap(vf)!!
        val bHigh = eq["brightness"]!!.toDouble()
        assertTrue(kotlin.math.abs(bHigh - 1.0) < 1e-3, "Expected +1.0, was $bHigh (vf=$vf)")
    }

    @Test
    fun boosted_saturation_passes_through() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 1.0f, saturation = 2.0f, whiteBalance = WhiteBalanceV1(0f)))
        val eq = parseEqMap(vf)!!
        val saturation = eq["saturation"]!!.toDouble()

        assertTrue(kotlin.math.abs(saturation - 2.0) < 1e-3, "Expected +100 saturation to pass through as 2.0, was $saturation (vf=$vf)")
    }

    @Test
    fun full_desaturation_stays_grayscale() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 1.0f, saturation = 0.0f, whiteBalance = WhiteBalanceV1(0f)))
        val eq = parseEqMap(vf)!!
        val saturation = eq["saturation"]!!.toDouble()

        assertTrue(kotlin.math.abs(saturation) < 1e-3, "Expected saturation 0.0 to stay grayscale, was $saturation (vf=$vf)")
    }

    @Test
    fun negative_contrast_export_supports_current_ui_floor() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 0.5f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f)))
        val eq = parseEqMap(vf)!!
        val contrast = eq["contrast"]!!.toDouble()
        assertTrue(kotlin.math.abs(contrast - 0.5) < 1e-3, "Expected -50 contrast to pass through as 0.5, was $contrast (vf=$vf)")
    }

    @Test
    fun positive_contrast_still_passes_through() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 2.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0f)))
        val eq = parseEqMap(vf)!!
        val contrast = eq["contrast"]!!.toDouble()

        assertTrue(kotlin.math.abs(contrast - 2.0) < 1e-3, "Expected +100 contrast to pass through as 2.0, was $contrast (vf=$vf)")
    }

    @Test
    fun white_balance_temperature_adds_a_hue_filter_and_leaves_the_eq_values_alone() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.0f, contrast = 1.0f, saturation = 1.0f, whiteBalance = WhiteBalanceV1(0.5f)))
        val hue = parseHueMap(vf)!!

        assertTrue(vf.contains("hue="), "Expected hue filter, was: $vf")
        // Temperature is a hue rotation only, so the identity eq filter is left out of the command.
        assertNull(parseEqMap(vf), "WB temperature must not add an eq filter, vf=$vf")
        assertTrue(kotlin.math.abs(hue["h"]!!.toDouble() - 10.0) < 1e-3, "Expected WB temp hue 10 degrees, vf=$vf")
    }

    @Test
    fun shadows_and_highlights_add_a_luma_lut_and_leave_the_eq_values_alone() {
        val vf = buildVf(
            AdjustmentsV1(
                brightness = 1.0f,
                contrast = 1.0f,
                saturation = 1.0f,
                shadows = 0.5f,
                highlights = -0.5f,
                whiteBalance = WhiteBalanceV1(0f),
            )
        )

        // The tone curve is a luma lookup table only, so the identity eq filter is left out.
        assertNull(parseEqMap(vf), "Shadows/highlights must not add an eq filter, vf=$vf")
        assertTrue(vf.contains("lutyuv=y=val+31.8750*"), "Expected the shadows lift, was: $vf")
        assertTrue(vf.contains("+-31.8750*"), "Expected the highlights lift, was: $vf")
        // lutyuv clips chroma to the legal range unless u and v pass the input through unchanged.
        assertTrue(vf.contains(":u=val:v=val"), "Expected chroma passthrough, was: $vf")
    }

    @Test
    fun identity_tone_sliders_add_no_lut_filter() {
        val vf = buildVf(AdjustmentsV1(brightness = 1.2f, contrast = 1.0f, saturation = 1.0f))

        assertTrue(!vf.contains("lutyuv"), "Expected no lutyuv filter, was: $vf")
    }
}
