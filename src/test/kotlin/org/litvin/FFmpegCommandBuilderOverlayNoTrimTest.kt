package org.litvin

import kotlin.test.Test
import kotlin.test.assertTrue

class FFmpegCommandBuilderOverlayNoTrimTest {
    private val preset = ExportPresetsIO.load().let { presets ->
        presets[ExportPresetsIO.defaultBalancedIndex(presets)]
    }

    @Test
    fun idleTrim_OFF_uses_vf_with_scale_then_subtitles() {
        val res = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "input.mp4",
                outputPath = "out.mp4",
                preset = preset,
                outWidth = 1920,
                outHeight = 1080,
                encoderLabel = "H.264 (libx264)",
                idleTrim = false,
                keeps = emptyList(),
                subtitlesAssPath = "C:/tmp/overlay.ass",
            )
        )
        val vfIdx = res.args.indexOf("-vf")
        assertTrue(vfIdx >= 0, "-vf expected when idleTrim is false")
        val vf = res.args[vfIdx + 1]
        // Expect scale first and subtitles second to match render flow
        assertTrue(vf.contains("scale=1920:-2"), "Expected scale in -vf: $vf")
        assertTrue(vf.contains("subtitles='C\\:/tmp/overlay.ass'"), "Expected subtitles in -vf: $vf")
        val scaleIndex = vf.indexOf("scale=")
        val subIndex = vf.indexOf("subtitles=")
        assertTrue(scaleIndex >= 0 && subIndex > scaleIndex, "Expected scale before subtitles: $vf")
    }
}
