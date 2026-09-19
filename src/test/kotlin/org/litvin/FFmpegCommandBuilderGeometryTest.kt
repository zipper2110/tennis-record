package org.litvin

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.GeometryPlan
import org.litvin.markup.PointV1
import org.litvin.media.mpv.MpvShaderParams
import org.litvin.media.mpv.MpvVideoInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FFmpegCommandBuilderGeometryTest {
    private val preset = ExportPresetsIO.load().let { presets ->
        presets[ExportPresetsIO.defaultBalancedIndex(presets)]
    }

    private fun build(adjustments: AdjustmentsV1?, idleTrim: Boolean = false, subtitles: String? = null) =
        FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "input.mp4",
                outputPath = "out.mp4",
                preset = preset,
                outWidth = 1920,
                outHeight = 1080,
                idleTrim = idleTrim,
                keeps = if (idleTrim) listOf(PointV1(id = "A", startMs = 0, endMs = 1000)) else emptyList(),
                subtitlesAssPath = subtitles,
                adjustments = adjustments,
                sourceWidth = 3840,
                sourceHeight = 2160,
            )
        ).args

    private fun vf(args: List<String>): String = args[args.indexOf("-vf") + 1]

    @Test
    fun `identity geometry adds no rotate or crop filter`() {
        val filter = vf(build(AdjustmentsV1()))

        assertFalse(filter.contains("rotate="), filter)
        assertFalse(filter.contains("crop="), filter)
    }

    @Test
    fun `rotation adds rotate and the inscribed crop before the scale filter`() {
        val filter = vf(build(AdjustmentsV1(rotationDeg = 5.0f)))

        assertTrue(filter.startsWith("rotate=0.087266:ow=iw:oh=ih:c=black,crop=3334:1876:252:142,"), filter)
        assertTrue(filter.indexOf("crop=") < filter.indexOf("scale="), filter)
    }

    @Test
    fun `zoom and pan add only a crop in even source pixels`() {
        val filter = vf(build(AdjustmentsV1(zoom = 2.0f, panX = 1.0f, panY = 1.0f)))

        assertEquals("crop=1920:1080:1920:0,scale=1920:-2", filter)
    }

    @Test
    fun `unknown source size falls back to a crop with frame fractions`() {
        val args = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "input.mp4",
                outputPath = "out.mp4",
                preset = preset,
                outWidth = 1920,
                outHeight = 1080,
                idleTrim = false,
                adjustments = AdjustmentsV1(zoom = 2.0f, panX = 1.0f, panY = 1.0f),
            )
        ).args

        assertEquals("crop=w=iw*0.500000:h=ih*0.500000:x=iw*0.500000:y=ih*0.000000,scale=1920:-2", vf(args))
    }

    @Test
    fun `geometry comes before color and subtitles`() {
        val filter = vf(build(AdjustmentsV1(rotationDeg = -3.0f, contrast = 1.3f), subtitles = "overlay.ass"))

        val rotate = filter.indexOf("rotate=")
        val scale = filter.indexOf("scale=")
        val eq = filter.indexOf("eq=")
        val subtitles = filter.indexOf("subtitles=")
        assertTrue(rotate in 0 until scale && scale < eq && eq < subtitles, filter)
    }

    @Test
    fun `idle trim path applies the geometry after concat`() {
        val args = build(AdjustmentsV1(zoom = 1.5f), idleTrim = true)
        val graph = args[args.indexOf("-filter_complex") + 1]

        assertTrue(graph.contains("[vcat]crop=2560:1440:640:360,scale=1920:-2[vsc]"), graph)
    }

    @Test
    fun `export and preview use the same crop rectangle`() {
        val adjustments = AdjustmentsV1(rotationDeg = 7.5f, zoom = 1.4f, panX = -0.3f, panY = 0.6f)
        val plan = GeometryPlan.of(adjustments, 3840, 2160)
        val shader = MpvShaderParams.build(adjustments, MpvVideoInfo(3840, 2160, "bt.709", "limited"))
            .split(",").associate { it.substringBefore("=") to it.substringAfter("=").toDouble() }

        assertEquals(plan.crop.x, shader.getValue("tr_crop_x"), 1e-6)
        assertEquals(plan.crop.y, shader.getValue("tr_crop_y"), 1e-6)
        assertEquals(plan.crop.width, shader.getValue("tr_crop_w"), 1e-6)
        assertEquals(plan.crop.height, shader.getValue("tr_crop_h"), 1e-6)
        assertEquals(plan.rotationDeg, shader.getValue("tr_rotation_deg"), 1e-6)
        val (w, h, x, y) = plan.cropPixels(3840, 2160).toList()
        assertTrue(vf(build(adjustments)).contains("crop=$w:$h:$x:$y,"), vf(build(adjustments)))
        assertTrue(listOf(w, h, x, y).all { it % 2 == 0 }, "even pixels: $w $h $x $y")
    }
}
