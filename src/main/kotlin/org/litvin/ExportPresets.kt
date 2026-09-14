package org.litvin

// Lightweight DTOs for export presets (Task 3.2)
data class VideoPreset(
    val codec: String? = null,
    val x264Preset: String? = null,
    val crf: Int? = null,
    val vbvMaxrateK: Int? = null,
    val vbvBufsizeK: Int? = null,
    val pixelFormat: String? = null,
    val gopSeconds: Int? = null,
)

data class AudioPreset(
    val codec: String? = null,
    val bitrateK: Int? = null,
    val channels: Int? = null,
)

data class ContainerPreset(
    val format: String? = null,
    val fastStart: Boolean? = null,
)

data class ExportPreset(
    val id: String,
    val label: String,
    val description: String? = null,
    val video: VideoPreset = VideoPreset(),
    val audio: AudioPreset? = null,
    val container: ContainerPreset? = null,
)

object ExportPresetsIO {
    private fun defaultPresets(): List<ExportPreset> = ExportQualityProfiles.all().map { quality ->
        ExportPreset(
            id = quality.id,
            label = quality.label,
            description = "Bitrate adjusts to the selected resolution and FPS",
            video = VideoPreset(
                codec = "libx264",
                x264Preset = quality.x264Preset,
                crf = quality.crf,
                pixelFormat = "yuv420p",
                gopSeconds = 2,
            ),
            audio = AudioPreset(codec = "aac", bitrateK = 192, channels = 2),
            container = ContainerPreset(format = "mp4", fastStart = true),
        )
    }

    fun load(): List<ExportPreset> = defaultPresets()

    fun defaultBalancedIndex(presets: List<ExportPreset>): Int {
        val idx = presets.indexOfFirst { it.id.equals("balanced", ignoreCase = true) }
        return if (idx >= 0) idx else 0
    }
}
