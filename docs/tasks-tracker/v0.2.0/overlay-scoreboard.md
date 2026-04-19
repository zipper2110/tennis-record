# v0.2.0 — Scoring overlay: robust scoreboard above the video

Summary

- Implement a robust scoreboard overlay for the Scoring tab that always renders above the VLCJ video across platforms/monitors and during window focus/resize/move events.
- Replace the temporary removal in v0.1.0 with a production‑ready solution.

Goals

- Render a compact scoreboard (players, per‑set game counts, current game points) visually consistent with `design/scoring.html`.
- Overlay must track the video area precisely when the window is moved, resized, or when tabs are switched.
- Avoid heavyweight/lightweight z‑ordering issues so the overlay never appears behind the video.
- Provide a clear lifecycle so there are no leaks, flicker, or orphan windows when switching tabs/projects.

Acceptance Criteria

- The overlay appears above the video reliably on Windows/macOS/Linux (primary target: Windows).
- It updates immediately when the selected point/outcome changes (reflecting games/sets/points, including tiebreaks).
- The overlay position and size stay correct on:
  - main window resize/maximize/restore,
  - moving the window between monitors (different DPI/scales),
  - switching tabs and projects,
  - starting the app minimized and restoring it.
- No visual flicker during normal usage; no stray windows after closing the tab/app.
- The overlay can be toggled on/off by a developer flag (for troubleshooting), default ON in release builds.

Technical Notes & Options

- Hosting options to evaluate:
  1) Dedicated heavyweight `JWindow` tracked over the embedded VLC canvas (preferred for VLCJ/Canvas because of z‑order constraints).
     - Keep `alwaysOnTop=true`; bring to front after bounds updates when needed.
     - Track bounds via `locationOnScreen` with a bounded retry on initial realization.
     - Parent the `JWindow` to the app window to ensure z‑grouping with the main UI.
  2) Composition inside a Swing container with a non‑Canvas video component (if we migrate away from AWT Canvas in future). Not in scope now but document for later.
- Synchronization: hook `ComponentListener` on both the aspect panel and the VLC component to trigger bounds recalculation.
- Lifecycle:
  - Create window on `addNotify()`; update bounds when the video component becomes visible/realized.
  - Hide/dispose on `removeNotify()`; cancel any timers.
- DPI/Scaling: consider rounding positions to int pixels; verify on mixed‑DPI setups.

Implementation Plan

1. Reintroduce overlay UI panel (content builder) decoupled from hosting so it can be embedded in tests without a window.
2. Implement overlay host with `JWindow`:
   - Owner = main window; transparent background; focus disabled.
   - `alwaysOnTop=true` and `toFront()` after setBounds.
   - Bounded retry (e.g., up to 30 attempts every 100ms) if `locationOnScreen` is unavailable.
   - Add listeners to re‑position on move/resize/show of the video and aspect containers.
3. Wire lifecycle in `SwingScoringPanel` and ensure calls are resilient to visibility changes.
4. Hook scoring state updates to rebuild overlay content (reuse existing rules engine state).
5. Testing checklist:
   - Manual: resize/move between monitors; open/close tab; app start minimized; verify overlay z‑order and tracking.
   - Automated: unit tests for the content builder (pure Swing panel without window) using sample states.
6. Developer toggle to disable overlay quickly (system property or settings flag), default ON.

Out of Scope

- Export‑time overlay rendering; this task is runtime/UI only.
- Player metadata (names/colors) beyond placeholders already used.

Migration Notes

- v0.1.0 temporarily removed the overlay. This task restores it with a robust host and lifecycle. No data migrations required.
