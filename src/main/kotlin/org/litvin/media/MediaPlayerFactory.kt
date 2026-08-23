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

class VlcjMediaPlayerFactory : MediaPlayerFactory {
    private val resources = CopyOnWriteArrayList<AutoCloseable>()
    private val closed = AtomicBoolean(false)

    override fun create(screen: MediaScreen): SwingMediaPlayer =
        track(VlcjSwingMediaPlayerAdapter())

    override fun createFrameCapture(): StillFrameCaptureService =
        track(VlcjStillFrameCaptureService())

    private fun <T : AutoCloseable> track(resource: T): T {
        if (closed.get()) {
            resource.close()
            error("Media-player factory is closed")
        }
        resources += resource
        if (closed.get() && resources.remove(resource)) {
            resource.close()
            error("Media-player factory is closed")
        }
        return resource
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
}
