# v0.1.0 — Scoring tab: Review points and record outcomes — Tasks

Notes

- The Scoring tab consumes the points created on the Markup tab (EDL v1) and lets the user assign outcomes per point.
- Outcomes drive a tennis score model (points → games → sets) and are later used by Export when rendering the final video (export usage is out of scope here).
- UI and interactions must follow the mock closely — see `design/scoring.html`.

User workflow (center of this spec)

- After marking points on the Markup tab, the user opens the Scoring tab.
- The left panel shows the list of marked points with a small "point is scored" check indicator; initially, all are unscored.
- The first point in the list becomes selected automatically when entering the tab.
- The video area shows only the selected point’s segment (start ≤ t < end). Scrubbing and playback are constrained to this segment.
- The user reviews the point: play/pause, seek back/forward (1s / 10s), and adjust playback speed from a compact dropdown.
- The user chooses the outcome: Point for Player 1, No Point, or Point for Player 2.
- When an outcome is chosen, the point is marked as scored and the choice is persisted immediately.
- The bottom player panels and the overlay scoreboard are updated according to tennis rules (including auto-pressing Game Won / Set Won when applicable).
- The user proceeds to the next point (button or W hotkey), or manually selects any point in the list. Selecting a previously scored point restores the pressed state for its outcome buttons.
- The flow repeats until all points are scored.

General findings & scope notes (review)

- Consistency with mock: `design/scoring.html` shows the left list (with counts and check marks), a scoreboard overlay above the video, a per-point scrub bar under the video, top action row (Point P1 — centered No Point — Point P2) aligned with side player buttons, playback transport and a compact speed control with ↑/↓ hint, bottom player panels with per-player Points, Games, Sets and action buttons, and a Next Point button (W) at the bottom of the list.
- Tennis rules engine: for v0.1.0, implement standard game scoring (0, 15, 30, 40, deuce/advantage) and set progression with configurable match format (best‑of‑3 default). Tiebreaks are supported (see 4.8 for details of the chosen implementation).
- Persistence: outcomes should be saved alongside points (a separate `score.json` v1 is preferred to keep EDL clean) with autosave behavior consistent with Markup.
- Keyboard focus: Space play/pause and arrow seeks should work like on Markup; ensure inputs don’t interfere with text fields or dropdowns (speed control) when they are focused.
- Export usage: computing scoreboard overlay during export is out of scope for this file, but stored outcomes and computed states must be sufficient for later use.

Decisions incorporated in this spec (answers provided):

- Player names are user-configurable via two text inputs on the Scoring tab (v0.1.0); player accent colors remain fixed. Ignore server/receiver.
- Manual Marker and Scoreboard Settings buttons are present in the UI but do nothing in v0.1.0 (no-ops).
- No auto-advancement after choosing an outcome; selection stays on the current point until the user navigates.
- Playback speed choice is remembered while the app is open (session-scoped), resets on app restart.
- If a scored point is deleted/edited in Markup, prompt the user: either keep subsequent scores as-is or remove scoring for subsequent points (recompute from the change).
- Scoreboard overlay set columns are dynamic — render as many set columns as exist in the computed state at the selected point.
- Time formatting follows Markup helpers; screen reader specifics are out of scope in v0.1.0.
- On entering Scoring, auto-select the first unscored point; if all points are scored, select the first point.
- Layout: the left points list has a fixed width of 140 px; the video area fits the remaining horizontal space; the video scales to fit (no internal scrollbars) while preserving 16:9.
- Scoreboard overlay titles (player/league labels) use static placeholder strings in v0.1.0.

