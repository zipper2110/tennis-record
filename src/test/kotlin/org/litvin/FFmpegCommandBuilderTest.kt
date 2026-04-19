package org.litvin

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class FFmpegCommandBuilderTest {
    private val preset = ExportPresetsIO.load().let { presets ->
        presets[ExportPresetsIO.defaultBalancedIndex(presets)]
    }

    @Test
    fun build_noTrim_simpleScale_x264() {
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
                subtitlesAssPath = null,
            )
        )
        // Should use -vf scale and not -filter_complex
        assertTrue(res.args.contains("-vf"), "Expected -vf to be present")
        val vfIdx = res.args.indexOf("-vf")
        assertTrue(vfIdx >= 0 && vfIdx + 1 < res.args.size)
        val vf = res.args[vfIdx + 1]
        assertTrue(vf.contains("scale=1920:-2"), "Expected scale filter to be present in -vf, was: $vf")
        // Should use libx264 options
        assertTrue(res.args.contains("-c:v"))
        assertTrue(res.args.contains("libx264"))
        // Preview must include ffmpeg prefix
        assertTrue(res.preview.startsWith("ffmpeg "), "Preview should start with 'ffmpeg '")
    }

    @Test
    fun build_noTrim_withSubtitles_chainOrder() {
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
                subtitlesAssPath = "C:/tmp/score.ass",
            )
        )
        val vf = res.args[res.args.indexOf("-vf") + 1]
        // Expect scale then subtitles burn-in
        assertTrue(vf.contains("scale=1920:-2"))
        assertTrue(vf.contains("subtitles='"))
        assertTrue(vf.endsWith(".ass'"), "Expected subtitles path to end with .ass'")
    }

    @Test
    fun build_idleTrim_concat_and_maps_present() {
        val keeps = listOf(
            PointV1(id = "A", startMs = 0, endMs = 1000),
            PointV1(id = "B", startMs = 2000, endMs = 3000),
        )
        val res = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "input.mp4",
                outputPath = "out.mp4",
                preset = preset,
                outWidth = 1280,
                outHeight = 720,
                encoderLabel = "H.264 (libx264)",
                idleTrim = true,
                keeps = keeps,
                subtitlesAssPath = null,
            )
        )
        // Filter complex should exist and contain trim + concat with correct n
        val fcIdx = res.args.indexOf("-filter_complex")
        assertTrue(fcIdx >= 0, "-filter_complex must be present for idleTrim with keeps")
        val fc = res.args[fcIdx + 1]
        assertTrue(fc.contains("trim=start="))
        assertTrue(fc.contains("concat=n=2"), "Expected concat n=2, was: $fc")
        // Ensure mapping is present
        val mapIdx = res.args.indexOf("-map")
        assertTrue(mapIdx >= 0, "Expected -map entries present for vout/aout")
        // Output path is last
        assertEquals("out.mp4", res.args.last())
    }

    @Test
    fun build_hw_nvenc_maps_crf_to_cq_and_preset() {
        val res = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "input.mp4",
                outputPath = "out.mp4",
                preset = preset.copy(video = preset.video.copy(crf = 21, x264Preset = "medium")),
                outWidth = 1920,
                outHeight = 1080,
                encoderLabel = "H.264 NVENC",
                idleTrim = false,
            )
        )
        // Expect h264_nvenc codec, -cq mapped from CRF, and -preset set
        val argsStr = res.args.joinToString(" ")
        assertTrue(argsStr.contains("-c:v h264_nvenc"), "Expected NVENC codec")
        assertTrue(argsStr.contains("-cq 21"), "Expected NVENC CQ mapped from CRF=21")
        assertTrue(argsStr.contains("-preset p5"), "Expected medium → p5 preset for NVENC")
    }
}
