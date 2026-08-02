# Publishing Readiness Audit

Date: 2026-08-03

Scope:
- UI/form separation from logic.
- Logic-component test coverage.
- UI uniformity, duplication, style constants.
- Basic clean-code responsibilities, names, and class cognitive load.

Status: Initial audit complete. This file is intentionally findings-only; implementation work is tracked in `docs/publishing-readiness-optimization-plan.md`.

## Summary

The codebase already has strong architecture guidance in `docs/architecture-rules.md`, including Passive View/MVP expectations for Swing UI. The Projects and Crop/Rotate tabs are the best current examples of that direction. The highest publishing risk is that Scoring, Markup, Export, and Color Adjustments still mix Swing rendering with workflow state, IO, media control, persistence, and domain calculations.

The test suite is strongest where logic has already been extracted into pure services or presenters. Coverage is weakest where logic remains inside large Swing containers or process-heavy singletons.

## High Findings

1. `src/main/kotlin/org/litvin/ui/tabs/scoring/SwingScoringPanel.kt` is still a workflow/business container rather than a passive form.

   Evidence:
   - Imports and owns manifest IO, EDL IO, score IO, adjustment store, VLCJ media player, scoring engine, scoreboard timeline building, and preview overlay rendering at lines 3-25.
   - Loads project, EDL, score, player names, colors, and media in `setProjectManifest` at lines 221-261.
   - Reloads EDL on activation in `refreshPointsFromProject` at lines 265-290.
   - Writes favorites back to EDL in `toggleFavorite` at lines 741-754.
   - Writes `score.json` in `saveNow` at lines 825-837.
   - Builds scoreboard preview overlay in `refreshVideoScoreboardOverlay` at lines 847-871.
   - Computes scoring timeline/display state in `updateScore` and `updateBottomPanels` at lines 874-919.

   Impact:
   - The scoring form is hard to test without Swing/VLC.
   - Domain and persistence behavior can regress while smoke tests still pass.
   - The class is also over the 500-line guideline.

2. `src/main/kotlin/org/litvin/ui/tabs/export/SwingExportPanel.kt` owns export orchestration and repeated project reads.

   Evidence:
   - Broad import from `org.litvin.*` plus manifest, EDL, and score IO at lines 2-9.
   - Cancels/observes the global queue directly at lines 194 and 331.
   - `onInitializeRender` reads manifest/EDL/score, validates EDL, chooses output path, builds overlay timeline, constructs `RenderJob`, and enqueues it at lines 419-514.
   - Export rules like `validateEdl`, `suggestFilename`, `parseDims`, `ensureExtension`, `formatSize`, `hasAnyMarkedPoints`, `validFavoriteCount`, `hasAnyScoredPoints`, `updatePointsSummary`, and `updateInitButtonState` live privately in the Swing panel at lines 517-747.

   Impact:
   - Important export rules are not independently testable.
   - The UI performs IO repeatedly while updating labels and button state.
   - The class is over the 500-line guideline.

3. `src/main/kotlin/org/litvin/ui/tabs/markup/SwingMarkupPanel.kt` mixes form rendering with EDL persistence, media control, and markup workflow state.

   Evidence:
   - Imports manifest IO, adjustment storage, EDL IO, dispatcher, and VLCJ at lines 3-19.
   - Owns media player and dispatcher at lines 44 and 52.
   - Performs debounced EDL writes inside the panel at lines 181-192.
   - Loads project manifest, EDL, media, and adjustments in `setProjectManifest` at lines 452-480.
   - Refreshes EDL from disk in `refreshPointsFromProject` at lines 495-520.

   Impact:
   - Existing `MarkupActions` and `MarkupViewState` are good starts, but the panel still owns the logic behind them.
   - Behavior is difficult to test outside Swing/VLC.
   - The class is over the 500-line guideline.

4. `src/main/kotlin/org/litvin/ui/tabs/adjustments/SwingColorAdjustmentsPanel.kt` directly handles persistence, preview media, preferences, and model merging.

   Evidence:
   - Imports manifest IO, VLCJ media adapter, adjustment store, `File`, and `Preferences` at lines 3-18.
   - Slider listeners write to `AdjustmentsStore` at lines 225-232.
   - Loads project adjustment/media state in `setProjectManifest` at lines 420-439.
   - Saves current adjustments on deactivation at lines 454-460.
   - Owns model merge behavior in `mergeColorInto` at lines 463-470.

   Impact:
   - The conversion helper is pure and tested, but the panel remains responsible for store IO and preview side effects.
   - This should follow the Crop/Rotate presenter pattern.

5. Logic-heavy UI has uneven tests.

   Evidence:
   - `ScoringEngine.computeTimeline` is used by `SwingScoringPanel` at line 876, but there is no direct `ScoringEngineTest`.
   - Export rules are private in `SwingExportPanel` and have no focused tests.
   - Scoring Swing tests are mainly smoke/hotkey tests and may skip when VLC is unavailable.

   Impact:
   - High-risk behavior can only be verified through large UI objects.