- [x]  4.1 — Scoring tab shell (UI scaffolding)

  - Description: Implement the Scoring tab per `design/scoring.html` with left points list, center video with scoreboard overlay, per‑point scrub bar, top action row, transport, speed control, and bottom player panels.
  - Acceptance Criteria:

    - The Scoring tab is reachable and matches the mock layout closely.
    - Primary areas exist: Left list (with counts and scored check), Video with overlay, Scrub bar (segment), Top action row, Playback transport, Speed control, Bottom player panels.
    - Left navigation sidebar is present and highlights Scoring as active.
  - Implementation Guide:

    - Reuse the same UI stack used elsewhere (Swing for current app). Mirror styles where practical.
    - Wire UI elements to dispatcher methods (placeholders OK for early increments) matching interactions defined below.
  - Decisions:

    - Include the two footer buttons from the mock (“Manual Marker”, “Scoreboard Settings”) but make them no-ops in v0.1.0.
    - Layout: Left points list has a fixed width of 140 px; the video area fits the remaining horizontal space.
- [x]  4.2 — Load points list and selection behavior

  - Description: Populate the left list from EDL points; show start time and duration; show a small scored check icon when an outcome exists.
  - Acceptance Criteria:

    - On entering Scoring, points are loaded sorted by `startMs` ascending and shown with: index/label, start time (hh:mm:ss), duration.
    - Count badges appear in the list header: Total and Scored (as in mock).
    - Auto-select the first unscored point; if all points are scored, select the first point in the list.
    - Clicking a row selects it and scrolls it into view. Double‑click is out of scope in v0.1.0.
    - When a point is selected, the corresponding video fragment and scoring state should be loaded on the UI.
  - Implementation Guide:
      - Use the same time formatting helpers as Markup (see 2.9 in Markup spec).
      - Keep a `scored` boolean derived from outcome presence; render the check icon accordingly.
    - Decisions:
      - “Scored” count includes `NONE` outcomes.
- [x]  4.3 — Segment‑limited video playback and scrubbing

  - Description: While a point is selected, playback and scrubbing are limited to that point’s `[startMs, endMs)` segment.
  - Acceptance Criteria:
    - Playhead clamps to the segment; reaching `endMs` pauses. If the user seeks past end manually, clamp to `endMs - 1ms` (or nearest valid frame boundary as supported).
    - The scrub bar under the video represents 0–100% of the current selected segment only; labels show segment start and current time within the segment (as in mock: left = start ts, right = current ts + "Point segment").
    - Switching selection jumps playback to `startMs` and focuses the player.
  - Implementation Guide:
    - Reuse the media adapter from Markup; add segment clamping and relative scrub range mapping.
  - Decisions:
    - No auto-advance; on reaching `endMs`, pause at end.
    - Seeking past bounds clamps to the nearest bound; no wrap.
    - Do not show absolute end timestamp in v0.1.0; keep labels as in mock.
- [x]  4.4 — Transport controls and seeks (match Markup)

  - Description: Provide on‑screen play/pause and seek buttons; keyboard seeks of ±1 s and ±10 s.
  - Acceptance Criteria:
    - On‑screen: rewind, play/pause, forward as shown in the mock.
    - Keyboard: Left/Right = ±1000 ms, Shift+Left/Right = ±10_000 ms, clamped to segment bounds.
    - Space toggles play/pause (with focus safeguards matching Markup 2.5).
  - Implementation Guide:
    - Centralize key handling; ensure behavior mirrors Markup’s constants for seek sizes.
  - Open questions:
    - Key handling scope: Should seek keys (Left/Right, Shift+arrows) be active when the speed dropdown has focus, or only Up/Down are captured by the speed control while Left/Right still perform seek?
    - Consistency with Markup: Confirm the same 1s / 10s increments and acceleration constants should be reused rather than redefined.
