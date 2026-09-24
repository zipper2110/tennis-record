package org.litvin.ui.tabs.scoring.ui

import com.formdev.flatlaf.FlatClientProperties
import org.litvin.scoring.Outcome
import org.litvin.ui.UiStyles
import org.litvin.ui.tabs.scoring.VideoPlayerActions
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.border.EmptyBorder

/**
 * Bottom panel of the Scoring tab. The panel is a grid of three columns and two rows:
 *
 * ```
 * | P1 points, Point for P1 |      No point      | P2 points, Point for P2 |
 * | P1 games, P1 sets       | transport, speed   | P2 games, P2 sets       |
 * ```
 *
 * The two side columns always have the same width, so the center column stays in the middle.
 * The center column gets the width that the side columns do not use (see [ControlsGridLayout.sideWidth]).
 * If the center column is too narrow for the full seek labels (for example, on a 1376x768 screen),
 * the video controls change to compact labels.
 */
class ScoringControlsPanel(
    videoActions: VideoPlayerActions,
    onOutcome: (Outcome) -> Unit,
    onManualGameWon: (Outcome) -> Unit = {},
    onManualSetWon: (Outcome) -> Unit = {},
    onServe: (Outcome) -> Unit = {},
) : JPanel() {

    val player1 = PlayerControls(
        isPrimary = true,
        onPointClicked = { onOutcome(Outcome.P1) },
        onGameWonClicked = { onManualGameWon(Outcome.P1) },
        onSetWonClicked = { onManualSetWon(Outcome.P1) },
        onServeClicked = { onServe(Outcome.P1) },
    )
    val player2 = PlayerControls(
        isPrimary = false,
        onPointClicked = { onOutcome(Outcome.P2) },
        onGameWonClicked = { onManualGameWon(Outcome.P2) },
        onSetWonClicked = { onManualSetWon(Outcome.P2) },
        onServeClicked = { onServe(Outcome.P2) },
    )
    val videoSync = VideoSyncPanel(videoActions)

    private val noPointBtn = JToggleButton("No point [W]").apply {
        isFocusPainted = false
        foreground = UiStyles.FG_PRIMARY
        background = UiStyles.CARD_BORDER
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiStyles.CARD_BORDER, 1),
            EmptyBorder(6, 10, 6, 10),
        )
        name = "no-point"
        toolTipText = "W - No point"
        // Neutral fill when selected; the player point buttons use the player colors (see PlayerControls)
        putClientProperty(FlatClientProperties.STYLE, "selectedBackground: #5A5A5A")
        addActionListener { onOutcome(Outcome.NONE) }
    }

    init {
        isOpaque = true
        background = UiStyles.CARD_BG
        border = EmptyBorder(12, 16, 14, 16)
        val grid = ControlsGridLayout(
            header = listOf(player1.header, noPointBtn, player2.header),
            body = listOf(player1.stats, videoSync, player2.stats),
            videoSync = videoSync,
        )
        layout = grid
        grid.components.forEach { add(it) }
    }

    /** Enable or disable the three outcome buttons (P1, No point, P2) and the two serve buttons. */
    fun setOutcomeButtonsEnabled(enabled: Boolean) {
        player1.setPointButtonEnabled(enabled)
        player2.setPointButtonEnabled(enabled)
        noPointBtn.isEnabled = enabled
        player1.setServeButtonEnabled(enabled)
        player2.setServeButtonEnabled(enabled)
    }

    /** Shows the server of the selected point. [server] is 1, 2 or null (not known). [marked] is true for a mark on this point. */
    fun setServer(server: Int?, marked: Boolean) {
        fun state(player: Int) = when {
            server != player -> ServeState.NOT_SERVING
            marked -> ServeState.SERVING_MARKED
            else -> ServeState.SERVING
        }
        player1.setServeState(state(1))
        player2.setServeState(state(2))
    }

    /** Manual scoring makes the Game Won and Set Won markers clickable. [enabled] is false when no point is selected. */
    fun setManualScoring(manual: Boolean, enabled: Boolean) {
        player1.setManualScoring(manual, enabled)
        player2.setManualScoring(manual, enabled)
    }

    /** Show [outcome] as the selected outcome button; null clears the selection. */
    fun setSelectedOutcome(outcome: Outcome?) {
        player1.setPointSelected(outcome == Outcome.P1)
        player2.setPointSelected(outcome == Outcome.P2)
        noPointBtn.model.isSelected = outcome == Outcome.NONE
    }

    private class ControlsGridLayout(
        private val header: List<Component>,
        private val body: List<Component>,
        private val videoSync: VideoSyncPanel,
    ) : LayoutManager {

        val components: List<Component> get() = header + body

        override fun layoutContainer(parent: Container) {
            val ins = parent.insets
            val available = parent.width - ins.left - ins.right - 2 * COLUMN_GAP
            val sideWidth = sideWidth(available)
            val centerWidth = (available - 2 * sideWidth).coerceAtLeast(0)
            videoSync.compact = centerWidth < videoSync.fullPreferredWidth

            val headerHeight = headerHeight()
            val bodyHeight = maxOf(
                bodyHeight(),
                parent.height - ins.top - ins.bottom - headerHeight - ROW_GAP,
            )
            val xs = intArrayOf(
                ins.left,
                ins.left + sideWidth + COLUMN_GAP,
                ins.left + sideWidth + COLUMN_GAP + centerWidth + COLUMN_GAP,
            )
            val headerY = ins.top
            val bodyY = headerY + headerHeight + ROW_GAP

            // Side cells fill their cell. Center cells keep their preferred height and are centered in the cell.
            for (col in 0..2) {
                if (col == CENTER) {
                    val button = header[col]
                    val buttonSize = button.preferredSize
                    val buttonWidth = buttonSize.width.coerceAtMost(centerWidth)
                    button.setBounds(
                        xs[col] + (centerWidth - buttonWidth) / 2,
                        headerY + (headerHeight - buttonSize.height) / 2,
                        buttonWidth,
                        buttonSize.height,
                    )
                    val controlsHeight = body[col].preferredSize.height
                    body[col].setBounds(xs[col], bodyY + (bodyHeight - controlsHeight) / 2, centerWidth, controlsHeight)
                } else {
                    header[col].setBounds(xs[col], headerY, sideWidth, headerHeight)
                    body[col].setBounds(xs[col], bodyY, sideWidth, bodyHeight)
                }
            }
        }

        /**
         * Width priorities, from high to low:
         * 1. The center column fits the compact video controls.
         * 2. The side columns fit their content (from [SIDE_MIN_WIDTH] to [SIDE_MAX_WIDTH]).
         *    A long player name that does not fit is shortened with "...".
         * 3. The center column fits the full video controls.
         */
        private fun sideWidth(available: Int): Int {
            val sideLimit = ((available - videoSync.compactPreferredWidth) / 2).coerceAtLeast(0)
            val sideNeed = listOf(header[LEFT], header[RIGHT], body[LEFT], body[RIGHT])
                .maxOf { it.preferredSize.width }
                .coerceIn(SIDE_MIN_WIDTH, SIDE_MAX_WIDTH)
            val sideFit = (available - videoSync.fullPreferredWidth) / 2
            return sideFit.coerceIn(sideNeed, SIDE_MAX_WIDTH).coerceAtMost(sideLimit)
        }

        private fun headerHeight(): Int = header.maxOf { it.preferredSize.height }

        private fun bodyHeight(): Int = body.maxOf { it.preferredSize.height }

        override fun preferredLayoutSize(parent: Container): Dimension {
            val ins = parent.insets
            val width = 2 * SIDE_MAX_WIDTH + videoSync.fullPreferredWidth + 2 * COLUMN_GAP
            val height = headerHeight() + ROW_GAP + bodyHeight()
            return Dimension(width + ins.left + ins.right, height + ins.top + ins.bottom)
        }

        override fun minimumLayoutSize(parent: Container): Dimension {
            val ins = parent.insets
            val height = headerHeight() + ROW_GAP + bodyHeight()
            return Dimension(2 * SIDE_MIN_WIDTH + 2 * COLUMN_GAP + ins.left + ins.right, height + ins.top + ins.bottom)
        }

        override fun addLayoutComponent(name: String?, comp: Component?) {}

        override fun removeLayoutComponent(comp: Component?) {}
    }

    private companion object {
        const val LEFT = 0
        const val CENTER = 1
        const val RIGHT = 2
        const val SIDE_MIN_WIDTH = 260
        const val SIDE_MAX_WIDTH = 360
        const val COLUMN_GAP = 16
        const val ROW_GAP = 10
    }
}
