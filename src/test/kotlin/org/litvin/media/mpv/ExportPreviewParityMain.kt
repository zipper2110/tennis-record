package org.litvin.media.mpv

import org.litvin.ExportPresetsIO
import org.litvin.FFmpegCommandBuilder
import org.litvin.adjustments.AdjustmentsV1

/**
 * Manual parity helper (not a unit test). Prints the export -vf filter and the preview shader options
 * for the same adjustments, so both can be rendered and compared.
 */
fun main() {
    val adjustments = AdjustmentsV1(rotationDeg = 7.5f, zoom = 1.4f, panX = -0.3f, panY = 0.6f, contrast = 1.3f)
    val presets = ExportPresetsIO.load()
    val args = FFmpegCommandBuilder.build(
        FFmpegCommandBuilder.BuildParams(
            sourcePath = "in.mkv",
            outputPath = "out.mkv",
            preset = presets[ExportPresetsIO.defaultBalancedIndex(presets)],
            outWidth = 3840,
            outHeight = 2160,
            idleTrim = false,
            adjustments = adjustments,
            sourceWidth = 3840,
            sourceHeight = 2160,
        )
    ).args
    println("VF=" + args[args.indexOf("-vf") + 1])
    println("OPTS=" + MpvShaderParams.build(adjustments, MpvVideoInfo(3840, 2160, "bt.709", "limited")))
}
