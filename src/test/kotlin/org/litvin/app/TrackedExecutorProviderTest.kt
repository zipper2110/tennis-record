package org.litvin.app

import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TrackedExecutorProviderTest {
    @Test
    fun executorNamesIncludeProviderPrefixRequestedNameAndDeterministicSequence() {
        val provider = TrackedExecutorProvider("deterministic-provider", Duration.ofSeconds(5))
        try {
            val first = provider.createExecutor("worker")
            val second = provider.createScheduledExecutor("autosave")

            assertEquals("deterministic-provider-worker-1", first.submit<String> { Thread.currentThread().name }.get())
            assertEquals("deterministic-provider-autosave-2", second.submit<String> { Thread.currentThread().name }.get())
        } finally {
            provider.close()
        }
    }

    @Test
    fun creationIsRejectedAfterClose() {
        val provider = TrackedExecutorProvider("closed-provider", Duration.ofSeconds(5))
        provider.close()

        assertFailsWith<IllegalStateException> { provider.createExecutor("worker") }
        assertFailsWith<IllegalStateException> { provider.createScheduledExecutor("autosave") }
    }

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
