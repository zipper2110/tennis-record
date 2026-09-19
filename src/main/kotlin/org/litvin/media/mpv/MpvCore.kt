package org.litvin.media.mpv

import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * One libmpv player instance with its own event thread.
 *
 * All methods are thread-safe. After [close] starts, calls do nothing. The read-write lock makes sure
 * that no API call runs while mpv_terminate_destroy runs.
 *
 * [onEvent] runs on the event thread. The [MpvEvent] is valid only during the call.
 */
internal class MpvCore(
    private val lib: LibMpv,
    options: List<Pair<String, String>>,
    private val name: String,
    private val onEvent: (MpvEvent) -> Unit,
) : AutoCloseable {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val handle: Pointer = lib.mpv_create() ?: error("mpv_create returned null")
    private val lock = ReentrantReadWriteLock()
    @Volatile private var closing = false
    private val eventThread: Thread

    init {
        try {
            for ((key, value) in options) {
                val result = lib.mpv_set_option_string(handle, key, value)
                if (result < 0) logger.warn { "mpv[$name] rejected option $key=$value: ${errorText(result)}" }
            }
            val result = lib.mpv_initialize(handle)
            check(result >= 0) { "mpv_initialize failed: ${errorText(result)}" }
            lib.mpv_request_log_messages(handle, "warn")
        } catch (t: Throwable) {
            lib.mpv_terminate_destroy(handle)
            throw t
        }
        eventThread = Thread(::eventLoop, "mpv-events-$name").apply {
            isDaemon = true
            start()
        }
    }

    val isClosed: Boolean get() = closing

    fun observe(property: String, format: Int) = call { lib.mpv_observe_property(handle, 0L, property, format) }

    /** Runs a command asynchronously. Errors come back as log lines. */
    fun command(vararg args: String) = call {
        val result = lib.mpv_command_async(handle, 0L, arrayOf(*args, null))
        if (result < 0) logger.warn { "mpv[$name] command ${args.toList()} failed: ${errorText(result)}" }
    }

    /** Runs a command and waits for the result. Use only for quick commands. */
    fun commandSync(vararg args: String): Int = call { lib.mpv_command(handle, arrayOf(*args, null)) } ?: -1

    fun setProperty(property: String, value: String) = command("set", property, value)

    fun getProperty(property: String): String? = call {
        val pointer = lib.mpv_get_property_string(handle, property) ?: return@call null
        try {
            pointer.getString(0, "UTF-8")
        } finally {
            lib.mpv_free(pointer)
        }
    }

    private inline fun <T> call(block: () -> T): T? = lock.read {
        if (closing) null else block()
    }

    private fun eventLoop() {
        while (true) {
            val event = MpvEvent(lib.mpv_wait_event(handle, -1.0))
            when (event.id) {
                LibMpv.EVENT_NONE -> if (closing) return
                LibMpv.EVENT_SHUTDOWN -> return
                LibMpv.EVENT_LOG_MESSAGE -> event.logMessage()?.let { (prefix, level, text) ->
                    logger.warn { "mpv[$name] $level [$prefix] $text" }
                }
                else -> try {
                    onEvent(event)
                } catch (t: Throwable) {
                    logger.warn(t) { "mpv[$name] event handler failed for event ${event.id}" }
                }
            }
            if (closing) return
        }
    }

    override fun close() {
        lock.write {
            if (closing) return
            closing = true
        }
        lib.mpv_wakeup(handle)
        if (Thread.currentThread() !== eventThread) {
            eventThread.join(5_000)
        }
        logger.info { "Destroying mpv[$name]" }
        lib.mpv_terminate_destroy(handle)
    }

    private fun errorText(code: Int): String = runCatching { lib.mpv_error_string(code) }.getOrDefault("error $code")
}
