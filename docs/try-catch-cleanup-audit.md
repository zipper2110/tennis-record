# Try-Catch Cleanup Audit

**Scope:** Every Kotlin file under `src/main/kotlin` and `src/test/kotlin` (148 files when this audit was created).

## Policy

Unnecessary `try`/`catch` wrapping is prohibited. It obscures failures, suppresses diagnostics, and makes ordinary programming errors harder to find. In particular, the following are bad code unless their need is explicitly documented and tested:

- empty or logging-free catches, especially `catch (_: Throwable)`;
- catch-all wrappers around ordinary property updates, UI state changes, or internal method calls;
- catches that silently substitute a default value or continue when the caller needs to know the operation failed;
- broad catches used to compensate for uncertain control flow or incomplete null/state checks.

`VideoSyncPanel.setFrameStepEnabled()` is the canonical example: wrapping the assignment to `frameStepCheckbox.isSelected` in a silent `Throwable` catch serves no defined recovery path and must be removed.

A catch is acceptable only when all of the following are true:

1. The operation crosses a real failure boundary (for example, file I/O, process management, native/media integration, or framework shutdown).
2. It catches the narrowest expected exception type—not `Throwable`—unless there is a documented, unavoidable platform boundary.
3. It either makes a specific, observable recovery or rethrows with useful context.
4. Its behavior is covered by a focused test when it changes user-visible or persisted behavior.

`try`/`finally` used solely for deterministic cleanup is outside this policy unless it also contains a catch.

## Review Procedure

For each tracker entry:

1. Inspect every `try`/`catch` in the file.
2. Mark each catch as **remove**, **narrow/rework**, or **justified** against the policy.
3. Make and test the smallest safe cleanup for entries needing change; retain narrowly justified boundary handling.
4. Record the outcome and evidence in **Findings / evidence**.
5. Move status through: `Cleanup first` → `Reviewing` → `Cleanup required`, `Changed`, `Clean`, or `Exception justified`.

Agents review one tracker entry at a time and report findings to the coordinator; the coordinator updates this document so concurrent reviews do not overwrite each other.

## Tracker

