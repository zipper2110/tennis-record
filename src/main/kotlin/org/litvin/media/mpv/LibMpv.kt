package org.litvin.media.mpv

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.ApplicationLayout

/**
 * JNA mapping of the libmpv client API (mpv/client.h, API version 2.x).
 *
 * All strings are UTF-8. The struct layouts are read with fixed offsets in [MpvEvent].
 */
@Suppress("FunctionName")
internal interface LibMpv : Library {
    fun mpv_client_api_version(): NativeLong
    fun mpv_error_string(error: Int): String
    fun mpv_free(data: Pointer)
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?

    /** [args] must end with a null item. */
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int

    /** [args] must end with a null item. */
    fun mpv_command_async(ctx: Pointer, replyUserdata: Long, args: Array<String?>): Int
    fun mpv_observe_property(ctx: Pointer, replyUserdata: Long, name: String, format: Int): Int
    fun mpv_request_log_messages(ctx: Pointer, minLevel: String): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer
    fun mpv_wakeup(ctx: Pointer)

    companion object {
        const val LIBRARY_NAME = "libmpv-2"

        const val FORMAT_NONE = 0
        const val FORMAT_STRING = 1
        const val FORMAT_FLAG = 3
        const val FORMAT_INT64 = 4
        const val FORMAT_DOUBLE = 5

        const val EVENT_NONE = 0
        const val EVENT_SHUTDOWN = 1
        const val EVENT_LOG_MESSAGE = 2
        const val EVENT_COMMAND_REPLY = 5
        const val EVENT_START_FILE = 6
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8
        const val EVENT_VIDEO_RECONFIG = 17
        const val EVENT_SEEK = 20
        const val EVENT_PLAYBACK_RESTART = 21
        const val EVENT_PROPERTY_CHANGE = 22

        const val END_FILE_REASON_ERROR = 4

        private val logger = KotlinLogging.logger {}

        @Volatile private var loaded: LibMpv? = null
        @Volatile private var loadFailure: Throwable? = null

        /** Returns the loaded library, or null when libmpv is not available. */
        fun instanceOrNull(): LibMpv? {
            loaded?.let { return it }
            if (loadFailure != null) return null
            return synchronized(this) {
                loaded ?: runCatching { load() }
                    .onFailure {
                        loadFailure = it
                        logger.warn(it) { "libmpv is not available." }
                    }
                    .getOrNull()
                    ?.also { loaded = it }
            }
        }

        fun instance(): LibMpv = instanceOrNull()
            ?: throw IllegalStateException("libmpv is not available.", loadFailure)

        private fun load(): LibMpv {
            val directory = ApplicationLayout.current().mpvDirectory
            if (directory != null && directory.isDirectory) {
                NativeLibrary.addSearchPath(LIBRARY_NAME, directory.absolutePath)
            }
            val library = Native.load(
                LIBRARY_NAME,
                LibMpv::class.java,
                mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"),
            )
            val version = library.mpv_client_api_version().toLong()
            logger.info {
                "Loaded libmpv client API ${version shr 16}.${version and 0xffff} from ${directory ?: "system path"}"
            }
            return library
        }
    }
}

/** Read-only view of an mpv_event, with the field offsets of mpv/client.h on 64-bit platforms. */
internal class MpvEvent(private val pointer: Pointer) {
    val id: Int get() = pointer.getInt(0)
    val error: Int get() = pointer.getInt(4)
    val replyUserdata: Long get() = pointer.getLong(8)
    private val data: Pointer? get() = pointer.getPointer(16)

    /** mpv_event_property: name at 0, format at 8, data at 16. */
    fun propertyName(): String? = data?.getPointer(0)?.getString(0, "UTF-8")

    fun propertyFormat(): Int = data?.getInt(8) ?: LibMpv.FORMAT_NONE

    fun propertyDouble(): Double? = data?.getPointer(16)?.getDouble(0)

    fun propertyFlag(): Boolean? = data?.getPointer(16)?.getInt(0)?.let { it != 0 }

    fun propertyLong(): Long? = data?.getPointer(16)?.getLong(0)

    fun propertyString(): String? = data?.getPointer(16)?.getPointer(0)?.getString(0, "UTF-8")

    /** mpv_event_end_file: reason at 0, error at 4. */
    fun endFileReason(): Int? = data?.getInt(0)

    fun endFileError(): Int? = data?.getInt(4)

    /** mpv_event_log_message: prefix at 0, level at 8, text at 16. */
    fun logMessage(): Triple<String, String, String>? {
        val message = data ?: return null
        return Triple(
            message.getPointer(0)?.getString(0, "UTF-8").orEmpty(),
            message.getPointer(8)?.getString(0, "UTF-8").orEmpty(),
            message.getPointer(16)?.getString(0, "UTF-8").orEmpty().trimEnd(),
        )
    }
}