- [x]  4.5 — Playback speed control (compact dropdown with ↑/↓ hint)

  - Description: Provide a compact dropdown under transport with options: 2×, 1× (default), 0.5×, 0.25×, 0.1×. Show a small "↑/↓ speed" hint (tooltip on small screens).
  - Acceptance Criteria:
    - Dropdown always visible; changing speed affects playback immediately.
    - Arrow Up increases speed to the next higher preset; Arrow Down decreases speed to the next lower preset (when the speed dropdown or player area has focus).
    - Tooltip present on smaller layouts if hint cannot be fully shown.
    - The last chosen speed is remembered for the duration of the app session (resets on app restart).
  - Implementation Guide:
    - Keep a fixed ordered list of presets and a helper to move to previous/next index on ↑/↓.
    - Store the selected preset in a session-scoped setting shared across Markup/Scoring so it stays consistent while the app is open.
  - Open questions:
    - Focus rules: Should Up/Down change speed only when the dropdown or player area is focused, or always (global handler) unless a text input is focused?
    - Non‑preset speeds: Any need for fine‑grained speeds (e.g., 0.33×) via hidden debug/advanced toggle, or strictly the five presets?
- [x]  4.6 — Top action row: No Point only (P1/P2 via side panels + hotkeys)

  - Description: The top action row now contains only a centered "No Point" button. "Point for Player 1" and "Point for Player 2" actions are available via the side player panels and hotkeys (A/L).
  - Acceptance Criteria:
    - Hotkeys: A = Point for Player 1, N = No Point, L = Point for Player 2 (match on‑screen hints where shown; mock shows A/L labels and a centered No Point button).
    - Clicking or pressing a hotkey sets the outcome for the selected point, marks it as scored, persists it, and updates score state.
    - If the user changes the outcome later, recompute score state accordingly (see 4.8 recompute rules).
  - Implementation Guide:
    - Disabled state: if no point is selected, these actions are disabled.
    - Visual state: when revisiting a scored point, the corresponding action appears pressed/selected.
  - Open questions:
    - Hotkeys: Spec proposes A/N/L. Are there any conflicts with existing global shortcuts? Should we expose hints (KBD tags) next to all three buttons consistently (mock shows A/L; what about N)?
    - Changing outcomes: If the user changes an already‑scored point’s outcome, should the UI immediately recompute and update all subsequent states, even if that moves “pressed” indicators for previously visited points?
    - Disabled states: If the selected point has invalid duration (edge case), all three actions are disabled. Should we also show an inline error banner near the top action row?
- [x]  4.7 — Bottom player panels: Points, Games, Sets and quick actions

  - Description: Implement per‑player panels under the video showing current Points, Games, Sets, "Game Won", and "Set Won" as in the mock.
  - Acceptance Criteria:
    - Panels display the current computed match state (Points within game, Games within set, Sets in match) for the time at/after the selected point’s outcome.
    - If tennis rules imply the game or set is completed when a point is awarded, the corresponding "Game Won" or "Set Won" button should show a pressed/armed visual state (non‑interactive indicator in v0.1.0).
  - Implementation Guide:
    - Keep display read‑only for Game/Set buttons in v0.1.0 (they reflect computed results). Actual manual override can be considered later.
  - Decisions:
    - Game Won / Set Won are strictly non-clickable; ignore clicks and optionally show a tooltip like “Computed automatically”.
    - Player accent colors are fixed (match mock blue/red) and are not user-configurable in v0.1.0.
  - Open questions:
    - Synchronization: Panels mirror the state “after” applying the selected point’s outcome. Should there be a way to preview state “before” the point (e.g., hover)? Out of scope for v0.1.0?
