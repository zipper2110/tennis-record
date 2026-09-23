package org.litvin

import org.litvin.export.scoreboard.ScoreboardAss
import org.litvin.export.scoreboard.ScoreboardLayouts
import org.litvin.scoring.ScoreboardSettingsV1
import java.io.File
import java.util.Locale

/**
 * Task 3.19 — Helper to generate an ASS subtitles file for the scoreboard overlay.
 *
 * The scoreboard comes from [ScoreboardLayouts] and [ScoreboardAss]. The scoring preview sends the
 * same ASS events to mpv, so the preview and the export look the same.
 * Each span writes all board events with the same start and end, so the board changes atomically
 * at point boundaries. The layer number keeps the drawing order.
 */
object AssOverlayWriter {
    /** Comment events use layers above all scoreboard layers. */
    private const val COMMENT_LAYER = 1000

    /**
     * Write an ASS file using the given video resolution and spans.
     * Fonts, sizes, paddings are scaled relative to 1080p.
     */
    fun write(
        file: File,
        spans: List<OverlaySpan>,
        outWidth: Int,
        outHeight: Int,
        settings: ScoreboardSettingsV1 = ScoreboardSettingsV1(),
    ) = write(
        file = file,
        scoreboardSpans = spans,
        commentSpans = emptyList(),
        outWidth = outWidth,
        outHeight = outHeight,
        settings = settings,
    )

