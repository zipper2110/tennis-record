package org.litvin.media

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.media.mpv.LibMpv
import org.litvin.media.mpv.MpvSwingMediaPlayerAdapter
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

private val logger = KotlinLogging.logger {}

/**
 * Selects the preview engine. `-Dtennis.record.player=mpv` uses libmpv for the live previews.
 * The default is VLC. The Crop/Rotate still-frame capture uses VLC with both engines.
 * If mpv is requested but libmpv cannot load, the factory uses VLC and logs a warning.
 */
fun productionMediaPlayerFactory(
    engine: String? = System.getProperty("tennis.record.player"),
): MediaPlayerFactory {
    val requested = engine?.trim()?.lowercase()
    val shown = engine ?: "<not set>"
    if (requested == "mpv") {
        if (LibMpv.instanceOrNull() != null) {
            logger.info { "Preview engine: MPV (tennis.record.player=$shown). Crop/Rotate still frames use VLC." }
            return VlcjMediaPlayerFactory(playerCreator = { MpvSwingMediaPlayerAdapter() })
        }
        logger.warn { "Preview engine: VLC. tennis.record.player=mpv, but libmpv is not available. See the warning above." }
        return VlcjMediaPlayerFactory()
    }
    logger.info { "Preview engine: VLC (tennis.record.player=$shown). Use -Dtennis.record.player=mpv for mpv." }
    return VlcjMediaPlayerFactory()
}

internal fun engineName(resource: Any): String = when (resource) {
    is MpvSwingMediaPlayerAdapter -> "MPV"
    is VlcjSwingMediaPlayerAdapter, is VlcjStillFrameCaptureService -> "VLC"
    else -> resource::class.simpleName ?: "unknown"
}

class VlcjMediaPlayerFactory internal constructor(
    private val playerCreator: () -> SwingMediaPlayer = { VlcjSwingMediaPlayerAdapter() },
    private val frameCaptureCreator: () -> StillFrameCaptureService = { VlcjStillFrameCaptureService() },
    private val afterRegistration: () -> Unit = { },
) : MediaPlayerFactory {
    private val resources = CopyOnWriteArrayList<ManagedResource>()
    private val closed = AtomicBoolean(false)

    override fun create(screen: MediaScreen): SwingMediaPlayer {
        val player = playerCreator()
        logger.info { "Preview player for $screen: ${engineName(player)}" }
        return register(ManagedSwingMediaPlayer(player, ::unregister))
    }

    override fun createFrameCapture(): StillFrameCaptureService {
        val capture = frameCaptureCreator()
        logger.info { "Crop/Rotate still-frame capture: ${engineName(capture)}" }
        return register(ManagedStillFrameCaptureService(capture, ::unregister))
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
