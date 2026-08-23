package org.litvin

import org.litvin.export.FileCompletedRendersRepository
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompletedRendersStoreTest {
    @Test
    fun repositoriesRemainIsolatedByTheirInjectedFiles() {
        val tempDir = kotlin.io.path.createTempDirectory("trs-store-").toFile()
        try {
            val first = FileCompletedRendersRepository(File(tempDir, "first.json"))
            val second = FileCompletedRendersRepository(File(tempDir, "second.json"))
            val job = RenderJob(
                id = "job-1",
                projectId = null,
                sourcePath = "C:/vids/src.mp4",
                edlSnapshot = emptyList(),
                presetId = "balanced",
                outWidth = 1920,
                outHeight = 1080,
                encoderLabel = "H.264 (libx264)",
                idleTrim = false,
                includeScoreboard = true,
                overlayTimeline = emptyList(),
                outputPath = File(tempDir, "out.mp4").absolutePath,
            )
            job.status = RenderStatus.COMPLETED
            job.bytesWritten = 1234567

            first.append(job)
            val list = first.loadAll()
            assertEquals(1, list.size)
            val item = list.first()
            assertEquals(job.id, item.id)
            assertEquals(File(job.outputPath).name, item.fileName)
            assertEquals(job.outputPath, item.outputPath)
            assertEquals(job.encoderLabel, item.encoderLabel)
            assertEquals(job.outWidth, item.outWidth)
            assertEquals(job.outHeight, item.outHeight)
            assertEquals(job.bytesWritten, item.bytesWritten)
            assertTrue(item.includeScoreboard)

            assertTrue(second.loadAll().isEmpty())
            first.clear()
            assertTrue(first.loadAll().isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
