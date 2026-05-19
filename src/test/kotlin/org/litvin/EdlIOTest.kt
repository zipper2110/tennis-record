package org.litvin

import org.litvin.markup.EdlIO
import org.litvin.markup.EdlV1
import org.litvin.markup.PointV1
import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Files
import java.nio.file.Path

class EdlIOTest {
    @Test
    fun roundTrip_edlJson() {
        val tmpDir: Path = Files.createTempDirectory("edl_rt_")
        val edlPath = tmpDir.resolve("edl.json").toFile().absolutePath

        val edl = EdlV1(
            points = listOf(
                PointV1(id = "A", startMs = 0, endMs = 500),
                PointV1(id = "B", startMs = 1_000, endMs = 2_000, label = "Rally"),
                PointV1(id = "C", startMs = 2_000, endMs = 2_500, notes = "Nice serve")
            ),
            version = 1
        )

        EdlIO.write(edlPath, edl)
        val readBack = EdlIO.read(edlPath)

        assertEquals(1, readBack.version, "Version must be 1")
        assertEquals(edl.points.size, readBack.points.size, "Points count should match")
        assertEquals(edl, readBack, "Round-trip should preserve structure and values")
    }

    @Test
    fun read_missingFile_returnsEmptyEdl() {
        val tmpDir: Path = Files.createTempDirectory("edl_missing_")
        val edlPath = tmpDir.resolve("does_not_exist.json").toFile().absolutePath

        val readBack = EdlIO.read(edlPath)
        assertEquals(1, readBack.version)
        assertEquals(0, readBack.points.size)
    }
}
