# v0.1.0 — Markup features: Points from single video — Tasks

Notes
- The Markup tab works with a single source video selected for the project (see Projects tasks 0.3/0.4).
- Users place Point Start and Point End times based on the video viewer. When both are defined, a point is created automatically. No separate Add/Save action.
- Rendering a video that crops out empty segments between points is out of scope for this file (scheduled later under Export or a future version).

User workflow (center of this spec)
- Seek through the video to find where the first point play starts.
- Place a "Point Start" — this creates a new marked point in the list, selects it, and shows it as "no end yet".
- Seek to where the point play ends.
- Place a "Point End" — this updates the currently active/selected marked point by filling its end time.
- Seek to the start of the next point and repeat until all desired points are marked.
- At any time, clicking "Go to marked point" on a row should jump playback to that point's start time.
- While playing or seeking, whichever point contains the current time (start ≤ t < end) should become active/selected automatically.
- The timeline at the bottom shows two separate tracks: VIDEO (source media spans) and MARKS (point intervals).

General findings & scope notes (review)
- Consistency with mock: design/markup.html shows two primary controls labeled “Point Start [C]” and “Point End [V]”, a points list with a count badge, transport controls, a timecode, and a dual‑track footer (VIDEO, MARKS). Tasks map well to these elements.
- Time display: the mock shows a frame‑like timecode (00:14:22:04). For v0.1.0 we’ll use milliseconds as specified in tasks (hh:mm:ss.mmm). This is acceptable and avoids frame‑rate dependency; a frame‑accurate display can be considered later.
- Single source video: the VIDEO track in the mock shows multiple clips as examples. Our scope is a single selected source video (Projects 0.3/0.4), so the VIDEO track will render one continuous span labeled with the file name.
- No reverse playback: We standardize on 1s seeks on Left/Right Arrow (−1000 ms / +1000 ms) and 10s seeks on Shift + Left/Right Arrow (−10_000 ms / +10_000 ms) per 2.5.
- Keyboard focus: Spacebar play/pause depends on focus. Ensure the Markup tab captures Space when appropriate without breaking text field editing.
- Performance: Viewer time updates at ~10Hz (2.12) and time display at ≤100ms (2.2) are consistent. Prefer a single ticker feeding both to avoid duplicate timers.
- Overlaps policy: Adjacent points that touch at boundaries are allowed; overlaps are rejected (2.7). Boundary tie‑break rules are defined under 2.12.
- Out of scope reaffirmed: cropping/rendering is excluded here; wiring to Export comes later.

- [ ] 2.1 — Markup tab shell (UI scaffolding)
  - Description: Implement the Markup tab per `design/markup.html` with a video preview, transport controls, two point controls (Point Start, Point End), and a Points list area.
  - Acceptance Criteria:
    - The Markup tab is reachable and matches the draft layout.
    - Primary areas exist: Viewer, Transport, Point Controls (Start/End only), Points List.
  - Implementation Guide:
    - UI toolkit: JavaFX (or Compose for Desktop). Keep structure minimal and reflect `design/markup.html`.
    - Create a `MarkupTab` view with placeholders for listed areas and wire basic button actions to dispatcher methods.
  - Review notes:
    - Confirm that the Points list includes a visible count badge as in the mock (non-functional label is OK in v0.1.0).

- [x] 2.2 — Video preview: open/play/pause/seek
  - Description: Provide basic playback functionality against the project’s `sourceVideo`.
  - Acceptance Criteria:
    - If a project has `sourceVideo`, the viewer loads it when entering Markup.
    - Play/Pause/Seek via on-screen controls and spacebar.
    - Current time display (hh:mm:ss.mmm) updates at least every 100 ms while playing.
  - Implementation Guide:
    - Use the same media preview stack chosen for the app (e.g., VLCJ + JavaFX Canvas, or JavaFX MediaPlayer if sufficient).
    - Expose `getPlayheadMs()` and `seekTo(ms)` APIs from the viewer component.
  - Review notes:
    - Mock displays frame-like timecode; tasks specify milliseconds. Decision: show ms for v0.1.0. Q: Should we also show frames if FPS is known? (defer if not trivial).

