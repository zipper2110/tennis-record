package org.litvin

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Attempts to hint Windows to use the High Performance GPU for this application by
 * setting HKCU\Software\Microsoft\DirectX\UserGpuPreferences for the current executable path.
 *
 * This affects future launches of the app (Windows reads the preference when a process starts),
 * so a restart is recommended after a change is applied.
 */
object WindowsGpuPreference {
    private val logger = KotlinLogging.logger {}

    @Volatile private var applied = false
    @Volatile private var failedReason: String? = null

    fun wasChangeApplied(): Boolean = applied
    fun failureMessage(): String? = failedReason

    fun ensureHighPerformancePreference() {
        try {
            val os = System.getProperty("os.name")?.lowercase() ?: ""
            if (!os.contains("windows")) return

            val targets = candidateExecutablePaths()
            if (targets.isEmpty()) return

            val regPath = "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences"
            val desired = "GpuPreference=2;" // 2 = High Performance GPU

            var anyChanged = false
            for (exePath in targets) {
                val current = readRegistryValue(regPath, exePath)
                val already = current?.contains("GpuPreference=2") == true
                if (!already) {
                    val ok = writeRegistryValue(regPath, exePath, desired)
                    if (ok) {
                        anyChanged = true
                        logger.info { "Set HighPerformance GPU preference for $exePath" }
                    } else {
                        logger.warn { "Failed to set HighPerformance GPU preference for $exePath" }
                    }
                } else {
                    logger.debug { "HighPerformance GPU preference already set for $exePath" }
                }
            }
            applied = anyChanged
            if (!anyChanged) {
                failedReason = null // not an error; nothing to do
            }
        } catch (t: Throwable) {
            failedReason = t.message
            logger.warn(t) { "Failed to ensure HighPerformance GPU preference." }
        }
    }

    private fun candidateExecutablePaths(): List<String> {
        return try {
            val javaHome = System.getProperty("java.home") ?: return emptyList()
            val javaw = java.nio.file.Paths.get(javaHome, "bin", "javaw.exe").toFile()
            val java = java.nio.file.Paths.get(javaHome, "bin", "java.exe").toFile()
            val list = mutableListOf<String>()
            if (javaw.exists()) list.add(javaw.absolutePath)
            if (java.exists()) list.add(java.absolutePath)
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun readRegistryValue(path: String, valueName: String): String? {
        try {
            val builder = ProcessBuilder(
                "reg",
                "query",
                path,
                "/v",
                valueName
            )
            builder.redirectErrorStream(true)
            val proc = builder.start()
            BufferedReader(InputStreamReader(proc.inputStream)).use { br ->
                val out = br.readText()
                proc.waitFor()
                if (proc.exitValue() == 0) {
                    // reg query output includes the value on the last line usually
                    return out
                }
            }
        } catch (_: Throwable) { }
        return null
    }

    private fun writeRegistryValue(path: String, valueName: String, data: String): Boolean {
        return try {
            val builder = ProcessBuilder(
                "reg",
                "add",
                path,
                "/v",
                valueName,
                "/t",
                "REG_SZ",
                "/d",
                data,
                "/f"
            )
            builder.redirectErrorStream(true)
            val proc = builder.start()
            val code = proc.waitFor()
            code == 0
        } catch (_: Throwable) {
            false
        }
    }
}
