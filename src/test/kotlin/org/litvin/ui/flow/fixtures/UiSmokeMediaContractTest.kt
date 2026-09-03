package org.litvin.ui.flow.fixtures

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiSmokeMediaContractTest {
    @Test
    fun `shared UI smoke fixture remains the approved bounded MP4`() {
        val resource = javaClass.classLoader.getResource(RESOURCE_PATH)
        assertNotNull(resource, "$RESOURCE_PATH must be present on the test classpath")

        val fixture = Path.of(requireNotNull(resource).toURI())
        assertTrue(fixture.isRegularFile(), "$RESOURCE_PATH must be a regular file")
        assertEquals("mp4", fixture.extension.lowercase())
        assertTrue(fixture.fileSize() > 0, "$RESOURCE_PATH must not be empty")
        assertTrue(fixture.fileSize() <= MAX_BYTES, "$RESOURCE_PATH must be at most 5 MiB")
        assertEquals(EXPECTED_SHA_256, fixture.sha256())
    }

    private fun Path.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(this).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val RESOURCE_PATH = "media/ui-smoke.mp4"
        const val MAX_BYTES = 5L * 1024L * 1024L
        const val EXPECTED_SHA_256 = "87feface1dedc57e4c65d6a77afa17e7208802baf59392713022d5fb41c03db9"
    }
}