- [x] 2.3 — Point data model and storage (EDL v1 – points only)
  - Description: Define an initial EDL (edit decision list) structure for points representing “keep” intervals within the single video.
  - Acceptance Criteria:
    - Data model fields: `id`, `startMs`, `endMs`, optional `label`, optional `notes`.
    - Stored in project under `edl.json` (or embedded under `manifest.points` if preferred and simpler) with schema v1.
    - Loading the project reads existing points; saving persists changes.
  - Implementation Guide:
    - Kotlin data classes: `PointV1`, `EdlV1(points: List<PointV1>, version: 1)`.
    - Extend existing `ManifestIO` or add `EdlIO` for `read/write` with Jackson or kotlinx.serialization (match existing choice).
    - Times stored as integers in milliseconds. Preserve unknown fields on read if possible.
  - Review notes:
    - Decision needed: prefer a separate `edl.json` to keep manifest lean; embedding under manifest is acceptable short-term. Suggest `edl.json` for clarity.

- [x] 2.4 — Auto-create point from Start/End
  - Description: Allow the user to set Point Start (C) and Point End (V) from the live playhead. When both bounds are valid, create a point automatically and reset pending markers. Placing Point Start creates a new list entry immediately and selects it as the active point; placing Point End completes the currently active point.
  - Acceptance Criteria:
    - Pressing C sets a pending Start from current playhead and creates a new point row in the list, marked as “no end yet”, and selects it.
    - Pressing V fills End for the currently active/selected point. If no active pending point exists, pressing V has no effect (or shows a gentle hint) and does not create a new point.
    - When both are set and valid, the point is created instantly; UI clears pending start/end indicators.
    - Visual indicators show pending Start/End times while incomplete; active row styling matches design/markup.html.
  - Implementation Guide:
    - Keep pending marks in view-model state. No Add/Save button; creation triggers automatically when valid.
    - Ensure “active point” state is unique (only one can be pending at a time). Selecting another row cancels pending end.
    - Round to the nearest 10 ms (configurable) to stabilize times from the player.
  - Review notes:
    - If C is pressed again while a point is pending (no End yet), replace the Start time on the same pending row (do NOT create another row). [Confirmed]
    - If V is pressed before any C, no point is created; show a subtle hint near controls. [Confirmed]
    - After a point is completed (has End), no new pending row is created automatically; the next pending row is created only when the user presses C, and it becomes selected. [Confirmed]
    - Creation must obey 2.7 validation: if the pending interval would overlap, block finalization and show inline error on the row.

- [x] 2.5 — Keyboard shortcuts and nudge
  - Description: Provide efficient keyboard operations to mark points and fine-tune boundaries.
  - Acceptance Criteria:
    - Space: Play/Pause.
    - C: set Point Start; V: set Point End; Delete: Delete selected point.
    - Left/Right Arrow: seek ±1000 ms (±1s).
    - Shift + Left/Right Arrow: seek ±10_000 ms (±10s).
    - No bindings for J/K/L in this version.
  - Implementation Guide:
    - Centralize input handling in the Markup tab. Make increments configurable constants.
  - Review notes:
    - When focus is in a text field (e.g., Label), do not hijack typing. Space/arrow keys should respect focus; provide a global focus area to capture playback controls otherwise.
    - No reverse playback; Arrow/Shift+Arrow perform fixed seeks.
    - J/K/L are intentionally unused; Space replaces K for Play/Pause.

- [x] 2.6 — Points list UI (auto-populated; view, select, jump, edit, delete)
  - Description: Show a list/table of created points (auto-populated when Start+End are set) with ability to select, jump, edit, and delete. Include an explicit “Go to marked point” action as shown in design/markup.html.
  - Acceptance Criteria:
    - Columns: # (order), Start, End, Duration, Label.
    - Each row exposes a “Go to marked point” button that seeks the viewer to Start and focuses the player.
    - Single-clicking a row also seeks to Start; double-click toggles Start/End quick preview.
    - Active/selected row is visually highlighted and auto-scrolled into view when changed.
    - Editing Start/End inline validates and persists; Delete removes the point after confirmation.
  - Implementation Guide:
    - Keep points sorted by `startMs`. Recompute order index automatically.
    - Display mm:ss.mmm formatting and total duration per row.
  - Review notes:
    - “Go to marked point” action: in the mock it’s an icon button per row; acceptance criteria already require it. Ensure it also focuses the player so Space controls playback immediately after jumping.
    - Inline edit validation: on invalid edit (overlap, negative duration), show inline error and revert on blur unless user fixes it.
    - Sorting after edit: if Start changes, resort and maintain selection/scroll position.
    - Delete flow: a lightweight confirm dialog is sufficient for v0.1.0; no undo/redo yet (out of scope).

