package org.litvin.export

import org.litvin.ActiveQueueSnapshot
import org.litvin.RenderJob
import org.litvin.RenderQueueManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

interface RenderService : AutoCloseable {
    fun enqueue(job: RenderJob)
    fun cancelCurrent()
    fun cancelQueued(jobId: String): Boolean
    fun observe(observer: (ActiveQueueSnapshot) -> Unit): AutoCloseable
}

internal interface RenderQueueGateway {
    fun enqueue(job: RenderJob)
    fun cancelCurrent()
    fun cancelQueued(jobId: String): Boolean
    fun addObserver(observer: (ActiveQueueSnapshot) -> Unit)
    fun removeObserver(observer: (ActiveQueueSnapshot) -> Unit)
}

private object GlobalRenderQueueGateway : RenderQueueGateway {
    override fun enqueue(job: RenderJob) = RenderQueueManager.enqueue(job)
    override fun cancelCurrent() = RenderQueueManager.cancelCurrent()
    override fun cancelQueued(jobId: String): Boolean = RenderQueueManager.cancelQueued(jobId)
    override fun addObserver(observer: (ActiveQueueSnapshot) -> Unit) = RenderQueueManager.addObserver(observer)
    override fun removeObserver(observer: (ActiveQueueSnapshot) -> Unit) = RenderQueueManager.removeObserver(observer)
}

class ProductionRenderService internal constructor(
    private val gateway: RenderQueueGateway = GlobalRenderQueueGateway,
) : RenderService {
    private val subscriptions = CopyOnWriteArrayList<AutoCloseable>()
    private val closed = AtomicBoolean(false)

    override fun enqueue(job: RenderJob) = gateway.enqueue(job)

    override fun cancelCurrent() = gateway.cancelCurrent()

    override fun cancelQueued(jobId: String): Boolean = gateway.cancelQueued(jobId)

    override fun observe(observer: (ActiveQueueSnapshot) -> Unit): AutoCloseable {
        synchronized(subscriptions) {
            check(!closed.get()) { "Render service is closed" }
            val subscriptionClosed = AtomicBoolean(false)
            val handle = AutoCloseable {
                if (subscriptionClosed.compareAndSet(false, true)) {
                    gateway.removeObserver(observer)
                }
            }
            subscriptions += handle
            try {
                gateway.addObserver(observer)
            } catch (failure: Throwable) {
                subscriptions.remove(handle)
                handle.close()
                throw failure
            }
            return handle
        }
    }

    override fun close() {
        val owned = synchronized(subscriptions) {
            if (!closed.compareAndSet(false, true)) return
            subscriptions.toList().asReversed().also { subscriptions.clear() }
        }
        owned.forEach { it.close() }
        cancelCurrent()
    }
}
