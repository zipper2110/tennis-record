package org.litvin

import java.io.File
import java.util.Locale

/**
 * Task 3.19 — Helper to generate an ASS subtitles file for the scoreboard overlay.
 *
 * Generates a styled scoreboard in the top-left safe area, matching the v0.1.0 visual.
 * One Dialogue block per span is emitted with all static/text elements grouped so they
 * switch atomically at point boundaries.
 */
object AssOverlayWriter {
    /**
     * Write an ASS file using the given video resolution and spans.
     * Fonts, sizes, paddings are scaled relative to 1080p.
     */
    fun write(file: File, spans: List<OverlaySpan>, outWidth: Int, outHeight: Int) {
        val playResX = outWidth.coerceAtLeast(16)
        val playResY = outHeight.coerceAtLeast(16)
        val scale = (playResY / 1080.0).coerceAtLeast(0.25)

        // Layout metrics per spec (1080p → scale proportionally)
        val marginTL = (48 * scale).toInt()
        val panelW = (420 * scale).toInt()
        val panelH = (200 * scale).toInt()
        val radius = (12 * scale).toInt() // rounded corners omitted in MVP (ASS limitation)

        // Header
        val headerH = (56 * scale).toInt()
        val dotSize = (16 * scale).toInt()
        val titleFont = (28 * scale).coerceIn(14.0, 96.0)

        // Rows
        val rowGap = (28 * scale).toInt()
        val iconSize = (24 * scale).toInt()
        val nameFont = (30 * scale).coerceIn(14.0, 96.0)
        val cellW = (44 * scale).toInt()
        val cellGap = (22 * scale).toInt()
        val nameCellGap = (190 * scale).toInt()
        val bigScoreW = (120 * scale).toInt()
        val bigScoreFont = (42 * scale).coerceIn(24.0, 160.0)
        val setFont = (36 * scale).coerceIn(14.0, 96.0)

        file.parentFile?.mkdirs()
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            // Header
            w.appendLine("[Script Info]")
            w.appendLine("ScriptType: v4.00+")
            w.appendLine("PlayResX: $playResX")
            w.appendLine("PlayResY: $playResY")
            w.appendLine("ScaledBorderAndShadow: yes")
            w.appendLine()

            // Styles
            w.appendLine("[V4+ Styles]")
            w.appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
            // Colors use &HAABBGGRR (AA: 00 opaque, FF transparent)
            val neon = assColor(0xC4FF4D, 0x00)
            val textPrimary = assColor(0xE7ECEF, 0x13) // ~92% opacity
            val textMuted = assColor(0xCCCCCC, 0x00)
            val panelBg = assColor(0x0E1116, 0x80) // 50% opacity background
            val white = assColor(0xFFFFFF, 0x00)
            val black = assColor(0x000000, 0x00)

            // Base styles
            w.appendLine("Style: Title,Arial,${fmt(titleFont)},$neon,&H000000FF,&H00000000,$black,1,0,0,0,100,100,2,0,1,1.5,0,7,0,0,0,0")
            w.appendLine("Style: Name,Arial,${fmt(nameFont)},$textPrimary,&H000000FF,&H00000000,$white,0,0,0,0,100,100,0,0,1,1.2,0,7,0,0,0,0")
            w.appendLine("Style: Cell,Arial,${fmt(setFont)},$textMuted,&H000000FF,&H00000000,$white,0,0,0,0,100,100,0,0,1,0.2,0,7,0,0,0,0")
            w.appendLine("Style: BigScore,Arial,${fmt(bigScoreFont)},$neon,&H000000FF,&H00000000,$white,1,0,0,0,100,100,0,0,1,0.2,0,9,0,0,0,0")
            w.appendLine("Style: BigScoreDim,Arial,${fmt(bigScoreFont)},$neon,&H000000FF,&H00000000,$black,1,0,0,0,100,100,0,0,1,1.2,0,9,0,0,0,0")
            w.appendLine("Style: Dot,Arial,${fmt(nameFont)},$neon,&H000000FF,&H00000000,$black,1,0,0,0,100,100,0,0,1,1.2,0,7,0,0,0,0")
            w.appendLine("Style: Panel,Arial,20,${withAlpha(black, 0x50)},&H000000FF,&H00000000,$black,0,0,0,0,100,100,0,0,1,0,0,7,0,0,0,0")
            w.appendLine("Style: Square,Arial,20,${withAlpha(neon, 0x30)},&H000000FF,&H00000000,$black,0,0,0,0,100,100,0,0,1,0,0,7,0,0,0,0")
            w.appendLine()

            // Events
            w.appendLine("[Events]")
            w.appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")

            for (s in spans) {
                if (s.endMs <= s.startMs) continue
                val start = toAssTs(s.startMs)
                val end = toAssTs(s.endMs)
                // Parse scoreboard state from text
                val st = parseState(s.text)

                // Panel background (simple rectangle; rounded/shadow omitted in MVP)
                val px = marginTL
                val py = marginTL
                val rect = drawRect(px, py, px + panelW, py + panelH)
                w.appendLine("Dialogue: 0,$start,$end,Panel,,0,0,0,,{\\p1}\\1c$panelBg$rect{\\p0}")

                // Header: dot + title
                val dotX = px + (20 * scale).toInt()
                val dotY = py + headerH / 2
                // Using bullet as dot (approximate circle)
                w.appendLine("Dialogue: 1,$start,$end,Dot,,0,0,0,,{\\pos(${dotX},${dotY})}•")
                val titleX = dotX + dotSize + (12 * scale).toInt()
                val titleY = py + (headerH * 0.5).toInt()
                w.appendLine("Dialogue: 1,$start,$end,Title,,0,0,0,,{\\an7} {\\pos(${titleX},${titleY})}TennisRecord app")

                // Player rows positions
                val row1Y = py + headerH + (15 * scale).toInt()
                val row2Y = row1Y + (nameFont * 1.2 + rowGap).toInt()
                val contentX = px + (24 * scale).toInt()

                // Icon squares (drawn as filled rectangles)
                val icon1 = drawRect(contentX, row1Y, contentX + iconSize, row1Y + iconSize)
                val icon2 = drawRect(contentX, row2Y - iconSize, contentX + iconSize, row2Y)
                w.appendLine("Dialogue: 1,$start,$end,Square,,0,0,0,,{\\p1}\\1c${assColor(0x4DA3FF, 0x00)}$icon1{\\p0}")
                w.appendLine("Dialogue: 1,$start,$end,Square,,0,0,0,,{\\p1}\\1c${assColor(0xFF6B6B, 0x00)}$icon2{\\p0}")

                // Names (use provided names with fallback; uppercase at render time; apply simple ellipsis)
                val nameX = contentX + iconSize + (12 * scale).toInt()
                val p1Name = (s.p1Name ?: "Player 1").ifBlank { "Player 1" }
                val p2Name = (s.p2Name ?: "Player 2").ifBlank { "Player 2" }
                val maxChars = 20
                fun ellipsizeName(n: String): String {
                    val up = n.uppercase()
                    return if (up.length <= maxChars) up else up.substring(0, maxChars - 1) + "\u2026"
                }
                w.appendLine("Dialogue: 2,$start,$end,Name,,0,0,0,,{\\an7} {\\pos(${nameX},${row1Y})}${ellipsizeName(p1Name)}")
                w.appendLine("Dialogue: 2,$start,$end,Name,,0,0,0,,{\\an7} {\\pos(${nameX},${row2Y})}${ellipsizeName(p2Name)}")

                // Reserve right side for big score and compute cell block bounds
                val rightPad = (24 * scale).toInt()
                val scoreRightX = px + panelW - rightPad
                val rightLimit = scoreRightX - bigScoreW - (36 * scale).toInt()

                // Set cells (last two sets)
                val cell1X = nameX + nameCellGap
                val sets = st.completedSets.takeLast(2)
                val totalCells = sets.size + 1 // +1 for current games column
                val totalCellsW = totalCells * cellW + (totalCells - 1) * cellGap
                var cx = cell1X
                sets.forEach { (s1, s2) ->
                    // top row value for P1, bottom for P2
                    w.appendLine("Dialogue: 2,$start,$end,Cell,,0,0,0,,{\\an7} {\\pos(${cx},${row1Y})}${s1}")
                    w.appendLine("Dialogue: 2,$start,$end,Cell,,0,0,0,,{\\an7} {\\pos(${cx},${row2Y})}${s2}")
                    cx += cellW + cellGap
                }

                // Current games per set (ongoing set) shown as additional cells
                w.appendLine("Dialogue: 2,$start,$end,Cell,,0,0,0,,{\\an7} {\\pos(${cx},${row1Y})}${st.gamesP1}")
                w.appendLine("Dialogue: 2,$start,$end,Cell,,0,0,0,,{\\an7} {\\pos(${cx},${row2Y})}${st.gamesP2}")
                cx += cellW + cellGap

                // Big point score at the right (top-right aligned, anchored to panel right pad)
                val scoreX = scoreRightX
                val scoreY1 = row1Y - 4
                val scoreY2 = row2Y - 4
                val p1Lead = isP1Leading(st)
                val p1PtsTxt = pointsLabel(st.p1Pts, st.p2Pts)
                val p2PtsTxt = pointsLabel(st.p2Pts, st.p1Pts)
                val style1 = if (p1Lead) "BigScore" else "BigScoreDim"
                val style2 = if (p1Lead) "BigScoreDim" else "BigScore"
                w.appendLine("Dialogue: 3,$start,$end,$style1,,0,0,0,,{\\an9} {\\pos(${scoreX},${scoreY1})}${p1PtsTxt}")
                w.appendLine("Dialogue: 3,$start,$end,$style2,,0,0,0,,{\\an9} {\\pos(${scoreX},${scoreY2})}${p2PtsTxt}")
            }
        }
    }

    // ----- Parsing helpers -----
    private data class State(
        val p1Pts: Int, val p2Pts: Int,
        val gamesP1: Int, val gamesP2: Int,
        val setsP1: Int, val setsP2: Int,
        val completedSets: List<Pair<Int, Int>>,
    )

    private fun parseState(text: String): State {
        // Expected format from ScoreboardTimelineBuilder.stateText()
        // Player 1: pts X, games G, sets S  |  Player 2: pts Y, games G2, sets S2 [a-b, c-d]
        var p1Pts = 0; var p2Pts = 0; var g1 = 0; var g2 = 0; var s1 = 0; var s2 = 0
        val sets = mutableListOf<Pair<Int, Int>>()
        try {
            val sep = "  |  "
            val left = text.substringBefore(sep, text)
            val right = text.substringAfter(sep, "")
            fun nums(s: String): Triple<Int, Int, Int> {
                // returns (pts, games, sets)
                val r = Regex("pts\\s+(\\w+).*?games\\s+(\\d+).*?sets\\s+(\\d+)", RegexOption.IGNORE_CASE)
                val m = r.find(s)
                val ptsStr = m?.groupValues?.getOrNull(1) ?: "0"
                val pts = when (ptsStr.trim().lowercase()) {
                    "0" -> 0
                    "15" -> 1
                    "30" -> 2
                    "40" -> 3
                    "ad" -> 4 // advantage
                    else -> ptsStr.toIntOrNull() ?: 0
                }
                val games = m?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
                val setsV = m?.groupValues?.getOrNull(3)?.toIntOrNull() ?: 0
                return Triple(pts, games, setsV)
            }
            val (lp, lg, ls) = nums(left)
            val (rp, rg, rs) = nums(right)
            p1Pts = lp; g1 = lg; s1 = ls
            p2Pts = rp; g2 = rg; s2 = rs
            val br = Regex("\\[(.*)]").find(text)?.groupValues?.getOrNull(1)
            if (!br.isNullOrBlank()) {
                br.split(",").forEach { token ->
                    val t = token.trim()
                    val mm = Regex("(\\d+)\\s*[-:]\\s*(\\d+)").find(t)
                    if (mm != null) {
                        val a = mm.groupValues[1].toInt()
                        val b = mm.groupValues[2].toInt()
                        sets += a to b
                    }
                }
            }
        } catch (_: Throwable) { }
        return State(p1Pts, p2Pts, g1, g2, s1, s2, sets)
    }

    private fun isP1Leading(s: State): Boolean {
        // Determine leading by point score first; tie → no lead
        val pMap = fun(pts: Int): Int = when {
            pts >= 4 -> 4 else -> pts
        }
        val p1 = pMap(s.p1Pts)
        val p2 = pMap(s.p2Pts)
        if (p1 != p2) return p1 > p2
        return false
    }

    private fun pointsLabel(mine: Int, other: Int): String {
        val base = arrayOf("0", "15", "30", "40")
        if (mine < 4 && other < 4) return base[mine.coerceIn(0, 3)]
        if (mine == other) return "40"
        if (mine > other) return "Ad"
        return "40"
    }

    // ----- ASS helpers -----
    private fun toAssTs(ms: Long): String {
        val totalCs = (ms / 10).coerceAtLeast(0)
        val cs = (totalCs % 100).toInt()
        val totalSec = totalCs / 100
        val s = (totalSec % 60).toInt()
        val totalMin = totalSec / 60
        val m = (totalMin % 60).toInt()
        val h = (totalMin / 60).toInt()
        return String.format(Locale.US, "%d:%02d:%02d.%02d", h, m, s, cs)
    }

    private fun drawRect(x1: Int, y1: Int, x2: Int, y2: Int): String {
        return "m ${x1} ${y1} l ${x2} ${y1} l ${x2} ${y2} l ${x1} ${y2}"
    }

    private fun assColor(rgb: Int, alpha: Int): String {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        val aa = alpha.coerceIn(0, 255)
        // &HAABBGGRR
        return String.format(Locale.US, "&H%02X%02X%02X%02X", aa, b, g, r)
    }

    private fun withAlpha(col: String, aa: Int): String {
        // Replace the AA part in &HAABBGGRR
        return col.replace(Regex("^&H[0-9A-Fa-f]{2}")) { _ ->
            String.format(Locale.US, "&H%02X", aa.coerceIn(0, 255))
        }
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.1f", v)
}