- [x] 2.7 — Validation rules
  - Description: Prevent invalid intervals; no snapping in v0.1.0.
  - Acceptance Criteria:
    - A point must satisfy `0 <= startMs < endMs`; duration >= 200 ms.
    - Start placement is not allowed if the proposed Start lies inside an existing point interval [startMs, endMs); adjacency at boundaries is allowed.
    - No overlaps between points after creation/edit; adjacent points allowed via the half‑open model [start, end).
    - When a validation blocks an action (e.g., End without Start, invalid duration, overlap, Start inside an existing interval), a user‑visible notification is shown (toast‑style in v0.1.0).
  - Implementation Guide:
    - On auto-create/edit, if overlap or other validation is detected, block commit; show an inline error on the row when applicable and also surface a user‑visible notification (toast). Auto‑fix (trim to nearest non‑overlapping boundary) can be offered but is optional for v0.1.0.
  - Review notes:
    - Overlap definition uses half-open intervals [start, end); this aligns with 2.12 activation rule (start ≤ t < end). Confirm this is the intended model.
    - No snapping in v0.1.0 (explicitly disabled).
    - Minimum duration (200 ms) is OK for v0.1.0; consider making it a constant for easy tuning.
    - Transient toast notifications are acceptable for v0.1.0; a persistent status area can be considered later.

- [x] 2.8 — Autosave points and project integration
  - Description: Persist changes shortly after edits and integrate with current project context.
  - Acceptance Criteria:
    - Changes autosave within ~300 ms with debounce; manual Save All also available.
    - Loading a project populates the list; switching projects unloads state safely.
  - Implementation Guide:
    - Reuse/extend `AutosaveScheduler` proposed under Projects 0.7 with a 300 ms debounce window. Save to `edl.json` or manifest.
    - Emit project-changed events so Markup refreshes state on load/unload.
  - Review notes:
    - Debounce window: 300 ms; ensure flush on app shutdown and on project switch.
    - Save target: aligns with 2.3 preference for `edl.json`; if embedded, ensure manifest autosave doesn’t race with EDL saves.
    - Persistent save status indicator (e.g., "Saving…/Saved/Error") should be present in the UI instead of transient notifications, but is out of scope for v0.1.0.

- [x] 2.9 — Time formatting helpers and total duration summary
  - Description: Provide consistent time display and a running summary of total “kept” time across points.
  - Acceptance Criteria:
    - Shared utilities to format ms to `hh:mm:ss.mmm` and parse if needed.
    - Under the Marked points list, display the count of points and the total kept duration.
  - Implementation Guide:
    - Add `Timecode.kt` utility or similar with unit tests.
  - Review notes:
    - Ensure consistent rounding rules with 2.4 (nearest 10 ms). Avoid drift between display and storage.
    - Consider a helper to compute total kept duration efficiently whenever the list changes.

- [x] 2.10 — Basic persistence and validation tests
  - Description: Add unit tests for EDL read/write and validation rules.
  - Acceptance Criteria:
    - Round-trip JSON for `EdlV1` with a few points.
    - Validation rejects overlaps and negative durations; accepts adjacent points.
  - Implementation Guide:
    - Place tests under `src/test/kotlin/...`; use the same JSON library as runtime.
  - Review notes:
    - Include tests for boundary activation logic assumptions (half-open intervals) to keep 2.12 behavior consistent with validation.

- [x] 2.11 — Navigation wiring to Markup tab
  - Description: Ensure app can navigate to Markup after New/Open project when `sourceVideo` is present.
  - Acceptance Criteria:
    - From Projects flows, opening/creating a project with `sourceVideo` lands on Markup.
    - If `sourceVideo` is missing, route to Select Source first, then Markup.
  - Implementation Guide:
    - Extend `Navigator` described in Projects 0.10 with a `Markup`/`Step2Trim` route.
  - Review notes:
    - Ensure the Markup tab initializes with the viewer loaded and the Points list focused appropriately for keyboard shortcuts.
    - When `sourceVideo` becomes unavailable (moved/missing), handle gracefully: show error and route back to Select Source.


- [x] 2.12 — Auto-activate point on playback/seek
  - Description: Keep the currently relevant point active/selected while the user plays back or seeks the video.
  - Acceptance Criteria:
    - When the playhead time t satisfies startMs ≤ t < endMs for a point, that point becomes the active/selected row.
    - The list auto-scrolls to reveal the active row if it is out of view.
    - When t is outside all points, clear the active selection unless a pending Start is in progress.
    - Edge cases handled: exactly at a point’s endMs selects the next point if it starts at the same time; gaps produce no active row.
  - Implementation Guide:
    - Subscribe to viewer time updates (~10Hz or on seek complete). Compute active point via binary search on sorted points.
    - Avoid thrashing by debouncing rapid updates and only changing selection when identity changes.
  - Review notes:
    - Source of truth for activation is the sorted points list; pending (Start‑only) row should be considered active only if the playhead ≥ Start and no completed point matches.
    - Debounce suggestion: 50–100 ms to reduce flicker but keep responsiveness.

