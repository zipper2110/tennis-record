package org.litvin

import org.litvin.points.PointV1
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
                outputFrameRate = "30000/1001",
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
        val frameRateArgIndex = res.args.indexOf("-r")
        assertEquals("30000/1001", res.args[frameRateArgIndex + 1])
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
        // Each kept segment is seeked on its own input rather than trimmed after decoding
        assertEquals(2, res.args.count { it == "-i" }, "Expected one input per kept segment")
        val ssValues = res.args.withIndex().filter { it.value == "-ss" }.map { res.args[it.index + 1] }
        val tValues = res.args.withIndex().filter { it.value == "-t" }.map { res.args[it.index + 1] }
        assertEquals(listOf("0.000", "2.000"), ssValues, "Expected an input seek per segment start")
        assertEquals(listOf("1.000", "1.000"), tValues, "Expected an input duration per segment")
        // Every -ss/-t pair must precede its -i, otherwise it would seek the output instead
        res.args.withIndex().filter { it.value == "-ss" }.forEach { (idx, _) ->
            assertEquals("-t", res.args[idx + 2], "Expected the segment length right after the seek")
            assertEquals("-i", res.args[idx + 4], "-ss must be an input option")
        }
        // Filter complex should exist and concat the segments with correct n
        val fcIdx = res.args.indexOf("-filter_complex")
        assertTrue(fcIdx >= 0, "-filter_complex must be present for idleTrim with keeps")
        val fc = res.args[fcIdx + 1]
        assertTrue(!fc.contains("trim="), "Decoded-then-discarded trim must not be used, was: $fc")
        assertTrue(fc.contains("[0:v]setpts=PTS-STARTPTS"), "Expected first segment rebased, was: $fc")
        assertTrue(fc.contains("[1:v]setpts=PTS-STARTPTS"), "Expected second segment rebased, was: $fc")
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

    @Test
    fun build_balanced4k60_capsVideoAt20Mbps() {
        val res = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "input.mp4",
                outputPath = "out.mp4",
                preset = preset,
                outWidth = 3840,
                outHeight = 2160,
                outputFrameRate = "60",
                encoderLabel = "H.264 (libx264)",
                idleTrim = false,
            )
        )

        assertEquals("20000k", res.args[res.args.indexOf("-maxrate") + 1])
        assertEquals("40000k", res.args[res.args.indexOf("-bufsize") + 1])
    }
}
