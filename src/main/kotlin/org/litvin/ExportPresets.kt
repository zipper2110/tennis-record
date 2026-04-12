package org.litvin

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

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
    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)

    private fun defaultPresets(): List<ExportPreset> = listOf(
        ExportPreset(
            id = "fast",
            label = "Fast",
            description = "Fastest export, smaller files",
            video = VideoPreset(
                codec = "libx264",
                x264Preset = "veryfast",
                crf = 24,
                vbvMaxrateK = 6000,
                vbvBufsizeK = 12000,
                pixelFormat = "yuv420p",
                gopSeconds = 2
            ),
            audio = AudioPreset(codec = "aac", bitrateK = 128, channels = 2),
            container = ContainerPreset(format = "mp4", fastStart = true)
        ),
        ExportPreset(
            id = "balanced",
            label = "Balanced",
            description = "Good quality vs size (default)",
            video = VideoPreset(
                codec = "libx264",
                x264Preset = "medium",
                crf = 21,
                vbvMaxrateK = 8000,
                vbvBufsizeK = 16000,
                pixelFormat = "yuv420p",
                gopSeconds = 2
            ),
            audio = AudioPreset(codec = "aac", bitrateK = 192, channels = 2),
            container = ContainerPreset(format = "mp4", fastStart = true)
        ),
        ExportPreset(
            id = "quality",
            label = "Quality",
            description = "Best visual quality, slower",
            video = VideoPreset(
                codec = "libx264",
                x264Preset = "slow",
                crf = 18,
                vbvMaxrateK = null,
                vbvBufsizeK = null,
                pixelFormat = "yuv420p",
                gopSeconds = 2
            ),
            audio = AudioPreset(codec = "aac", bitrateK = 256, channels = 2),
            container = ContainerPreset(format = "mp4", fastStart = true)
        ),
    )

    fun load(path: String = File("docs", "export-presets.json").absolutePath): List<ExportPreset> {
        return try {
            val f = File(path)
            if (!f.exists()) defaultPresets() else mapper.readValue(f)
        } catch (t: Throwable) {
            System.err.println("[WARN] Failed to read presets from $path: ${t.message}. Using defaults.")
            defaultPresets()
        }
    }

    fun defaultBalancedIndex(presets: List<ExportPreset>): Int {
        val idx = presets.indexOfFirst { it.id.equals("balanced", ignoreCase = true) }
        return if (idx >= 0) idx else 0
    }
}
