package org.litvin

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lightweight pub/sub bus to propagate AdjustmentsV1 changes across tabs (Task 5.3).
 * This is a temporary bridge before the full AdjustmentsStore in task 5.5.
 */
object AdjustmentsBus {
    private val listeners = CopyOnWriteArrayList<(AdjustmentsV1) -> Unit>()

    fun subscribe(listener: (AdjustmentsV1) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    fun publish(adj: AdjustmentsV1) {
        for (l in listeners) {
            try { l(adj) } catch (_: Throwable) { }
        }
    }
}