| Kotlin file | Status | Findings / evidence | |
| --- | --- | --- | --- |
| `src/main/kotlin/org/litvin/adjustments/CropGeometryMath.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/AdjustmentsIO.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/AdjustmentsStore.kt` | Cleanup required | Remove the redundant `saver.request()` catch at 112; narrow and surface persistence, transform, UI-dispatch, and listener failures, with focused failure tests. |
| `src/main/kotlin/org/litvin/AppInfo.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ApplicationLayout.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/AssOverlayWriter.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/AssPreviewScoreboardWriter.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/CompletedRendersStore.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/DebouncedSaver.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/DistributionDiagnostics.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/EdlIO.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/export/ExportFrameRates.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/export/ExportPlanner.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/export/RenderFormatting.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ExportPresets.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/FFmpegCapabilities.kt` | Cleanup required | Remove silent wrappers around process destruction/exit access; narrow process I/O handling, preserve interrupts, and add stream/interruption/launch-failure tests. |
| `src/main/kotlin/org/litvin/FFmpegCommandBuilder.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/GeometryViewportPanel.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/MarkupDispatcher.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/media/ColorPreviewAdjustmentStrategy.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/media/PlayerStatus.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/media/StillFrameCaptureService.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/media/VlcCropGeometry.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/media/VlcjStillFrameCaptureService.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/media/VlcjSwingMediaPlayerAdapter.kt` | Cleanup required | 21 `Throwable` catches: replace with specific VLCJ/native exceptions and observable boundary handling; remove silent EDT wrappers at 508–515. Add adapter seam tests for fallback and failure behavior. |
| `src/main/kotlin/org/litvin/media/VlcPreviewMediaOptions.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/Pagination.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ProgressParser.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/projects/ManifestIO.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/projects/ProjectManifestV1.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/projects/ProjectsRepository.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/projects/RecentsProvider.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/RenderQueue.kt` | Cleanup required | Remove redundant silent catch arms at 85–86, 241, 442, 457, 463, 475, 477, and 485; narrow real process/I/O boundaries and add focused RenderQueue failure tests. |
| `src/main/kotlin/org/litvin/RulesEngine.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ScoreboardStyle.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ScoreboardTimeline.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ScoreIO.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/scoring/ScoringEngine.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ScoringRules.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/SessionSettings.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/SwingMainApp.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/Timecode.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/commons/AppShortcuts.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/commons/AspectPanel.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/commons/Dialogs.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/commons/Html.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/commons/IconBrowserDialog.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/commons/PackageMarker.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/commons/ScrollHelper.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/commons/ScrubBar.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/commons/TransportBar.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/commons/UiSafe.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/help/HelpCatalog.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/help/HelpDialog.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/help/HelpPreferences.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/adjustments/AdjustmentsUiConverter.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/adjustments/SwingColorAdjustmentsPanel.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/crop/CropRotateCanvas.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/crop/CropTransformControls.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/crop/presenter/CropRotateContracts.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/crop/presenter/DefaultCropRotatePresenter.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/crop/SwingCropRotatePanel.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/export/CompletedRendersList.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/export/EncoderSummaryPanel.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/export/ExportSettingsPreferences.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/export/RenderQueueList.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/export/SwingExportPanel.kt` | Cleanup required | 14 `Throwable` catches need typed project-read handling that distinguishes missing optional data from unreadable/corrupt data; add focused fault-path tests. The sole try/finally is acceptable. |
| `src/main/kotlin/org/litvin/ui/tabs/markup/AutosaveController.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/markup/ContextMenuBuilder.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/markup/MarkupContracts.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/markup/SwingMarkupPanel.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/markup/ui/EditPointDialog.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/markup/ui/Keybindings.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/markup/ui/PointsCardsView.kt` | Cleanup required | Remove the four non-boundary wrappers at 59–62, 72–85, 92–95, and 100–112; narrow edit/delete/hover handling and add feedback/fallback tests. |
| `src/main/kotlin/org/litvin/ui/tabs/markup/ui/SwingTimelineComponent.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/markup/ui/TransportControls.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/projects/components/ProjectCard.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/projects/components/ProjectsEmptyListCard.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/projects/components/ProjectsHeader.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/projects/components/ProjectsPaginationBar.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/projects/presenter/DefaultProjectsPresenter.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/projects/presenter/ProjectsContracts.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/projects/SwingProjectsPanel.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/scoring/ScoringContracts.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/scoring/SwingScoringPanel.kt` | Cleanup required | Remove timer catches at 312 and 322; narrow and make observable the remaining media, preview, focus, and persistence boundaries, with focused failure-path tests. |
| `src/main/kotlin/org/litvin/ui/tabs/scoring/ui/ControlsToolbar.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/scoring/ui/LeftListPanel.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/scoring/ui/MatchHeaderPanel.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/scoring/ui/PlayerPanel.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/scoring/ui/PointsListPanel.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/scoring/ui/PreviewScoreboardRenderer.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/scoring/ui/ScoreEntryPanel.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/scoring/ui/ScrubPanel.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/scoring/ui/TimelineSection.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/tabs/scoring/ui/VideoSyncPanel.kt` | Cleanup required | Lines 108–111, 115–118, and 137–140: remove silent `catch (_: Throwable)` wrappers around initialized Swing control assignments. Add focused setter-state tests. |
| `src/main/kotlin/org/litvin/ui/tabs/test/SwingTestPanel.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/ui/UiStyles.kt` | Cleanup required | Remove seven broad styling/UI-manager wrappers; refactor the icon fallback to a narrow observable boundary and add focused fallback/style tests. |
| `src/main/kotlin/org/litvin/VlcBootstrap.kt` | Cleanup first | — |
| `src/main/kotlin/org/litvin/WindowsGpuPreference.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/adjustments/CropGeometryMathTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/AdjustmentsIOTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ApplicationLayoutResolverTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ArchitectureDependencyHygieneTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/AssPreviewScoreboardWriterTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/CompletedRendersStoreTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/EdlIOTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/export/ExportFrameRatesTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/export/ExportPlannerTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/FFmpegCapabilitiesTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/FFmpegCommandBuilderAdjustmentsScaleTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/FFmpegCommandBuilderAdjustmentsTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/FFmpegCommandBuilderOverlayNoTrimTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/FFmpegCommandBuilderOverlayTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/FFmpegCommandBuilderTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/GoldenLoadApplyTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/MarkupDispatcherTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/media/CalibratedVlcColorPreviewAdjustmentStrategyTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/media/VlcCropGeometryCalculatorTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/media/VlcPreviewMediaOptionsTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/OverlayAssWriterTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/OverlayProgressParsingWithFiltersTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/PaginationTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/RenderProgressParsingTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ScoreboardComponentTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ScoreboardTimelineBuilderTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ScoreIOTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/scoring/ScoringEngineTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ScoringRulesTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/SessionSettingsTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/TiebreakTimelineTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/TimecodeTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ui/commons/HtmlTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ui/help/HelpButtonCallbacksTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ui/help/HelpCatalogTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ui/help/HelpPanelTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ui/help/HelpPreferencesTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ui/tabs/adjustments/AdjustmentsUiConverterTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ui/tabs/crop/CropTransformControlsTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ui/tabs/export/ExportSettingsPreferencesTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ui/tabs/markup/MarkupKeybindingsTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ui/tabs/markup/ui/TransportControlsTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ui/tabs/projects/presenter/DefaultProjectsPresenterTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ui/tabs/scoring/SwingScoringPanelHotkeysTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ui/tabs/scoring/SwingScoringPanelSmokeTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ui/tabs/scoring/ui/LeftListPanelTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ui/tabs/scoring/ui/PointsListPanelTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ui/tabs/scoring/ui/PreviewScoreboardRendererTest.kt` | Cleanup first | — |
| `src/test/kotlin/org/litvin/ui/tabs/scoring/VideoSyncPanelTest.kt` | Cleanup first | — |
