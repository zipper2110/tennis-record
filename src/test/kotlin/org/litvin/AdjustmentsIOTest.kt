package org.litvin

import org.litvin.adjustments.AdjustmentsIO
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.WhiteBalanceV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.io.File

class AdjustmentsIOTest {
    @Test
    fun roundTrip_adjustmentsJson() {
        val tmpDir: Path = Files.createTempDirectory("adj_rt_")
        val adjPath = tmpDir.resolve("adjustments.json").toFile().absolutePath

        val adj = AdjustmentsV1(
            brightness = 0.1f,
            contrast = 1.25f,
            saturation = 0.9f,
            whiteBalance = WhiteBalanceV1(temperature = 0.2f, tint = -0.1f),
            zoom = 1.3f,
            panX = -0.2f,
            panY = 0.4f,
            rotationDeg = -5.5f,
            version = 1
        )

        AdjustmentsIO.write(adjPath, adj)
        val readBack = AdjustmentsIO.read(adjPath)

        assertEquals(1, readBack.version, "Version must be 1")
        assertEquals(adj.brightness, readBack.brightness)
        assertEquals(adj.contrast, readBack.contrast)
        assertEquals(adj.saturation, readBack.saturation)
        assertEquals(adj.whiteBalance, readBack.whiteBalance)
        assertEquals(adj.zoom, readBack.zoom)
        assertEquals(adj.panX, readBack.panX)
        assertEquals(adj.panY, readBack.panY)
        assertEquals(adj.rotationDeg, readBack.rotationDeg)
    }

    @Test
    fun read_missingFile_returnsIdentityDefaults() {
        val tmpDir: Path = Files.createTempDirectory("adj_missing_")
        val adjPath = tmpDir.resolve("does_not_exist.json").toFile().absolutePath

        val readBack = AdjustmentsIO.read(adjPath)
        assertEquals(1, readBack.version)
        assertEquals(1.0f, readBack.brightness)
        assertEquals(1.0f, readBack.contrast)
        assertEquals(1.0f, readBack.saturation)
        assertEquals(null, readBack.whiteBalance)
        assertEquals(1.0f, readBack.zoom)
        assertEquals(0.0f, readBack.panX)
        assertEquals(0.0f, readBack.panY)
        assertEquals(0.0f, readBack.rotationDeg)
    }

    @Test
    fun read_write_preservesUnknownFields() {
        val tmpDir: Path = Files.createTempDirectory("adj_unknown_")
        val f = tmpDir.resolve("adjustments.json").toFile()
        f.writeText(
            """
            {
              "version": 1,
              "brightness": 0.2,
              "unknown": {"foo": 1, "bar": true}
            }
            """.trimIndent()
        )
        val readBack = AdjustmentsIO.read(f.absolutePath)
        // Write back and ensure unknown are still present
        AdjustmentsIO.write(f.absolutePath, readBack)
        val txt = f.readText()
        assertTrue(txt.contains("\"unknown\""), "Unknown field should be preserved on write")
        assertTrue(txt.contains("\"foo\""))
        assertTrue(txt.contains("\"bar\""))
    }
}
