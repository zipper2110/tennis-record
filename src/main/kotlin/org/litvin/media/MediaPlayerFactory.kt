package org.litvin.media

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.media.mpv.LibMpv
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

/**
 * Selects the preview engine. The default is mpv (libmpv). `-Dtennis.record.player=vlc` selects VLC.
 * If libmpv cannot load, the factory uses VLC and logs a warning.
 */
fun productionMediaPlayerFactory(
    engine: String? = System.getProperty("tennis.record.player"),
): MediaPlayerFactory {
    val shown = engine ?: "<not set>"
    if (engine?.trim()?.lowercase() == "vlc") {
        logger.info { "Preview engine: VLC (tennis.record.player=$shown)." }
        return VlcjMediaPlayerFactory()
    }
    if (LibMpv.instanceOrNull() == null) {
        logger.warn { "Preview engine: VLC. libmpv is not available (tennis.record.player=$shown). See the warning above." }
        return VlcjMediaPlayerFactory()
    }
    logger.info { "Preview engine: MPV (tennis.record.player=$shown). Use -Dtennis.record.player=vlc for VLC." }
    return VlcjMediaPlayerFactory(playerCreator = { MpvSwingMediaPlayerAdapter() })
}

internal fun engineName(resource: Any): String = when (resource) {
    is MpvSwingMediaPlayerAdapter -> "MPV"
    is VlcjSwingMediaPlayerAdapter -> "VLC"
    else -> resource::class.simpleName ?: "unknown"
}

class VlcjMediaPlayerFactory internal constructor(
    private val playerCreator: () -> SwingMediaPlayer = { VlcjSwingMediaPlayerAdapter() },
    private val afterRegistration: () -> Unit = { },
) : MediaPlayerFactory {
    private val resources = CopyOnWriteArrayList<ManagedResource>()
    private val closed = AtomicBoolean(false)

    override fun create(screen: MediaScreen): SwingMediaPlayer {
        val player = playerCreator()
        logger.info { "Preview player for $screen: ${engineName(player)}" }
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
