package org.litvin.ui.flow.fakes

import org.litvin.ActiveQueueSnapshot
import org.litvin.RenderJob
import org.litvin.RenderStatus
import org.litvin.export.RenderService
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class FakeRenderService : RenderService {
    private val submitted = CopyOnWriteArrayList<RenderJob>()
    private val observers = CopyOnWriteArrayList<(ActiveQueueSnapshot) -> Unit>()
    private val scriptedSnapshots = ArrayDeque<ActiveQueueSnapshot>()
    private val recordedCalls = CopyOnWriteArrayList<String>()
    private val closed = AtomicBoolean(false)

    val jobs: List<RenderJob> get() = submitted.map(::immutableSnapshot)
    val calls: List<String> get() = recordedCalls.toList()

    override fun enqueue(job: RenderJob) {
        checkOpen()
        recordedCalls += "enqueue:${job.id}"
        submitted += immutableSnapshot(job)
    }

    override fun cancelCurrent() {
        checkOpen()
        recordedCalls += "cancel-current"
    }

    override fun cancelQueued(jobId: String): Boolean {
        checkOpen()
        recordedCalls += "cancel-queued:$jobId"
        return submitted.any { it.id == jobId && it.status == RenderStatus.QUEUED }
    }

    override fun observe(observer: (ActiveQueueSnapshot) -> Unit): AutoCloseable {
        checkOpen()
        recordedCalls += "observe"
        observers += observer
        observer(nextSnapshotOrEmpty())
        return AutoCloseable {
            observers.remove(observer)
            recordedCalls += "remove-observer"
        }
    }

    @Synchronized
    fun scriptSnapshots(vararg snapshots: ActiveQueueSnapshot) {
        snapshots.forEach { scriptedSnapshots += immutableSnapshot(it) }
    }

    fun emitNextSnapshot() {
        val snapshot = nextSnapshotOrEmpty()
        observers.forEach { observer -> observer(snapshot) }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            recordedCalls += "close"
            observers.clear()
            synchronized(this) { scriptedSnapshots.clear() }
        }
    }

    @Synchronized
    private fun nextSnapshotOrEmpty(): ActiveQueueSnapshot =
        if (scriptedSnapshots.isEmpty()) ActiveQueueSnapshot(null, emptyList()) else scriptedSnapshots.removeFirst()

    private fun checkOpen() = check(!closed.get()) { "Fake render service is closed" }

    private fun immutableSnapshot(snapshot: ActiveQueueSnapshot) = ActiveQueueSnapshot(
        current = snapshot.current?.let(::immutableSnapshot),
        queued = snapshot.queued.map(::immutableSnapshot),
    )

    private fun immutableSnapshot(job: RenderJob): RenderJob = job.copy(
        edlSnapshot = job.edlSnapshot.map { it.copy() },
        overlayTimeline = job.overlayTimeline.toList(),
    )
}
