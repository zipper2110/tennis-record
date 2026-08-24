package org.litvin.app

import java.awt.EventQueue
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JFrame

class SwingApplicationHandle(
    val frame: JFrame,
    private val closeActions: List<() -> Unit>,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (EventQueue.isDispatchThread()) {
            closeOnEventDispatchThread()
            return
        }

        try {
            EventQueue.invokeAndWait(::closeOnEventDispatchThread)
        } catch (failure: InvocationTargetException) {
            throw failure.cause ?: failure
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while closing Swing application", failure)
        }
    }

    private fun closeOnEventDispatchThread() {
        if (!closed.compareAndSet(false, true)) return

        val failures = mutableListOf<Throwable>()
        closeActions.asReversed().forEach { action ->
            try {
                action()
            } catch (failure: Throwable) {
                failures += failure
            }
        }
        frame.ownedWindows.forEach { window ->
            try {
                window.dispose()
            } catch (failure: Throwable) {
                failures += failure
            }
        }
        try {
            frame.dispose()
        } catch (failure: Throwable) {
            failures += failure
        }

        if (failures.isNotEmpty()) {
            throw IllegalStateException("Failed to close Swing application").apply {
                failures.forEach(::addSuppressed)
            }
        }
    }
}
