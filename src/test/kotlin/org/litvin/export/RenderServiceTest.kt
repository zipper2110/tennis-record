package org.litvin.export

import org.junit.jupiter.api.Test
import org.litvin.ActiveQueueSnapshot
import org.litvin.RenderJob
import kotlin.test.assertEquals

class RenderServiceTest {
    @Test
    fun observationHandleRemovesExactlyItsObserver() {
        val gateway = RecordingRenderQueueGateway()
        val service = ProductionRenderService(gateway)
        val first: (ActiveQueueSnapshot) -> Unit = { }
        val second: (ActiveQueueSnapshot) -> Unit = { }

        val firstHandle = service.observe(first)
        service.observe(second)
        firstHandle.close()
        firstHandle.close()

        assertEquals(listOf(second), gateway.observers)
        service.close()
    }

    private class RecordingRenderQueueGateway : RenderQueueGateway {
        val observers = mutableListOf<(ActiveQueueSnapshot) -> Unit>()

        override fun enqueue(job: RenderJob) = Unit
        override fun cancelCurrent() = Unit
        override fun cancelQueued(jobId: String): Boolean = false
        override fun addObserver(observer: (ActiveQueueSnapshot) -> Unit) {
            observers += observer
        }
        override fun removeObserver(observer: (ActiveQueueSnapshot) -> Unit) {
            observers.remove(observer)
        }
    }
}