- [x]  4.8 — Tennis rules engine and recomputation

  - Description: Implement a rules engine that converts the sequence of per‑point outcomes into game and set progress, respecting deuce/advantage and tiebreaks.
  - Acceptance Criteria:
    - Given an ordered list of outcomes, the engine produces a derived timeline of game/point/set state after each scored point.
    - Changing any point’s outcome triggers recomputation from that point onward.
    - Match format default: best‑of‑3 sets; regular games are win‑by‑2 after 40–40 (deuce/advantage cycles unlimited).
    - Tiebreaks are supported: at 6–6 in games within a set, start a standard 7‑point tiebreak (first to 7, win‑by‑2). The winner takes the set 7–6.
  - Implementation Guide:
    - Pure functions with deterministic output; separate from UI for testability.
    - Represent outcomes as an enum: `P1`, `P2`, `NONE`; store per point ID.
    - Represent tiebreak state distinctly (e.g., `isTiebreak=true`, `tbPointsP1/P2`). During tiebreak, map outcomes to numeric points with win‑by‑2 logic; after set ends, reset to regular game scoring.
    - Ignore serving order entirely in v0.1.0.
  - Decisions:
    - Support standard 7‑point (win‑by‑2) tiebreak at 6–6 in every set; record final set score as 7–6 for the set winner.
  - Decisions — Answers to previously open questions:
    - Match format config: Store match format (default best‑of‑3) in `score.json` (v1). No per‑match overrides in v0.1.0; may be added later.
    - No‑ad scoring: Not implemented in v0.1.0. Design the engine for future extension (a parameter to switch ad/no‑ad) in later versions.
    - Long deuce handling: Support extended deuce/advantage sequences with a practical cap of 100 points within a single game. Unit tests should include long sequences up to this cap.
- [moved] 4.9 — Persistence model for scoring (ScoreV1) (moved to v0.2.0/scoring.md on 2026-04-21)

  - Description: Define storage for outcomes and optional metadata separate from EDL.
  - Acceptance Criteria:
    - File: `score.json` (or embed under `manifest.score` if simpler short‑term; prefer separate file for clarity) with schema v1.
    - Records map `pointId -> outcome` plus optional audit fields (createdAt, updatedAt). Keep unknown fields on read if possible.
    - Autosave with ~300 ms debounce on changes; integrate with project open/close lifecycle.
  - Implementation Guide:
    - Kotlin data classes: `Outcome { P1, P2, NONE }`, `ScoreV1(version=1, player1Name: String = "Player 1", player2Name: String = "Player 2", outcomes: Map<PointId, Outcome>)`.
    - Reuse existing JSON IO patterns from `EdlIO`/`ManifestIO`.
  - Decisions:
    - When a previously scored point is deleted or its timing is edited on Markup, prompt the user: either keep subsequent scores as‑is (no recompute) or remove scoring for subsequent points and recompute from the change.
  - Decisions — Answers to previously open questions:
    - File location and naming: Use `score.json` alongside `edl.json` in the project root; no versioned filenames (no `score.v1.json`).
    - Schema shape: Keep it simple — `Map<PointId, Outcome>` only; no extra metadata for `NONE` in v0.1.0.
    - Orphans and migrations: Ignore for now — on load, silently ignore outcomes whose `pointId` no longer exists (no notice).
- [moved] 4.10 — Next Point navigation and hotkeys (moved to v0.2.0/scoring.md on 2026-04-21)

  - Description: Implement a compact "Next Point" button at the bottom of the list and a W hotkey that advances to the next point.
  - Acceptance Criteria:
    - Button and W hotkey select the next point in strict order (next index). On the last point, they do nothing; the button appears disabled and W is ignored.
    - After choosing an outcome, focus remains in the player area so Space/arrow keys continue to work.
    - Pressing W or the button selects the next point and keeps playback paused (no auto-start).
  - Implementation Guide:
    - Respect disabled states in UI; show the W hotkey hint on the button as in the mock.
  - Decisions:
    - No auto-advance anywhere; W follows strict next-index selection and does not skip scored points.
  - Open questions:
    - None at this time.
- [moved] 4.11 — Selecting a scored point restores pressed state (moved to v0.2.0/scoring.md on 2026-04-21)

  - Description: When the user selects a point that already has an outcome, the corresponding action button shows as pressed/selected.
  - Acceptance Criteria:
    - All three action areas (top row, bottom P1/P2 buttons) reflect the stored outcome consistently.
    - "No Point" selection leaves scoreboard and counters unchanged for that step but marks the point as scored.
  - Decisions — Answers to previously open questions:
    - Visual cohesion: Snap immediately to the stored outcome; no animations in v0.1.0 (keep it simple).
    - Conflicts/stale state: Show the last known local cache while async IO completes; update to persisted state once loaded. No special neutral/loading state in v0.1.0.
