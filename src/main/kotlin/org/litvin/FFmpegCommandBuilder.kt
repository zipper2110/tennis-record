package org.litvin

import org.litvin.points.PointV1

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
 * - Idle-trim ON → one input-level seek (-ss/-t) per kept segment, joined with the concat filter,
 *   so decoding costs the kept duration rather than the whole source span.
 */
object FFmpegCommandBuilder {
    data class BuildParams(
        val sourcePath: String,
        val outputPath: String,
        val preset: ExportPreset,
        val outWidth: Int,
        val outHeight: Int,
        val outputFrameRate: String? = null,
        val encoderLabel: String = "H.264 (libx264)",
        val idleTrim: Boolean = true,
        val keeps: List<PointV1> = emptyList(),
        val subtitlesAssPath: String? = null, // when non-null, burn-in subtitles (scoreboard)
        val adjustments: org.litvin.adjustments.AdjustmentsV1? = null, // optional color/geometry adjustments
        // Source frame size for the crop/rotate geometry. When unknown, the output size gives the aspect.
        val sourceWidth: Int? = null,
        val sourceHeight: Int? = null,
        // Output time at which this command's first kept segment starts. Non-zero only when the
        // export is split into chunks: the scoreboard ASS is written once on the whole output
        // timeline, so a chunk has to shift its frames into that timeline before the burn-in.
        val outputTimeOffsetMs: Long = 0,
        // True when this command writes an intermediate chunk that a later pass joins by stream copy.
        val chunkOutput: Boolean = false,
    )

    data class Result(
        val args: List<String>,
        val preview: String,
    )

