package org.litvin.ui.tabs.stats

import org.litvin.adjustments.AdjustmentsV1
import java.awt.image.BufferedImage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StatsFrameLoaderTest {
    private val request = StatsFrameRequest("match.mp4", 60_000, AdjustmentsV1())

    @Test
    fun theLoaderReadsAFrameOnceAndThenUsesTheCache() {
        val image = BufferedImage(16, 9, BufferedImage.TYPE_INT_RGB)
        var reads = 0
        val loader = StatsFrameLoader { reads++; image }

        assertSame(image, loadAndWait(loader))
        assertSame(image, loadAndWait(loader))
        assertEquals(1, reads)
        assertSame(image, loader.cached(request))
    }

    @Test
    fun aFailedReadGivesNoImageAndCallsBackOnTheEventThread() {
        var onEventThread = false
        val loader = StatsFrameLoader { error("ffmpeg failed") }
        val done = CountDownLatch(1)
        var result: BufferedImage? = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)

        loader.load(request) { image ->
            onEventThread = SwingUtilities.isEventDispatchThread()
            result = image
            done.countDown()
        }

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(null, result)
        assertTrue(onEventThread)
    }

    private fun loadAndWait(loader: StatsFrameLoader): BufferedImage? {
        val done = CountDownLatch(1)
        var result: BufferedImage? = null
        loader.load(request) { image ->
            result = image
            done.countDown()
        }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        return result
    }
}
