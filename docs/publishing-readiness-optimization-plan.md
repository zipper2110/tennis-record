# Publishing Readiness Optimization Plan

Date: 2026-08-03

Source: `docs/publishing-readiness-audit.md`

## Execution Slice For This Pass

- [x] Task 1: Create the consolidated publishing-readiness audit.
  - Result: `docs/publishing-readiness-audit.md`.

- [x] Task 2: Extract export planning rules from `SwingExportPanel`.
  - Create a pure/testable export planner for EDL validation, export summary, filename/extension handling, output dimensions, scoreboard timeline selection, and `RenderJob` construction.
  - Update `SwingExportPanel` to delegate these rules to the planner while keeping dialogs and file chooser behavior in the Swing view.
  - Add focused tests for the extracted planner.
  - Result: Added `org.litvin.export.ExportPlanner`, `ExportRenderPlanRequest`, `ExportRenderPlan`, and `ExportPlannerTest`.

- [x] Task 3: Add direct `ScoringEngine` tests.
  - Cover deuce/advantage, tiebreak completion, `Outcome.NONE`, missing outcomes, recompute-after-edit, and best-of-3 terminal carry-forward.
  - Keep the tests independent of Swing/VLC.
  - Result: Added `ScoringEngineTest`.

- [x] Task 4: Remove duplicated transport controls from scoring UI.
  - Make `VideoSyncPanel` compose `ui.commons.TransportBar`.
  - Keep scoring-specific no-point, speed, and frame-step controls inside `VideoSyncPanel`.
  - Run/update existing `VideoSyncPanel` and scoring hotkey tests as needed.
  - Result: Reworked `VideoSyncPanel` to compose `TransportBar`; updated `VideoSyncPanelTest`.

- [x] Task 5: Extract duplicated export formatting helper.
  - Move byte-size formatting out of `SwingExportPanel`/`CompletedRendersList`.
  - Reuse one tested helper for render-progress and completed-render labels.
  - Result: Added `RenderFormatting`; `SwingExportPanel` and `CompletedRendersList` use it for active/completed render sizes.

## Follow-Up Presenter Migrations

- [ ] Task 6: Move Scoring workflow logic behind a presenter.
  - Introduce `ScoringPresenter`, intents, effects, and a fuller immutable state.
  - Move score/EDL persistence, favorite toggling, selection state, score timeline derivation, and preview overlay preparation out of `SwingScoringPanel`.

- [ ] Task 7: Move Markup workflow logic behind a presenter.
  - Use existing `MarkupActions`/`MarkupViewState` as the starting contract.
  - Move `MarkupDispatcher`, autosave, project loading, EDL refresh, and media-session commands out of `SwingMarkupPanel`.

- [ ] Task 8: Move Color Adjustments workflow logic behind a presenter.
  - Mirror the crop/rotate presenter pattern.
  - Move `AdjustmentsStore`, manifest/media loading, save-on-deactivate, and preview side effects out of `SwingColorAdjustmentsPanel`.

- [ ] Task 9: Move Export queue orchestration behind a presenter.
  - Follow the planner extraction with a passive export view contract.
  - Move queue observation, cancellation, completed-render actions, encoder capability loading, and output-file effects out of Swing components.

## Follow-Up Guardrails And Coverage

- [ ] Task 10: Add architecture guardrails for passive UI boundaries.
  - Forbid non-presenter UI packages from importing persistence/process/media services, with documented temporary allowlist entries for known legacy panels.

- [ ] Task 11: Add tests for process-heavy and stateful services after seams exist.
  - `RenderQueueManager`, `AdjustmentsStore`, project repository/manifest/recents IO, `AssOverlayWriter`, `ExportPresetsIO`, `DebouncedSaver`, `AutosaveController`, and `EdlIO` ID repair.

## Notes

The current execution slice deliberately focuses on extracted pure logic and duplicated UI components. It improves publishing readiness immediately while creating stepping stones for the larger passive-view migrations.

## Verification

- [x] Focused tests passed: `mvn "-Dtest=ExportPlannerTest,ScoringEngineTest,VideoSyncPanelTest" test`.
- [ ] Full `mvn test` is not clean yet.
  - Current unrelated failures: `VlcCropGeometryCalculatorTest.centeredZoomUsesSourceAspectCrop` and `VlcCropGeometryCalculatorTest.panMapsToCropTravelWithoutChangingSize`.
  - Observed actual crop sizes are wider/taller than expected; these failures are in media geometry behavior outside this execution slice.
