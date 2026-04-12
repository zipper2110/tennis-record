# v0.2.0 — Markup: Timeline Zoom/Pan and Interaction — Tasks

Notes
- This file defines tasks only; no implementation is required in v0.2.0 planning stage.
- These tasks build on v0.1.0 timeline work: 2.13 (Timeline marks bar). v0.2.0 introduces zooming/panning and richer interactions.
- Unless stated otherwise, all hotkeys are active while the Markup tab has focus and should respect text-input focus rules from 2.5.


- [ ] 3.1 — Timeline zoom controls (infinite zoom in/out)
  - Description: Add zoom capability to the timeline with no hard limits in either direction (zoom out to show the entire media compactly; zoom in arbitrarily for fine control).
  - Acceptance Criteria:
    - Zoom in: Ctrl + '+' (or Ctrl + '='); Zoom out: Ctrl + '-'.
    - Ctrl + Mouse Wheel/Scroll over the timeline zooms centered at the mouse cursor position.
    - Zoom has no hard cap; internal implementation may enforce practical bounds to avoid numeric/CPU issues, but UI should not present fixed ends.
    - Zoom level persists while the Markup tab is open and resets when switching projects.
    - Ruler tick density adapts to scale to remain readable.
  - Implementation Guide:
    - Maintain a scale factor in pixels-per-second (px/s). Initial value defined by 3.2 ("100% zoom").
    - Use exponential steps per wheel notch and per keypress (e.g., ×1.1 / ÷1.1) to feel smooth.
    - Anchor zoom at cursor: worldX = (cursorX - originX)/scale; adjust originX so that worldX maps back to cursorX after scale change.
    - Debounce expensive layout recalculations (~50–100 ms) while preserving immediate visual feedback.
  - Review notes:
    - Verify large-duration videos (e.g., 3–4 hours) remain interactive when fully zoomed out.
    - Verify very deep zoom (sub-millisecond per pixel) still renders guides without UI thrash; degrade tick detail if needed.

- [ ] 3.2 — Define and reset to 100% zoom (1s = 1px)
  - Description: Establish a canonical "100%" zoom level for the timeline and provide controls to return to it.
  - Acceptance Criteria:
    - 100% zoom is defined as 1 second of video equals 1 pixel on the timeline (scale = 1 px/s).
    - Provide a visible UI button "100%" (or reset icon) near the timeline to reset zoom.
    - Hotkey Ctrl + '0' resets to exactly 100%.
    - Reset centers the view on the current playback time if the timeline is scrollable; otherwise anchors left at 0.
  - Implementation Guide:
    - Expose a helper setZoomToOnePxPerSec() that updates scale and viewport while preserving the playhead center when possible.
    - Ensure the UI button and hotkey share the same command path for consistency and testability.
  - Review notes:
    - Consider showing the current zoom factor (e.g., "0.5 px/s", "4 px/s") as a compact label; optional in v0.2.0.

- [ ] 3.3 — Fixed-position playhead; timeline pans under it
  - Description: The playhead is a vertical line at a fixed X position within the timeline viewport; on seek/scroll/drag, the content moves under the playhead instead of the playhead moving.
  - Acceptance Criteria:
    - The playhead visual remains at a fixed X (e.g., center of the timeline area) regardless of zoom level.
    - During playback, the timeline content scrolls to keep the current time under the fixed playhead when auto-follow is enabled.
    - Toggling Play/Pause affects only auto-following motion, not the playhead’s screen position.
    - v0.2.0 does not allow dragging the playhead handle itself (consistent with the issue request).
  - Implementation Guide:
    - Introduce a timeline viewport with origin (world start time mapped to pixel X=0). Playhead screenX is constant; world time maps via origin and scale.
    - Add an "auto-follow" toggle (default ON) to keep playhead centered while playing; when OFF, playback continues without auto-panning.
    - Define the playhead as a vertical line element in the timeline overlay layer; keep screenX constant and update origin/scale mapping to move content under it.
  - Review notes:
    - Ensure z-order keeps the playhead above tracks and ruler.
    - Verify no jitter at different scales and during window resizes.