- [moved] 4.12 — Overlay scoreboard above the video (moved to v0.2.0/scoring.md on 2026-04-21)

  - Description: Render a compact scoreboard overlay (players, sets/games/points) positioned as in the mock (`design/scoring.html`).
  - Acceptance Criteria:
    - Initially shows 0–0; after scoring points, updates to reflect the state after the selected point.
    - Visual style reasonably similar to the mock; shows per‑set game counts and current game points (40/15 etc.).
    - Number of set columns is dynamic: render as many columns as there are completed sets plus the current set.
    - During a tiebreak, display the set as 7–6 for the winner once completed; the in‑progress tiebreak can be reflected as numeric points in the current game cell if practical.
  - Implementation Guide:
    - For v0.1.0, render a minimal layout consistent with the rest of the app’s UI stack; no animation required.
    - Player names come from user input (see 4.17) while colors remain fixed (match mock blue/red accents); ignore server indicator.
  - Decisions:
    - Set columns are dynamic (no fixed limit); rely on computed scoring to determine how many to display.
    - Player/league labels: use static placeholder strings in v0.1.0.
  - Open questions:
    - Overlay visibility toggle: Do we need a quick “show/hide overlay” toggle for preview (for v0.1.0)? If yes, where should it live? Answer: no, no need for such toggle.
- [moved] 4.13 — Autosave and project integration (moved to v0.2.0/scoring.md on 2026-04-21)

  - Description: Persist scoring outcomes shortly after changes and reload them on project open.
  - Acceptance Criteria:
    - Outcomes are saved immediately on change (no debounce). Manual Save All also saves scoring.
    - Switching projects unloads scoring state safely without leaks.
  - Implementation Guide:
    - Reuse `AutosaveScheduler` pattern used for Markup (2.8) and wiring in Projects flows.
  - Decisions — Answers to previously open questions:
    - Debounce window: Save immediately on change (no debounce). No visual saving indicator in v0.1.0 (follow Markup’s approach; out of scope).
    - Save scope: Save score independently. On the Scoring tab, only score data changes; save it separately from EDL/other data.
- [moved] 4.14 — Tests: rules engine and persistence (moved to v0.2.0/scoring.md on 2026-04-21)

  - Description: Add unit tests for the rules engine (points → games → sets) and for `ScoreV1` read/write.
  - Acceptance Criteria:
    - Rules engine: cover deuce/advantage sequences, game and set completion, and recomputation from a changed point.
    - Persistence: round‑trip JSON for `ScoreV1`; outcomes load and apply to UI state.
  - Implementation Guide:
    - Place tests under `src/test/kotlin/...`; mirror approach used by `EdlIOTest` and `MarkupDispatcherTest`.
  - Decisions — Answers to previously open questions:
    - Test coverage minima: Yes. Include extended 6–6 sets including a standard 7‑point (win‑by‑2) tiebreak, long deuce/advantage sequences up to the 100‑point practical cap, and a test that flips an early outcome and verifies correct recomputation from that point onward.
    - Golden files: Yes. Include a small golden `edl.json` + `score.json` pair to validate end‑to‑end load/apply behavior in tests.
- [moved] 4.15 — Accessibility and focus management (moved to v0.2.0/scoring.md on 2026-04-21)

  - Description: Ensure keyboard usage mirrors Markup behavior and controls are accessible.
  - Acceptance Criteria:
    - Space/arrow keys behave correctly when the player area is focused; speed dropdown uses ↑/↓ without breaking global seeks when focused elsewhere.
    - Buttons have accessible names/labels (match aria‑labels shown in the mock where applicable).
    - Time formatting mirrors Markup helpers; locale/language specifics are the same as Markup.
  - Decisions:
    - Screen reader specifics are out of scope for v0.1.0; provide reasonable component names only.
  - Decisions — Answers to previously open questions:
    - Keyboard focus map / tab order: No explicit tab order; follow Swing/toolkit defaults.
