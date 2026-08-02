package org.litvin.ui.help

import org.litvin.ui.commons.AppShortcut
import org.litvin.ui.commons.AppShortcuts

enum class HelpPage(val title: String) {
    OVERVIEW("Overview"),
    PROJECTS("Projects"),
    COLORS("Colors"),
    CROP("Crop & Rotate"),
    RALLIES("Rallies"),
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
                "Optionally tune the color and frame the video with Crop & Rotate.",
                "Mark the start and end of each rally.",
                "Assign each marked point to a player or mark it as no point.",
                "Choose export settings and render the finished video.",
            ),
            actions = listOf(
                "Projects, rally marks, adjustments, and scoring are saved in project files.",
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
                "The opened project becomes the current project and unlocks the editing tabs.",
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
                "Adjust brightness, contrast, saturation, white-balance temperature, and tint.",
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
                "Use the seek bar to choose a frame for alignment.",
                "Drag inside the crop rectangle to move it, drag handles to resize it, and drag the round handle to rotate.",
                "Use the Zoom, Pan X, Pan Y, and Rotation sliders or type exact values.",
            ),
            actions = listOf(
                "Reset restores the transform controls; Reset All restores all video adjustments.",
                "Hold Shift while rotating to snap the angle.",
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.LEFT, "Move the focused crop rectangle left by 1 pixel"),
                HelpShortcut(AppShortcuts.RIGHT, "Move the focused crop rectangle right by 1 pixel"),
                HelpShortcut(AppShortcuts.UP, "Move the focused crop rectangle up by 1 pixel"),
                HelpShortcut(AppShortcuts.DOWN, "Move the focused crop rectangle down by 1 pixel"),
                HelpShortcut(AppShortcuts.SHIFT_LEFT, "Move left by 10 pixels"),
                HelpShortcut(AppShortcuts.SHIFT_RIGHT, "Move right by 10 pixels"),
                HelpShortcut(AppShortcuts.SHIFT_UP, "Move up by 10 pixels"),
                HelpShortcut(AppShortcuts.SHIFT_DOWN, "Move down by 10 pixels"),
                HelpShortcut(AppShortcuts.HELP, "Open Crop & Rotate help"),
            ),
            notes = listOf("Arrow-key nudging works while the crop canvas has focus."),
        ),
        HelpContent(
            page = HelpPage.RALLIES,
            summary = "Mark the video interval for every rally that should be kept.",
            workflow = listOf(
                "Move the playhead to the beginning of a point and set Point Start.",
                "Move to the end and set Point End to create a marked point.",
                "Select point cards to seek, then edit, delete, or mark favorites as needed.",
            ),
            actions = listOf(
                "The timeline shows marked intervals and supports seeking.",
                "Point cards expose Favorite, Edit, and Delete actions on hover.",
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
                HelpShortcut(AppShortcuts.HELP, "Open Rallies help"),
            ),
        ),
        HelpContent(
            page = HelpPage.SCORING,
            summary = "Assign an outcome to each marked point and preview the resulting scoreboard.",
            workflow = listOf(
                "Enter player names and choose their scoreboard colors.",
                "Select a marked point, review the video segment, and choose Player 1, No point, or Player 2.",
                "Advance through the list until every point has the intended outcome.",
                "Enable Include Scoreboard on Export to burn the score into the rendered video.",
            ),
            actions = listOf(
                "Star or unstar the current point without returning to Rallies.",
                "Choose playback speed and optionally enable frame-by-frame arrow-key stepping while paused.",
                "Scoring, names, and colors autosave to the project.",
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.SCORE_PLAYER_1, "Point for Player 1"),
                HelpShortcut(AppShortcuts.SCORE_NO_POINT, "No point"),
                HelpShortcut(AppShortcuts.SCORE_PLAYER_2, "Point for Player 2"),
                HelpShortcut(AppShortcuts.NEXT_POINT, "Advance to the next point"),
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
            notes = listOf("Up and Down change speed when focus is in the video area."),
        ),
        HelpContent(
            page = HelpPage.EXPORT,
            summary = "Configure and render the final video, then monitor it through completion.",
            workflow = listOf(
                "Choose a quality preset, output resolution, and encoder.",
                "Choose whether to cut idle time, keep only favorites, and include the scoreboard.",
                "Select Initialize Render, choose the output file, and monitor progress.",
            ),
            actions = listOf(
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
