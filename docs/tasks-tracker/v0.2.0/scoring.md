# v0.2.0 — Scoring — Tasks

Notes
- This file collects Scoring work deferred from v0.1.0 and continues the scoring UX and persistence.
- Builds on v0.1.0 Scoring 4.1–4.8 (shell, list, playback, transport, speed, actions, panels, rules engine).

Carried over from v0.1.0

- [ ] 4.9 — Persistence model for scoring (ScoreV1)
  - Description: Define storage for outcomes and optional metadata separate from EDL in `score.json` (schema v1). Map `pointId -> outcome` and optional audit fields; keep unknown fields on read if possible. Autosave on change with small debounce. Names for players are persisted as well.

- [ ] 4.10 — Next Point navigation and hotkeys
  - Description: Add a compact "Next Point" button at the bottom of the list and a W hotkey that advances to the next point (no auto-advance otherwise).

- [ ] 4.11 — Selecting a scored point restores pressed state
  - Description: Selecting a scored point restores the pressed/selected state for outcome actions consistently across all action areas.

- [ ] 4.12 — Overlay scoreboard above the video
  - Description: Render a compact scoreboard overlay (players, sets/games/points) positioned as in the mock; updates after the selected point.

- [ ] 4.13 — Autosave and project integration
  - Description: Persist outcomes shortly after changes; reload on project open; reuse AutosaveScheduler pattern.

- [ ] 4.14 — Tests: rules engine and persistence
  - Description: Unit tests for rules engine (points→games→sets) and `ScoreV1` read/write including tiebreak and long deuce sequences; include a small golden `edl.json` + `score.json` pair.

- [ ] 4.15 — Accessibility and focus management
  - Description: Ensure keyboard usage mirrors Markup behavior; add accessible names/labels; keep locale/time helpers consistent.

- [ ] 4.16 — Empty state and edge cases
  - Description: Handle no-points projects (empty state guidance) and malformed zero-duration segments (disable actions; inline error) gracefully.

- [ ] 4.17 — Player names inputs and dynamic labels
  - Description: Two text inputs (Player 1/2 name) persisted to `score.json` (v1). Actions/buttons and overlays reflect names live as the user types.

- [ ] 4.18 — Playback reflects Adjustments
  - Description: The Scoring tab video preview reflects current project Adjustments (color and geometry) live as the user changes them on the Adjustments tab, without requiring full re-render.
  - Acceptance Criteria:
    - Subscribes to the shared adjustments state/service and applies updates immediately.
    - Color: brightness/contrast/saturation (and WB approximation) mapped to libVLC adjust filter or equivalent.
    - Geometry: zoom/pan/rotation applied as a preview transform (libVLC or UI-layer fallback).
    - Resets on project switch; defaults to identity when `adjustments.json` is absent.
    - Smooth at 30–60 FPS on 1080p sources where feasible.
  - Cross-reference: See Adjustments v0.2.0 epic (5.3/5.4) in `docs/tasks-tracker/v0.2.0/adjustments.md`.
