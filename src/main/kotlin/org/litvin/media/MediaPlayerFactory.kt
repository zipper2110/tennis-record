package org.litvin.media

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.media.mpv.MpvSwingMediaPlayerAdapter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

enum class MediaScreen {
    MARKUP,
    COLORS,
    CROP,
    SCORING,
    TEST,
}

interface MediaPlayerFactory : AutoCloseable {
    fun create(screen: MediaScreen): SwingMediaPlayer
}

private val logger = KotlinLogging.logger {}

/** The preview engine is libmpv. */
fun productionMediaPlayerFactory(): MediaPlayerFactory {
    logger.info { "Preview engine: mpv (libmpv)." }
    return PreviewMediaPlayerFactory()
}

class PreviewMediaPlayerFactory internal constructor(
    private val playerCreator: () -> SwingMediaPlayer = { MpvSwingMediaPlayerAdapter() },
    private val afterRegistration: () -> Unit = { },
) : MediaPlayerFactory {
    private val resources = CopyOnWriteArrayList<ManagedResource>()
    private val closed = AtomicBoolean(false)

    override fun create(screen: MediaScreen): SwingMediaPlayer {
        val player = playerCreator()
        logger.info { "Preview player for $screen: ${player::class.simpleName}" }
        return register(ManagedSwingMediaPlayer(player, ::unregister))
    }

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
}
