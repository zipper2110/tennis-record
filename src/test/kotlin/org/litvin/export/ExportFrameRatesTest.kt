package org.litvin.export

import kotlin.test.Test
import kotlin.test.assertEquals

class ExportFrameRatesTest {
    @Test
    fun parsesAveragedFfprobeRateWithoutRoundingItsFfmpegArgument() {
        val rate = ExportFrameRates.parse("30000/1001")

        assertEquals("30000/1001", rate?.ffmpegArgument)
        assertEquals(29.97002997002997, rate?.framesPerSecond)
    }

    @Test
    fun capsStandardChoicesAtTheSourceRateAndLabelsTheSourceChoice() {
        val sourceRate = requireNotNull(ExportFrameRates.parse("30000/1001"))

        val choices = ExportFrameRates.availableFor(sourceRate)

        assertEquals(
            listOf("24 FPS", "25 FPS", "29.97 FPS (source)"),
            choices.map { it.label },
        )
    }

    @Test
    fun labelsTheNativeRateInsteadOfAddingItTwiceWhenItIsAStandardChoice() {
        val sourceRate = requireNotNull(ExportFrameRates.parse("60/1"))

        val choices = ExportFrameRates.availableFor(sourceRate)

        assertEquals(
            listOf("24 FPS", "25 FPS", "30 FPS", "50 FPS", "60 FPS (source)"),
            choices.map { it.label },
        )
    }

    @Test
    fun retainsTheSavedRateWhenItIsSupportedByTheNewSource() {
        val sourceRate = requireNotNull(ExportFrameRates.parse("60/1"))

        val selected = ExportFrameRates.preferredOption(
            options = ExportFrameRates.availableFor(sourceRate),
            savedFrameRate = "30",
        )

        assertEquals("30 FPS", selected?.label)
    }
}
