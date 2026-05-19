package org.litvin.shared.util

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Simple debounced saver utility to coalesce rapid save requests.
 * - Call request() to schedule a save after [delayMs]. Subsequent requests reset the timer.
 * - Call flush() to run immediately (if pending) on the current thread.
 * - Call close() to shutdown the scheduler (optionally flush first).
 *
 * Intended for autosaving small JSON files (EDL/Score/Adjustments) with a 300–500 ms debounce.
 */
class DebouncedSaver(
    private val delayMs: Long = 300,
    private val saveAction: () -> Unit
) : AutoCloseable {

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "debounced-saver").apply { isDaemon = true }
    }

    @Volatile private var future: ScheduledFuture<*>? = null
    private val running = AtomicBoolean(false)

    @Synchronized
    fun request() {
        // cancel previous task if any
        future?.cancel(false)
        future = scheduler.schedule({
            if (running.compareAndSet(false, true)) {
                try { saveAction.invoke() } finally { running.set(false) }
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun flush() {
        // if a task is scheduled, cancel and run now on caller thread
        val hadPending = future?.let { !it.isDone && !it.isCancelled } ?: false
        future?.cancel(false)
        future = null
        if (hadPending) {
            if (running.compareAndSet(false, true)) {
                try { saveAction.invoke() } finally { running.set(false) }
            }
        }
    }

    override fun close() {
        try {
            scheduler.shutdownNow()
        } catch (_: Throwable) {}
    }
}
