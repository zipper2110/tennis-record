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
}
