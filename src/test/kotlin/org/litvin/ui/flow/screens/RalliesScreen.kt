package org.litvin.ui.flow.screens

import org.litvin.markup.EdlIO
import org.litvin.shared.util.Timecode
import org.litvin.ui.commons.AppShortcuts
import java.nio.file.Path
import javax.swing.KeyStroke

internal class RalliesScreen(application: ApplicationScreen) : UserFlowScreen(application) {
    fun open(): RalliesScreen = apply { open("nav-rallies", "rallies-point-start") }

    fun assertReady() = assertVisible("rallies-point-start")

    fun markPoint(startMs: Long, endMs: Long): RalliesScreen = apply {
        require(startMs >= 0) { "point start must not be negative" }
        require(endMs > startMs) { "point end must be after its start" }
        val player = context.mediaPlayers.players.firstOrNull()
            ?: error("Rallies media player has not been created")
        player.seek(startMs)
        application.eventually("rallies playhead to reach $startMs ms") {
            context.driver.requireText("rallies-current-time", formatTime(startMs))
        }
        context.driver.press(KeyStroke.getKeyStroke(AppShortcuts.POINT_START.keyStroke))
        player.seek(endMs)
        application.eventually("rallies playhead to reach $endMs ms") {
            context.driver.requireText("rallies-current-time", formatTime(endMs))
        }
        context.driver.press(KeyStroke.getKeyStroke(AppShortcuts.POINT_END.keyStroke))
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

    fun assertSinglePointPersisted(projectDirectory: Path, startMs: Long, endMs: Long): String {
        application.eventually("exactly one point $startMs..$endMs to be persisted") {
            val points = EdlIO.readForProjectDir(projectDirectory.toString()).points
            if (points.size != 1 || points.single().startMs.toLong() != startMs || points.single().endMs.toLong() != endMs) {
                throw AssertionError("Persisted points were $points")
            }
            if (points.single().id.isBlank()) throw AssertionError("Persisted point id was blank")
        }
        return EdlIO.readForProjectDir(projectDirectory.toString()).points.single().id
    }

    private fun formatTime(ms: Long): String = Timecode.format(ms)
}
