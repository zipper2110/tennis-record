# E-SC-001: SwingScoringPanel componentization epic

- [x] Done

Date: 2026-05-19 21:05 (local)

Context
- `src\main\kotlin\org\litvin\ui\tabs\scoring\SwingScoringPanel.kt` is ~1465 lines (see T-FS-001). It mixes layouting, event wiring, rendering, and logic for multiple UI subdomains in the Scoring tab.
- Goal: decompose into cohesive Swing components with clear responsibilities and APIs, aligned with architecture rules.

Supported user workflows (in scope)
- View live score, set, and match status within Scoring tab.
- Enter/adjust points and game/set winners.
- Navigate and review timeline/points list.
- Toggle overlays/indicators related to scoring (serve order, tiebreak, challenges, etc.).
- Video/timecode alignment controls needed during scoring (basic sync controls presently embedded in panel).
- Keyboard shortcuts for scoring operations.

Out of scope (for this epic)
- Markup tab functionality (handled by its own panel).
- Export and Projects tabs.
- Non-scoring analytics beyond what’s already present in Scoring.

Solution outline
- Extract leaf Swing components with dedicated purpose and minimal public API. Example slices:
  - `MatchHeaderPanel` — match title/players, current set/game, serve indicator.
  - `ScoreEntryPanel` — buttons/inputs for entering points, undo/redo indicators.
  - `ControlsToolbar` — toolbar of actions (reset, save, settings) specific to scoring.
  - `TimelineSection` — integrates existing `PointsListPanel` for listing/navigating points.
  - `HotkeysHelpPanel` — compact legend of scoring hotkeys.
  - `VideoSyncPanel` — small cluster for timecode/video sync relevant to scoring.
- Define narrow interfaces for data flow and commands:
  - View-to-domain: `ScoringActions` (e.g., `pointWon(side)`, `undo()`, `redo()`, `toggleServe()`...).
  - Domain-to-view: `ScoringViewState` (immutable snapshot) consumed by components.
- Wiring rules:
  - `SwingScoringPanel` becomes a thin container that composes components and binds them to existing domain services (e.g., `RulesEngine`, `ScoreboardTimeline`).
  - No cross-component coupling; communicate via container and the shared state/actions interfaces.
  - Follow architecture rules in `docs/architecture-rules.md` for dependency direction and package layout.
- Performance/UX constraints
  - UI updates should remain responsive at 60 FPS target for repaint-intensive areas.
  - Avoid blocking EDT; long ops off-EDT.
  - Preserve existing keyboard shortcuts and mouse interactions.

Architecture compliance
- Keep UI components under `org.litvin.ui.tabs.scoring.*` subpackages (e.g., `scoring.header`, `scoring.entry`, ...).
- Container (`SwingScoringPanel`) may depend on domain modules (`RulesEngine`, `ScoreboardTimeline`), leaves depend only on state/action interfaces defined next to container.

Major invariants
- Component APIs do not leak domain types unnecessarily; prefer simple DTOs/state snapshots.
- Event handling stays deterministic; no duplicate event dispatch on refactor.
- Functional parity with current `SwingScoringPanel`.

Tasks

## T1: Define component map and contracts
- [x] Done

Acceptance criteria
- A concise component map documented in KDoc near `SwingScoringPanel` and in this epic.
- Kotlin interfaces `ScoringActions` and `ScoringViewState` (or equivalent) drafted and referenced by all new components.
- No functional change yet; compile passes.

Implementation guide
- Sketch interface methods based on current UI controls in `SwingScoringPanel.kt`.
- Identify which parts already exist (e.g., `PointsListPanel`) and integrate via `TimelineSection` wrapper later.

Component map (draft)
- `org.litvin.ui.tabs.scoring.header.MatchHeaderPanel` — match title/players, current set/game, serve indicator.
- `org.litvin.ui.tabs.scoring.entry.ScoreEntryPanel` — primary scoring inputs (points, undo/redo).
- `org.litvin.ui.tabs.scoring.toolbar.ControlsToolbar` — top-level actions (reset/save/settings).
- `org.litvin.ui.tabs.scoring.timeline.TimelineSection` — wraps existing `PointsListPanel`.
- `org.litvin.ui.tabs.scoring.help.HotkeysHelpPanel` — compact legend of scoring hotkeys.
- `org.litvin.ui.tabs.scoring.video.VideoSyncPanel` — basic video/timecode sync controls for scoring.

Contracts
- `org.litvin.ui.tabs.scoring.ScoringActions` — view-to-domain/container commands (pointWon, undo/redo, toggleServe, navigate, play/seek, save).
- `org.litvin.ui.tabs.scoring.ScoringViewState` — immutable snapshot consumed by leaves (names, serving, points/games/sets, selection, player status).

Open questions
- Should `ScoringViewState` be a single snapshot or split per component? (default: single snapshot with sub-views)

## T2: Extract MatchHeaderPanel
- [x] Done

Acceptance criteria
- A new `MatchHeaderPanel` renders player names, set/game score, and serve indicator.
- Receives only a read-only slice of `ScoringViewState`.
- No domain service references inside.

Implementation guide
- Move header rendering code and related small helpers.
- Ensure text truncation/tooltip parity if any existed.

