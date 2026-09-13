# Rally Comments Design

## Goal

Allow users to attach a readable text comment to a moment in a project video. Comments are created and managed in the Rallies tab, are visible in the timeline and the unified event list, and can optionally be burned into an export.

## Scope

This feature adds time-based text annotations only. It does not add an in-player comment preview, configurable typography/layout, or comments tied to scoring outcomes.

## Persistence

Comments live in the project's existing `edl.json`, alongside rally (`PointV1`) intervals. This keeps project loading, autosave, and export snapshots in one source of truth.

`EdlV1` gains:

- `comments: List<CommentV1>`, defaulting to an empty list.
- `commentDefaults: CommentDefaultsV1`, whose initial `colorHex` is `#FFFFFF`.
- `nextCommentId: Int`, whose initial value is `1`.

`CommentV1` contains:

- `id: Int` — a stable, positive, project-local display number.
- `startMs: Int` — the source-video timestamp at which the comment begins.
- `durationMs: Int` — its positive source-independent display duration.
- `text: String` — non-blank comment text.
- `colorHex: String` — display color, normalized to `#RRGGBB`.

New comments receive `nextCommentId`, then increment it. IDs never change and are not reused after deletion. Legacy EDL files load with no comments, a white default color, and `nextCommentId = 1`; malformed or duplicated persisted IDs are repaired deterministically and the next ID is advanced past the highest retained ID.

The last color selected for a comment is written to that project's `commentDefaults.colorHex`. It supplies the initial color for the next new comment in that same project and is never shared with another project.

## Rallies User Flows

### Create a comment

The Rallies control strip gains an **Add comment** action. It opens a compact modal editor with:

- a multi-line text field;
- a start timestamp, prefilled from the current playhead and still editable;
- a duration input in seconds, accepting fractional values and stored as milliseconds;
- a color control prefilled from the project's last-used comment color.

Creation requires non-blank text and a positive duration. It creates `Comment #N`, persists through the existing debounced autosave, and selects/scrolls to the new event.

### Browse, select, edit, and delete

The right-side heading changes from **Marked points** to **Rallies & events**. Its single chronological list interleaves rallies and comments by source start time.

- Existing rally cards retain their interval, favorite, edit, and delete behavior.
- Comment cards show `Comment #N`, start time, duration, a wrapped text preview, and a color swatch.
- Clicking either kind of card selects it and seeks to its start.
- A comment card's color swatch opens the color control directly. Updating it changes that comment and the project's default color.
- Its edit action opens the comment editor to change text, start, duration, or color; its delete action requires the existing confirmation pattern.

Comments may overlap rallies, each other, or idle gaps. Unlike rallies, they do not participate in EDL interval-overlap validation.

## Timeline

The timeline retains VIDEO and MARKS tracks and adds a COMMENTS track.

For every comment, draw a colored, narrow vertical bar at `startMs` across all three tracks. On the COMMENTS track, attach a compact labeled box immediately to the **right** of the bar. The label is the stable comment number, for example `#12`.

Clicking a comment box selects that exact comment, seeks to its start, and scrolls/focuses its card. Comment boxes that would collide use additional compact lanes in the COMMENTS track; the track grows enough to preserve each visible ID. The marker bar still spans the complete combined track area.

## Export

Export gains an independent, unchecked-by-default **Include comments** checkbox beside **Include Scoreboard**. It remains available when comments are absent (the resulting output simply contains no comment subtitles) and has explanatory tooltip text.

The checkbox snapshots comment rendering in the queued `RenderJob`, independently from the scoreboard flag. Completed-render metadata and summaries record both enabled overlay types.

Comments render as ASS subtitles in a fixed lower-third treatment:

- horizontally centered;
- vertically in the lower third;
- large text in the comment's color;
- multiline wrapped to a safe screen width;
- a half-transparent dark backing that expands to the wrapped text.

Comment text must be escaped for ASS syntax and newlines must be converted to ASS line breaks. Font sizing, margins, and wrapping width scale with the selected output resolution.

### Source-to-output timing

The source timestamp determines whether a comment is eligible. Output timing determines how long it remains visible.

For a full-video export, an eligible comment starts at `startMs` and runs to `startMs + durationMs`, clipped only at the output video's end.

For a points-only export, define *kept intervals* as the valid points actually emitted after applying idle-trim and (if enabled) favorite-only filtering.

1. A comment is included only when `startMs` falls in a kept interval using half-open bounds: `point.startMs <= startMs < point.endMs`.
2. Its output start is the total duration of earlier kept intervals plus its offset from the containing interval's start.
3. Its output end is `outputStart + durationMs`, clipped only at the final output duration.

Consequently, a comment beginning in a kept rally continues for its full selected duration across concatenated rallies; removed idle time consumes no output time. A comment beginning in an idle gap, a non-favorite rally excluded by favorite-only export, or a boundary at a point's end is omitted from that trimmed export.

## Architecture and Component Changes

- `EdlIO.kt`: add comment schema/defaults, validation and ID repair, preserving legacy file compatibility.
- `MarkupDispatcher.kt`: continue owning rally boundaries; add a dedicated `CommentDispatcher` for comment validation, identifiers, create/update/delete, and project color defaults.
- `MarkupContracts.kt`: replace point-only presentation contracts with typed rally-or-comment event DTOs and actions.
- `SwingMarkupPanel.kt`: load/save both collections, coordinate selection across event types, provide the new creation action, and expose both event collections to the timeline/list.
- `PointsCardsView.kt`: rename/rework as an event cards view that renders mixed chronological rally and comment cards. Add `EditCommentDialog.kt`.
- `SwingTimelineComponent.kt`: accept comments and selection callbacks, render markers/labels/lanes, and support comment-box hit testing.
- `SwingExportPanel.kt`: add the checkbox and snapshot it into the render request.
- `ExportPlanner.kt`: map eligible comments to output-time comment spans using the same kept-interval selection that drives trimming.
- `RenderQueue.kt`, `RenderJob`, and completed-render storage: retain the comment overlay snapshot and display its inclusion state.
- `AssOverlayWriter.kt`: emit scoreboard and comment dialogue layers into one ASS file whenever either overlay type is selected. A separate comment-layout helper keeps text escaping, wrapping, scaling, and lower-third geometry isolated from scoreboard rendering.

## Verification

Add focused automated coverage for:

- legacy and round-trip EDL persistence; project-scoped default colors; monotonic comment IDs after edit/delete/load repair;
- comment validation and event-list chronological ordering;
- timeline marker geometry, right-attached label placement, collision lanes, and label click selection;
- source-to-output mapping for full video, idle trim, favorite-only export, starts in omitted intervals, half-open boundaries, tails crossing cut gaps, and final-output clipping;
- ASS escaping, wrapping, position, color, backing, and coexistence with the scoreboard;
- export-control behavior and render-job/completed-render snapshots;
- Rallies UI flows for add, edit, direct color changes, persistence after reopening a project, selection, and deletion.
