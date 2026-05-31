package org.litvin

import org.litvin.markup.PointV1

/**
 * Task 3.7 — FFmpeg command builder (v1)
 *
 * Builds an ffmpeg command line from: source file, EDL keep list (optional),
 * encoder, preset, resolution.
 *
 * Scope (v0.1.0):
 * - H.264 (libx264) MP4 output only.
 * - Applies scale when needed.
 * - Uses CRF and x264 preset from ExportPreset; also uses VBV maxrate/bufsize if provided.
 * - Idle-trim ON → uses filter_complex trim/atrim + concat with accurate re-encode at segment borders.
 */
object FFmpegCommandBuilder {
    data class BuildParams(
        val sourcePath: String,
        val outputPath: String,
        val preset: ExportPreset,
        val outWidth: Int,
        val outHeight: Int,
        val encoderLabel: String = "H.264 (libx264)",
        val idleTrim: Boolean = true,
        val keeps: List<PointV1> = emptyList(),
        val subtitlesAssPath: String? = null, // when non-null, burn-in subtitles (scoreboard)
        val adjustments: org.litvin.adjustments.AdjustmentsV1? = null, // optional color/geometry adjustments
    )

    data class Result(
        val args: List<String>,
        val preview: String,
    )

    fun build(params: BuildParams): Result {
        val p = params
        val v = p.preset.video
        val a = p.preset.audio
        val c = p.preset.container

        // Select video codec based on encoder label; default to libx264
        val isNvenc = p.encoderLabel.contains("NVENC", ignoreCase = true)
        val isQsv = p.encoderLabel.contains("QSV", ignoreCase = true)
        val isAmf = p.encoderLabel.contains("AMF", ignoreCase = true)
        val videoCodec = when {
            isNvenc -> "h264_nvenc"
            isQsv -> "h264_qsv"
            isAmf -> "h264_amf"
            p.encoderLabel.contains("libx264", ignoreCase = true) -> "libx264"
            else -> (v.codec ?: "libx264")
        }

        val args = mutableListOf<String>()
        // Enable info-level logs and show banner; also request machine-readable progress to stderr
        args += listOf("-y", "-v", "info", "-progress", "pipe:2", "-nostats")
        args += listOf("-i", p.sourcePath)

        var filterComplex: String? = null
        var vMap = "[vout]"
        var aMap = "[aout]"

        fun buildColorFilter(adj: org.litvin.adjustments.AdjustmentsV1?): String? {
            if (adj == null) return null
            val wb = adj.whiteBalance
            // Convert model (VLCJ-oriented) scale → FFmpeg eq scale:
            // - Model brightness is [0..3] with identity 1.0; FFmpeg expects [-1..+1] with identity 0.0 → b_ff = (b_model - 1)
            // - Contrast and saturation are multipliers already (identity 1.0) → pass through (with clamp to eq supported ranges)
            // - Gamma derived from tint (identity 1.0) and slight saturation tweak from temperature for preview parity.
            val bModel = adj.brightness // [~0..3], identity 1.0 in current UI/model mapping
            val cModel = adj.contrast   // [~0..3], identity 1.0
            var sModel = adj.saturation // [~0..3], identity 1.0
            val gamma = 1.0 + ((wb?.tint ?: 0.0f).toDouble() * 0.2)
            sModel += ((wb?.temperature ?: 0.0f) * 0.05f)
            // Map and clamp
            val bc = (bModel - 1.0f).coerceIn(-1.0f, 1.0f).toDouble()
            val cc = cModel.coerceIn(0.0f, 3.0f).toDouble()
            val sc = sModel.coerceIn(0.0f, 3.0f).toDouble()
            val gc = gamma.coerceIn(0.1, 10.0)
            val isIdentity = (Math.abs(bc) < 1e-6) && (Math.abs(cc - 1.0) < 1e-6) && (Math.abs(sc - 1.0) < 1e-6) && (Math.abs(gc - 1.0) < 1e-6)
            if (isIdentity) return null
            // Use Locale.US formatting to ensure dot decimal
            fun fmt(d: Double): String = java.lang.String.format(java.util.Locale.US, "%.4f", d)
            return "eq=brightness=${fmt(bc)}:contrast=${fmt(cc)}:saturation=${fmt(sc)}:gamma=${fmt(gc)}"
        }

        if (p.idleTrim && p.keeps.isNotEmpty()) {
            // Build trim/concat graph
            val parts = mutableListOf<String>()
            val vLabels = mutableListOf<String>()
            val aLabels = mutableListOf<String>()
            p.keeps.forEachIndexed { idx, keep ->
                val s = keep.startMs / 1000.0
                val e = keep.endMs / 1000.0
                val vLbl = "v$idx"
                val aLbl = "a$idx"
                parts += "[0:v]trim=start=${s.formatSecs()}:end=${e.formatSecs()},setpts=PTS-STARTPTS[$vLbl]"
                parts += "[0:a]atrim=start=${s.formatSecs()}:end=${e.formatSecs()},asetpts=PTS-STARTPTS[$aLbl]"
                vLabels += "[$vLbl]"
                aLabels += "[$aLbl]"
            }
            // Concat and scale after concat
            parts += vLabels.joinToString(separator = "") + "concat=n=" + p.keeps.size + ":v=1:a=0[vcat]"
            val scaleStr = "scale=${p.outWidth}:-2"
            // Output scaled video into an intermediate label; we may append subtitles next
            parts += "[vcat]$scaleStr[vsc]"
            parts += aLabels.joinToString(separator = "") + "concat=n=${p.keeps.size}:v=0:a=1" + aMap
            // Apply color adjustments after scale to match preview, before subtitles
            val color = buildColorFilter(p.adjustments)
            var baseLabel = "[vsc]"
            if (color != null) {
                parts += "$baseLabel$color[vclr]"
                baseLabel = "[vclr]"
            }
            // If subtitles present, append burn-in after color; else map baseLabel directly
            val subPath = p.subtitlesAssPath
            if (subPath != null) {
                val esc = escapeForFilterPath(subPath)
                parts += "$baseLabel" + "subtitles='${esc}'" + vMap
            } else {
                vMap = baseLabel
            }
            filterComplex = parts.joinToString(";")
        }

        // Video options
        args += listOf("-c:v", videoCodec)
        if (isNvenc) {
            // Map CRF to CQ for NVENC; choose a reasonable preset
            val cq = (v.crf ?: 21).coerceIn(0, 51)
            args += listOf("-cq", cq.toString())
            // NVENC presets: default to a medium level if not specified
            val nvPreset = when (v.x264Preset?.lowercase()) {
                "veryfast" -> "p1"
                "faster", "fast" -> "p3"
                "medium", null -> "p5"
                "slow" -> "p6"
                else -> "p5"
            }
            args += listOf("-preset", nvPreset)
            // Use high profile yuv420p unless overridden
            args += listOf("-profile:v", "high")
        } else if (p.encoderLabel.contains("libx264", ignoreCase = true)) {
            // Software x264 tuning
            v.x264Preset?.let { args += listOf("-preset", it) }
            v.crf?.let { args += listOf("-crf", it.toString()) }
        } else {
            // Other hardware encoders (QSV/AMF): keep defaults; consider mapping CRF to a vendor-specific quality if needed in future.
        }
        v.vbvMaxrateK?.let { maxk ->
            args += listOf("-maxrate", "${maxk}k")
            v.vbvBufsizeK?.let { bufk -> args += listOf("-bufsize", "${bufk}k") }
        }
        v.pixelFormat?.let { args += listOf("-pix_fmt", it) }
        // GOP length optional — without knowing FPS, skip to avoid incorrect value in MVP

        // Audio options
        if (a != null) {
            val acodec = a.codec ?: "aac"
            args += listOf("-c:a", acodec)
            a.bitrateK?.let { args += listOf("-b:a", "${it}k") }
            a.channels?.let { args += listOf("-ac", it.toString()) }
        } else {
            // Default to AAC stereo
            args += listOf("-c:a", "aac", "-b:a", "192k", "-ac", "2")
        }

        // Filters
        if (filterComplex != null) {
            args += listOf("-filter_complex", filterComplex, "-map", vMap, "-map", aMap)
        } else {
            // simple scale, optionally followed by color adjustments and subtitles burn-in
            val sub = p.subtitlesAssPath
            val color = buildColorFilter(p.adjustments)
            val filters = mutableListOf<String>()
            filters += "scale=${p.outWidth}:-2"
            if (color != null) filters += color
            if (sub != null) {
                val esc = escapeForFilterPath(sub)
                filters += "subtitles='${esc}'"
            }
            val vf = filters.joinToString(",")
            args += listOf("-vf", vf)
        }

        // Container options
        // Always specify container format when known to support temporary .part outputs where extension is not standard.
        c?.format?.let { fmt -> args += listOf("-f", fmt) }
        if (c?.fastStart == true) {
            args += listOf("-movflags", "+faststart")
        }

        args += p.outputPath

        val preview = buildString {
            append("ffmpeg ")
            args.forEach { a ->
                // Quote paths/filters containing special characters
                val needQuote = a.any { it.isWhitespace() } || a.contains(":") || a.contains(";") || a.contains("=") || a.contains("[")
                if (needQuote) append('"').append(a.replace("\\", "\\\\")).append('"') else append(a)
                append(' ')
            }
        }.trim()

        return Result(args = args, preview = preview)
    }
}

private fun Double.formatSecs(): String {
    // Keep 3 decimal places for sub-second accuracy; force dot decimal regardless of locale
    return java.lang.String.format(java.util.Locale.US, "%.3f", this)
}

private fun escapeForFilterPath(path: String): String {
    // Escape for use inside ffmpeg filter arguments on Windows:
    // - escape backslashes
    // - escape drive letter colon and any other colons
    // - escape single quotes (we wrap in single quotes)
    return path
        .replace("\\", "\\\\")
        .replace(":", "\\:")
        .replace("'", "\\'")
}
