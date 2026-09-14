package org.litvin.analytics

import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsTransportTest {
    @Test
    fun `schedules HTTPS delivery on a daemon thread and never throws to the caller`() {
        val observedDaemon = booleanArrayOf(false)
        val completed = CountDownLatch(1)
        val transport = JdkAnalyticsTransport(URI("https://127.0.0.1:1/v1/events/batch")) { daemon ->
            observedDaemon[0] = daemon
            completed.countDown()
        }

        transport.send("{\"events\":[]}")
        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertTrue(observedDaemon[0])
        transport.close()
        assertFalse(transport.isOpen())
    }
}
