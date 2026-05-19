package org.litvin

import org.litvin.markup.PointV1
import kotlin.test.Test
import kotlin.test.assertTrue

class FFmpegCommandBuilderOverlayTest {
    private val preset = ExportPresetsIO.load().let { presets ->
        presets[ExportPresetsIO.defaultBalancedIndex(presets)]
    }

    @Test
    fun idleTrim_ON_concat_scale_then_subtitles_and_maps() {
        val keeps = listOf(
            PointV1(id = "A", startMs = 0, endMs = 1000),
            PointV1(id = "B", startMs = 2000, endMs = 3000),
        )
        val res = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "input.mp4",
                outputPath = "out.mp4",
                preset = preset,
                outWidth = 1920,
                outHeight = 1080,
                encoderLabel = "H.264 (libx264)",
                idleTrim = true,
                keeps = keeps,
                subtitlesAssPath = "C:/tmp/overlay.ass",
            )
        )
        val fcIdx = res.args.indexOf("-filter_complex")
        assertTrue(fcIdx >= 0, "-filter_complex expected when idleTrim and keeps present")
        val fc = res.args[fcIdx + 1]
        // Expect concat label -> scale -> subtitles -> [vout]
        assertTrue(fc.contains("concat=n=2:v=1:a=0[vcat]"), "Expected video concat to vcat: $fc")
        assertTrue(fc.contains("[vcat]scale=1920:-2[vsc]"), "Expected scale after concat to vsc: $fc")
        assertTrue(fc.contains("[vsc]subtitles='"), "Expected subtitles after scaling: $fc")
        assertTrue(fc.contains("[aout]"), "Expected audio mapping present: $fc")
        // Ensure -map entries exist for vout and aout
        val maps = res.args.withIndex().filter { it.value == "-map" }
        assertTrue(maps.size >= 2, "Expected at least two -map entries for vout/aout")
    }
}
