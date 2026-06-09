package org.litvin

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompletedRendersStoreTest {
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = createTempDir(prefix = "trs-store-")
        System.setProperty("tennis.record.appDataDir", tempDir.absolutePath)
        ApplicationLayout.resetForTests()
        // Ensure clean
        CompletedRendersStore.clear()
    }

    @AfterTest
    fun tearDown() {
        try {
            System.clearProperty("tennis.record.appDataDir")
            ApplicationLayout.resetForTests()
            tempDir.deleteRecursively()
        } catch (_: Throwable) {}
    }

    @Test
    fun append_and_load_round_trip() {
        // Prepare a completed-like job
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

        // Append and reload
        CompletedRendersStore.append(job)
        val list = CompletedRendersStore.loadAll()
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

        // Clear works
        CompletedRendersStore.clear()
        assertTrue(CompletedRendersStore.loadAll().isEmpty())
    }
}
