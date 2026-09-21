package org.litvin

import org.litvin.points.PointV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Covers the pieces that only a chunked export exercises. */
class FFmpegCommandBuilderChunkTest {
    private val preset = ExportPresetsIO.load().let { presets ->
        presets[ExportPresetsIO.defaultBalancedIndex(presets)]
    }

    private val keeps = listOf(
        PointV1(id = "A", startMs = 60_000, endMs = 65_000),
        PointV1(id = "B", startMs = 120_000, endMs = 124_000),
    )

    private fun build(
        outputTimeOffsetMs: Long = 0,
        chunkOutput: Boolean = false,
        subtitles: String? = null,
    ) = FFmpegCommandBuilder.build(
        FFmpegCommandBuilder.BuildParams(
            sourcePath = "input.mp4",
            outputPath = if (chunkOutput) "out.mp4.part.chunk1.ts" else "out.mp4",
            preset = preset,
            outWidth = 1920,
            outHeight = 1080,
            encoderLabel = "H.264 (libx264)",
            idleTrim = true,
            keeps = keeps,
            subtitlesAssPath = subtitles,
            outputTimeOffsetMs = outputTimeOffsetMs,
            chunkOutput = chunkOutput,
        )
    )

    @Test
    fun seekIsProportionalToKeptDuration_notSourceSpan() {
        val args = build().args
        // Inputs carry the segment start and its own length, so nothing before 60s is ever decoded.
        val seeks = args.withIndex().filter { it.value == "-ss" }.map { args[it.index + 1] }
        val durations = args.withIndex().filter { it.value == "-t" }.map { args[it.index + 1] }
        assertEquals(listOf("60.000", "120.000"), seeks)
        assertEquals(listOf("5.000", "4.000"), durations)
    }

    @Test
    fun chunkOutput_usesTransportStreamAndSkipsFaststart() {
        val args = build(chunkOutput = true).args
        assertEquals("mpegts", args[args.indexOf("-f") + 1])
        assertFalse(args.contains("-movflags"), "A chunk is remuxed later, so faststart is pointless")
    }

    @Test
    fun finalOutput_keepsPresetContainer() {
        val args = build(chunkOutput = false).args
        assertEquals(preset.container?.format, args[args.indexOf("-f") + 1])
    }

    @Test
    fun subtitlesWithoutOffset_burnInDirectly() {
        val fc = build(subtitles = "C:/tmp/score.ass").args.let { it[it.indexOf("-filter_complex") + 1] }
        assertTrue(fc.contains("subtitles='"), "Expected a burn-in, was: $fc")
        assertFalse(fc.contains("setpts=PTS+"), "The first chunk needs no timeline shift, was: $fc")
    }

    @Test
    fun subtitlesWithOffset_shiftOntoOutputTimelineThenRebase() {
        // A later chunk's frames start at zero, but the ASS is written on the whole-output timeline.
        val fc = build(outputTimeOffsetMs = 90_500, subtitles = "C:/tmp/score.ass")
            .args.let { it[it.indexOf("-filter_complex") + 1] }
        val shift = "setpts=PTS+90.500/TB,subtitles='"
        assertTrue(fc.contains(shift), "Expected the burn-in to see output time, was: $fc")
        // And the encoded chunk itself must still start at zero so the concat join lines up.
        val afterSubtitles = fc.substringAfter(shift).substringAfter(".ass'")
        assertTrue(afterSubtitles.startsWith(",setpts=PTS-STARTPTS"), "Expected a rebase after burn-in, was: $fc")
    }
}
