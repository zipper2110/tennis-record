package org.litvin.ui.tabs.markup

import java.awt.EventQueue
import javax.swing.Timer
import kotlin.concurrent.thread

/**
 * Debounced autosave controller for the Markup tab.
 *
 * - Schedules autosave with a debounce timer (no EDT blocking)
 * - Executes provided [saver] off-EDT
 * - Notifies UI about state changes via [onStateChanged] on the EDT
 */
class AutosaveController(
    debounceMs: Int = 300,
    private val saver: () -> Unit,
    private val onStateChanged: (AutosaveState) -> Unit,
) {
    private val timer = Timer(debounceMs) { _ -> triggerSave() }.apply { isRepeats = false }

    @Volatile private var _lastSavedAtMs: Long? = null

    fun schedule() {
        try { timer.restart() } catch (_: Throwable) { /* ignore */ }
        publish()
    }

    fun autosaveNow() {
        try { if (timer.isRunning) timer.stop() } catch (_: Throwable) { }
        triggerSave()
    }

    fun saveNow() = autosaveNow()

    fun isPending(): Boolean = try { timer.isRunning } catch (_: Throwable) { false }

    val lastSavedAtMs: Long?
        get() = _lastSavedAtMs

    private fun triggerSave() {
        // Run IO off-EDT
        thread(name = "autosave") {
            try {
                saver()
                _lastSavedAtMs = System.currentTimeMillis()
            } catch (_: Throwable) {
                // saver() is responsible for surfacing the error to the UI if needed
            } finally {
                publish()
            }
        }
    }

    private fun publish() {
        val snapshot = AutosaveState(pending = isPending(), lastSavedAtMs = _lastSavedAtMs)
        EventQueue.invokeLater { onStateChanged(snapshot) }
    }
}