    fun build(params: BuildParams): Result {
        val p = params
        val v = ExportQualityProfiles.resolve(
            preset = p.preset,
            outWidth = p.outWidth,
            outHeight = p.outHeight,
            outputFrameRate = p.outputFrameRate,
        ).video
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

        // Idle-trim opens the source once per kept segment with an input-level seek. `-ss` before
        // `-i` seeks through the index and decodes only from the preceding keyframe, so the work is
        // proportional to the kept duration. Cutting with the trim filter instead would decode the
        // whole span up to the last keep and throw most of it away, which made a 5-second export of
        // a one-hour source cost the same as exporting the hour. `-ss` before the input has been
        // frame-accurate since ffmpeg 2.1 (it decodes and discards up to the exact point), so the
        // segment durations still match what ScoreboardTimelineBuilder assumes.
        val segmentInputs = p.idleTrim && p.keeps.isNotEmpty()
        if (segmentInputs) {
            p.keeps.forEach { keep ->
                val startSecs = keep.startMs / 1000.0
                val durationSecs = (keep.endMs - keep.startMs).coerceAtLeast(0) / 1000.0
                args += listOf("-ss", startSecs.formatSecs(), "-t", durationSecs.formatSecs())
                args += listOf("-i", p.sourcePath)
            }
        } else {
            args += listOf("-i", p.sourcePath)
        }

        var filterComplex: String? = null
        var vMap = "[vout]"
        var aMap = "[aout]"

        fun buildColorFilter(adj: org.litvin.adjustments.AdjustmentsV1?): String? {
            if (adj == null) return null
            val values = FfmpegColorAdjustmentStrategy.map(adj)
            if (!values.hasEqualizerAdjustments && !values.hasHueAdjustments && !values.hasToneAdjustments) return null
            // Use Locale.US formatting to ensure dot decimal
            fun fmt(d: Double): String = java.lang.String.format(java.util.Locale.US, "%.4f", d)
            val filters = mutableListOf<String>()
            if (values.hasEqualizerAdjustments) {
                filters += "eq=brightness=${fmt(values.brightness)}:contrast=${fmt(values.contrast)}:saturation=${fmt(values.saturation)}"
            }
            if (values.hasHueAdjustments) {
                filters += "hue=h=${fmt(values.hueDegrees)}"
            }
            if (values.hasToneAdjustments) {
                // Shadows/highlights as a luma lookup table. The cubes are written out as products
                // because a comma inside pow() would end the filter in the filtergraph syntax, and
                // u/v are passed through because lutyuv otherwise clips chroma to the legal range.
                val range = FfmpegColorAdjustmentStrategy.TONE_CODE_RANGE.toInt()
                val dark = "(($range-val)/$range)"
                val light = "(val/$range)"
                filters += "lutyuv=y=val" +
                    "+${fmt(values.shadowsLift)}*$dark*$dark*$dark" +
                    "+${fmt(values.highlightsLift)}*$light*$light*$light" +
                    ":u=val:v=val"
            }
            return filters.joinToString(",")
        }

        // Rotate (clockwise, same frame size, black corners) and crop, before the scale filter.
        // With a known source size, the crop uses the same even pixels as the preview shader.
        // Without it, the crop uses fractions of the frame (iw/ih) and the output size gives the aspect.
        fun buildGeometryFilter(adj: org.litvin.adjustments.AdjustmentsV1?): String? {
            if (adj == null) return null
            val sourceWidth = p.sourceWidth?.takeIf { it > 0 }
            val sourceHeight = p.sourceHeight?.takeIf { it > 0 }
            val knownSize = sourceWidth != null && sourceHeight != null
            val plan = org.litvin.adjustments.GeometryPlan.of(adj, sourceWidth ?: p.outWidth, sourceHeight ?: p.outHeight)
            if (plan.isIdentity) return null
            fun fmt(d: Double): String = java.lang.String.format(java.util.Locale.US, "%.6f", d)
            val filters = mutableListOf<String>()
            if (plan.hasRotation) {
                filters += "rotate=${fmt(Math.toRadians(plan.rotationDeg))}:ow=iw:oh=ih:c=black"
            }
            if (plan.hasCrop) {
                filters += if (knownSize) {
                    val (w, h, x, y) = plan.cropPixels(sourceWidth!!, sourceHeight!!).toList()
                    "crop=$w:$h:$x:$y"
                } else {
                    val c = plan.crop
                    "crop=w=iw*${fmt(c.width)}:h=ih*${fmt(c.height)}:x=iw*${fmt(c.x)}:y=ih*${fmt(c.y)}"
                }
            }
            return filters.joinToString(",")
        }
        val geometry = buildGeometryFilter(p.adjustments)

        if (segmentInputs) {
            // Build the concat graph over the per-segment inputs. Each input was already seeked and
            // length-limited on the command line, so the segment only needs its timestamps rebased.
            val parts = mutableListOf<String>()
            val vLabels = mutableListOf<String>()
            val aLabels = mutableListOf<String>()
            p.keeps.forEachIndexed { idx, _ ->
                val vLbl = "v$idx"
                val aLbl = "a$idx"
                parts += "[$idx:v]setpts=PTS-STARTPTS[$vLbl]"
                parts += "[$idx:a]asetpts=PTS-STARTPTS[$aLbl]"
                vLabels += "[$vLbl]"
                aLabels += "[$aLbl]"
            }
            // Concat and scale after concat
            parts += vLabels.joinToString(separator = "") + "concat=n=" + p.keeps.size + ":v=1:a=0[vcat]"
            val scaleStr = "scale=${p.outWidth}:-2"
            // Output scaled video into an intermediate label; we may append subtitles next
            parts += "[vcat]" + listOfNotNull(geometry, scaleStr).joinToString(",") + "[vsc]"
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
                // The subtitles filter picks events by frame timestamp. A chunk's frames start at
                // zero, so shift them onto the whole-output timeline for the burn-in and rebase
                // afterwards; the chunk still encodes from zero.
                val offsetSecs = p.outputTimeOffsetMs / 1000.0
                val burnIn = if (p.outputTimeOffsetMs > 0) {
                    "setpts=PTS+${offsetSecs.formatSecs()}/TB,subtitles='${esc}',setpts=PTS-STARTPTS"
                } else {
                    "subtitles='${esc}'"
                }
                parts += "$baseLabel" + burnIn + vMap
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
        p.outputFrameRate?.let { args += listOf("-r", it) }
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
            if (geometry != null) filters += geometry
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
        if (p.chunkOutput) {
            // Chunks are joined by the concat demuxer and copied into the real container afterwards.
            // MPEG-TS is the container meant for that: it carries H.264/AAC, tolerates being cut and
            // appended at arbitrary points, and remuxes back out without touching the frames.
            // faststart would only relocate an index this file never keeps.
            args += listOf("-f", "mpegts")
        } else {
            c?.format?.let { fmt -> args += listOf("-f", fmt) }
            if (c?.fastStart == true) {
                args += listOf("-movflags", "+faststart")
            }
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
