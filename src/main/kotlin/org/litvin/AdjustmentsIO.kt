package org.litvin.adjustments

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

/**
 * Adjustments v1 schema and JSON read/write helpers (epic 5.1).
 *
 * Storage:
 * - File name: adjustments.json placed in the project directory (next to edl.json/score.json)
 * - JSON library: Jackson Kotlin module (same configuration as EdlIO/ScoreIO)
 * - Unknown fields are preserved on read/write where feasible.
 */

/** Optional white balance group (v1). */
data class WhiteBalanceV1(
    val temperature: Float = 0.0f, // normalized [-1.0, +1.0]
    val tint: Float = 0.0f         // normalized [-1.0, +1.0]
)

/**
 * Adjustments model (v1). All fields optional with identity defaults.
 */
data class AdjustmentsV1(
    // Color
    val brightness: Float = 0.0f,  // [-1.0, +1.0] offset; identity is 0.0
    val contrast: Float = 1.0f,    // [0.0, 3.0]
    val saturation: Float = 1.0f,  // [0.0, 3.0]
    val whiteBalance: WhiteBalanceV1? = null, // optional; default identity

    // Geometry
    val zoom: Float = 1.0f,        // multiplier; clamp [0.1, 4.0] at sinks
    val panX: Float = 0.0f,        // normalized [-1.0, +1.0]
    val panY: Float = 0.0f,        // normalized [-1.0, +1.0]
    val rotationDeg: Float = 0.0f, // [-180.0, +180.0]

    val version: Int = 1,
) {
    // Preserve unknown JSON properties for forward compatibility.
    @JsonIgnore
    private val unknowns: MutableMap<String, Any?> = LinkedHashMap()

    @JsonAnySetter
    fun setUnknown(key: String, value: Any?) {
        // Avoid clobbering known fields if Jackson routes them here for any reason
        when (key) {
            "brightness", "contrast", "saturation", "whiteBalance",
            "zoom", "panX", "panY", "rotationDeg", "version" -> return
            else -> unknowns[key] = value
        }
    }

    @JsonAnyGetter
    fun getUnknowns(): Map<String, Any?> = unknowns
}

object AdjustmentsIO {
    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)

    /** Returns absolute path to adjustments.json for a given project directory. */
    fun adjustmentsFilePath(projectDir: String): String = File(projectDir, "adjustments.json").absolutePath

    /** Write AdjustmentsV1 to the given file path. */
    fun write(filePath: String, adj: AdjustmentsV1) {
        mapper.writeValue(File(filePath), adj)
    }

    /** Read AdjustmentsV1 from the given file path; returns identity defaults if file does not exist. */
    fun read(filePath: String): AdjustmentsV1 {
        val f = File(filePath)
        if (!f.exists()) return AdjustmentsV1()
        return mapper.readValue(f)
    }

    /** Convenience: read for a given project directory. */
    fun readForProjectDir(projectDir: String): AdjustmentsV1 = read(adjustmentsFilePath(projectDir))

    /** Convenience: write for a given project directory. */
    fun writeForProjectDir(projectDir: String, adj: AdjustmentsV1) = write(adjustmentsFilePath(projectDir), adj)
}
