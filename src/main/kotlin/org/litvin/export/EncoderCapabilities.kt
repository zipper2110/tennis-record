package org.litvin.export

import org.litvin.FFmpegCapabilities

data class EncoderCapabilities(
    val availableIds: Set<String>,
    val preferredId: String?,
) {
    companion object {
        val NONE = EncoderCapabilities(emptySet(), null)

        fun production(): EncoderCapabilities {
            val available = try {
                FFmpegCapabilities.h264Encoders()
            } catch (_: Throwable) {
                emptySet()
            }
            val preferred = try {
                FFmpegCapabilities.preferredH264Encoder(available)
            } catch (_: Throwable) {
                null
            }
            return EncoderCapabilities(available, preferred)
        }
    }
}
