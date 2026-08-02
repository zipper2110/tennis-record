package org.litvin.ui.tabs.scoring.ui

import org.litvin.ScoreboardDisplay
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

object PreviewScoreboardRenderer {
    private const val WIDTH = 440
    private const val HEIGHT = 166

    fun render(display: ScoreboardDisplay): BufferedImage {
        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.composite = AlphaComposite.Src
            g.color = Color(display.style.palette.panelBgRgb or (210 shl 24), true)
            g.fillRoundRect(0, 0, WIDTH, HEIGHT, 18, 18)

            g.font = Font(display.style.fontFamily, Font.BOLD, 18)
            g.color = Color(display.style.palette.accentRgb)
            g.drawString(display.style.title, 20, 29)

            drawPlayerRow(g, display, first = true, y = 76)
            drawPlayerRow(g, display, first = false, y = 132)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun drawPlayerRow(g: Graphics2D, display: ScoreboardDisplay, first: Boolean, y: Int) {
        val playerColor = if (first) display.player1Rgb else display.player2Rgb
        val name = if (first) display.player1PreviewName else display.player2PreviewName
        val games = if (first) display.player1Games else display.player2Games
        val points = if (first) display.player1PointText else display.player2PointText
        val sets = display.completedSets.joinToString(" ") {
            if (first) it.first.toString() else it.second.toString()
        }

        g.color = Color(playerColor)
        g.fillRoundRect(20, y - 17, 12, 24, 4, 4)
        g.font = Font(display.style.previewFontFamily, Font.BOLD, 20)
        g.color = Color(display.style.palette.textPrimaryRgb)
        g.drawString(name, 44, y)

        g.font = Font(display.style.previewFontFamily, Font.PLAIN, 19)
        g.color = Color(display.style.palette.textMutedRgb)
        g.drawString(sets, 258, y)
        g.color = Color(display.style.palette.textPrimaryRgb)
        g.drawString(games.toString(), 352, y)
        g.font = Font(display.style.previewFontFamily, Font.BOLD, 22)
        g.color = Color(display.style.palette.accentRgb)
        g.drawString(points, 392, y)
    }
}
