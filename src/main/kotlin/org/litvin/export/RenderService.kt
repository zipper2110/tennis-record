package org.litvin.export

import org.litvin.ActiveQueueSnapshot
import org.litvin.RenderJob
import org.litvin.RenderQueueManager
import org.litvin.adjustments.AdjustmentsSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

interface RenderService : AutoCloseable {
    fun enqueue(job: RenderJob)
    fun cancelCurrent()
    fun cancelQueued(jobId: String): Boolean
    fun observe(observer: (ActiveQueueSnapshot) -> Unit): AutoCloseable
}

internal const val LEGACY_RENDER_OWNER_ID = "legacy-render-queue"

internal enum class RenderTerminalOutcome { COMPLETED, FAILED, CANCELED }

internal class RenderQueueRequest(
    val ownerId: String,
    val job: RenderJob,
    val adjustments: AdjustmentsSession,
    val completedRenders: CompletedRendersRepository,
    private val onTerminal: (RenderTerminalOutcome) -> Unit = { },
) {
    private val terminalSignaled = AtomicBoolean(false)

    fun signalTerminal(outcome: RenderTerminalOutcome) {
        if (terminalSignaled.compareAndSet(false, true)) onTerminal(outcome)
    }
}

internal interface RenderQueueGateway {
    fun enqueue(request: RenderQueueRequest)
    fun cancelCurrent(ownerId: String)
    fun cancelQueued(ownerId: String, jobId: String): Boolean
    fun closeOwner(ownerId: String)
    fun addObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit)
    fun removeObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit)
}

private object GlobalRenderQueueGateway : RenderQueueGateway {
    override fun enqueue(request: RenderQueueRequest) = RenderQueueManager.enqueue(request)
    override fun cancelCurrent(ownerId: String) = RenderQueueManager.cancelCurrent(ownerId)
    override fun cancelQueued(ownerId: String, jobId: String): Boolean = RenderQueueManager.cancelQueued(ownerId, jobId)
    override fun closeOwner(ownerId: String) = RenderQueueManager.closeOwner(ownerId)
    override fun addObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit) =
        RenderQueueManager.addObserver(ownerId, observer)
    override fun removeObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit) =
        RenderQueueManager.removeObserver(ownerId, observer)
}

class ProductionRenderService internal constructor(
    private val adjustments: AdjustmentsSession,
    private val completedRenders: CompletedRendersRepository,
    private val gateway: RenderQueueGateway = GlobalRenderQueueGateway,
) : RenderService {
    private companion object {
        private val ownerSequence = AtomicLong(0L)
    }

    private val ownerId = "render-service-${ownerSequence.incrementAndGet()}"
    private val subscriptions = CopyOnWriteArrayList<AutoCloseable>()
    private val ownedJobIds = ConcurrentHashMap.newKeySet<String>()
    private val closed = AtomicBoolean(false)

    override fun enqueue(job: RenderJob) {
        synchronized(subscriptions) {
            check(!closed.get()) { "Render service is closed" }
            ownedJobIds += job.id
            try {
                gateway.enqueue(
                    RenderQueueRequest(ownerId, job, adjustments, completedRenders) {
                        ownedJobIds.remove(job.id)
                    },
                )
            } catch (failure: Throwable) {
                ownedJobIds.remove(job.id)
                throw failure
            }
        }
    }

    override fun cancelCurrent() = gateway.cancelCurrent(ownerId)

    override fun cancelQueued(jobId: String): Boolean = gateway.cancelQueued(ownerId, jobId).also { removed ->
        if (removed) ownedJobIds.remove(jobId)
    }

    override fun observe(observer: (ActiveQueueSnapshot) -> Unit): AutoCloseable {
        synchronized(subscriptions) {
            check(!closed.get()) { "Render service is closed" }
            val subscriptionClosed = AtomicBoolean(false)
            val handle = AutoCloseable {
                if (subscriptionClosed.compareAndSet(false, true)) {
                    gateway.removeObserver(ownerId, observer)
                }
            }
            subscriptions += handle
            try {
                gateway.addObserver(ownerId, observer)
            } catch (failure: Throwable) {
                subscriptions.remove(handle)
                handle.close()
                throw failure
            }
            return handle
        }
    }

    override fun close() {
        val (ownedSubscriptions, queuedJobIds) = synchronized(subscriptions) {
            if (!closed.compareAndSet(false, true)) return
            val observers = subscriptions.toList().asReversed().also { subscriptions.clear() }
            observers to ownedJobIds.toList()
        }
        var failure: Throwable? = null
        ownedSubscriptions.forEach { subscription ->
            failure = attemptCleanup(failure) { subscription.close() }
        }
        queuedJobIds.forEach { jobId ->
            failure = attemptCleanup(failure) { cancelQueued(jobId) }
        }
        failure = attemptCleanup(failure) { gateway.closeOwner(ownerId) }
        failure?.let { throw it }
    }

    private fun attemptCleanup(previous: Throwable?, action: () -> Unit): Throwable? = try {
        action()
        previous
    } catch (failure: Throwable) {
        if (previous == null) failure else previous.apply { addSuppressed(failure) }
    }
}
