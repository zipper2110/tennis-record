package org.litvin.adjustments

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

/**
 * Application-owned cross-tab adjustments state service.
 * - Holds the current AdjustmentsV1 for the open project
 * - Subscribers are notified on the EDT within ~100 ms budget
 * - Debounced autosave (350 ms) to adjustments.json in the current project directory
 * - Thread-safe; prevents feedback loops by diffing state
 */
class AdjustmentsSession(
    private val scheduler: ScheduledExecutorService,
    private val saveDelayMs: Long = 350L,
) : AutoCloseable {
    @Volatile private var state: AdjustmentsV1 = AdjustmentsV1()
    @Volatile private var projectDir: String? = null
    private val listeners = CopyOnWriteArrayList<(AdjustmentsV1) -> Unit>()
    private val closed = AtomicBoolean(false)
    private var pendingSave: ScheduledFuture<*>? = null

    /** Current full state snapshot. */
    fun get(): AdjustmentsV1 = state

    /**
     * Set a new state computed from the previous one. If the resulting value
     * equals the current state, no notifications are sent and nothing is saved.
     */
    @Synchronized
    fun set(partial: (AdjustmentsV1) -> AdjustmentsV1) {
        val prev = state
        val next = try { partial(prev) } catch (_: Throwable) { prev }
        if (next == prev) return
        state = clamp(next)
        notifyChange()
        requestSave()
    }

    /** Replace state with a new value (will be clamped). */
    @Synchronized
    fun set(newValue: AdjustmentsV1) {
        val next = clamp(newValue)
        if (next == state) return
        state = next
        notifyChange()
        requestSave()
    }

    /** Reset to identity values. */
    @Synchronized
    fun reset() {
        set(AdjustmentsV1())
    }

    /** Subscribe to changes; returns an unsubscribe function. Listener is called on EDT. */
    fun subscribe(listener: (AdjustmentsV1) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    fun unsubscribe(listener: (AdjustmentsV1) -> Unit) {
        listeners.remove(listener)
    }

    /** Load adjustments for a project directory and update state. Notifies listeners. */
    @Synchronized
    fun load(projectDirPath: String) {
        projectDir = File(projectDirPath).absolutePath
        val loaded = try { AdjustmentsIO.readForProjectDir(projectDir!!) } catch (_: Throwable) { AdjustmentsV1() }
        state = clamp(loaded)
        // Fresh state from disk – notify subscribers so all tabs reflect it
        notifyChange()
    }

    /** Force-save current state for the active project. */
    @Synchronized
    fun save(projectDirPath: String? = null) {
        if (projectDirPath != null) projectDir = File(projectDirPath).absolutePath
        persist()
    }

    @Synchronized
    fun flush() {
        pendingSave?.cancel(false)
        pendingSave = null
        persist()
    }

    private fun persist() {
        try {
            val dir = projectDir ?: return
            AdjustmentsIO.writeForProjectDir(dir, state)
        } catch (_: Throwable) { }
    }

    /** Ensure values are within safe ranges per spec, and have non-null WB. */
    private fun clamp(m: AdjustmentsV1): AdjustmentsV1 {
        val wb = m.whiteBalance ?: WhiteBalanceV1()
        return m.copy(
            brightness = m.brightness.coerceIn(0.0f, 2.0f),
            contrast = m.contrast.coerceIn(0.0f, 2.0f),
            saturation = m.saturation.coerceIn(0.0f, 3.0f),
            whiteBalance = WhiteBalanceV1(
                temperature = wb.temperature.coerceIn(-1.0f, 1.0f),
                tint = wb.tint.coerceIn(-1.0f, 1.0f)
            ),
            zoom = m.zoom.coerceIn(0.1f, 4.0f),
            panX = m.panX.coerceIn(-1.0f, 1.0f),
            panY = m.panY.coerceIn(-1.0f, 1.0f),
            rotationDeg = m.rotationDeg.coerceIn(-180.0f, 180.0f)
        )
    }

    private fun requestSave() {
        if (closed.get()) return
        try {
            pendingSave?.cancel(false)
            pendingSave = scheduler.schedule({ save() }, saveDelayMs, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) { }
    }

    private fun notifyChange() {
        val snapshot = state
        // Deliver on EDT to keep UI thread budget under control
        if (SwingUtilities.isEventDispatchThread()) {
            deliver(snapshot)
        } else {
            try { SwingUtilities.invokeLater { deliver(snapshot) } } catch (_: Throwable) { deliver(snapshot) }
        }
    }

    private fun deliver(adj: AdjustmentsV1) {
        for (l in listeners) {
            try { l(adj) } catch (_: Throwable) { }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        flush()
        listeners.clear()
    }
}

/**
 * Transitional compatibility facade for callers that Task 6 will migrate to
 * an application-owned [AdjustmentsSession].
 */
object AdjustmentsStore {
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "legacy-adjustments-autosave").apply { isDaemon = true }
    }
    private val session = AdjustmentsSession(scheduler)

    fun get(): AdjustmentsV1 = session.get()
    fun set(partial: (AdjustmentsV1) -> AdjustmentsV1) = session.set(partial)
    fun set(newValue: AdjustmentsV1) = session.set(newValue)
    fun reset() = session.reset()
    fun subscribe(listener: (AdjustmentsV1) -> Unit): () -> Unit = session.subscribe(listener)
    fun unsubscribe(listener: (AdjustmentsV1) -> Unit) = session.unsubscribe(listener)
    fun load(projectDirPath: String) = session.load(projectDirPath)
    fun save(projectDirPath: String? = null) = session.save(projectDirPath)
    internal fun legacySession(): AdjustmentsSession = session
}
