package org.litvin.export

import org.litvin.CompletedRender
import org.litvin.RenderJob
import org.litvin.points.PointV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExportCardInfoTest {
    @Test
    fun activeJobShowsItsPathSettingsAndSizeAgainstTheExpectedSize() {
        val job = RenderJob(
            projectName = "Club final",
            sourcePath = "D:/src.mp4",
            edlSnapshot = listOf(PointV1(id = "p1", startMs = 0, endMs = 1_000), PointV1(id = "p2", startMs = 2_000, endMs = 3_000)),
            presetId = "fast",
            outWidth = 1920,
            outHeight = 1080,
            outputFrameRate = "30",
            videoBitrateK = 6_000,
            expectedBytes = 480_000_000,
            encoderLabel = "H.264 (NVENC)",
            idleTrim = true,
            includeScoreboard = true,
            outputPath = "D:/exports/final.mp4",
        ).apply { bytesWritten = 120_500_000 }

        val info = ExportCardInfo.of(job)

        assertEquals("D:/exports/final.mp4", info.outputPath)
        assertEquals("Club final", info.projectName)
        assertEquals("Only points (2 points)  ·  Scoreboard: on  ·  Comments: off", info.content)
        assertEquals("Fast export  ·  1080p  ·  30 FPS  ·  6.0 Mbit/s  ·  H.264 (NVENC)", info.video)
        assertEquals("120.50 MB / ~480.00 MB", info.size)
    }

    @Test
    fun oldCompletedEntryWithoutTheNewFieldsStillShowsItsSettings() {
        val item = CompletedRender(
            id = "job-1",
            projectName = " ",
            outputPath = "D:/exports/full.mp4",
            fileName = "full.mp4",
            encoderLabel = "H.264 (libx264)",
            outWidth = 3840,
            outHeight = 2160,
            bytesWritten = 2_000_000_000,
        )

        val info = ExportCardInfo.of(item)

        assertNull(info.projectName)
        assertEquals("Full video  ·  Scoreboard: off  ·  Comments: off", info.content)
        assertEquals("4K  ·  Original FPS  ·  H.264 (libx264)", info.video)
        assertEquals("2.00 GB", info.size)
    }
}
