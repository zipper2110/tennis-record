package org.litvin

import java.io.File
import java.util.Locale

object AssPreviewScoreboardWriter {
    fun write(
        file: File,
        spans: List<OverlaySpan>,
        outWidth: Int,
        outHeight: Int,
        style: ScoreboardStyle = ScoreboardStyles.default(),
    ) {
        val playResX = outWidth.coerceAtLeast(16)
        val playResY = outHeight.coerceAtLeast(16)
        val scale = (playResY / 1080.0).coerceAtLeast(0.25)
        val fontSize = (30 * scale).coerceIn(16.0, 72.0)
        val margin = (style.layout.marginBase * scale).toInt()

        file.parentFile?.mkdirs()
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.appendLine("[Script Info]")
            w.appendLine("ScriptType: v4.00+")
            w.appendLine("PlayResX: $playResX")
            w.appendLine("PlayResY: $playResY")
            w.appendLine("ScaledBorderAndShadow: yes")
            w.appendLine()
            w.appendLine("[V4+ Styles]")
            w.appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
            w.appendLine("Style: PreviewBoard,${style.previewFontFamily},${fmt(fontSize)},${assColor(style.palette.textPrimaryRgb, 0x00)},&H000000FF,${assColor(0x000000, 0x00)},${assColor(style.palette.panelBgRgb, 0x70)},1,0,0,0,100,100,0,0,3,12,0,7,0,0,0,0")
            w.appendLine()
            w.appendLine("[Events]")
            w.appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")

            spans.forEach { span ->
                if (span.endMs <= span.startMs) return@forEach
                val text = buildText(ScoreboardComponent.display(span, style))
                w.appendLine("Dialogue: 0,${toAssTs(span.startMs)},${toAssTs(span.endMs)},PreviewBoard,,0,0,0,,{\\an7\\pos($margin,$margin)}$text")
            }
        }
    }

    private fun buildText(display: ScoreboardDisplay): String {
        val style = display.style
        val p1Sets = display.completedSets.joinToString("") { hardPad(it.first.toString(), 4) }
        val p2Sets = display.completedSets.joinToString("") { hardPad(it.second.toString(), 4) }
        val p1Color = assTextColor(display.player1Rgb)
        val p2Color = assTextColor(display.player2Rgb)
        val textColor = assTextColor(style.palette.textPrimaryRgb)
        val scoreColor = assTextColor(style.palette.accentRgb)

        return buildString {
            append("{\\c").append(scoreColor).append("}* ").append(escape(style.title))
            append("\\N")
            append("{\\c").append(p1Color).append("}# ")
            append("{\\c").append(textColor).append("}").append(hardPad(escape(display.player1PreviewName), style.layout.previewNameMaxChars))
            append(hardPad(p1Sets + display.player1Games.toString(), 10))
            append("{\\c").append(scoreColor).append("}").append(hardPad(display.player1PointText, 4))
            append("\\N")
            append("{\\c").append(p2Color).append("}# ")
            append("{\\c").append(textColor).append("}").append(hardPad(escape(display.player2PreviewName), style.layout.previewNameMaxChars))
            append(hardPad(p2Sets + display.player2Games.toString(), 10))
            append("{\\c").append(scoreColor).append("}").append(hardPad(display.player2PointText, 4))
        }
    }

    private fun hardPad(value: String, width: Int): String {
        val clipped = value.take(width)
        return clipped + "\\h".repeat((width - clipped.length).coerceAtLeast(0))
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("{", "\\{")
            .replace("}", "\\}")
    }

    private fun assTextColor(rgb: Int): String {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return String.format(Locale.US, "&H%02X%02X%02X&", b, g, r)
    }

    private fun assColor(rgb: Int, alpha: Int): String {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return String.format(Locale.US, "&H%02X%02X%02X%02X", alpha.coerceIn(0, 255), b, g, r)
    }

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

    private fun fmt(value: Double): String = String.format(Locale.US, "%.1f", value)
}
