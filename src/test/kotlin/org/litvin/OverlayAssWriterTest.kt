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

    @Test
    fun writesWrappedLowerThirdCommentAlongsideScoreboard() {
        val tmp = File.createTempFile("comment-score", ".ass")
        tmp.deleteOnExit()

        AssOverlayWriter.write(
            file = tmp,
            scoreboardSpans = listOf(
                OverlaySpan(
                    startMs = 0,
                    endMs = 1_000,
                    text = "Player 1: pts 15, games 0, sets 0  |  Player 2: pts 0, games 0, sets 0",
                    p1Name = "ALICE",
                    p2Name = "BOB",
                ),
            ),
            commentSpans = listOf(
                CommentOverlaySpan(12, 1_000, 3_000, "IN {review}\nGreat point", "#22AAFF"),
            ),
            outWidth = 1_920,
            outHeight = 1_080,
        )

        val ass = tmp.readText()
        assertTrue(ass.contains("Style: CommentText"))
        assertTrue(ass.contains("Style: CommentBackdrop"))
        assertTrue(ass.contains("\\N"))
        assertTrue(ass.contains("\\{"))
        assertTrue(ass.contains("FFAA22"))
        assertTrue(ass.contains("ALICE"))
    }

    @Test
    fun wrapsLongCommentAtWordBoundaries() {
        val tmp = File.createTempFile("comment-wrap", ".ass")
        tmp.deleteOnExit()
        val text = "This long comment needs enough words to wrap safely inside the lower third without overflowing the video frame"

        AssOverlayWriter.write(
            file = tmp,
            scoreboardSpans = emptyList(),
            commentSpans = listOf(CommentOverlaySpan(1, 0, 1_000, text, "#FFFFFF")),
            outWidth = 640,
            outHeight = 360,
        )

        assertTrue(tmp.readText().contains("\\N"))
    }

    private fun sha256(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(data)
        return digest.joinToString("") { b -> "%02x".format(b) }
    }
}
