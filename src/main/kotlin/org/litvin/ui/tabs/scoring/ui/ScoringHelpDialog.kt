package org.litvin.ui.tabs.scoring.ui

import org.litvin.ui.UiStyles
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.util.function.Supplier
import javax.swing.BorderFactory
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

/**
 * ScoringHelpDialog (v0.3.1)
 *
 * Modal dialog that shows brief guidance for the Scoring tab and a table of hotkeys.
 * - Passive leaf; accepts hotkeys Supplier to keep in sync with key bindings.
 */
class ScoringHelpDialog(
    owner: Component?,
    private val p1NameProvider: () -> String,
    private val p2NameProvider: () -> String,
) : JDialog(SwingUtilities.getWindowAncestor(owner) as? java.awt.Window, "Scoring — Help", ModalityType.APPLICATION_MODAL) {

    companion object {
        private var lastSize: Dimension? = null
    }

    private fun currentHotkeys(): Map<String, String> {
        return mapOf(
            "Q" to "Point for ${p1NameProvider()}",
            "W" to "No Point",
            "E" to "Point for ${p2NameProvider()}",
            "R" to "Next Point",
            "SPACE" to "Play/Pause",
            "← / →" to "Seek ±1s",
            "Shift+← / Shift+→" to "Seek ±10s",
            "↑ / ↓" to "Change speed",
        )
    }

    init {
        contentPane = buildContent()
        isResizable = true
        minimumSize = Dimension(560, 500)
        if (lastSize != null) size = lastSize else setSize(780, 560)
        setLocationRelativeTo(owner)
    }

    private fun buildContent(): JPanel {
        val root = JPanel(BorderLayout(12, 12))
        root.border = EmptyBorder(12, 12, 12, 12)
        root.background = Color(0x12, 0x12, 0x12)

        // Left: What to do here — use a single wrapping HTML pane (no horizontal scroll)
        class WrappingHtmlPane : javax.swing.JEditorPane("text/html", "") {
            init {
                isEditable = false
                isOpaque = false
                putClientProperty("JEditorPane.honorDisplayProperties", true)
            }
            override fun getScrollableTracksViewportWidth(): Boolean = true
        }
        val html = StringBuilder().apply {
            append("""
                <html>
                <head>
                  <style>
                    body { color: #CCCCCC; font-size: 12px; margin: 0; }
                    h3 { color: #E0E0E0; font-size: 14px; margin: 0 0 6px 0; }
                    ul { margin: 0 0 10px 18px; padding: 0; }
                    li { margin: 4px 0; }
                  </style>
                </head>
                <body>
                  <h3>What to do here</h3>
                  <ul>
                    <li>Input player names for the scoreboard (left panel footer).</li>
                    <li>Review points created on the Markup tab (timeline list).</li>
                    <li>For each point choose: Player 1, No point, or Player 2.</li>
                    <li>Scoreboard overlays are produced during Video export.</li>
                  </ul>
                </body>
                </html>
            """.trimIndent())
        }.toString()
        val htmlPane = WrappingHtmlPane()
        htmlPane.text = html
        // Make the HTML pane itself the viewport view so it tracks width and wraps correctly
        htmlPane.isOpaque = true
        htmlPane.background = Color(0x14, 0x14, 0x14)
        htmlPane.foreground = Color(0xCC, 0xCC, 0xCC)
        htmlPane.border = EmptyBorder(10, 12, 12, 12)

        val leftScroll = JScrollPane(htmlPane)
        leftScroll.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        leftScroll.border = BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x33))

        // Right: Hotkeys table — two sections with explicit ordering
        val rightPanel = JPanel()
        rightPanel.layout = javax.swing.BoxLayout(rightPanel, javax.swing.BoxLayout.Y_AXIS)
        rightPanel.isOpaque = true
        rightPanel.background = Color(0x14, 0x14, 0x14)
        rightPanel.border = EmptyBorder(10, 12, 12, 12)

        val hkTitle = JLabel("Hotkeys")
        hkTitle.font = hkTitle.font.deriveFont(Font.BOLD, 14f)
        hkTitle.foreground = Color(0xE0, 0xE0, 0xE0)
        rightPanel.add(hkTitle)
        rightPanel.add(javax.swing.Box.createVerticalStrut(6))

        // Scoring section: Q, E, W, R (in this order)
        val scoringOrdered = Supplier {
            val map = try { currentHotkeys() } catch (_: Throwable) { emptyMap() }
            listOf(
                Pair("Q", map["Q"] ?: "Point for Player 1"),
                Pair("E", map["E"] ?: "Point for Player 2"),
                Pair("W", map["W"] ?: "No Point"),
                Pair("R", map["R"] ?: "Next Point"),
            )
        }
        val scoringPanel = HotkeysHelpPanel(orderedSupplier = scoringOrdered, titleText = "Scoring")
        rightPanel.add(scoringPanel)
        rightPanel.add(javax.swing.Box.createVerticalStrut(8))
        rightPanel.add(javax.swing.JSeparator())
        rightPanel.add(javax.swing.Box.createVerticalStrut(8))

        // Playback section: Space, arrows ±1s, Shift+arrows ±10s, Up/Down speed
        val playbackOrdered = Supplier {
            val map = try { currentHotkeys() } catch (_: Throwable) { emptyMap() }
            listOf(
                Pair("SPACE", map["SPACE"] ?: "Play/Pause"),
                Pair("← / →", map["← / →"] ?: "Seek ±1s"),
                Pair("Shift+← / Shift+→", map["Shift+← / Shift+→"] ?: "Seek ±10s"),
                Pair("↑ / ↓", map["↑ / ↓"] ?: "Change speed"),
            )
        }
        val playbackPanel = HotkeysHelpPanel(orderedSupplier = playbackOrdered, titleText = "Playback")
        rightPanel.add(playbackPanel)

        val rightScroll = JScrollPane(rightPanel)
        rightScroll.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        rightScroll.border = BorderFactory.createLineBorder(Color(0x48, 0x48, 0x47, 0x33))

        val split = JPanel(java.awt.GridLayout(1, 2, 12, 0))
        split.isOpaque = false
        split.add(leftScroll)
        split.add(rightScroll)

        root.add(split, BorderLayout.CENTER)
        return root
    }

    override fun dispose() {
        lastSize = size
        super.dispose()
    }
}
