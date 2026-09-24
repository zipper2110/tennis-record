package org.litvin.ui.help

import org.litvin.ui.commons.AppShortcut
import org.litvin.ui.commons.AppShortcuts

enum class HelpPage(val title: String) {
    OVERVIEW("Overview"),
    PROJECTS("Projects"),
    COLORS("Colors"),
    CROP("Transform"),
    POINTS("Points"),
    SCORING("Scoring"),
    EXPORT("Export"),
}

data class HelpShortcut(
    val shortcut: AppShortcut,
    val action: String,
)

data class HelpContent(
    val page: HelpPage,
    val summary: String,
    val workflow: List<String>,
    val actions: List<String>,
    val shortcuts: List<HelpShortcut>,
    val notes: List<String> = emptyList(),
)

object HelpCatalog {
    val pages: List<HelpContent> = listOf(
        HelpContent(
            page = HelpPage.OVERVIEW,
            summary = "Turn a full tennis recording into a compact, scored video without changing the source file.",
            workflow = listOf(
                "Import a source video or open a recent project.",
                "Optionally tune the color and frame the video with Transform.",
                "Mark the start and end of each point, and add comments where necessary.",
                "Assign each marked point to a player or mark it as no point.",
                "Choose export settings and render the finished video.",
            ),
            actions = listOf(
                "Projects, point marks, comments, adjustments, and scoring are saved in project files.",
                "Edits are non-destructive: the original video is only read, never rewritten.",
                "Most editing changes autosave as you work.",
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.HELP, "Open Help for the current tab"),
            ),
        ),
        HelpContent(
            page = HelpPage.PROJECTS,
            summary = "Create a project from a video or reopen a match you worked on earlier.",
            workflow = listOf(
                "Choose Import New Match and select a supported video file.",
                "Use Open Project on a recent match to continue working.",
                "The opened project becomes the current project, unlocks the editing tabs, and opens the Points tab.",
            ),
            actions = listOf(
                "Double-click a recent project card as an alternative to Open Project.",
                "Use Prev and Next when the recent-project list has multiple pages.",
                "If a source video moved, select it again when prompted.",
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.HELP, "Open Projects help"),
            ),
        ),
        HelpContent(
            page = HelpPage.COLORS,
            summary = "Preview and save color corrections that will be applied during export.",
            workflow = listOf(
                "Scrub to a representative frame and play or pause the preview.",
                "Adjust brightness, contrast, saturation, shadows, highlights, and white-balance temperature.",
                "Use Reset to restore all color controls to their defaults.",
            ),
            actions = listOf(
                "Slider changes are saved to the project and shared with other previews.",
                "A system without live color-preview support still saves values for export.",
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.PLAY_PAUSE, "Play or pause"),
                HelpShortcut(AppShortcuts.HELP, "Open Colors help"),
            ),
        ),
        HelpContent(
            page = HelpPage.CROP,
            summary = "Reframe the output by zooming, panning, resizing, and rotating the source.",
            workflow = listOf(
                "Play the video or use the seek bar to choose a moment for alignment.",
                "Drag inside the crop rectangle to move it, drag handles to resize it, and drag the round handle to rotate.",
                "Click the video before you use the arrow keys to move the crop rectangle.",
                "Use the Zoom, Pan X, Pan Y, Rotation, and Fine Rotation sliders or type exact values.",
            ),
            actions = listOf(
                "Reset restores the transform controls.",
                "Resizing keeps the aspect ratio and the center of the crop rectangle.",
                "Fine Rotation adds up to 5 degrees to the Rotation value in steps of 0.1 degree.",
                "The rotation handle snaps to 0, 90, and 180 degrees when the angle is within 2 degrees of them.",
                "Hold Shift while rotating to snap the angle to the nearest multiple of 90 degrees.",
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.PLAY_PAUSE, "Play or pause"),
                HelpShortcut(AppShortcuts.LEFT, "Move the focused crop rectangle left by 1 pixel"),
                HelpShortcut(AppShortcuts.RIGHT, "Move the focused crop rectangle right by 1 pixel"),
                HelpShortcut(AppShortcuts.UP, "Move the focused crop rectangle up by 1 pixel"),
                HelpShortcut(AppShortcuts.DOWN, "Move the focused crop rectangle down by 1 pixel"),
                HelpShortcut(AppShortcuts.SHIFT_LEFT, "Move left by 10 pixels"),
                HelpShortcut(AppShortcuts.SHIFT_RIGHT, "Move right by 10 pixels"),
                HelpShortcut(AppShortcuts.SHIFT_UP, "Move up by 10 pixels"),
                HelpShortcut(AppShortcuts.SHIFT_DOWN, "Move down by 10 pixels"),
                HelpShortcut(AppShortcuts.HELP, "Open Transform help"),
            ),
            notes = listOf("Arrow-key nudging works while the crop canvas has focus."),
        ),
        HelpContent(
            page = HelpPage.POINTS,
            summary = "Mark the video interval for every point that should be kept, and add comments to the video.",
            workflow = listOf(
                "Move the playhead to the beginning of a point and set Point Start.",
                "Move to the end and set Point End to create a marked point.",
                "Select point cards to seek, then edit, delete, or mark favorites as needed.",
                "Move the playhead to the moment you want to explain and click Add comment.",
                "Give the comment its text, start time, duration, and color, then save it.",
            ),
            actions = listOf(
                "The timeline shows marked intervals and supports seeking.",
                "Point cards expose Favorite, Edit, and Delete actions on hover.",
                "The COMMENTS track shows each comment. Point at a marker to read it, or click it to seek.",
                "Comment cards expose Edit and Delete actions on hover. Edit also changes the color.",
                "The export shows each comment on the video for its duration.",
                "Changes autosave to the current project.",
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.PLAY_PAUSE, "Play or pause"),
                HelpShortcut(AppShortcuts.POINT_START, "Set point start at the playhead"),
                HelpShortcut(AppShortcuts.POINT_END, "Set point end at the playhead"),
                HelpShortcut(AppShortcuts.TOGGLE_FAVORITE, "Toggle favorite on the selected point"),
                HelpShortcut(AppShortcuts.DELETE, "Delete the selected point"),
                HelpShortcut(AppShortcuts.LEFT, "Seek back 1 second"),
                HelpShortcut(AppShortcuts.RIGHT, "Seek forward 1 second"),
                HelpShortcut(AppShortcuts.SHIFT_LEFT, "Seek back 5 seconds"),
                HelpShortcut(AppShortcuts.SHIFT_RIGHT, "Seek forward 5 seconds"),
                HelpShortcut(AppShortcuts.HELP, "Open Points help"),
            ),
            notes = listOf("To delete a comment, use the Delete button on its card. The Delete key deletes points only."),
        ),
        HelpContent(
            page = HelpPage.SCORING,
            summary = "Assign an outcome to each marked point and preview the resulting scoreboard.",
            workflow = listOf(
                "The first time you open Scoring for a project, Score Settings opens. Enter the player names and colors, and select the match format.",
                "Select a marked point, review the video segment, and choose Player 1, No point, or Player 2.",
                "Advance through the list until every point has the intended outcome.",
                "On the Export tab, enable Include Scoreboard to burn the score into the rendered video.",
            ),
            actions = listOf(
                "Star or unstar the current point without returning to Points.",
                "Use Score Settings to change the player names and colors, the match format (for example best of 3 sets, match tiebreak, pro set, games only), the deuce rule, or to turn on fully manual scoring.",
                "In fully manual scoring, the app counts points only. Click Game Won or Set Won to mark a win on the current point.",
                "Use Scoreboard Style to choose the scoreboard style, title, bottom app line, position, size, background, and accent color. The exported video uses the same scoreboard as the preview. New projects start with the last saved style.",
                "Choose playback speed and optionally enable frame-by-frame arrow-key stepping while paused.",
                "Scoring, score settings, and the scoreboard style autosave to the project.",
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.SCORE_PLAYER_1, "Point for Player 1"),
                HelpShortcut(AppShortcuts.SCORE_NO_POINT, "No point"),
                HelpShortcut(AppShortcuts.SCORE_PLAYER_2, "Point for Player 2"),
                HelpShortcut(AppShortcuts.NEXT_POINT, "Advance to the next point and start playback"),
                HelpShortcut(AppShortcuts.PREVIOUS_POINT, "Go back to the previous point"),
                HelpShortcut(AppShortcuts.TOGGLE_FAVORITE, "Toggle favorite on the selected point"),
                HelpShortcut(AppShortcuts.TOGGLE_FRAME_STEP, "Toggle frame-by-frame stepping"),
                HelpShortcut(AppShortcuts.PLAY_PAUSE, "Play or pause"),
                HelpShortcut(AppShortcuts.LEFT, "Seek back 1 second, or one frame while paused in frame mode"),
                HelpShortcut(AppShortcuts.RIGHT, "Seek forward 1 second, or one frame while paused in frame mode"),
                HelpShortcut(AppShortcuts.SHIFT_LEFT, "Seek back 5 seconds"),
                HelpShortcut(AppShortcuts.SHIFT_RIGHT, "Seek forward 5 seconds"),
                HelpShortcut(AppShortcuts.UP, "Increase playback speed"),
                HelpShortcut(AppShortcuts.DOWN, "Decrease playback speed"),
                HelpShortcut(AppShortcuts.HELP, "Open Scoring help"),
            ),
            notes = listOf(
                "Up and Down change speed when focus is in the video area.",
                "Left and Right seek only inside the selected point.",
            ),
        ),
        HelpContent(
            page = HelpPage.EXPORT,
            summary = "Configure and render the final video, then monitor it through completion.",
            workflow = listOf(
                "Choose whether to cut idle time between points, keep only favorite points, include the scoreboard, and include comments.",
                "Choose the output resolution, frame rate (FPS), bitrate (quality) preset, and encoder.",
                "Select Initialize Render, choose the output file, and monitor progress.",
            ),
            actions = listOf(
                "Only favorite points requires Cut idle time between points and at least one favorite point.",
                "Include Scoreboard requires at least one scored point.",
                "Cancel the active render or a queued render.",
                "Open the output folder from Completed Renders.",
                "Clear finished entries without deleting rendered video files.",
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.HELP, "Open Export help"),
            ),
            notes = listOf("Export has no other tab-specific keyboard shortcuts."),
        ),
    )

    fun content(page: HelpPage): HelpContent = pages.first { it.page == page }
}
