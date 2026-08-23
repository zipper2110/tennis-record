package org.litvin.export

import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderRequestCoordinatorTest {
    @Test
    fun ownerCloseAfterDequeueButBeforeCurrentPublicationPreventsStartupWithoutCancelingAnotherOwner() {
        val coordinator = RenderRequestCoordinator()
        val dequeued = CountDownLatch(1)
        val continueStartup = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val processStarted = AtomicBoolean(false)
        val firstMayContinue = AtomicBoolean(true)
        coordinator.register("owner-a", "job-a")
        coordinator.register("owner-b", "job-b")

        val worker = thread(name = "render-startup-race-test") {
            dequeued.countDown()
            continueStartup.await()
            firstMayContinue.set(coordinator.markCurrent("owner-a", "job-a"))
            if (firstMayContinue.get()) {
                coordinator.startProcess("owner-a", "job-a") {
                    processStarted.set(true)
                    error("Canceled owner must not start a process")
                }
            }
            coordinator.finish("owner-a", "job-a")
            finished.countDown()
        }

        assertTrue(dequeued.await(5, TimeUnit.SECONDS), "Request was not dequeued")
        coordinator.closeOwner("owner-a")
        continueStartup.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS), "Startup did not finish")
        worker.join(5_000)

        assertFalse(firstMayContinue.get())
        assertFalse(processStarted.get())
        assertTrue(coordinator.markCurrent("owner-b", "job-b"), "Unrelated owner was canceled")
        coordinator.finish("owner-b", "job-b")
    }
}
