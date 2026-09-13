package org.litvin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenderOverlayScriptTest {
    @Test
    fun writesCommentOverlayWhenCommentsAreIncluded() {
        val tempDir = kotlin.io.path.createTempDirectory("render-overlay-").toFile()
        try {
            val script = RenderOverlayScript.writeFor(
                job = renderJob(
                    includeComments = true,
                    comments = listOf(CommentOverlaySpan(5, 1_000, 4_000, "Call was in", "#FFFFFF")),
                ),
                partOutput = File(tempDir, "out.mp4.part"),
            )

            assertNotNull(script)
            assertTrue(script.exists())
            assertTrue(script.readText().contains("CommentText"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun returnsNoScriptWhenNeitherOverlayIsIncluded() {
        val tempDir = kotlin.io.path.createTempDirectory("render-overlay-").toFile()
        try {
            assertNull(RenderOverlayScript.writeFor(renderJob(), File(tempDir, "out.mp4.part")))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun renderJob(
        includeComments: Boolean = false,
        comments: List<CommentOverlaySpan> = emptyList(),
    ) = RenderJob(
        sourcePath = "input.mp4",
        presetId = "balanced",
        outWidth = 1_920,
        outHeight = 1_080,
        encoderLabel = "H.264 (libx264)",
        idleTrim = false,
        outputPath = "out.mp4",
        includeComments = includeComments,
        commentOverlayTimeline = comments,
    )
}
