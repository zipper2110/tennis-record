package org.litvin.ui.flow.screens

import org.litvin.markup.EdlIO
import java.nio.file.Path

internal class RalliesScreen(application: ApplicationScreen) : UserFlowScreen(application) {
    fun open(): RalliesScreen = apply { open("nav-rallies", "rallies-point-start") }

    fun markPoint(startMs: Long, endMs: Long): RalliesScreen = apply {
        require(startMs >= 0) { "point start must not be negative" }
        require(endMs > startMs) { "point end must be after its start" }
        val player = context.mediaPlayers.players.firstOrNull()
            ?: error("Rallies media player has not been created")
        player.seek(startMs)
        application.eventually("rallies playhead to reach $startMs ms") {
            context.driver.requireText("rallies-current-time", formatTime(startMs))
        }
        context.driver.click("rallies-point-start")
        player.seek(endMs)
        application.eventually("rallies playhead to reach $endMs ms") {
            context.driver.requireText("rallies-current-time", formatTime(endMs))
        }
        context.driver.click("rallies-point-end")
    }

    fun assertPointCount(marked: Int, favorites: Int = 0) {
        application.eventually("rallies point count to become $marked marked / $favorites favorite") {
            context.driver.requireText("rallies-point-count", "$marked MARKED / $favorites FAV")
        }
    }

    fun assertPointPersisted(projectDirectory: Path, startMs: Long, endMs: Long) {
        application.eventually("point $startMs..$endMs to be persisted") {
            val points = EdlIO.readForProjectDir(projectDirectory.toString()).points
            if (points.none { point -> point.startMs.toLong() == startMs && point.endMs.toLong() == endMs }) {
                throw AssertionError("Persisted points were $points")
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val hours = ms / 3_600_000
        val minutes = (ms / 60_000) % 60
        val seconds = (ms / 1_000) % 60
        val millis = ms % 1_000
        return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    }
}
