package org.litvin.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExportVideoOptionsTest {
    private val source4k60 = ExportSourceInfo(
        resolution = ExportResolution("4K", 3840, 2160),
        frameRate = ExportFrameRates.parse("60/1"),
        bitrate = ExportSourceBitrate(50_000_000),
        durationMs = 600_000,
    )

    @Test
    fun `best quality keeps the resolution, frame rate and bitrate of the source`() {
        val target = ExportVideoOptions.simpleTarget(ExportSimplePreset.BEST, source4k60)

        assertEquals("best", target.presetId)
        assertEquals(3840 to 2160, target.resolution.width to target.resolution.height)
        assertEquals("60/1", target.frameRate?.ffmpegArgument)
        assertEquals(50_000, target.bitrateK)
    }

    @Test
    fun `balanced uses three quarters of the source bitrate at the source size`() {
        val target = ExportVideoOptions.simpleTarget(ExportSimplePreset.BALANCED, source4k60)

        assertEquals(3840 to 2160, target.resolution.width to target.resolution.height)
        assertEquals(37_500, target.bitrateK)
    }

    @Test
    fun `fast scales down to 1080p with the source aspect and halves the bitrate`() {
        val fourByThree = source4k60.copy(resolution = ExportResolution("2880x2160", 2880, 2160))

        val target = ExportVideoOptions.simpleTarget(ExportSimplePreset.FAST, fourByThree)

        assertEquals(1440 to 1080, target.resolution.width to target.resolution.height)
        assertEquals(25_000, target.bitrateK)
    }

    @Test
    fun `fast does not scale up a source below 1080p`() {
        val small = source4k60.copy(resolution = ExportResolution("720p", 1280, 720))

        val target = ExportVideoOptions.simpleTarget(ExportSimplePreset.FAST, small)

        assertEquals(1280 to 720, target.resolution.width to target.resolution.height)
    }

    @Test
    fun `resolution cards above the source are unavailable and the source card is marked`() {
        val choices = ExportVideoOptions.resolutionChoices(ExportResolution("1080p", 1920, 1080))

        assertEquals(listOf("720p", "1080p", "4K"), choices.map { it.title })
        assertEquals(listOf(true, true, false), choices.map { it.available })
        assertEquals(listOf(false, true, false), choices.map { it.isSource })
        assertEquals("1080p", ExportVideoOptions.defaultResolution(choices).title)
    }

    @Test
    fun `a source with a non-standard size gets its own card`() {
        val choices = ExportVideoOptions.resolutionChoices(ExportResolution("2704x1520", 2704, 1520))

        assertEquals(listOf("720p", "1080p", "2704×1520", "4K"), choices.map { it.title })
        assertEquals(listOf(true, true, true, false), choices.map { it.available })
        assertEquals("2704×1520", ExportVideoOptions.defaultResolution(choices).title)
        // 2704x1520 is not exactly 16:9, so the width keeps the aspect ratio of the source.
        assertEquals(1922, choices[1].resolution.width)
        assertEquals(1080, choices[1].resolution.height)
    }

    @Test
    fun `common sizes show a short name and other sizes show pixels`() {
        assertEquals("1080p", ExportVideoOptions.displayResolution(1920, 1080))
        assertEquals("2K", ExportVideoOptions.displayResolution(2560, 1440))
        assertEquals("4K", ExportVideoOptions.displayResolution(3840, 2158))
        assertEquals("720×486", ExportVideoOptions.displayResolution(720, 486))
        assertEquals("2K", ExportVideoOptions.resolutionChoices(ExportResolution("2K", 2560, 1440)).single { it.isSource }.title)
    }

    @Test
    fun `frame rate cards mark a source rate near a standard rate`() {
        val choices = ExportVideoOptions.frameRateChoices(ExportFrameRates.parse("30000/1001"))

        assertEquals(listOf("24 fps", "29.97 fps", "60 fps"), choices.map { it.title })
        assertEquals(listOf(true, true, false), choices.map { it.available })
        val selected = ExportVideoOptions.defaultFrameRate(choices)
        assertTrue(selected.isSource)
        assertEquals("30000/1001", selected.frameRate.ffmpegArgument)
    }

    @Test
    fun `a 50 fps source gets its own card between 30 and 60`() {
        val choices = ExportVideoOptions.frameRateChoices(ExportFrameRates.parse("50"))

        assertEquals(listOf("24 fps", "30 fps", "50 fps", "60 fps"), choices.map { it.title })
        assertEquals(listOf(true, true, true, false), choices.map { it.available })
        assertNull(choices[2].level)
    }

    @Test
    fun `bitrate slider goes from a fifth of the source to the source`() {
        val range = ExportVideoOptions.bitrateRange(source4k60)

        assertEquals(ExportBitrateRange(maxK = 50_000, minK = 10_000, estimated = false), range)
    }

    @Test
    fun `an unknown source bitrate uses a typical camera bitrate`() {
        val range = ExportVideoOptions.bitrateRange(source4k60.copy(bitrate = null))

        assertEquals(67_500, range.maxK)
        assertTrue(range.estimated)
    }

    @Test
    fun `estimated size adds the audio bitrate`() {
        // (8000 + 192) kbit/s for 10 seconds is 10.24 MB.
        assertEquals(10_240_000, ExportVideoOptions.estimatedBytes(8_000, 10_000))
    }

    @Test
    fun `parses the flat ffprobe output of the source`() {
        val info = ExportSourceProbe.parseFlat(
            """
            streams.stream.0.width=720
            streams.stream.0.height=486
            streams.stream.0.r_frame_rate="30/1"
            streams.stream.0.avg_frame_rate="0/0"
            streams.stream.0.bit_rate="N/A"
            format.duration="4.480000"
            format.bit_rate="2526850"
            """.trimIndent(),
        )

        assertEquals(ExportResolution("720x486", 720, 486), info.resolution)
        assertEquals("30/1", info.frameRate?.ffmpegArgument)
        assertEquals(ExportSourceBitrate(2_526_850), info.bitrate)
        assertEquals(4_480, info.durationMs)
        assertEquals(false, info.variableFrameRate)
    }

    @Test
    fun `a phone video that changes between 60 and 30 fps keeps 60 fps and is marked as variable`() {
        // Values of a Pixel recording: 60 fps at the start, then 30 fps for most of the match.
        val info = ExportSourceProbe.parseFlat(
            """
            streams.stream.0.r_frame_rate="60/1"
            streams.stream.0.avg_frame_rate="1986146859/53830756"
            """.trimIndent(),
        )

        assertEquals("60/1", info.frameRate?.ffmpegArgument)
        assertEquals("36.90", info.averageFrameRate?.displayFps)
        assertTrue(info.variableFrameRate)
    }

    @Test
    fun `a time base in r_frame_rate falls back to the average rate`() {
        val info = ExportSourceProbe.parseFlat(
            """
            streams.stream.0.r_frame_rate="90000/1"
            streams.stream.0.avg_frame_rate="30000/1001"
            """.trimIndent(),
        )

        assertEquals("30000/1001", info.frameRate?.ffmpegArgument)
        assertEquals(false, info.variableFrameRate)
    }
}
