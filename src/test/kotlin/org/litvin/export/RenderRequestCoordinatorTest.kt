package org.litvin.export

import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertEquals
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

    @Test
    fun ownerCloseWinningAfterProcessExitSuppressesTerminalSideEffects() {
        val coordinator = RenderRequestCoordinator()
        val processExited = CountDownLatch(1)
        val allowTerminalization = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val finalFileMoved = AtomicBoolean(false)
        val completedStatePublished = AtomicBoolean(false)
        val repositoryAppended = AtomicBoolean(false)
        val terminalized = AtomicBoolean(true)
        coordinator.register("owner-a", "job-a")
        assertTrue(coordinator.markCurrent("owner-a", "job-a"))
        val worker = thread(name = "render-post-exit-race-test") {
            processExited.countDown()
            allowTerminalization.await()
            terminalized.set(
                coordinator.terminalize("owner-a", "job-a") {
                    finalFileMoved.set(true)
                    completedStatePublished.set(true)
                    repositoryAppended.set(true)
                },
            )
            coordinator.finish("owner-a", "job-a")
            finished.countDown()
        }

        assertTrue(processExited.await(5, TimeUnit.SECONDS), "Process-exit boundary was not reached")
        coordinator.closeOwner("owner-a")
        allowTerminalization.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS), "Canceled terminalization did not finish")
        worker.join(5_000)

        assertFalse(terminalized.get())
        assertFalse(finalFileMoved.get())
        assertFalse(completedStatePublished.get())
        assertFalse(repositoryAppended.get())
    }

    @Test
    fun terminalizationWinningMakesSameOwnerCloseWaitButDoesNotBlockAnotherOwner() {
        val coordinator = RenderRequestCoordinator()
        val terminalizationStarted = CountDownLatch(1)
        val allowTerminalization = CountDownLatch(1)
        val terminalizationFinished = CountDownLatch(1)
        val sameOwnerCloseFinished = CountDownLatch(1)
        val otherOwnerCloseFinished = CountDownLatch(1)
        val sameOwnerCloseFailure = AtomicReference<Throwable?>()
        val sameOwnerInterruptRestored = AtomicBoolean(false)
        coordinator.register("owner-a", "job-a")
        coordinator.register("owner-b", "job-b")
        assertTrue(coordinator.markCurrent("owner-a", "job-a"))

        val terminalizer = thread(name = "render-terminalization-test") {
            coordinator.terminalize("owner-a", "job-a") {
                terminalizationStarted.countDown()
                allowTerminalization.await()
            }
            terminalizationFinished.countDown()
        }
        assertTrue(terminalizationStarted.await(5, TimeUnit.SECONDS), "Terminalization did not start")
        val sameOwnerCloser = thread(name = "render-same-owner-close-test") {
            try {
                coordinator.closeOwner("owner-a")
                sameOwnerInterruptRestored.set(Thread.currentThread().isInterrupted)
            } catch (failure: Throwable) {
                sameOwnerCloseFailure.set(failure)
            } finally {
                sameOwnerCloseFinished.countDown()
            }
        }
        val otherOwnerCloser = thread(name = "render-other-owner-close-test") {
            coordinator.closeOwner("owner-b")
            otherOwnerCloseFinished.countDown()
        }

        assertTrue(otherOwnerCloseFinished.await(5, TimeUnit.SECONDS), "Unrelated owner close was blocked")
        sameOwnerCloser.interrupt()
        assertFalse(sameOwnerCloseFinished.await(200, TimeUnit.MILLISECONDS), "Same-owner close returned during terminalization")
        allowTerminalization.countDown()
        assertTrue(terminalizationFinished.await(5, TimeUnit.SECONDS), "Terminalization did not finish")
        assertTrue(sameOwnerCloseFinished.await(5, TimeUnit.SECONDS), "Same-owner close did not resume")
        assertEquals(null, sameOwnerCloseFailure.get())
        assertTrue(sameOwnerInterruptRestored.get(), "Close did not restore the interrupt flag")

        coordinator.finish("owner-a", "job-a")
        coordinator.finish("owner-b", "job-b")
        terminalizer.join(5_000)
        sameOwnerCloser.join(5_000)
        otherOwnerCloser.join(5_000)
    }
}