## Medium Findings

1. `src/main/kotlin/org/litvin/RenderQueue.kt` has no direct test class despite owning queue state, cancellation, FFmpeg process launch, progress parsing, `.part` cleanup/final move, failure reasons, and completed-render persistence.

   Remediation idea:
   - Add seams for process runner/layout/filesystem, then test queue snapshots, cancel queued, missing source failure, process success, nonzero exits, cancellation cleanup, and progress updates.

2. Export leaf components still call storage, process, and queue APIs directly.

   Evidence:
   - `CompletedRendersList.kt` loads and clears `CompletedRendersStore`, and opens folders via `Desktop`/`Runtime.exec`.
   - `RenderQueueList.kt` cancels queued jobs through `RenderQueueManager`.

   Remediation idea:
   - Pass callbacks/effects from the export workflow instead of letting leaf components call process/storage APIs.

3. `EncoderSummaryPanel` runs capability detection from component construction.

   Evidence:
   - `EncoderSummaryPanel.kt` calls `FFmpegCapabilities.h264Encoders()` while populating Swing UI.

   Remediation idea:
   - Move detection behind an injected capability service/presenter and render an encoder state.

4. Style constants exist, but scoring UI bypasses them heavily.

   Evidence:
   - `UiStyles.kt` defines palette constants at lines 104-130.
   - Repeated literals appear in scoring panels, especially `Color(0xAD, 0xAA, 0xAA)`, `Color(0x26, 0x26, 0x26)`, `Color.WHITE`, and repeated spacing/border dimensions.

   Remediation idea:
   - Add named style tokens/helpers for scoring surfaces, borders, badges, fixed spacing, text colors, and hex color parsing.

5. Transport controls are duplicated.

   Evidence:
   - `ui/commons/TransportBar.kt` and `ui/tabs/scoring/ui/VideoSyncPanel.kt` both define seek/play button sizing, labels, icons, and layout.

   Remediation idea:
   - Make `VideoSyncPanel` compose `TransportBar` and only add scoring-specific speed/frame-step/no-point controls.

6. Several files exceed the documented 500-line guideline.

   Evidence:
   - `SwingScoringPanel.kt`: 823+ lines in current checkout.
   - `SwingExportPanel.kt`: 682+ lines in current checkout.
   - `SwingMarkupPanel.kt`: 575+ lines in current checkout.
   - `UiStyles.kt`: 551+ lines in current checkout.

   Remediation idea:
   - Split by presenters, workflow services, pure state builders, and smaller style modules.

7. Some pure/domain IO pieces need stronger direct tests.

   Examples:
   - `AdjustmentsStore`: clamping, subscriber behavior, load/save fallback.
   - `ProjectsRepository`/`ManifestIO`/`RecentsProvider`: real filesystem behavior.
   - `AssOverlayWriter`: existing test computes a hash but does not assert it.
   - `ExportPresetsIO`: fallback/default selection behavior.
   - `DebouncedSaver` and `AutosaveController`: debounce/flush/pending behavior.
   - `EdlIO`: ID repair for blank/duplicate IDs.

## Low Findings

1. Small utilities are duplicated.

   Evidence:
   - Hex color parsing appears in scoring list/player components.
   - `formatSize` exists in both `SwingExportPanel` and `CompletedRendersList`.

   Remediation idea:
   - Extract small shared helpers to `ui.commons` or feature-level domain helpers depending on whether the helper is visual or domain formatting.

## Positive Patterns To Reuse

1. Projects tab MVP pattern:
   - `SwingProjectsPanel` forwards intents and renders `ProjectsViewState`.
   - `DefaultProjectsPresenter` owns repository calls, preferences, IO executor, state, and effects.
   - `DefaultProjectsPresenterTest` uses a fake repository and recording view.

2. Crop/Rotate MVP pattern:
   - `SwingCropRotatePanel` and `DefaultCropRotatePresenter` are separated by contracts.
   - Presenter owns adjustment persistence, manifest loading, frame capture, lifecycle, and state rendering.

3. Pure helper/test pattern:
   - `AdjustmentsUiConverter` is small, deterministic, and already tested.
   - `CropGeometryMathTest`, `VlcPreviewMediaOptionsTest`, `CalibratedVlcColorPreviewAdjustmentStrategyTest`, `PaginationTest`, `FFmpegCommandBuilder*Test`, and `ScoreboardTimelineBuilderTest` are useful templates for future extraction.

## Common Themes

- The repo has the right architecture direction, but the migration is uneven.
- Logic is easiest to test where it is in presenters, pure helpers, or services.
- Large Swing panels still contain the riskiest publishing behavior.
- UI style constants exist but are not consistently consumed.
- Some duplicated helpers are small enough to fix quickly while larger MVP migrations should be staged.
