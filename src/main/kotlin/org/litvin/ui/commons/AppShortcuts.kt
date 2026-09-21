package org.litvin.ui.commons

data class AppShortcut(
    val keyStroke: String,
    val display: String,
)

object AppShortcuts {
    val HELP = AppShortcut("F1", "F1")
    val PLAY_PAUSE = AppShortcut("SPACE", "Space")
    val POINT_START = AppShortcut("C", "C")
    val POINT_END = AppShortcut("V", "V")
    val TOGGLE_FAVORITE = AppShortcut("A", "A")
    val DELETE = AppShortcut("DELETE", "Delete")
    val LEFT = AppShortcut("LEFT", "Left")
    val RIGHT = AppShortcut("RIGHT", "Right")
    val UP = AppShortcut("UP", "Up")
    val DOWN = AppShortcut("DOWN", "Down")
    val SHIFT_LEFT = AppShortcut("shift LEFT", "Shift+Left")
    val SHIFT_RIGHT = AppShortcut("shift RIGHT", "Shift+Right")
    val SHIFT_UP = AppShortcut("shift UP", "Shift+Up")
    val SHIFT_DOWN = AppShortcut("shift DOWN", "Shift+Down")
    val SCORE_PLAYER_1 = AppShortcut("Q", "Q")
    val SCORE_NO_POINT = AppShortcut("W", "W")
    val SCORE_PLAYER_2 = AppShortcut("E", "E")
    val NEXT_POINT = AppShortcut("R", "R")
    val PREVIOUS_POINT = AppShortcut("shift R", "Shift+R")
    val TOGGLE_FRAME_STEP = AppShortcut("F", "F")
}