Open questions
- Confirm exact fields for serve indicator (boolean + side vs derived).

## T3: Extract ControlsToolbar
- [x] Done

Acceptance criteria
- Toolbar hosts top-level scoring actions (e.g., reset, settings, save/export shortcuts if present on Scoring tab).
- Emits callbacks via `ScoringActions` only.

Implementation guide
- Port button creation and action listeners.
- Keep icons and style references unchanged.

Open questions
- Which actions belong here vs in `ScoreEntryPanel`?

## T4: Extract ScoreEntryPanel
- [x] Done

Acceptance criteria
- Contains primary scoring inputs (point won left/right, game/set finalize if applicable, undo/redo).
- Hotkeys kept functional and documented in component KDoc.

Implementation guide
- Move action wiring; delegate to `ScoringActions`.
- Keep layout minimal to simplify further iterations.

Open questions
- Need dedicated `ScoreEntryModel` or reuse generic `ScoringViewState` slice?

## T5: Integrate PointsListPanel as TimelineSection
- [x] Done

Acceptance criteria
- `TimelineSection` wraps existing `PointsListPanel` for list and navigation.
- State and actions passed via the same contracts; no direct domain references from the section itself.

Implementation guide
- Introduce thin adapter if `PointsListPanel` API differs; avoid modifying `PointsListPanel` unless necessary.

Open questions
- Do we need virtualized rendering for very long matches? (probably later)

## T6: Extract HotkeysHelpPanel
- [x] Done

Acceptance criteria
- Displays current scoring hotkeys in a compact view.
- Stays in sync if hotkeys are configurable.

Implementation guide
- Populate from a static map initially; consider DI later if hotkeys are dynamic.

Open questions
- Are hotkeys centralized anywhere? If not, add TODO to centralize.

## T7: Extract VideoSyncPanel (scoring scope)
- [x] Done

Acceptance criteria
- Provides basic video/timecode sync controls that were embedded in scoring.
- Uses actions interface; no media service coupling inside the leaf.

Implementation guide
- Move control cluster; keep look and feel.

Open questions
- Confirm minimal set of controls needed on Scoring vs full video tab.

## T8: Make SwingScoringPanel a thin container
- [x] Done

Acceptance criteria
- `SwingScoringPanel` composes the extracted components and binds them to domain services.
- All direct UI logic left in `SwingScoringPanel` is limited to composition and binding.
- File size target ≤ 400–500 lines.

Implementation guide
- Create subpackages; instantiate components; pass `ScoringActions` impl and `ScoringViewState` supplier/updates.
- Keep existing public API of `SwingScoringPanel` stable for external callers.

Open questions
- Choose update propagation: observer pattern vs explicit `render(state)` calls.

## T9: Smoke tests for composition and hotkeys
- [x] Done

Acceptance criteria
- UI composition smoke test(s) ensure components can be instantiated and basic actions can be invoked without NPEs.
- Hotkey mappings preserved; at least one test verifies a representative shortcut path.

Implementation guide
- Use existing test infra patterns in `src/test/.../ui`.

Open questions
- Do we need headless checks for CI environments?

## T10: Styles/assets migration
- [x] Done

Acceptance criteria
- Existing styles and icons continue to render correctly after extraction.
- No duplicated resources.

Implementation guide
- Keep resource paths stable; refactor constants if they lived in `SwingScoringPanel`.

Open questions
- Are there any runtime-loaded assets tied to the old layout?

## T11: Documentation and checklist
- [x] Done

Acceptance criteria
- KDoc at the top of each new component explains its purpose and API.
- This epic is updated with links to new classes and final notes.
- Cross-reference added from `T-FS-001` to this epic for the scoring item.

Links to implemented classes
- src/main/kotlin/org/litvin/ui/tabs/scoring/ScoringContracts.kt
- src/main/kotlin/org/litvin/ui/tabs/scoring/header/MatchHeaderPanel.kt
- src/main/kotlin/org/litvin/ui/tabs/scoring/toolbar/ControlsToolbar.kt
- src/main/kotlin/org/litvin/ui/tabs/scoring/entry/ScoreEntryPanel.kt
- src/main/kotlin/org/litvin/ui/tabs/scoring/timeline/TimelineSection.kt
- src/main/kotlin/org/litvin/ui/tabs/scoring/help/HotkeysHelpPanel.kt
- src/main/kotlin/org/litvin/ui/tabs/scoring/video/VideoSyncPanel.kt

Final notes
- Leaf components depend only on `ScoringActions` and `ScoringViewState` DTOs.
- `SwingScoringPanel` remains the container responsible for composition and binding to domain/services.
- KDoc has been added at the top of each new component to describe purpose and API surface.

Implementation guide
- Add file-level KDoc and update `docs/tasks-tracker/v0.3.0/T-FS-001-file-size-audit-2026-05-19.md` with a short note referencing this epic when T8 completes.

Open questions
- None.

Open topics/questions (cross-cutting)
- Event dispatch model consistency across components.
- Whether to introduce a lightweight view-model layer for Scoring to decouple from `RulesEngine`.

Traceability
- Related audit: `T-FS-001-file-size-audit-2026-05-19.md` (Scoring item).
- Commit/PRs should include task IDs (T1…T11) in messages.
