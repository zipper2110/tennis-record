package org.litvin.app

import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

interface ExecutorProvider : AutoCloseable {
    fun createExecutor(name: String): ExecutorService

    fun createScheduledExecutor(name: String): ScheduledExecutorService
}

class TrackedExecutorProvider(
    private val threadPrefix: String = "tennis-record",
    private val shutdownTimeout: Duration = Duration.ofSeconds(5),
) : ExecutorProvider {
    private val executors = CopyOnWriteArrayList<ExecutorService>()
    private val threadSequence = AtomicInteger(0)
    private val closed = AtomicBoolean(false)

    override fun createExecutor(name: String): ExecutorService =
        track(Executors.newSingleThreadExecutor(threadFactory(name)))

    override fun createScheduledExecutor(name: String): ScheduledExecutorService =
        track(Executors.newSingleThreadScheduledExecutor(threadFactory(name)))

    private fun threadFactory(name: String): ThreadFactory {
        require(name.isNotBlank()) { "Executor name must not be blank" }
        check(!closed.get()) { "Executor provider is closed" }
        return ThreadFactory { runnable ->
            Thread(runnable, "$threadPrefix-$name-${threadSequence.incrementAndGet()}")
        }
    }

    private fun <T : ExecutorService> track(executor: T): T {
        if (closed.get()) {
            executor.shutdownNow()
            error("Executor provider is closed")
        }
        executors.add(executor)
        if (closed.get() && executors.remove(executor)) {
            executor.shutdownNow()
            error("Executor provider is closed")
        }
        return executor
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        val owned = executors.toList()
        owned.forEach { it.shutdownNow() }
        val deadline = System.nanoTime() + shutdownTimeout.toNanos().coerceAtLeast(0L)
        owned.forEach { executor ->
            val remaining = deadline - System.nanoTime()
            if (remaining > 0L) {
                try {
                    executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
        }
    }
}
