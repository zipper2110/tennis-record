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
    STATISTICS("Statistics"),
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
    val goodToKnow: List<String>,
    val shortcuts: List<HelpShortcut>,
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
                "Review the match statistics, and select the statistics for the video.",
                "Choose export settings and export the finished video.",
            ),
            actions = listOf(
                "Press F1 on a tab to open the help for that tab.",
                "Select a page in the list on the left to read the help for a different tab.",
            ),
            goodToKnow = listOf(
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
                "In the New project dialog, change the project name or the video if necessary, then choose Create project.",
                "Use Open Project on a recent match to continue working.",
                "The opened project becomes the current project, unlocks the editing tabs, and opens the Points tab.",
            ),
            actions = listOf(
                "Double-click a recent project card as an alternative to Open Project.",
                "Use the pencil button on a project card to rename the project.",
                "Read the video duration, the file size, the scored points of all points, and the favorite points in the columns of each recent project.",
                "Use Prev and Next when the recent-project list has multiple pages.",
                "Use the trash button on a project card to delete the project.",
            ),
            goodToKnow = listOf(
                "When you delete a project, the video file stays on the disk.",
                "You cannot delete the open project.",
                "A red crossed-out video icon shows when the video of a project is not on the disk. Put the video back at the same location to open the project.",
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
            ),
            actions = listOf(
                "Use Reset to restore all color controls to their defaults.",
            ),
            goodToKnow = listOf(
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
                "Use Reset to restore the transform controls.",
                "Hold Shift while rotating to snap the angle to the nearest multiple of 90 degrees.",
            ),
            goodToKnow = listOf(
                "Resizing keeps the aspect ratio and the center of the crop rectangle.",
                "Fine Rotation adds up to 5 degrees to the Rotation value in steps of 0.1 degree.",
                "The rotation handle snaps to 0, 90, and 180 degrees when the angle is within 2 degrees of them.",
                "Arrow-key nudging works while the crop canvas has focus.",
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
                "Click the timeline to seek. The timeline shows the marked intervals.",
                "Point at a point card to show its Favorite, Edit, and Delete actions.",
                "Point at a marker on the COMMENTS track to read the comment, or click the marker to seek.",
                "Point at a comment card to show its Edit and Delete actions. Edit also changes the color.",
            ),
            goodToKnow = listOf(
                "The export shows each comment on the video for its duration.",
                "Changes autosave to the current project.",
                "To delete a comment, use the Delete button on its card. The Delete key deletes points only.",
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
        ),
        HelpContent(
            page = HelpPage.SCORING,
            summary = "Assign an outcome to each marked point and preview the resulting scoreboard.",
            workflow = listOf(
                "The first time you open Scoring for a project, Scoring Settings opens. Enter the player names and colors, and select the match format.",
                "Select a marked point, review the video segment, and choose Player 1, No point, or Player 2.",
                "Advance through the list until every point has the intended outcome.",
                "On the Export tab, enable Include Scoreboard to burn the score into the exported video.",
            ),
            actions = listOf(
                "Star or unstar the current point without returning to Points.",
                "Use Scoring Settings to change the player names and colors, the match format (for example best of 3 sets, match tiebreak, pro set, games only, plain points), the deuce rule, or to turn on fully manual scoring.",
                "In fully manual scoring, click Game Won or Set Won to mark a win on the current point.",
                "To show the serve, click the racket button next to the player who serves (or press S). You can mark the server on any point. Click a marked racket again to clear the mark.",
                "Use Scoreboard Style to choose the scoreboard style, title, player colors, serve ball, bottom app line, position, size, background, and accent color.",
                "Choose playback speed and optionally enable frame-by-frame arrow-key stepping while paused.",
            ),
            goodToKnow = listOf(
                "In fully manual scoring, the app counts points only.",
                "The server changes after each game, and in a tiebreak after the first point and then after every two points. A new mark sets the server until the end of that game.",
                "If you do not mark a server, the app does not track the serve.",
                "The exported video uses the same scoreboard as the preview. New projects start with the last saved scoreboard style.",
                "Scoring, score settings, and the scoreboard style autosave to the project.",
                "Up and Down change speed when focus is in the video area.",
                "Left and Right seek only inside the selected point.",
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.SCORE_PLAYER_1, "Point for Player 1"),
                HelpShortcut(AppShortcuts.SCORE_NO_POINT, "No point"),
                HelpShortcut(AppShortcuts.SCORE_PLAYER_2, "Point for Player 2"),
                HelpShortcut(AppShortcuts.NEXT_POINT, "Advance to the next point and start playback"),
                HelpShortcut(AppShortcuts.PREVIOUS_POINT, "Go back to the previous point"),
                HelpShortcut(AppShortcuts.SWITCH_SERVE, "Switch the server of the selected point"),
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
        ),
        HelpContent(
            page = HelpPage.STATISTICS,
            summary = "See the match statistics, and select the statistics that the exported video shows.",
            workflow = listOf(
                "Score the points in the Scoring tab. To get the serve and break point statistics, mark the server of one point.",
                "Open Stats to see the statistics of the full match. If the match has two sets or more, select a set to see the statistics of that set.",
                "Select In video for each row that the exported video must show.",
            ),
            actions = listOf(
                "Put the pointer on the info icon of a gray row to see why the row has no value.",
                "Set the limits of Short points won and Long points won at the top of the Point length group.",
                "Put the pointer on the Momentum chart to see the point and the lead. Click the chart to open that point in the Scoring tab.",
                "Select In video next to Momentum to add the chart to the statistics card.",
                "Click a value with a play icon, for example the longest point, to open that point in the Scoring tab.",
                "Use Card transparency to set how much of the video shows through the card. The In the video preview shows the card over a frame of your video.",
            ),
            goodToKnow = listOf(
                "The statistics use only the points that have a winner. When some points have no winner, the tab shows how many points it used.",
                "A gray row has no value. For example, the serve statistics need a server mark.",
                "Break points and set points need automatic scoring. Manual scoring does not know which points can win a game or a set.",
                "The time statistics use the start and end of each marked point. The average time between points is correct only when the video has no cuts.",
                "Short points won and Long points won compare the points by their length. They are in the Point length group, with Average point won. The default limits, 7 s and 15 s, are about 0-4 shots and 9 or more shots: the ATP rally length ranges. If your marks start well before the serve, make the limits larger.",
                "Return games won shows the service games of the opponent that the player won (breaks). Compare it with Service games won.",
                "The Momentum chart shows the point difference. The line goes up when the first player wins a point, and down when the second player wins a point. A dashed line marks the start of a set.",
                "The Momentum chart is the last page of the statistics card. A set summary shows the chart of its set.",
                "A value with a play icon comes from one point.",
                "The exported video uses the same card transparency as the preview.",
                "On the card, a dark player color becomes lighter, so that it shows on the dark card. The table and the scoreboard keep the player color.",
                "The In video selection autosaves to the project.",
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.HELP, "Open Statistics help"),
            ),
        ),
        HelpContent(
            page = HelpPage.EXPORT,
            summary = "Configure and export the final video, then monitor it through completion.",
            workflow = listOf(
                "In Content, select Full video, Only points, or Only favorites. The table shows the length of each exported video and the number of points in it.",
                "Select Include scoreboard and Include comments if necessary. The text next to Include scoreboard shows how many points are scored.",
                "Select Include statistics card to add the match statistics at the end of the video. The Stats tab sets which statistics the card shows. The text next to the checkbox shows how long the card is.",
                "Select Include set summaries to add the statistics of each set after the last point of the set. Set summaries are available only for Only points and Only favorites. A set without an exported point has no summary.",
                "In Quality and file size, use the Simple tab to select Original quality, Balanced, or Fast export. The selected card opens and shows the resolution, FPS, bitrate, file size, and export time. The \"original\" tag marks a value that is the same as in the original video.",
                "Use the Advanced tab to set the resolution, the FPS, the bitrate, and the encoder yourself.",
                "Select Start export, choose the output file, and monitor progress. When an export runs, the button changes to Enqueue export, and the new export starts after the current exports.",
            ),
            actions = listOf(
                "Cancel the active export or a queued export with Cancel export.",
                "Open the output folder with Open folder on an active, queued, or completed export.",
                "Use Clear list to remove the completed entries from the list.",
            ),
            goodToKnow = listOf(
                "Original quality keeps the resolution, FPS, and bitrate of the source. Balanced uses 75% of the source bitrate. Fast export makes the video 1080p at most and uses 50% of the source bitrate.",
                "On the Advanced tab, you can select only a resolution and an FPS that are not higher than the source. The values of the original video have the \"original\" tag and are selected by default.",
                "The bitrate slider goes from one fifth of the source bitrate (worst quality, smallest file) to the source bitrate (best quality).",
                "The app detects the GPU and tests each encoder. It shows only the encoders that work on this PC, and the Simple tab uses the best one.",
                "Only favorites requires at least one favorite point. Include scoreboard and Include statistics card require at least one scored point.",
                "A statistics card shows each page for 6 seconds over a frozen frame. The cards add this time to the video length and to the file size.",
                "The file size is an estimate (~). The real size can be different, because fast motion needs more data.",
                "Each export card shows the full path of the file, the content and video settings, and the written size against the expected size.",
                "Clear list does not delete the exported video files.",
                "Export has no other tab-specific keyboard shortcuts.",
                "Troubleshooting: if an export fails for an unclear reason, open the Advanced tab and select Software (x264). The software encoder is slower, but it works on all PCs. A hardware encoder can fail after a graphics driver update.",
                "Troubleshooting: if the export still fails, select a lower resolution or FPS, or select Full video to find out if the points cause the problem.",
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.HELP, "Open Export help"),
            ),
        ),
    )

    fun content(page: HelpPage): HelpContent = pages.first { it.page == page }
}
