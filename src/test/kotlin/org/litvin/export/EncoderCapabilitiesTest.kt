package org.litvin.export

import kotlin.test.Test
import kotlin.test.assertEquals

class EncoderCapabilitiesTest {
    @Test
    fun `shows the preferred hardware encoder first and software last`() {
        val capabilities = EncoderCapabilities(setOf("libx264", "h264_qsv", "h264_nvenc"), "h264_nvenc", listOf("NVIDIA GeForce RTX 3060"))

        assertEquals(listOf(ExportEncoder.NVENC, ExportEncoder.QSV, ExportEncoder.SOFTWARE), capabilities.options)
        assertEquals(ExportEncoder.NVENC, capabilities.best)
    }

    @Test
    fun `uses the software encoder when no hardware encoder works`() {
        assertEquals(listOf(ExportEncoder.SOFTWARE), EncoderCapabilities.NONE.options)
        assertEquals(ExportEncoder.SOFTWARE, EncoderCapabilities.NONE.best)
    }
}
