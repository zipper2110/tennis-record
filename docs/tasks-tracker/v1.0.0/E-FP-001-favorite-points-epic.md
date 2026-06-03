# E-FP-001: Favorite Points epic

- [x] Done

Date: 2026-06-03

Context
- Users need to mark highlight-worthy points in Markup and Scoring, then export a reel from only those points.
- Favorite state is point-level metadata and belongs in `edl.json` with the existing point IDs used by scoring.

Supported user workflows (in scope)
- Mark/unmark a completed point as favorite from Markup point cards.
- Mark/unmark a point as favorite from the Scoring points list.
- Use `A` to toggle the selected point's favorite state on Markup and Scoring tabs.
- See favorite count above the Markup, Scoring, and Export point lists/summaries.
- Export only favorite points when idle-trim is enabled.
- Include scoreboard on favorite-only exports with score computed from the full match, not only favorite points.

Out of scope (for this epic)
- Favoriting pending draft points before they are finalized.
- Filtering the visible point lists to favorites only.
- A separate favorites management tab.

Solution outline
- Add `favorite: Boolean = false` to `PointV1`; keep `EdlV1.version = 1` because missing legacy fields safely default to false.
- Thread favorite state through Markup DTOs/actions and Scoring point-list rendering.
- Persist favorite toggles by rewriting `edl.json`, preserving point IDs and score outcomes.
- Add Export checkbox `Only favorite points`, enabled only when idle-trim is on and at least one valid favorite point exists.
- Extend `ScoreboardTimelineBuilder` with an optional exported-point filter: compute score snapshots over all ordered points, then emit overlay spans only for exported points in compact output time.

Architecture compliance
- Domain persistence remains in `org.litvin.markup`.
- Scoreboard timeline logic remains UI-agnostic.
- Swing components consume state/actions and do not introduce new domain-to-UI dependencies.

Tasks

## T1: Persist favorite state in EDL
- [x] Done

Acceptance criteria
- `edl.json` round-trips `favorite` for each point.
- Legacy EDL files without `favorite` load with all points non-favorite.
- Existing ID repair behavior is unchanged.

Implementation guide
- Update `PointV1`.
- Extend `EdlIOTest` for favorite round-trip and legacy defaults.

Open questions
- None.

## T2: Add favorite commands to Markup state and persistence
- [x] Done

Acceptance criteria
- Markup can toggle favorite for completed points by ID.
- Pressing `A` toggles the selected completed point.
- Pending points cannot be favorited until finalized.
- Toggling favorite uses the existing EDL autosave path.

Implementation guide
- Add favorite fields to Markup DTOs and a `toggleFavorite(id)` action.
- Add dispatcher support and Markup keybinding support.

Open questions
- None.

## T3: Render favorite UI in Markup point cards
- [x] Done

Acceptance criteria
- Completed point cards show star favorite state.
- Each card includes a favorite button with `[A]` hint.
- Markup header displays total marked and favorite counts.

Implementation guide
- Update `PointsCardsView` and shared `UiStyles` icon helpers.

Open questions
- None.

## T4: Render and toggle favorites in Scoring points list
- [x] Done

Acceptance criteria
- Scoring rows show star favorite state and a favorite button.
- Pressing `A` toggles the selected scoring point.
- Header shows total, scored, and favorite counts.
- Favorite state persists across tab switches.

Implementation guide
- Extend `PointsListPanel`, `TimelineSection`, `LeftListPanel`, and `SwingScoringPanel` wiring.

Open questions
- None.

## T5: Export UI favorite filter
- [x] Done

Acceptance criteria
- Export tab includes `Only favorite points` with explanatory tooltip.
- Checkbox is enabled only when idle-trim is on and valid favorites exist.
- Turning idle-trim off clears/disables favorite-only export.
- Export summary shows favorite count next to point count.

Implementation guide
- Update Export summary, checkbox listeners, and initialize-button gating.

Open questions
- None.

## T6: Favorite-only render pipeline and scoreboard correctness
- [x] Done

Acceptance criteria
- Favorite-only export sends only favorite intervals to FFmpeg.
- Normal export behavior is unchanged when favorite-only is off.
- Scoreboard state for favorite clips includes non-favorite earlier outcomes.
- Overlay spans align to concatenated favorite-only output time.

Implementation guide
- Filter `keeps` at export initialization.
- Add exported-point filtering to `ScoreboardTimelineBuilder`.

Open questions
- None.

## T7: Tests and regression coverage
- [x] Done

Acceptance criteria
- EDL favorite persistence and legacy defaults are covered.
- Hotkey registration is covered for Markup and Scoring.
- Scoreboard favorite-only score carry-through is covered.

Implementation guide
- Extend `EdlIOTest`, `ScoreboardTimelineBuilderTest`, and focused UI hotkey tests.

Open questions
- None.

Traceability
- Commit/PR titles should include `E-FP-001`.
