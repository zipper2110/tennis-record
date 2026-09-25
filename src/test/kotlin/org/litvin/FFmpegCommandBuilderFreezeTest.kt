package org.litvin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Covers the freeze pass under the statistics card. */
class FFmpegCommandBuilderFreezeTest {
    private val preset = ExportPresetsIO.load().let { presets ->
        presets[ExportPresetsIO.defaultBalancedIndex(presets)]
    }

    private fun build(freeze: FFmpegCommandBuilder.FreezeFrame?, subtitles: String? = null) = FFmpegCommandBuilder.build(
        FFmpegCommandBuilder.BuildParams(
            sourcePath = "input.mp4",
            outputPath = "out.mp4.part.chunk3.ts",
            preset = preset,
            outWidth = 1920,
            outHeight = 1080,
            outputFrameRate = "30",
            encoderLabel = "H.264 (libx264)",
            idleTrim = freeze == null,
            subtitlesAssPath = subtitles,
            chunkOutput = true,
            videoBitrateK = 8_000,
            freezeFrame = freeze,
        )
    ).args

    private fun List<String>.valueOf(flag: String) = this[indexOf(flag) + 1]

    @Test
    fun theFreezeReadsAShortWindowBeforeTheEndOfTheLastPoint() {
        val args = build(FFmpegCommandBuilder.FreezeFrame(atMs = 125_000, durationMs = 12_000))

        assertEquals("124.750", args.valueOf("-ss"))
        assertEquals("0.250", args.valueOf("-t"))
        assertEquals(1, args.count { it == "-i" })
    }

    @Test
    fun withoutAPointTheFreezeReadsTheEndOfTheSource() {
        val args = build(FFmpegCommandBuilder.FreezeFrame(atMs = null, durationMs = 6_000))

        assertEquals("-0.250", args.valueOf("-sseof"))
        assertFalse("-ss" in args)
    }

    @Test
    fun theFreezeRepeatsTheLastFrameAndHasSilentAudioOfTheSameDuration() {
        val filter = build(FFmpegCommandBuilder.FreezeFrame(atMs = 5_000, durationMs = 12_000), "card.ass")
            .let { it.valueOf("-filter_complex") }

        assertTrue("tpad=stop_mode=clone" in filter, filter)
        assertTrue("trim=start=0.250" in filter, filter)
        assertTrue("trim=duration=12.000" in filter, filter)
        assertTrue("subtitles='card.ass'" in filter, filter)
        assertTrue("volume=0,apad,atrim=duration=12.000[aout]" in filter, filter)
    }

    @Test
    fun theFreezeUsesTheEncoderSettingsOfTheOtherChunks() {
        val freeze = build(FFmpegCommandBuilder.FreezeFrame(atMs = 5_000, durationMs = 6_000))
        val chunk = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "input.mp4",
                outputPath = "out.mp4.part.chunk0.ts",
                preset = preset,
                outWidth = 1920,
                outHeight = 1080,
                outputFrameRate = "30",
                encoderLabel = "H.264 (libx264)",
                idleTrim = true,
                keeps = listOf(org.litvin.points.PointV1(id = "a", startMs = 0, endMs = 5_000)),
                chunkOutput = true,
                videoBitrateK = 8_000,
            )
        ).args

        fun encoderPart(args: List<String>) = args.subList(args.indexOf("-c:v"), args.indexOf("-filter_complex"))
        assertEquals(encoderPart(chunk), encoderPart(freeze))
        assertEquals("mpegts", freeze.valueOf("-f"))
    }

    @Test
    fun aStillFrameIsTheLastFrameOfThePointWithTheColorAdjustments() {
        val adjustments = org.litvin.adjustments.AdjustmentsV1(brightness = 1.2f)

        val args = FFmpegCommandBuilder.stillFrameArgs("input.mp4", atMs = 125_000, outputPath = "frame.png", width = 1280, adjustments = adjustments)

        assertEquals("124.960", args.valueOf("-ss"))
        assertEquals("1", args.valueOf("-frames:v"))
        assertTrue(args.valueOf("-vf").startsWith("scale=1280:-2,eq="), args.valueOf("-vf"))
        assertEquals("frame.png", args.last())
    }
}