- [x] 2.13 — Timeline marks bar with two tracks (per design/markup.html)
  - Description: Render a compact timeline footer showing two distinct tracks: VIDEO (source media spans) and MARKS (point intervals).
  - Acceptance Criteria:
    - A footer timeline shows: a ruler, a VIDEO track row, and a MARKS track row.
    - Each point is visualized as a span on the MARKS track; clicking a span seeks to its start.
  - Implementation Guide:
    - Compute pixel-per-millisecond scaling based on the total duration of the source video; no zoom levels in v0.1.0 (zoom is out of scope).
    - Use a lightweight canvas/Pane with absolute-positioned children for spans; reuse time formatting helpers from 2.9.
  - Review notes:
    - For single-source scope, render a single full-width span in VIDEO labeled with the file name from manifest.
    - MARKS spans should be click‑to‑seek.


- [ ] 2.14 — Inline editing for points (split from 2.6)
  - Description: Enable inline editing of Start/End (and Label) directly in the Marked points list. Edits must validate and persist. This work was split out of 2.6 to ship view/select/jump/delete first.
  - Acceptance Criteria:
    - Users can edit Start and End times inline for any completed point; pending row is not editable.
    - Users can edit the Label inline.
    - On commit, validation rules (see 2.7) are applied: 0 <= start < end, duration >= 200 ms, and no overlaps with other points.
    - On invalid edit, an inline error is shown and the edit is not committed until fixed; cancel/blur reverts to last valid value.
    - After a Start change, the list re-sorts by startMs and selection/scroll position are preserved sensibly.
  - Implementation Guide:
    - Provide text fields or time spinners in-row for Start/End with hh:mm:ss.mmm parsing helpers (reuse 2.9 when available).
    - Apply nearest 10 ms rounding behavior consistent with 2.4.
    - Use a view-model/edit buffer to allow validation before commit; only write to dispatcher/state on successful validation.
    - Emit events for autosave integration (2.8) after successful commits.
  - Review notes:
    - Ensure keyboard focus/Space playback shortcut rules (2.5) remain intact while editing.
    - Consider lightweight visual cues on rows that moved due to resort after edit.


- [ ] 2.15 — Left navigation sidebar present on Markup
  - Description: Ensure the Markup tab/page includes the same left navigation sidebar as the Projects page, maintaining consistent app-wide navigation and branding.
  - Acceptance Criteria:
    - A left navigation sidebar is visible on the Markup screen with the same layout, styling, and width (~80 px) as on Projects.
    - The "Markup" item is shown as the active/selected entry; "Projects" appears inactive.
    - Sidebar items do not need to navigate in v0.1.0 (clicks may be no-ops), but visual hover/active states should match Projects.
    - The main Markup content is laid out to the right of the sidebar without overlap; resizing keeps proportions sensible.
  - Implementation Guide:
    - Reuse the existing sidebar from Projects (extract a small helper/component if convenient) and include it in Markup’s root layout.
    - Apply the same CSS classes already used by Projects’ sidebar to ensure identical visuals.
    - Keep keyboard focus behavior: the Markup content area should still capture Space/arrow keys for playback when appropriate.
  - Review notes:
    - Verify consistent spacing, padding, and background with Projects.
    - Ensure no regression to Markup controls’ sizing due to added sidebar; media viewer should resize accordingly.


- [ ] 2.16 — Timeline playhead indicator (split from 2.13)
  - Description: Add a vertical playhead indicator to the timeline footer that reflects the current viewer time.
  - Acceptance Criteria:
    - A thin vertical line (playhead) is visible above the timeline tracks, positioned according to the current time within the total video duration.
    - The playhead position updates smoothly during playback and jumps immediately when seeking.
    - Toggling Play/Pause in the viewer reflects in the playhead movement/stop respectively.
    - No timeline scrubbing by dragging the playhead in v0.1.0.
  - Implementation Guide:
    - Subscribe to viewer time updates (~10 Hz or on seek complete) and convert time to X coordinate using the same scaling as 2.13 (total-duration-to-pixels).
    - Use a lightweight node (e.g., a 1–2 px wide Pane/Line) layered above the VIDEO and MARKS tracks.
    - Recompute layout on container resize; avoid jitter by debouncing to ~50–100 ms.
  - Review notes:
    - Ensure z-order keeps the playhead visually above both tracks and ruler.
    - Zoom levels and scrubbing are out of scope for v0.1.0.