- [moved] 4.16 — Empty state and edge cases (moved to v0.2.0/scoring.md on 2026-04-21)

  - Description: Handle projects with no points and other edge cases.
  - Acceptance Criteria:
    - If there are no points, show an empty state with guidance to create points on the Markup tab.
    - If a selected point has zero duration due to malformed data, disable outcome actions and show an inline error until fixed (validation should prevent this in normal flow).
  - Decisions — Answers to previously open questions:
    - Empty state CTA: No. Show a placeholder on the points panel instructing to add points on the Markup tab first; no direct CTA button to switch tabs in v0.1.0.
    - Malformed data: Show an inline label on the affected segment indicating invalid duration and disable outcome actions until fixed in Markup; no “Fix in Markup” quick action.
    - Mixed edits while scoring: Not applicable in v0.1.0 — the edit flow is fully sequential; no live mixed-edit handling.

Appendix — Implementation details mirrored from design/scoring.html (for fidelity)

- Left list header with badges: Total and Scored counts (mock shows e.g., "14 Total" and "1 Scored").
- Active row style: highlighted background and left stripe; scored points show a small filled check icon.
- Next Point button with W hotkey hint appears at the bottom of the list.
- Video frame maintains aspect ratio (16:9). Scoreboard overlay sits at top‑left of the frame.
- Per‑point scrub bar directly under the video shows segment start on the left and current time on the right with a "Point segment" label.
- Top action row contains only a centered "No Point" button; Player 1/2 scoring buttons live in the side panels and are also available via hotkeys (A/L).
- Transport controls: rewind, play/pause (prominent), forward — same icons/positions as on Markup.
- Playback speed dropdown: presets 2×, 1× (default), 0.5×, 0.25×, 0.1×; always visible; small "↑/↓ speed" hint next to it (tooltip on small screens).
- Bottom panels (P1/P2): show POINTS value, a "Point for Player" button (hotkeys A/L), and two small blocks for GAMES and SETS each with a reflecting press state for Game Won / Set Won when rules dictate completion.
- Navigation: W advances to next point; selecting a scored point shows its outcome as pressed.



Update — 2026-04-15

- Manual Marker and Scoreboard Settings buttons are temporarily disabled in the Scoring tab UI until their functionality is implemented. This is intentional for v0.1.0.
- Scoreboard overlay in the Scoring tab is temporarily disabled for v0.1.0. A proper, reliable overlay will be implemented in v0.2.0.

- [moved] 4.17 — Player names inputs and dynamic labels (moved to v0.2.0/scoring.md on 2026-04-21)

  - Description: Add two text inputs under the left points list: "Player 1 name" and "Player 2 name" with default values "Player 1" and "Player 2". Persist names to `score.json` (ScoreV1) and use them to dynamically label scoring buttons and overlays on this tab.
  - Acceptance Criteria:
    - Two inputs are visible under the points list with labels "Player 1 name" and "Player 2 name".
    - Defaults are prefilled as "Player 1" and "Player 2" on empty projects; on load, inputs reflect values from `score.json` when present.
    - On change, names are saved to `score.json` (v1) with minimal debounce (~300 ms) consistent with scoring autosave.
    - The actions/buttons read "Point for <Player 1 name>" and "Point for <Player 2 name>" in the UI (including any on‑screen hints) and update immediately as the user types.
  - Implementation Guide:
    - Bind inputs to a small `PlayerNames` model within scoring state and serialize into `ScoreV1` alongside `outcomes`.
    - Enforce a practical max length (e.g., 24 chars) and trim whitespace; allow empty to fallback to defaults in UI.
    - Consider simple uppercase transformation only in overlay/labels if needed for style; keep original casing in inputs and persistence.
  - Decisions:
    - Hotkeys remain A/N/L; only button texts change. Tooltips and aria‑labels include the current player names.
    - Player accent colors are unchanged (fixed), only names are user‑configurable in v0.1.0.
