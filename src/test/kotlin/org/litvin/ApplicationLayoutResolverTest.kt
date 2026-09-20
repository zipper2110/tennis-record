package org.litvin

import org.litvin.app.AppDataPaths
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
        val mpv = tempDir.resolve("Custom mpv").toFile().apply { mkdirs() }

        val layout = resolver(
            appHome = appHome,
            properties = mapOf(
                "os.name" to "Windows 11",
                "user.home" to tempDir.toString(),
                "tr.ffmpeg.path" to ffmpeg.absolutePath,
                "tr.ffprobe.path" to ffprobe.absolutePath,
                "tr.mpv.path" to mpv.absolutePath,
            ),
        ).resolve()

        assertEquals(ffmpeg.absolutePath, layout.ffmpegExecutable)
        assertEquals(ffprobe.absolutePath, layout.ffprobeExecutable)
        assertEquals(mpv.absoluteFile, layout.mpvDirectory)
    }

    @Test
    fun resolves_packaged_layout_and_sibling_ffprobe() {
        val appHome = tempDir.resolve("installed app").toFile().apply { mkdirs() }
        val appDirectory = File(appHome, "app").apply { mkdirs() }
        val mainJar = File(appDirectory, "tennisrecord.jar").apply { writeText("") }
        val nativeRoot = File(appHome, "natives/windows-x64")
        val mpv = File(nativeRoot, "mpv").apply {
            mkdirs()
            resolve("libmpv-2.dll").writeText("")
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
        assertEquals(mpv.absoluteFile, layout.mpvDirectory)
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
        assertNull(layout.mpvDirectory)
    }

    @Test
    fun uses_pinned_development_mpv_when_the_packaged_bundle_is_absent() {
        val workspace = tempDir.resolve("workspace").toFile().apply { mkdirs() }
        val appHome = workspace.resolve("target/classes").apply { mkdirs() }
        val developmentMpv = workspace.resolve("target/native/windows-x64/mpv").apply {
            mkdirs()
            resolve("libmpv-2.dll").writeText("")
        }

        val layout = ApplicationLayoutResolver(
            properties = mapOf(
                "os.name" to "Windows 11",
                "user.home" to tempDir.resolve("home").toString(),
                "tennis.record.appDir" to appHome.absolutePath,
            ),
            environment = emptyMap(),
            codeSourceLocation = null,
            workingDirectory = workspace,
        ).resolve()

        assertEquals(developmentMpv.absoluteFile, layout.mpvDirectory)
    }

    @Test
    fun resolves_app_data_from_system_property_before_environment() {
        val propertyRoot = tempDir.resolve("property-data").toFile()
        val environmentRoot = tempDir.resolve("environment-data").toFile()

        val layout = resolver(
            appHome = tempDir.toFile(),
            properties = mapOf(
                "os.name" to "Windows 11",
                "user.home" to tempDir.resolve("home").toString(),
                "tennis.record.appDataDir" to propertyRoot.path,
            ),
            environment = mapOf(
                "TENNIS_RECORD_APP_DATA_DIR" to environmentRoot.path,
                "APPDATA" to tempDir.resolve("roaming").toString(),
            ),
        ).resolve()

        assertEquals(propertyRoot.absoluteFile.normalize(), layout.appDataDirectory)
    }

    @Test
    fun resolves_app_data_from_environment_override_before_appdata() {
        val environmentRoot = tempDir.resolve("environment-data").toFile()

        val layout = resolver(
            appHome = tempDir.toFile(),
            properties = mapOf(
                "os.name" to "Windows 11",
                "user.home" to tempDir.resolve("home").toString(),
            ),
            environment = mapOf(
                "TENNIS_RECORD_APP_DATA_DIR" to environmentRoot.path,
                "APPDATA" to tempDir.resolve("roaming").toString(),
            ),
        ).resolve()

        assertEquals(environmentRoot.absoluteFile.normalize(), layout.appDataDirectory)
    }

    @Test
    fun resolves_app_data_below_windows_appdata_when_no_override_exists() {
        val appData = tempDir.resolve("roaming").toFile()

        val layout = resolver(
            appHome = tempDir.toFile(),
            properties = mapOf(
                "os.name" to "Windows 11",
                "user.home" to tempDir.resolve("home").toString(),
            ),
            environment = mapOf("APPDATA" to appData.path),
        ).resolve()

        assertEquals(appData.resolve("tennis-record"), layout.appDataDirectory)
    }

    @Test
    fun resolves_app_data_below_user_home_when_no_windows_location_exists() {
        val userHome = tempDir.resolve("home").toFile()

        val layout = resolver(
            appHome = tempDir.toFile(),
            properties = mapOf("os.name" to "Linux", "user.home" to userHome.path),
            environment = emptyMap(),
        ).resolve()

        assertEquals(userHome.resolve(".tennis-record"), layout.appDataDirectory)
    }

    @Test
    fun app_data_paths_derive_every_external_path_from_one_root() {
        val root = tempDir.resolve("isolated-data").toFile()

        val paths = AppDataPaths(root)

        assertEquals(root.resolve("projects"), paths.projects)
        assertEquals(root.resolve("completed-renders.json"), paths.completedRenders)
        assertEquals(root.resolve("logs"), paths.logs)
        assertEquals(root.resolve("tmp"), paths.temporary)
    }

    private fun resolver(
        appHome: File,
        properties: Map<String, String>,
        environment: Map<String, String> = emptyMap(),
    ) = ApplicationLayoutResolver(
        properties = properties + ("tennis.record.appDir" to appHome.absolutePath),
        environment = environment,
        codeSourceLocation = null,
        workingDirectory = tempDir.toFile(),
    )
}
