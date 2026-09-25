package org.litvin.export

import org.litvin.FFmpegCapabilities

/** The H.264 encoders that the export can use. [jobLabel] is the value that FFmpegCommandBuilder reads. */
enum class ExportEncoder(
    val id: String,
    val title: String,
    val jobLabel: String,
    val description: String,
    val hardware: Boolean,
) {
    NVENC(
        "h264_nvenc", "NVIDIA NVENC", "H.264 (NVENC)",
        "For PCs with an NVIDIA GeForce or RTX graphics card. Fast: the graphics card encodes the video.",
        hardware = true,
    ),
    AMF(
        "h264_amf", "AMD AMF", "H.264 (AMF)",
        "For PCs with AMD Radeon graphics. Fast: the graphics card encodes the video.",
        hardware = true,
    ),
    QSV(
        "h264_qsv", "Intel Quick Sync", "H.264 (QSV)",
        "For PCs with Intel graphics. Fast: the graphics part of the Intel processor encodes the video.",
        hardware = true,
    ),
    SOFTWARE(
        "libx264", "Software (x264)", "H.264 (libx264)",
        "Works on all PCs. Only the processor encodes the video, so it is the slowest, but the most reliable.",
        hardware = false,
    ),
    ;

    companion object {
        fun fromId(id: String?): ExportEncoder? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Result of the encoder detection.
 * [availableIds] has only the encoders that passed a test encode on this computer.
 */
data class EncoderCapabilities(
    val availableIds: Set<String>,
    val preferredId: String?,
    val gpuNames: List<String> = emptyList(),
) {
    /** The encoders to show: the preferred one first, then the other hardware encoders, then software. */
    val options: List<ExportEncoder>
        get() {
            val hardware = ExportEncoder.entries.filter { it.hardware && it.id in availableIds }
            return hardware.sortedByDescending { it.id == preferredId } + ExportEncoder.SOFTWARE
        }

    /** The encoder that the export uses by default. Software encoding works everywhere. */
    val best: ExportEncoder
        get() = ExportEncoder.fromId(preferredId)?.takeIf { it in options } ?: ExportEncoder.SOFTWARE

    companion object {
        val NONE = EncoderCapabilities(emptySet(), null)

        /** Runs ffmpeg and PowerShell. It takes some seconds, so do not call it on the UI thread. */
        fun production(): EncoderCapabilities {
            val listed = try {
                FFmpegCapabilities.h264Encoders()
            } catch (_: Throwable) {
                emptySet()
            }
            val working = listed.filter { id ->
                id == ExportEncoder.SOFTWARE.id || FFmpegCapabilities.canEncode(id)
            }.toSet()
            val gpuNames = try {
                FFmpegCapabilities.videoAdapters()
            } catch (_: Throwable) {
                emptyList()
            }
            val preferred = try {
                FFmpegCapabilities.preferredH264Encoder(working, gpuNames.joinToString("\n"))
            } catch (_: Throwable) {
                null
            }
            return EncoderCapabilities(working, preferred, gpuNames)
        }
    }
}
