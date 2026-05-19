package org.litvin

import org.litvin.markup.EdlIO
import org.litvin.scoring.ScoreIO
import kotlin.test.Test
import kotlin.test.assertEquals
import java.io.File

class GoldenLoadApplyTest {
    @Test
    fun load_golden_edl_and_score_and_apply_rules() {
        // Locate golden files in test resources
        val base = File(this::class.java.classLoader.getResource("golden/simple/edl.json")!!.toURI()).parentFile
        val edlPath = File(base, "edl.json").absolutePath
        val scorePath = File(base, "score.json").absolutePath

        val edl = EdlIO.read(edlPath)
        val score = ScoreIO.read(scorePath)

        val pointIds = edl.points.sortedBy { it.startMs }.map { it.id }
        val (timeline, sets) = RulesEngine.computeTimeline(pointIds, score.outcomes)

        // The golden represents: P1 wins two quick games, P2 wins one, then a deuce game P1 wins.
        // Check final after last point
        val st = timeline.last()
        assertEquals(3, st.gamesP1)
        assertEquals(1, st.gamesP2)
        assertEquals(0, st.p1Pts)
        assertEquals(0, st.p2Pts)
        assertEquals(0, st.setsP1)
        assertEquals(0, st.setsP2)
        // No completed sets yet
        assertEquals(true, sets.last().isEmpty())
    }
}
