package org.litvin.media.mpv

import org.litvin.FFmpegCommandBuilder
import org.litvin.ExportPreset
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.WhiteBalanceV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MpvShaderParamsTest {
    private val video = MpvVideoInfo(3840, 2160, "bt.709", "limited")

    private fun parse(opts: String): Map<String, String> =
        opts.split(",").associate { it.substringBefore("=") to it.substringAfter("=") }

    @Test
    fun `identity adjustments disable the shader pass`() {
        val values = parse(MpvShaderParams.build(AdjustmentsV1(), video))

        assertEquals("0", values["tr_active"])
        assertEquals("1.000000", values["tr_contrast"])
        assertEquals("0.000000", values["tr_brightness"])
        assertEquals("1.000000", values["tr_crop_w"])
    }

    @Test
    fun `color values are the same values that the export eq filter receives`() {
        val adjustments = AdjustmentsV1(
            brightness = 1.3f,
            contrast = 1.45f,
            saturation = 1.2f,
            whiteBalance = WhiteBalanceV1(temperature = 0.1f, tint = -0.2f),
        )
        val values = parse(MpvShaderParams.build(adjustments, video))
        val export = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "in.mp4",
                outputPath = "out.mp4",
                preset = ExportPreset(id = "balanced", label = "Balanced"),
                outWidth = 1920,
                outHeight = 1080,
                idleTrim = false,
                adjustments = adjustments,
            )
        ).args
        val vf = export[export.indexOf("-vf") + 1]
        val eq = Regex("eq=brightness=([-0-9.]+):contrast=([-0-9.]+):saturation=([-0-9.]+):gamma=([-0-9.]+)")
            .find(vf)!!.groupValues
        val hue = Regex("hue=h=([-0-9.]+)").find(vf)!!.groupValues[1]

        assertEquals("1", values["tr_active"])
        assertEquals(eq[1].toDouble(), values.getValue("tr_brightness").toDouble(), 1e-9)
        assertEquals(eq[2].toDouble(), values.getValue("tr_contrast").toDouble(), 1e-9)
        assertEquals(eq[3].toDouble(), values.getValue("tr_saturation").toDouble(), 1e-9)
        assertEquals(eq[4].toDouble(), values.getValue("tr_gamma").toDouble(), 1e-9)
        assertEquals(hue.toDouble(), values.getValue("tr_hue_deg").toDouble(), 1e-9)
    }

    @Test
    fun `rotation alone activates the pass and keeps the full inscribed crop`() {
        val values = parse(MpvShaderParams.build(AdjustmentsV1(rotationDeg = 5.0f), video))

        assertEquals("1", values["tr_active"])
        assertEquals(5.0, values.getValue("tr_rotation_deg").toDouble(), 1e-9)
        // The crop is the largest centered 16:9 rectangle inside the rotated frame (3334 px wide at 5 degrees).
        assertEquals(3334.0 / 3840.0, values.getValue("tr_crop_w").toDouble(), 0.001)
        val x = values.getValue("tr_crop_x").toDouble()
        assertEquals((1.0 - values.getValue("tr_crop_w").toDouble()) / 2.0, x, 1e-6)
    }

    @Test
    fun `zoom and pan give an off-center crop rectangle`() {
        val adjustments = AdjustmentsV1(zoom = 2.0f, panX = 1.0f, panY = 1.0f)
        val crop = MpvShaderParams.cropRect(adjustments, 0.0, video)

        assertEquals(0.5, crop.width, 1e-9)
        assertEquals(0.5, crop.height, 1e-9)
        assertEquals(0.5, crop.x, 1e-9) // pan right
        assertEquals(0.0, crop.y, 1e-9) // pan up
    }

    @Test
    fun `geometry waits for the video size`() {
        val values = parse(MpvShaderParams.build(AdjustmentsV1(zoom = 2.0f), null))

        assertEquals("1.000000", values["tr_crop_w"])
        assertEquals("0", values["tr_active"])
    }

    @Test
    fun `matrix coefficients follow the mpv color matrix name`() {
        assertEquals(0.2126 to 0.0722, MpvShaderParams.matrixCoefficients("bt.709"))
        assertEquals(0.2627 to 0.0593, MpvShaderParams.matrixCoefficients("bt.2020-ncl"))
        assertEquals(0.299 to 0.114, MpvShaderParams.matrixCoefficients("bt.601"))
        val full = parse(MpvShaderParams.build(AdjustmentsV1(), video.copy(colorLevels = "full")))
        assertTrue(full["tr_full_range"] == "1")
    }
}
