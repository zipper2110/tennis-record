package org.litvin.export

import org.litvin.export.ExportPassPlanner.Card
import org.litvin.export.ExportPassPlanner.Video
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.points.PointV1
import org.litvin.stats.SetSummaryCard
import org.litvin.stats.StatsCardVideo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ExportPassPlannerTest {
    // Each kept point is 5 s long.
    private val keeps = (1..6).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 5_000) }

    private fun card(pages: Int) = StatsCardVideo(List(pages) { ScoreboardScene(1920.0, 1080.0, emptyList()) })

    private fun set(number: Int, vararg ids: String, pages: Int = 1) = SetSummaryCard(number, ids.toSet(), card(pages))

    private fun List<ExportPassPlanner.Pass>.describe(): List<String> = map { pass ->
        when (pass) {
            is Video -> "video ${pass.chunk.keeps.joinToString("+") { it.id }} @${pass.chunk.outputOffsetMs}/${pass.progressBaseMs}"
            is Card -> "card ${pass.card.durationMs} at ${pass.freezeAtMs} @${pass.progressBaseMs}"
        }
    }

    @Test
    fun withoutCardsThePlanHasOnlyTheVideoChunks() {
        val passes = ExportPassPlanner.plan(keeps, maxSegmentsPerChunk = 4)

        assertEquals(listOf("video p1+p2+p3+p4 @0/0", "video p5+p6 @20000/20000"), passes.describe())
    }

    @Test
    fun aSetCardComesAfterTheLastKeptPointOfTheSet() {
        val passes = ExportPassPlanner.plan(
            keeps,
            setSummaries = listOf(set(1, "p1", "p2", "p3"), set(2, "p4", "p5", "p6", pages = 2)),
            endCard = card(1),
            maxSegmentsPerChunk = 8,
        )

        // The chunk offsets stay on the video timeline, for the scoreboard. The progress includes the cards.
        assertEquals(
            listOf(
                "video p1+p2+p3 @0/0",
                "card 6000 at 35000 @15000",
                "video p4+p5+p6 @15000/21000",
                "card 12000 at 65000 @36000",
                "card 6000 at 65000 @48000",
            ),
            passes.describe(),
        )
    }

    @Test
    fun aSetWithoutAKeptPointHasNoCard() {
        val favorites = listOf(keeps[3], keeps[4])

        val passes = ExportPassPlanner.plan(favorites, setSummaries = listOf(set(1, "p1", "p2", "p3"), set(2, "p4", "p5", "p6")))

        assertEquals(listOf("video p4+p5 @0/0", "card 6000 at 55000 @10000"), passes.describe())
    }

    @Test
    fun theChunkLimitAppliesInsideEachPartBetweenCards() {
        val passes = ExportPassPlanner.plan(keeps, setSummaries = listOf(set(1, "p1", "p2", "p3")), maxSegmentsPerChunk = 2)

        assertEquals(
            listOf(
                "video p1+p2 @0/0",
                "video p3 @10000/10000",
                "card 6000 at 35000 @15000",
                "video p4+p5 @15000/21000",
                "video p6 @25000/31000",
            ),
            passes.describe(),
        )
    }

    @Test
    fun aFullVideoHasOneVideoPassAndTheMatchCardAtTheEndOfTheSource() {
        val passes = ExportPassPlanner.plan(
            emptyList(),
            setSummaries = listOf(set(1, "p1")),
            endCard = card(2),
            fullVideoDurationMs = 90_000,
        )

        assertEquals(2, passes.size)
        assertIs<Video>(passes[0])
        val end = assertIs<Card>(passes[1])
        assertNull(end.freezeAtMs)
        assertEquals(90_000L, end.progressBaseMs)
        assertEquals(12_000L, ExportPassPlanner.cardDurationMs(passes))
    }
}
