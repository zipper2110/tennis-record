package org.litvin

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.WhiteBalanceV1
import kotlin.test.Test
import kotlin.test.assertTrue

class FFmpegCommandBuilderAdjustmentsTest {
    private val preset = ExportPresetsIO.load().let { presets ->
        presets[ExportPresetsIO.defaultBalancedIndex(presets)]
    }

    @Test
    fun build_includes_eq_filter_when_adjustments_non_default() {
        val adj = AdjustmentsV1(
            brightness = 0.1f, // non-identity
            contrast = 1.2f,
            saturation = 0.9f,
            whiteBalance = WhiteBalanceV1(temperature = 0.2f, tint = 0.1f)
        )
        val res = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "input.mp4",
                outputPath = "out.mp4",
                preset = preset,
                outWidth = 1920,
                outHeight = 1080,
                encoderLabel = "H.264 (libx264)",
                idleTrim = false,
                adjustments = adj,
            )
        )
        val vfIdx = res.args.indexOf("-vf")
        assertTrue(vfIdx >= 0, "-vf expected when no trim")
        val vf = res.args[vfIdx + 1]
        assertTrue(vf.contains("eq="), "Expected eq filter in vf, was: $vf")
    }
}
