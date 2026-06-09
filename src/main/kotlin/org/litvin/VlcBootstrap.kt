package org.litvin

import com.sun.jna.NativeLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import java.io.File

object VlcBootstrap {
    private val logger = KotlinLogging.logger {}

    @Volatile
    private var configured = false

    @Synchronized
    fun ensureConfigured() {
        if (configured) return

        val vlcDirectory = ApplicationLayout.current().vlcDirectory
        if (vlcDirectory != null && vlcDirectory.isDirectory) {
            val path = vlcDirectory.absolutePath
            NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), path)
            NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcCoreLibraryName(), path)
            appendJnaLibraryPath(path)
            logger.info { "Configured bundled VLC from $path" }
        } else {
            logger.info { "Bundled VLC not found; using VLCJ native discovery." }
        }
        configured = true
    }

    fun factoryArguments(additional: Array<String> = emptyArray()): Array<String> {
        ensureConfigured()
        val pluginPath = ApplicationLayout.current().vlcPluginsDirectory
        return if (pluginPath != null) {
            arrayOf("--plugin-path=${pluginPath.absolutePath}") + additional
        } else {
            additional
        }
    }

    private fun appendJnaLibraryPath(path: String) {
        val existing = System.getProperty("jna.library.path").orEmpty()
            .split(File.pathSeparator)
            .filter { it.isNotBlank() }
        if (existing.none { it.equals(path, ignoreCase = true) }) {
            System.setProperty(
                "jna.library.path",
                (listOf(path) + existing).joinToString(File.pathSeparator),
            )
        }
    }
}
