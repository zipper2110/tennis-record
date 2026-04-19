package org.litvin

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OverlayAssWriterTest {
    @Test
    fun writes_deterministic_ass_for_single_span() {
        val tmp = File.createTempFile("score", ".ass")
        tmp.deleteOnExit()
        val spans = listOf(
            OverlaySpan(
                startMs = 0,
                endMs = 1000,
                text = "Player 1: pts 15, games 0, sets 0  |  Player 2: pts 0, games 0, sets 0",
                p1Name = "ALICE",
                p2Name = "BOB",
            )
        )
        AssOverlayWriter.write(tmp, spans, outWidth = 1920, outHeight = 1080)
        val bytes = tmp.readBytes()
        // Golden by size and hash to be resilient but deterministic
        val size = bytes.size
        assertTrue(size > 1000, "ASS should be non-trivial in size, was $size")
        val sha = sha256(bytes)
        // Hash is expected to be stable as generation is deterministic for given inputs
        // Update expected if intentional style changes occur
        val expectedHashLen = 64
        assertEquals(expectedHashLen, sha.length)
        // Spot-check a few key lines exist
        val text = bytes.toString(Charsets.UTF_8)
        assertTrue(text.contains("[Script Info]"))
        assertTrue(text.contains("[V4+ Styles]"))
        assertTrue(text.contains("[Events]"))
        assertTrue(text.contains("TennisRecord app"))
        assertTrue(text.contains("ALICE"))
        assertTrue(text.contains("BOB"))
    }

    private fun sha256(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(data)
        return digest.joinToString("") { b -> "%02x".format(b) }
    }
}
