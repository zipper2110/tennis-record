package org.litvin

import java.io.File

/** Creates the temporary ASS overlay file used by a single render job. */
object RenderOverlayScript {
    fun writeFor(job: RenderJob, partOutput: File): File? {
        val scoreboardSpans = if (job.includeScoreboard) job.overlayTimeline else emptyList()
        val commentSpans = if (job.includeComments) job.commentOverlayTimeline else emptyList()
        if (scoreboardSpans.isEmpty() && commentSpans.isEmpty()) return null

        return File(partOutput.absolutePath + ".ass").also { file ->
            AssOverlayWriter.write(
                file = file,
                scoreboardSpans = scoreboardSpans,
                commentSpans = commentSpans,
                outWidth = job.outWidth,
                outHeight = job.outHeight,
            )
        }
    }
}
