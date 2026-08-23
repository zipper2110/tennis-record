package org.litvin.export

import org.junit.jupiter.api.Test
import org.litvin.ActiveQueueSnapshot
import org.litvin.CompletedRender
import org.litvin.RenderJob
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.app.TrackedExecutorProvider
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class RenderServiceTest {
    @Test
    fun enqueueBindsEachJobToTheOriginatingServiceGraphsOwnedStateAndRepository() {
        val executors = TrackedExecutorProvider("render-graph-test", Duration.ofSeconds(5))
        try {
            val firstAdjustments = AdjustmentsSession(executors.createScheduledExecutor("first"))
            val secondAdjustments = AdjustmentsSession(executors.createScheduledExecutor("second"))
            val firstCompleted = RecordingCompletedRendersRepository()
            val secondCompleted = RecordingCompletedRendersRepository()
            val gateway = RecordingRenderQueueGateway()
            val firstService = ProductionRenderService(firstAdjustments, firstCompleted, gateway)
            val secondService = ProductionRenderService(secondAdjustments, secondCompleted, gateway)
            val firstJob = renderJob("first")
            val secondJob = renderJob("second")

            firstService.enqueue(firstJob)
            secondService.enqueue(secondJob)

            assertSame(firstAdjustments, gateway.requests[0].adjustments)
            assertSame(firstCompleted, gateway.requests[0].completedRenders)
            assertSame(secondAdjustments, gateway.requests[1].adjustments)
            assertSame(secondCompleted, gateway.requests[1].completedRenders)
            firstService.close()
            secondService.close()
            firstAdjustments.close()
            secondAdjustments.close()
        } finally {
            executors.close()
        }
    }

    @Test
    fun closingAServiceCancelsOnlyRequestsOwnedByThatServiceGraph() {
        val executors = TrackedExecutorProvider("render-owner-test", Duration.ofSeconds(5))
        try {
            val firstAdjustments = AdjustmentsSession(executors.createScheduledExecutor("first"))
            val secondAdjustments = AdjustmentsSession(executors.createScheduledExecutor("second"))
            val gateway = RecordingRenderQueueGateway()
            val firstService = ProductionRenderService(firstAdjustments, RecordingCompletedRendersRepository(), gateway)
            val secondService = ProductionRenderService(secondAdjustments, RecordingCompletedRendersRepository(), gateway)
            val firstJob = renderJob("first-owned")
            val secondJob = renderJob("second-owned")
            firstService.enqueue(firstJob)
            secondService.enqueue(secondJob)

            firstService.close()

            val firstOwner = gateway.requests[0].ownerId
            val secondOwner = gateway.requests[1].ownerId
            assertNotEquals(firstOwner, secondOwner)
            assertEquals(listOf(firstOwner to firstJob.id), gateway.cancelQueuedRequests)
            assertEquals(listOf(firstOwner), gateway.cancelCurrentOwners)
            secondService.close()
            firstAdjustments.close()
            secondAdjustments.close()
        } finally {
            executors.close()
        }
    }

    @Test
    fun observationHandleRemovesExactlyItsObserver() {
        val executors = TrackedExecutorProvider("render-observer-test", Duration.ofSeconds(5))
        val gateway = RecordingRenderQueueGateway()
        val adjustments = AdjustmentsSession(executors.createScheduledExecutor("adjustments"))
        val service = ProductionRenderService(adjustments, RecordingCompletedRendersRepository(), gateway)
        try {
            val first: (ActiveQueueSnapshot) -> Unit = { }
            val second: (ActiveQueueSnapshot) -> Unit = { }

            val firstHandle = service.observe(first)
            service.observe(second)
            firstHandle.close()
            firstHandle.close()

            assertEquals(listOf(second), gateway.observers)
            service.close()
        } finally {
            adjustments.close()
            executors.close()
        }
    }

    @Test
    fun cancellationOperationsDelegateAndReturnTheGatewayResult() {
        val executors = TrackedExecutorProvider("render-delegation-test", Duration.ofSeconds(5))
        val adjustments = AdjustmentsSession(executors.createScheduledExecutor("adjustments"))
        val gateway = RecordingRenderQueueGateway().apply { cancelQueuedResult = true }
        val service = ProductionRenderService(adjustments, RecordingCompletedRendersRepository(), gateway)
        try {
            service.cancelCurrent()

            assertEquals(true, service.cancelQueued("queued-job"))
            assertEquals(1, gateway.cancelCurrentCalls)
            assertEquals(listOf("queued-job"), gateway.cancelQueuedIds)
        } finally {
            service.close()
            adjustments.close()
            executors.close()
        }
    }

    @Test
    fun closeAttemptsEveryObserverRemovalAndCancellationBeforePropagatingFailures() {
        val executors = TrackedExecutorProvider("render-cleanup-test", Duration.ofSeconds(5))
        val adjustments = AdjustmentsSession(executors.createScheduledExecutor("adjustments"))
        val gateway = FailingCleanupGateway()
        val service = ProductionRenderService(adjustments, RecordingCompletedRendersRepository(), gateway)
        try {
            val first: (ActiveQueueSnapshot) -> Unit = { }
            val second: (ActiveQueueSnapshot) -> Unit = { }
            service.observe(first)
            service.observe(second)
            gateway.failRemovalFor = second
            gateway.failCancellation = true

            val failure = assertFailsWith<IllegalStateException> { service.close() }

            assertEquals(listOf(second, first), gateway.removalAttempts)
            assertEquals(1, gateway.cancelCalls)
            assertEquals(1, failure.suppressed.size)
        } finally {
            adjustments.close()
            executors.close()
        }
    }

    private class RecordingRenderQueueGateway : RenderQueueGateway {
        val observers = mutableListOf<(ActiveQueueSnapshot) -> Unit>()
        val requests = mutableListOf<RenderQueueRequest>()
        var cancelCurrentCalls = 0
        val cancelQueuedIds = mutableListOf<String>()
        val cancelCurrentOwners = mutableListOf<String>()
        val cancelQueuedRequests = mutableListOf<Pair<String, String>>()
        var cancelQueuedResult = false

        override fun enqueue(request: RenderQueueRequest) {
            requests += request
        }
        override fun cancelCurrent(ownerId: String) {
            cancelCurrentCalls++
            cancelCurrentOwners += ownerId
        }
        override fun cancelQueued(ownerId: String, jobId: String): Boolean {
            cancelQueuedIds += jobId
            cancelQueuedRequests += ownerId to jobId
            return cancelQueuedResult
        }
        override fun closeOwner(ownerId: String) {
            cancelCurrentCalls++
            cancelCurrentOwners += ownerId
        }
        override fun addObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit) {
            observers += observer
        }
        override fun removeObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit) {
            observers.remove(observer)
        }
    }

    private class RecordingCompletedRendersRepository : CompletedRendersRepository {
        override fun loadAll(): List<CompletedRender> = emptyList()
        override fun append(job: RenderJob) = Unit
        override fun clear() = Unit
    }

    private class FailingCleanupGateway : RenderQueueGateway {
        val removalAttempts = mutableListOf<(ActiveQueueSnapshot) -> Unit>()
        var failRemovalFor: ((ActiveQueueSnapshot) -> Unit)? = null
        var failCancellation = false
        var cancelCalls = 0

        override fun enqueue(request: RenderQueueRequest) = Unit
        override fun cancelCurrent(ownerId: String) {
            cancelCalls++
            if (failCancellation) throw IllegalArgumentException("cancel failed")
        }
        override fun cancelQueued(ownerId: String, jobId: String): Boolean = false
        override fun closeOwner(ownerId: String) {
            cancelCalls++
            if (failCancellation) throw IllegalArgumentException("cancel failed")
        }
        override fun addObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit) = Unit
        override fun removeObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit) {
            removalAttempts += observer
            if (observer === failRemovalFor) throw IllegalStateException("remove failed")
        }
    }

    private fun renderJob(id: String) = RenderJob(
        id = id,
        sourcePath = "$id.mp4",
        presetId = "balanced",
        outWidth = 1920,
        outHeight = 1080,
        encoderLabel = "H.264 (libx264)",
        idleTrim = false,
        outputPath = "$id-output.mp4",
    )
}
