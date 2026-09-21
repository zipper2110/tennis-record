package org.litvin.analytics

sealed class AnalyticsEvent private constructor() {
    data object SessionStarted : AnalyticsEvent()
    data object SessionHeartbeat : AnalyticsEvent()
    data object SessionEnded : AnalyticsEvent()
    data object ProjectCreated : AnalyticsEvent()
    data object ProjectOpened : AnalyticsEvent()
    data class SourceVideoOpened(val result: SourceVideoResult) : AnalyticsEvent()
    data object PointAdded : AnalyticsEvent()
    data object PointRemoved : AnalyticsEvent()
    data object ScorePointRecorded : AnalyticsEvent()
    data class AdjustmentChanged(val category: AdjustmentCategory) : AnalyticsEvent()
    data class ExportStarted(val container: ExportContainer, val encoderFamily: EncoderFamily) : AnalyticsEvent()
    data class ExportCompleted(
        val container: ExportContainer,
        val encoderFamily: EncoderFamily,
        val duration: ExportDurationMs
    ) : AnalyticsEvent()
    data class ExportFailed(val category: ExportFailureCategory, val duration: ExportDurationMs) : AnalyticsEvent()
    data class ExportCancelled(val duration: ExportDurationMs) : AnalyticsEvent()
}

enum class SourceVideoResult(internal val wireValue: String) {
    SUCCESS("success"), UNSUPPORTED("unsupported"), INVALID_MEDIA("invalid_media"), UNAVAILABLE("unavailable"), OTHER("other")
}

enum class AdjustmentCategory(internal val wireValue: String) {
    CROP_ROTATE("crop_rotate"), COLOR("color"), SCALE("scale"), OTHER("other")
}

enum class ExportContainer(internal val wireValue: String) { MP4("mp4"), MOV("mov"), MKV("mkv"), OTHER("other") }

enum class EncoderFamily(internal val wireValue: String) {
    SOFTWARE("software"), NVIDIA("nvidia"), INTEL("intel"), AMD("amd"), APPLE("apple"), OTHER("other")
}

enum class ExportFailureCategory(internal val wireValue: String) {
    VALIDATION("validation"), DEPENDENCY_UNAVAILABLE("dependency_unavailable"), ENCODER_UNAVAILABLE("encoder_unavailable"),
    PROCESS_START("process_start"), PROCESSING("processing"), OUTPUT_WRITE("output_write"), OTHER("other")
}

@JvmInline
value class ExportDurationMs(val value: Int) {
    init {
        require(value in 0..AnalyticsEventRegistry.MAX_ELAPSED_MS) { "duration must be within the approved range" }
    }
}
