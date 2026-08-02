package org.litvin

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FFmpegCapabilitiesTest {
    private val allHardwareEncoders = setOf("libx264", "h264_nvenc", "h264_qsv", "h264_amf")

    @Test
    fun choosesEncoderMatchingDetectedGpuVendor() {
        assertEquals(
            "h264_nvenc",
            FFmpegCapabilities.preferredH264Encoder(allHardwareEncoders, "NVIDIA GeForce RTX"),
        )
        assertEquals(
            "h264_amf",
            FFmpegCapabilities.preferredH264Encoder(allHardwareEncoders, "AMD Radeon RX"),
        )
        assertEquals(
            "h264_qsv",
            FFmpegCapabilities.preferredH264Encoder(allHardwareEncoders, "Intel Arc Graphics"),
        )
    }

    @Test
    fun fallsBackToAnAvailableHardwareEncoder() {
        assertEquals(
            "h264_qsv",
            FFmpegCapabilities.preferredH264Encoder(setOf("libx264", "h264_qsv"), ""),
        )
    }
}
