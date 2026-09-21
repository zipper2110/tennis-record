package org.litvin

import org.litvin.export.FileCompletedRendersRepository
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompletedRendersStoreTest {
    @Test
    fun repositoryMapsJobsWithItsClockAndRemainsIsolatedByInjectedFile() {
        val tempDir = kotlin.io.path.createTempDirectory("trs-store-").toFile()
        try {
            val first = FileCompletedRendersRepository(File(tempDir, "first.json")) { 1_234L }
            val second = FileCompletedRendersRepository(File(tempDir, "second.json"))
            val job = RenderJob(
                id = "job-1",
                projectId = null,
                sourcePath = "C:/vids/src.mp4",
                edlSnapshot = emptyList(),
                presetId = "balanced",
                outWidth = 1920,
                outHeight = 1080,
                outputFrameRate = "30000/1001",
                encoderLabel = "H.264 (libx264)",
                idleTrim = true,
                favoriteOnly = true,
                includeScoreboard = true,
                includeComments = true,
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
            assertEquals(job.outputFrameRate, item.outputFrameRate)
            assertEquals(job.bytesWritten, item.bytesWritten)
            assertTrue(item.idleTrim)
            assertTrue(item.favoriteOnly)
            assertTrue(item.includeScoreboard)
            assertTrue(item.includeComments)
            assertEquals(1_234L, item.createdAtEpochMs)

            assertTrue(second.loadAll().isEmpty())
            first.clear()
            assertTrue(first.loadAll().isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun storeRoundTripsACompletedRenderWithoutOwningJobMappingOrTime() {
        val tempDir = kotlin.io.path.createTempDirectory("trs-format-").toFile()
        try {
            val file = File(tempDir, "records.json")
            val record = CompletedRender(
                id = "record-1",
                outputPath = "C:/videos/result.mp4",
                fileName = "result.mp4",
                encoderLabel = "H.264",
                outWidth = 1280,
                outHeight = 720,
                bytesWritten = 42L,
                createdAtEpochMs = 987_654L,
            )

            CompletedRendersStore.append(file, record)

            assertEquals(listOf(record), CompletedRendersStore.loadAll(file))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun concurrentAppendsRetainEveryRecord() {
        val tempDir = kotlin.io.path.createTempDirectory("trs-concurrent-format-").toFile()
        val file = File(tempDir, "records.json")
        val alias = File(tempDir, ".${File.separator}records.json")
        val baselineLockEntries = CompletedRendersStore.activeLockEntryCount
        val writers = 16
        val recordsPerWriter = 4
        val ready = CountDownLatch(writers)
        val start = CountDownLatch(1)
        val done = CountDownLatch(writers)
        val executor = Executors.newFixedThreadPool(writers)
        try {
            repeat(writers) { writer ->
                executor.execute {
                    ready.countDown()
                    start.await()
                    repeat(recordsPerWriter) { record ->
                        val id = "$writer-$record"
                        CompletedRendersStore.append(
                            if (writer % 2 == 0) file else alias,
                            CompletedRender(
                                id = id,
                                outputPath = "C:/videos/$id.mp4",
                                fileName = "$id.mp4",
                                encoderLabel = "H.264",
                                outWidth = 640,
                                outHeight = 360,
                                bytesWritten = 1L,
                            ),
                        )
                    }
                    done.countDown()
                }
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS), "Writers did not become ready")
            start.countDown()
            assertTrue(done.await(20, TimeUnit.SECONDS), "Concurrent appends did not finish")

            val records = CompletedRendersStore.loadAll(file)
            assertEquals(writers * recordsPerWriter, records.size)
            assertEquals(writers * recordsPerWriter, records.map { it.id }.toSet().size)
            assertEquals(baselineLockEntries, CompletedRendersStore.activeLockEntryCount)
        } finally {
            executor.shutdownNow()
            tempDir.deleteRecursively()
        }
    }
}