- [ ] 3.4 — Scrub by dragging on the VIDEO track (left/right drag)
  - Description: Allow users to click-and-drag horizontally on the VIDEO track to adjust playback position.
  - Acceptance Criteria:
    - Mouse down on the VIDEO track enters scrubbing mode.
    - Horizontal drag translates to time delta using the current scale; the viewer seeks accordingly in real-time.
    - Releasing the mouse ends scrubbing; a final seek ensures the last position is committed.
    - Cursor changes to indicate scrubbing while active.
  - Implementation Guide:
    - Convert deltaX (pixels) to deltaTime (ms) via scale; accumulate for smoothness.
    - Throttle seeks to a reasonable rate (e.g., 60–120 Hz, or based on player capability) to avoid overloading the media stack.
    - Cancel scrubbing if the pointer exits the timeline area with the button released.
  - Review notes:
    - Scrubbing should work consistently at any zoom level.

- [ ] 3.5 — Vertical scroll over timeline seeks backward/forward
  - Description: Scrolling the mouse wheel over the timeline (without Ctrl) nudges playback position backward/forward.
  - Acceptance Criteria:
    - Scrolling up moves backward; scrolling down moves forward.
    - Step size respects zoom level for intuitiveness, or use a fixed base (e.g., 500 ms) with Shift/Ctrl modifiers to adjust.
    - Behavior applies when the cursor is over the timeline area; does not interfere with overall page scroll.
  - Implementation Guide:
    - Provide configurable constants: BASE_NUDGE_MS, SHIFT_MULTIPLIER, ALT_FINE_MULTIPLIER.
    - Option A: Scale-aware nudge (e.g., one notch ≈ viewWidthPx * 0.02 in time units) to keep behavior consistent across zoom.
    - Option B: Fixed-step with modifiers; choose one, document in help tooltip.
  - Review notes:
    - Ensure this behavior does not conflict with 3.1 Ctrl+Wheel zoom.

- [ ] 3.6 — Click-to-seek anywhere on the VIDEO track
  - Description: Clicking on the VIDEO track jumps playback to the corresponding time under the cursor.
  - Acceptance Criteria:
    - Single click seeks the viewer to the clicked time and focuses the player.
    - Works consistently at any zoom level and viewport origin.
    - Does not start playback automatically (preserves current play/pause state).
  - Implementation Guide:
    - Map cursorX to world time using origin and scale; clamp within [0, duration).
    - Integrate with existing seekTo(ms) from v0.1.0 (2.2).
  - Review notes:
    - Ensure clicks on MARKS spans continue to work (2.13) and do not conflict; prefer identical behavior there too.

- [ ] 3.7 — Timeline panning (hand tool) without seeking
  - Description: Allow panning the timeline viewport (moving origin) without changing playback time.
  - Acceptance Criteria:
    - Middle-mouse drag or Space+Left Mouse drag pans horizontally without affecting current time.
    - While panning, the playhead stays fixed on screen; only content moves.
    - Inertial scroll is not required.
  - Implementation Guide:
    - Update origin by deltaX/scale; re-render without issuing player seeks.
    - Ensure conflicts with 3.4 scrubbing are resolved via distinct gesture areas or modifiers (e.g., scrubbing on VIDEO track left button; panning on middle button or with Space held).
  - Review notes:
    - Verify accessibility: provide a keyboard alternative (e.g., Shift + Arrow Left/Right to pan viewport without seeking).

- [ ] 3.8 — Persist and restore timeline view state per project
  - Description: Remember the user's last zoom level and viewport origin for each project.
  - Acceptance Criteria:
    - On opening a project, the timeline restores to the last used zoom and origin.
    - On project switch, states are saved/debounced.
  - Implementation Guide:
    - Extend manifest or a UI settings store with non-critical UI state (zoom, origin). Keep separate from EDL.
    - Provide safe defaults if media duration changes.
  - Review notes:
    - Consider a global preference to reset view state on open; optional.

- [ ] 3.9 — Help tooltip and shortcuts summary for timeline
  - Description: Add a compact help/tooltip or a "?" icon explaining timeline-specific interactions and shortcuts.
  - Acceptance Criteria:
    - Lists: Ctrl+'+' / Ctrl+'-' (zoom), Ctrl+Wheel (zoom), Ctrl+'0' (reset), Click-to-seek, Drag on VIDEO (scrub), Wheel (seek), Middle-drag or Space+Drag (pan).
    - Appears near the timeline; dismissible.
  - Implementation Guide:
    - Reuse existing tooltip/overlay components if available.
  - Review notes:
    - Keep wording concise; link to full docs if/when available.

Dependencies and integration notes
- Depends on: v0.1.0 2.13 (base timeline rendering), 2.5 (keyboard focus rules), 2.2 (seek/play APIs).
- Ensure MARKS interactions from 2.13 continue to function at all zoom levels.
- Validation/EDL logic (2.7, 2.8, 2.12, 2.14) is unaffected by view scale; only UI mapping changes.
