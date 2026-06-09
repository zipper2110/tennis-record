package org.litvin

import com.sun.jna.NativeLibrary
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import java.io.File
import java.util.concurrent.TimeUnit

data class DiagnosticCheck(val name: String, val passed: Boolean, val detail: String)

object DistributionDiagnostics {
    fun run(): Int {
        val checks = collect()
        println("${AppInfo.displayName} distribution diagnostics")
        checks.forEach { check ->
            println("[${if (check.passed) "PASS" else "FAIL"}] ${check.name}: ${check.detail}")
        }
        return if (checks.all { it.passed }) 0 else 1
    }

    internal fun collect(layout: RuntimeLayout = ApplicationLayout.current()): List<DiagnosticCheck> {
        val arch = System.getProperty("os.arch").orEmpty()
        return listOf(
            DiagnosticCheck(
                "Architecture",
                arch.equals("amd64", true) || arch.equals("x86_64", true),
                arch.ifBlank { "<unknown>" },
            ),
            DiagnosticCheck(
                "Java runtime",
                Runtime.version().feature() >= 17,
                System.getProperty("java.runtime.version").orEmpty(),
            ),
            fileCheck("libVLC", layout.vlcDirectory?.resolve("libvlc.dll")),
            nativeVlcCheck(),
            directoryCheck("VLC plugins", layout.vlcPluginsDirectory),
            executableCheck("FFmpeg", layout.ffmpegExecutable),
            executableCheck("FFprobe", layout.ffprobeExecutable),
            writableDirectoryCheck(layout.appDataDirectory),
        )
    }

    private fun fileCheck(name: String, file: File?): DiagnosticCheck = DiagnosticCheck(
        name,
        file?.isFile == true,
        file?.absolutePath ?: "not configured",
    )

    private fun directoryCheck(name: String, directory: File?): DiagnosticCheck = DiagnosticCheck(
        name,
        directory?.isDirectory == true && !directory.list().isNullOrEmpty(),
        directory?.absolutePath ?: "not configured",
    )

    private fun nativeVlcCheck(): DiagnosticCheck {
        return try {
            VlcBootstrap.ensureConfigured()
            val library = NativeLibrary.getInstance(RuntimeUtil.getLibVlcLibraryName())
            val detail = library.file?.absolutePath ?: library.toString()
            library.close()
            DiagnosticCheck("libVLC load", true, detail)
        } catch (t: Throwable) {
            DiagnosticCheck("libVLC load", false, "${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun executableCheck(name: String, executable: String): DiagnosticCheck {
        return try {
            val process = ProcessBuilder(executable, "-version")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            val firstLine = process.inputStream.bufferedReader().use { it.readLine() }.orEmpty()
            val passed = finished && process.exitValue() == 0
            DiagnosticCheck(
                name,
                passed,
                executable + firstLine.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
            )
        } catch (t: Throwable) {
            DiagnosticCheck(name, false, "$executable (${t.javaClass.simpleName}: ${t.message})")
        }
    }

    private fun writableDirectoryCheck(directory: File): DiagnosticCheck {
        return try {
            if (!directory.exists()) directory.mkdirs()
            val probe = File.createTempFile("write-probe-", ".tmp", directory)
            probe.delete()
            DiagnosticCheck("Application data", true, directory.absolutePath)
        } catch (t: Throwable) {
            DiagnosticCheck(
                "Application data",
                false,
                "${directory.absolutePath} (${t.javaClass.simpleName}: ${t.message})",
            )
        }
    }
}