    fun write(
        file: File,
        scoreboardSpans: List<OverlaySpan>,
        commentSpans: List<CommentOverlaySpan>,
        outWidth: Int,
        outHeight: Int,
        settings: ScoreboardSettingsV1 = ScoreboardSettingsV1(),
    ) {
        val playResX = outWidth.coerceAtLeast(16)
        val playResY = outHeight.coerceAtLeast(16)
        val scale = (playResY / 1080.0).coerceAtLeast(0.25)
        val commentFont = (78 * scale).coerceIn(27.0, 144.0)
        val fontFamily = "Arial"

        file.parentFile?.mkdirs()
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.appendLine("[Script Info]")
            w.appendLine("ScriptType: v4.00+")
            w.appendLine("PlayResX: $playResX")
            w.appendLine("PlayResY: $playResY")
            w.appendLine("ScaledBorderAndShadow: yes")
            // Without this header, FFmpeg converts the colors like VSFilter (TV range BT.601).
            // The mpv preview uses the RGB values as they are, so the export must do the same.
            w.appendLine("YCbCr Matrix: None")
            w.appendLine()

            // Colors use &HAABBGGRR (AA: 00 opaque, FF transparent)
            val black = assColor(0x000000, 0x00)
            val commentBackdrop = assColor(0x000000, 0x80)
            val commentAlpha = 0x80 // 50% transparent text and border
            val commentText = assColor(0xFFFFFF, commentAlpha)
            val commentOutline = assColor(0x000000, commentAlpha)

            w.appendLine("[V4+ Styles]")
            w.appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
            // Board events set all of their override tags; this style only supplies neutral values.
            w.appendLine("Style: Board,$fontFamily,20,${assColor(0xFFFFFF, 0x00)},&H000000FF,$black,$black,0,0,0,0,100,100,0,0,1,0,0,7,0,0,0,0")
            w.appendLine("Style: CommentText,$fontFamily,${fmt(commentFont)},$commentText,&H000000FF,$commentOutline,$black,1,0,0,0,100,100,0,0,1,1.2,0,2,0,0,0,0")
            w.appendLine("Style: CommentBackdrop,$fontFamily,20,$commentBackdrop,&H000000FF,&H00000000,$black,0,0,0,0,100,100,0,0,1,0,0,2,0,0,0,0")
            w.appendLine()

            w.appendLine("[Events]")
            w.appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")

            for (s in scoreboardSpans) {
                if (s.endMs <= s.startMs) continue
                val start = toAssTs(s.startMs)
                val end = toAssTs(s.endMs)
                val display = ScoreboardComponent.display(withStructuredState(s))
                val scene = ScoreboardLayouts.scene(display, settings)
                val placement = ScoreboardAss.place(scene, settings, 0.0, 0.0, playResX.toDouble(), playResY.toDouble())
                ScoreboardAss.events(scene, placement).forEachIndexed { layer, text ->
                    w.appendLine("Dialogue: $layer,$start,$end,Board,,0,0,0,,$text")
                }
            }

            for (comment in commentSpans) {
                if (comment.endMs <= comment.startMs || comment.text.isBlank()) continue
                val lines = wrapCommentLines(comment.text, maxCommentLineLength(playResX, commentFont))
                val longestLine = lines.maxOfOrNull { it.length } ?: 0
                val horizontalPadding = (28 * scale).toInt().coerceAtLeast(8)
                val verticalPadding = (18 * scale).toInt().coerceAtLeast(6)
                val lineHeight = (commentFont * 1.25).toInt().coerceAtLeast(18)
                val boxWidth = ((longestLine * commentFont * 0.56).toInt() + horizontalPadding * 2)
                    .coerceIn(1, (playResX * 0.93).toInt())
                val boxHeight = (lines.size * lineHeight + verticalPadding * 2).coerceAtLeast(1)
                val lowerThirdY = (playResY * 0.82).toInt()
                val boxLeft = ((playResX - boxWidth) / 2).coerceAtLeast(0)
                val boxTop = (lowerThirdY - boxHeight).coerceAtLeast(0)
                val box = drawRect(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight)
                val colorRgb = comment.colorHex.removePrefix("#").toIntOrNull(16) ?: 0xFFFFFF
                val start = toAssTs(comment.startMs)
                val end = toAssTs(comment.endMs)
                val escapedText = lines.joinToString("\\N") { escapeAssText(it) }
                val textY = lowerThirdY - verticalPadding

                w.appendLine("Dialogue: $COMMENT_LAYER,$start,$end,CommentBackdrop,,0,0,0,,{\\p1}\\1c$commentBackdrop$box{\\p0}")
                w.appendLine("Dialogue: ${COMMENT_LAYER + 1},$start,$end,CommentText,,0,0,0,,{\\an2\\pos(${playResX / 2},$textY)\\1c${assColor(colorRgb, 0x00)}\\1a${assAlpha(commentAlpha)}\\3a${assAlpha(commentAlpha)}}$escapedText")
            }
        }
    }

    /**
     * Returns the span with structured score fields. Old spans have only the state text,
     * so the fields come from parsing the text.
     */
    private fun withStructuredState(s: OverlaySpan): OverlaySpan {
        val structured = s.isTiebreak || s.p1Pts != 0 || s.p2Pts != 0 || s.gamesP1 != 0 || s.gamesP2 != 0 ||
            s.setsP1 != 0 || s.setsP2 != 0 || s.completedSets.isNotEmpty()
        if (structured) return s
        val p = parseState(s.text)
        return s.copy(
            p1Pts = p.p1Pts,
            p2Pts = p.p2Pts,
            gamesP1 = p.gamesP1,
            gamesP2 = p.gamesP2,
            setsP1 = p.setsP1,
            setsP2 = p.setsP2,
            completedSets = p.completedSets,
        )
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

    private fun maxCommentLineLength(playResX: Int, fontSize: Double): Int =
        ((playResX * 0.86) / (fontSize * 0.56)).toInt().coerceAtLeast(12)

    private fun wrapCommentLines(text: String, maxChars: Int): List<String> =
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').flatMap { line ->
            wrapCommentLine(line, maxChars)
        }

    private fun wrapCommentLine(line: String, maxChars: Int): List<String> {
        if (line.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in line.trim().split(Regex("\\s+"))) {
            if (word.length > maxChars) {
                if (current.isNotEmpty()) {
                    lines += current
                    current = ""
                }
                val chunks = word.chunked(maxChars)
                lines += chunks.dropLast(1)
                current = chunks.last()
            } else if (current.isEmpty()) {
                current = word
            } else if (current.length + 1 + word.length <= maxChars) {
                current += " $word"
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun escapeAssText(text: String): String = text
        .replace("\\", "\\\\")
        .replace("{", "\\{")
        .replace("}", "\\}")

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

    /** ASS alpha override, e.g. "&H99&" (00 opaque, FF transparent). */
    private fun assAlpha(aa: Int): String = String.format(Locale.US, "&H%02X&", aa.coerceIn(0, 255))

    private fun fmt(v: Double): String = String.format(Locale.US, "%.1f", v)
}
