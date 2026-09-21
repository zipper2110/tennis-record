package org.litvin.ui.tabs.points

import java.awt.EventQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Timer

/**
 * Debounced autosave controller for the Points tab.
 *
 * - Schedules autosave with a debounce timer (no EDT blocking)
 * - Executes provided [saver] off-EDT
 * - Notifies UI about state changes via [onStateChanged] on the EDT
 */
class AutosaveController(
    debounceMs: Int = 300,
    private val executor: ExecutorService,
    private val saver: () -> Unit
) : AutoCloseable {
    private val timer = Timer(debounceMs) { _ -> triggerSave() }.apply { isRepeats = false }
    private val closed = AtomicBoolean(false)
    private val saveLock = Any()
    private var inFlightSave: Future<*>? = null

    @Volatile
    private var _lastSavedAtMs: Long? = null

    fun schedule() {
        check(!closed.get()) { "Autosave controller is closed" }
        timer.restart()
    }

    fun autosaveNow() {
        if (timer.isRunning) timer.stop()
        triggerSave()
    }

    /**
     * Saves pending changes and waits for them to reach disk. Callers that hand the project over to
     * another view (a tab switch) need the file to be current before that view reads it.
     */
    fun flush() {
        if (closed.get()) return
        if (timer.isRunning) timer.stop()
        triggerSave()
        val pending = synchronized(saveLock) { inFlightSave }
        try {
            pending?.get()
        } catch (_: Exception) {
            // The saver reports its own failures; waiting for it must not fail the caller.
        }
    }

    fun isPending(): Boolean = try {
        timer.isRunning
    } catch (_: Throwable) {
        false
    }

    val lastSavedAtMs: Long?
        get() = _lastSavedAtMs

    private fun triggerSave() {
        synchronized(saveLock) {
            if (closed.get()) return
            inFlightSave = executor.submit {
                saver()
                _lastSavedAtMs = System.currentTimeMillis()
            }
        }
    }

    fun flushAndClose() {
        if (!closed.compareAndSet(false, true)) return
        val hadPendingTimer = timer.isRunning
        timer.stop()
        val pending = synchronized(saveLock) {
            if (hadPendingTimer) {
                executor.submit {
                    saver()
                    _lastSavedAtMs = System.currentTimeMillis()
                }.also { inFlightSave = it }
            } else {
                inFlightSave
            }
        }
        try {
            pending?.get()
        } finally {
            executor.shutdown()
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                executor.shutdownNow()
            }
        }
    }

    override fun close() = flushAndClose()
}
