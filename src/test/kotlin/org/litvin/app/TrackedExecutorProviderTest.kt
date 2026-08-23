package org.litvin.app

import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

class TrackedExecutorProviderTest {
    @Test
    fun close_interruptsAndTerminatesEveryOwnedExecutorWithoutLeakingThreads() {
        val prefix = "executor-provider-test-${System.nanoTime()}"
        val provider = TrackedExecutorProvider(prefix, Duration.ofSeconds(5))
        val executor = provider.createExecutor("worker")
        val scheduler = provider.createScheduledExecutor("autosave")
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)

        executor.submit {
            started.countDown()
            release.await()
        }
        scheduler.submit {
            started.countDown()
            release.await()
        }

        assertTrue(started.await(5, TimeUnit.SECONDS), "Owned work did not start")

        provider.close()
        provider.close()

        assertTrue(executor.isTerminated, "Ordinary executor did not terminate")
        assertTrue(scheduler.isTerminated, "Scheduled executor did not terminate")
        assertTrue(
            Thread.getAllStackTraces().keys.none { it.isAlive && it.name.startsWith(prefix) },
            "A provider-owned thread remained alive",
        )
    }
}
