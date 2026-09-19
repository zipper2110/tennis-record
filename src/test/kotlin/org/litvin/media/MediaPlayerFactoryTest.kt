package org.litvin.media

import org.junit.jupiter.api.Test
import org.litvin.adjustments.AdjustmentsV1
import java.awt.Component
import java.awt.image.RenderedImage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MediaPlayerFactoryTest {
    @Test
    fun consumerAndFactoryClosePlayersExactlyOnce() {
        val player = CountingSwingMediaPlayer()
        val factoryOwned = CountingSwingMediaPlayer()
        val created = ArrayDeque(listOf(player, factoryOwned))
        val factory = VlcjMediaPlayerFactory(playerCreator = { created.removeFirst() })

        val managedPlayer = factory.create(MediaScreen.MARKUP)
        factory.create(MediaScreen.CROP)
        managedPlayer.close()
        managedPlayer.close()
        factory.close()
        factory.close()

        assertEquals(1, player.closeCount.get())
        assertEquals(1, factoryOwned.closeCount.get())
    }

    @Test
    fun createRacingWithFactoryCloseClosesTheNewResourceExactlyOnceAndRejectsIt() {
        val creationStarted = CountDownLatch(1)
        val allowCreation = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val player = CountingSwingMediaPlayer()
        val factory = VlcjMediaPlayerFactory(
            playerCreator = {
                creationStarted.countDown()
                allowCreation.await()
                player
            },
        )
        var result: Result<SwingMediaPlayer>? = null
        val creator = Thread({
            result = runCatching { factory.create(MediaScreen.SCORING) }
            finished.countDown()
        }, "media-factory-race-test")

        creator.start()
        assertTrue(creationStarted.await(5, TimeUnit.SECONDS))
        factory.close()
        allowCreation.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        creator.join(5_000)

        assertIs<IllegalStateException>(result?.exceptionOrNull())
        assertEquals(1, player.closeCount.get())
    }

    @Test
    fun factoryCloseAfterRegistrationButBeforeClosedCheckRejectsTheAlreadyUnregisteredHandle() {
        val registered = CountDownLatch(1)
        val allowClosedCheck = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val player = CountingSwingMediaPlayer()
        val factory = VlcjMediaPlayerFactory(
            playerCreator = { player },
            afterRegistration = {
                registered.countDown()
                allowClosedCheck.await()
            },
        )
        var result: Result<SwingMediaPlayer>? = null
        val creator = Thread({
            result = runCatching { factory.create(MediaScreen.MARKUP) }
            finished.countDown()
        }, "media-post-registration-race-test")

        creator.start()
        assertTrue(registered.await(5, TimeUnit.SECONDS), "Resource was not registered")
        factory.close()
        allowClosedCheck.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS), "Registration did not finish")
        creator.join(5_000)

        assertIs<IllegalStateException>(result?.exceptionOrNull())
        assertEquals(1, player.closeCount.get())
    }

    private class CountingSwingMediaPlayer : SwingMediaPlayer {
        val closeCount = AtomicInteger(0)
        override val component: Component = JPanel()
        override var onReady: (() -> Unit)? = null
        override var onStatusChanged: ((PlayerStatus) -> Unit)? = null
        override var onTimeChanged: ((Long) -> Unit)? = null
        override fun load(file: File) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seek(ms: Long) = Unit
        override fun setRate(rate: Float) = Unit
        override fun status(): PlayerStatus = PlayerStatus.STOPPED
        override fun currentTimeMs(): Long = 0L
        override fun totalDurationMs(): Long = 0L
        override fun isAdjustSupported(): Boolean = true
        override fun applyColorAdjustments(adj: AdjustmentsV1) = Unit
        override fun applyGeometryAdjustments(adj: AdjustmentsV1): Boolean = true
        override fun applyPreviewAdjustments(adj: AdjustmentsV1) = Unit
        override fun applyPreviewRotation(rotationDeg: Float, reason: String) = Unit
        override fun setPreviewOverlayImage(image: RenderedImage?) = Unit
        override fun stepFrameForward(maximumTimeMs: Long): Long = 0L
        override fun stepFrameBackward(minimumTimeMs: Long): Long = 0L
        override fun nextFrame() = Unit
        override fun setSubtitleFile(file: File): Boolean = true
        override fun activatePreview(reason: String) = Unit
        override fun deactivatePreview(reason: String) = Unit
        override fun close() {
            closeCount.incrementAndGet()
        }
    }

}
