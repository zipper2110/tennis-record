package org.litvin

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApplicationLayoutResolverTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun prefers_explicit_tool_overrides_even_when_paths_contain_spaces() {
        val appHome = tempDir.resolve("Tennis Record").toFile().apply { mkdirs() }
        val ffmpeg = tempDir.resolve("Custom Tools/ffmpeg.exe").toFile().apply {
            parentFile.mkdirs()
            writeText("")
        }
        val ffprobe = tempDir.resolve("Custom Tools/ffprobe.exe").toFile().apply { writeText("") }
        val vlc = tempDir.resolve("Custom VLC").toFile().apply { mkdirs() }

        val layout = resolver(
            appHome = appHome,
            properties = mapOf(
                "os.name" to "Windows 11",
                "user.home" to tempDir.toString(),
                "tr.ffmpeg.path" to ffmpeg.absolutePath,
                "tr.ffprobe.path" to ffprobe.absolutePath,
                "tr.vlc.path" to vlc.absolutePath,
            ),
        ).resolve()

        assertEquals(ffmpeg.absolutePath, layout.ffmpegExecutable)
        assertEquals(ffprobe.absolutePath, layout.ffprobeExecutable)
        assertEquals(vlc.absoluteFile, layout.vlcDirectory)
    }

    @Test
    fun resolves_packaged_layout_and_sibling_ffprobe() {
        val appHome = tempDir.resolve("installed app").toFile().apply { mkdirs() }
        val appDirectory = File(appHome, "app").apply { mkdirs() }
        val mainJar = File(appDirectory, "tennisrecord.jar").apply { writeText("") }
        val nativeRoot = File(appHome, "natives/windows-x64")
        val vlc = File(nativeRoot, "vlc").apply {
            resolve("plugins").mkdirs()
            resolve("libvlc.dll").writeText("")
        }
        val ffmpeg = File(nativeRoot, "ffmpeg/bin/ffmpeg.exe").apply {
            parentFile.mkdirs()
            writeText("")
        }
        val ffprobe = File(ffmpeg.parentFile, "ffprobe.exe").apply { writeText("") }
        val launcher = File(appHome, "Tennis Record.exe").apply { writeText("") }

        val layout = ApplicationLayoutResolver(
            properties = mapOf("os.name" to "Windows 11", "user.home" to tempDir.toString()),
            environment = emptyMap(),
            codeSourceLocation = mainJar.toURI(),
            workingDirectory = tempDir.toFile(),
        ).resolve()

        assertEquals(appHome.absoluteFile, layout.appHome)
        assertEquals(vlc.absoluteFile, layout.vlcDirectory)
        assertEquals(ffmpeg.absolutePath, layout.ffmpegExecutable)
        assertEquals(ffprobe.absolutePath, layout.ffprobeExecutable)
        assertEquals(launcher.absoluteFile, layout.packagedLauncher)
    }

    @Test
    fun falls_back_to_path_commands_when_bundle_is_absent() {
        val appHome = tempDir.resolve("plain").toFile().apply { mkdirs() }
        val layout = resolver(
            appHome = appHome,
            properties = mapOf("os.name" to "Windows 10", "user.home" to tempDir.toString()),
        ).resolve()

        assertEquals("ffmpeg.exe", layout.ffmpegExecutable)
        assertEquals("ffprobe.exe", layout.ffprobeExecutable)
        assertNull(layout.vlcDirectory)
    }

    private fun resolver(
        appHome: File,
        properties: Map<String, String>,
    ) = ApplicationLayoutResolver(
        properties = properties + ("tennis.record.appDir" to appHome.absolutePath),
        environment = emptyMap(),
        codeSourceLocation = null,
        workingDirectory = tempDir.toFile(),
    )
}
