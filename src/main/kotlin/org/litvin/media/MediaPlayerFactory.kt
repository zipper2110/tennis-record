package org.litvin.media

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

enum class MediaScreen {
    MARKUP,
    COLORS,
    SCORING,
    TEST,
}

interface MediaPlayerFactory : AutoCloseable {
    fun create(screen: MediaScreen): SwingMediaPlayer
    fun createFrameCapture(): StillFrameCaptureService
}

class VlcjMediaPlayerFactory internal constructor(
    private val playerCreator: () -> SwingMediaPlayer = { VlcjSwingMediaPlayerAdapter() },
    private val frameCaptureCreator: () -> StillFrameCaptureService = { VlcjStillFrameCaptureService() },
    private val afterRegistration: () -> Unit = { },
) : MediaPlayerFactory {
    private val resources = CopyOnWriteArrayList<ManagedResource>()
    private val closed = AtomicBoolean(false)

    override fun create(screen: MediaScreen): SwingMediaPlayer =
        register(ManagedSwingMediaPlayer(playerCreator(), ::unregister))

    override fun createFrameCapture(): StillFrameCaptureService =
        register(ManagedStillFrameCaptureService(frameCaptureCreator(), ::unregister))

    private fun <T : ManagedResource> register(resource: T): T {
        if (closed.get()) {
            resource.close()
            error("Media-player factory is closed")
        }
        resources += resource
        afterRegistration()
        if (closed.get()) {
            resources.remove(resource)
            resource.close()
            error("Media-player factory is closed")
        }
        return resource
    }

    private fun unregister(resource: ManagedResource) {
        resources.remove(resource)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        resources.asReversed().forEach { resource ->
            try {
                resource.close()
            } catch (_: Throwable) {
            }
        }
        resources.clear()
    }

    private interface ManagedResource : AutoCloseable

    private class ManagedSwingMediaPlayer(
        private val delegate: SwingMediaPlayer,
        private val onClosed: (ManagedResource) -> Unit,
    ) : SwingMediaPlayer by delegate, ManagedResource {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            onClosed(this)
            delegate.close()
        }
    }

    private class ManagedStillFrameCaptureService(
        private val delegate: StillFrameCaptureService,
        private val onClosed: (ManagedResource) -> Unit,
    ) : StillFrameCaptureService by delegate, ManagedResource {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            onClosed(this)
            delegate.close()
        }
    }
}
