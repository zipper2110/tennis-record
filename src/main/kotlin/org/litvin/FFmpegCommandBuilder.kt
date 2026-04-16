package org.litvin

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
            // If subtitles present, append burn-in after scaling; else just map vsc as output
            val subPath = p.subtitlesAssPath
            if (subPath != null) {
                val esc = escapeForFilterPath(subPath)
                parts += "[vsc]subtitles='${esc}'$vMap"
            } else {
                // No extra node; map label directly by setting vMap to [vsc]
                vMap = "[vsc]"
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
            // simple scale, optionally followed by subtitles burn-in
            val sub = p.subtitlesAssPath
            val vf = if (sub != null) {
                val esc = escapeForFilterPath(sub)
                "scale=${p.outWidth}:-2,subtitles='${esc}'"
            } else {
                "scale=${p.outWidth}:-2"
            }
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
