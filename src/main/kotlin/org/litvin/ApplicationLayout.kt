package org.litvin

import java.io.File
import java.net.URI

data class RuntimeLayout(
    val appHome: File,
    val nativeRoot: File,
    val vlcDirectory: File?,
    val vlcPluginsDirectory: File?,
    val ffmpegExecutable: String,
    val ffprobeExecutable: String,
    val packagedLauncher: File?,
    val appDataDirectory: File,
)

class ApplicationLayoutResolver(
    private val properties: Map<String, String> = System.getProperties()
        .stringPropertyNames()
        .associateWith { System.getProperty(it) },
    private val environment: Map<String, String> = System.getenv(),
    private val codeSourceLocation: URI? = ApplicationLayoutResolver::class.java.protectionDomain
        ?.codeSource
        ?.location
        ?.toURI(),
    private val workingDirectory: File = File("."),
) {
    fun resolve(): RuntimeLayout {
        val appHome = resolveAppHome()
        val nativeRoot = File(appHome, "natives/windows-x64")
        val vlcDirectory = resolveVlcDirectory(nativeRoot)
        val ffmpegExecutable = resolveExecutable(
            propertyName = "tr.ffmpeg.path",
            environmentName = "FFMPEG_PATH",
            bundledFile = File(nativeRoot, "ffmpeg/bin/ffmpeg.exe"),
            fallbackCommand = if (isWindows()) "ffmpeg.exe" else "ffmpeg",
        )
        val ffprobeExecutable = resolveFfprobe(nativeRoot, ffmpegExecutable)

        return RuntimeLayout(
            appHome = appHome,
            nativeRoot = nativeRoot,
            vlcDirectory = vlcDirectory,
            vlcPluginsDirectory = vlcDirectory?.resolve("plugins")?.takeIf { it.isDirectory },
            ffmpegExecutable = ffmpegExecutable,
            ffprobeExecutable = ffprobeExecutable,
            packagedLauncher = File(appHome, "Tennis Record.exe").takeIf { it.isFile },
            appDataDirectory = resolveAppDataDirectory(),
        )
    }

    private fun resolveAppHome(): File {
        value("tennis.record.appDir")?.let { return File(it).absoluteFile.normalize() }

        val location = codeSourceLocation?.let(::File)?.absoluteFile?.normalize()
        if (location != null) {
            val container = if (location.isFile) location.parentFile else location
            if (container?.name.equals("app", ignoreCase = true)) {
                return container.parentFile
            }
        }
        return workingDirectory.absoluteFile.normalize()
    }

    private fun resolveVlcDirectory(nativeRoot: File): File? {
        value("tr.vlc.path")?.let { return File(it).absoluteFile.normalize() }
        environment["VLC_PATH"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return File(it).absoluteFile.normalize()
        }
        return File(nativeRoot, "vlc").takeIf { it.isDirectory }
    }

    private fun resolveFfprobe(nativeRoot: File, ffmpegExecutable: String): String {
        value("tr.ffprobe.path")?.let { return File(it).absolutePath }
        environment["FFPROBE_PATH"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return File(it).absolutePath
        }

        val ffmpegFile = File(ffmpegExecutable)
        if (ffmpegFile.isAbsolute || ffmpegFile.parentFile != null) {
            val sibling = File(ffmpegFile.parentFile, if (isWindows()) "ffprobe.exe" else "ffprobe")
            if (sibling.isFile) return sibling.absolutePath
        }

        val bundled = File(nativeRoot, "ffmpeg/bin/ffprobe.exe")
        if (bundled.isFile) return bundled.absolutePath
        return if (isWindows()) "ffprobe.exe" else "ffprobe"
    }

    private fun resolveExecutable(
        propertyName: String,
        environmentName: String,
        bundledFile: File,
        fallbackCommand: String,
    ): String {
        value(propertyName)?.let { return File(it).absolutePath }
        environment[environmentName]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return File(it).absolutePath
        }
        if (bundledFile.isFile) return bundledFile.absolutePath
        return fallbackCommand
    }

    private fun resolveAppDataDirectory(): File {
        value("tennis.record.appDataDir")?.let { return File(it).absoluteFile.normalize() }
        environment["APPDATA"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return File(it, "tennis-record")
        }
        return File(properties["user.home"] ?: ".", ".tennis-record")
    }

    private fun value(name: String): String? = properties[name]?.trim()?.takeIf { it.isNotEmpty() }

    private fun isWindows(): Boolean = properties["os.name"]
        ?.contains("windows", ignoreCase = true)
        ?: false
}

object ApplicationLayout {
    @Volatile
    private var cached: RuntimeLayout? = null

    fun current(): RuntimeLayout = cached ?: synchronized(this) {
        cached ?: ApplicationLayoutResolver().resolve().also { cached = it }
    }

    internal fun resetForTests() {
        cached = null
    }
}
