package org.litvin.adjustments

import org.litvin.shared.util.DebouncedSaver
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

/**
 * Central cross-tab adjustments state service (Task 5.5).
 * - Singleton store holding current AdjustmentsV1 for the open project
 * - Subscribers are notified on the EDT within ~100 ms budget
 * - Debounced autosave (350 ms) to adjustments.json in the current project directory
 * - Thread-safe; prevents feedback loops by diffing state
 */
object AdjustmentsStore {
    @Volatile private var state: AdjustmentsV1 = AdjustmentsV1()
    @Volatile private var projectDir: String? = null

    private val listeners = CopyOnWriteArrayList<(AdjustmentsV1) -> Unit>()

    // Debounced autosave per spec (300–500 ms); use 350 ms as in UI
    private var saver = DebouncedSaver(350) {
        try {
            val dir = projectDir ?: return@DebouncedSaver
            AdjustmentsIO.writeForProjectDir(dir, state)
        } catch (_: Throwable) {
        }
    }

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
        try { saver.request() } catch (_: Throwable) { }
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
}
