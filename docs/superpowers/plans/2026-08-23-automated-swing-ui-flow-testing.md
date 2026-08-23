# Automated Swing UI Flow Testing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish deterministic, locally runnable Swing user-flow tests with actionable failure artifacts, plus an on-demand packaged Windows smoke workflow that exercises real VLC and FFmpeg.

**Architecture:** Keep fast tests in Surefire, run real-window Swing flows sequentially through a project-owned driver and JUnit 5 extension in Failsafe, and isolate external effects through `AppServices`. Production startup composes real services; flow tests compose scripted pickers/dialogs, fake media/rendering, isolated preferences, tracked executors, and per-test app-data roots. A separate PowerShell workflow launches the packaged application against the same checked-in media fixture for native smoke coverage.

**Tech Stack:** Kotlin/JVM 17, Swing/FlatLaf, Maven Surefire and Failsafe, JUnit Jupiter 5.10.0, AssertJ Swing 3.17.1, AssertJ Core 3.27.7, GitHub Actions Ubuntu/Xvfb, PowerShell 7, VLCJ, FFmpeg, Codex Computer Use.

**Spec:** [2026-08-23-automated-swing-ui-flow-testing-design.md](../specs/2026-08-23-automated-swing-ui-flow-testing-design.md)

## Global Constraints

- Keep `mvn test` as the fast-test command; `*UiFlowIT` tests must run only in the `ui-flow` profile.
- The canonical deterministic UI command is `xvfb-run -a mvn -B -Pui-flow verify` on Linux and `mvn -B -Pui-flow verify` on Windows.
- Run Robot-driven tests sequentially in one reusable fork. Do not enable test parallelism.
- Scenario classes may use only screen objects and fixture APIs. Direct AssertJ Swing calls belong in `AssertJSwingDriver` and the compatibility spike.
- Locate production components by semantic Swing `name`, never coordinates, child indices, or layout hierarchy. Use displayed text only when text itself is under assertion.
- Synchronize on visible state, persistence, or recorded fake calls with bounded timeouts. Do not use arbitrary sleeps, pass-producing retries, or automatic retry extensions.
- The deterministic suite must not initialize VLC, run FFmpeg, open a native chooser, or touch developer preferences or app data.
- Every application-owned timer, media player, observer, autosave job, and executor must have an explicit owner and deterministic close path.
- Preserve the user's unrelated `.worktrees/` directory and any other unrelated working-tree changes.

---

## Task 1: Restore a Green, Terminating Fast-Test Baseline

**Files:**

- Modify: `src/test/kotlin/org/litvin/media/VlcCropGeometryCalculatorTest.kt`
- Modify: `src/main/kotlin/org/litvin/media/VlcCropGeometry.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/scoring/SwingScoringPanel.kt`
- Modify: `src/test/kotlin/org/litvin/ui/tabs/scoring/SwingScoringPanelSmokeTest.kt`
- Modify: `src/test/kotlin/org/litvin/ui/tabs/scoring/SwingScoringPanelHotkeysTest.kt`

- [ ] Add a regression assertion to `VlcCropGeometryCalculatorTest` proving that crop dimensions do not include the crop origin. For a 1920x1080 source at 1.5x zoom, assert `cropWidth == 1280` and `cropHeight == 720` for both centered and panned geometry.

- [ ] Run the focused test and confirm the existing red state:

  ```powershell
  mvn -B -Dtest=VlcCropGeometryCalculatorTest test
  ```

  Expected: two assertions fail because the calculator returns bottom-right coordinates as width and height.

- [ ] Fix `VlcCropGeometryCalculator.calculate` so the return value uses dimensions and origin in the constructor's declared order:

  ```kotlin
  return VlcCropGeometry(
      cropWidth = width,
      cropHeight = height,
      cropX = pannedMinX,
      cropY = pannedMinY,
  )
  ```

- [ ] Run the focused test again and require zero failures.

- [ ] Add an idempotent `dispose()` method to `SwingScoringPanel` that stops `namesSaveTimer`, deactivates the screen, clears player callbacks, clears the preview overlay, and calls `player.dispose()`.

- [ ] Change both scoring panel tests to wrap every successfully constructed panel in `try/finally` and invoke `dispose()` on the EDT in the `finally` block. Keep the existing assumption behavior for machines without VLC, but never leave a constructed native adapter undisposed.

- [ ] Run both scoring tests five times in the same Maven invocation and verify the fork exits:

  ```powershell
  1..5 | ForEach-Object {
      mvn -B -Dtest=SwingScoringPanelSmokeTest,SwingScoringPanelHotkeysTest test
      if ($LASTEXITCODE -ne 0) { throw "Scoring lifecycle run $_ failed" }
  }
  ```

- [ ] Run `mvn -B test`. Require a zero exit code and verify no child Java process remains after Maven returns:

  ```powershell
  mvn -B test
  if ($LASTEXITCODE -ne 0) { throw "Fast suite failed" }
  Get-CimInstance Win32_Process |
      Where-Object { $_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -match 'surefire|tennisrecord' }
  ```

  Expected: Maven reports all tests passing and the process query returns no rows.

- [ ] Commit the baseline separately:

  ```powershell
  git add src/main/kotlin/org/litvin/media/VlcCropGeometry.kt src/test/kotlin/org/litvin/media/VlcCropGeometryCalculatorTest.kt src/main/kotlin/org/litvin/ui/tabs/scoring/SwingScoringPanel.kt src/test/kotlin/org/litvin/ui/tabs/scoring/SwingScoringPanelSmokeTest.kt src/test/kotlin/org/litvin/ui/tabs/scoring/SwingScoringPanelHotkeysTest.kt
  git commit -m "fix: restore terminating fast test baseline"
  ```

## Task 2: Create the Maven UI-Test Lane

**Files:**

- Modify: `pom.xml`
- Create: `src/test/kotlin/org/litvin/ui/flow/UiFlowProfileGuardTest.kt`

- [ ] Add a fast guard test that asserts the system property `tennis.record.uiFlow` is absent during Surefire. This catches accidental activation of the UI harness in `mvn test`.

- [ ] Run `mvn -B -Dtest=UiFlowProfileGuardTest test` and require it to pass before changing Maven configuration.

- [ ] Add explicit test dependencies:

  ```xml
  <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-swing</artifactId>
      <version>3.17.1</version>
      <scope>test</scope>
  </dependency>
  <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <version>3.27.7</version>
      <scope>test</scope>
  </dependency>
  ```

- [ ] Add a `ui-flow` Maven profile. Configure Failsafe with `integration-test` and `verify` goals, `**/*UiFlowIT.*` includes, `forkCount=1`, `reuseForks=true`, `parallel=none`, a three-minute fork timeout, and `tennis.record.uiFlow=true`. Leave Surefire unchanged so `mvn test` remains fast.

- [ ] Verify dependency mediation selects Core 3.27.7:

  ```powershell
  mvn -B -Pui-flow dependency:tree -Dincludes=org.assertj
  ```

  Expected: `assertj-swing:3.17.1` and a single selected `assertj-core:3.27.7` appear.

- [ ] Verify lane separation:

  ```powershell
  mvn -B test
  mvn -B -Pui-flow -DskipTests verify
  ```

  Expected: fast tests pass; the profile configures Failsafe without running UI tests because tests were explicitly skipped.

- [ ] Commit:

  ```powershell
  git add pom.xml src/test/kotlin/org/litvin/ui/flow/UiFlowProfileGuardTest.kt
  git commit -m "test: add isolated Swing UI flow profile"
  ```

## Task 3: Complete the AssertJ Swing Compatibility Spike

**Files:**

- Create: `src/test/kotlin/org/litvin/ui/flow/driver/SwingUiDriver.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/driver/AssertJSwingDriver.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/harness/SwingTestDiagnostics.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/spike/SpikeWindow.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/spike/AssertJSwingCompatibilityUiFlowIT.kt`

- [ ] Define the project-owned driver contract around semantic actions and observable state:

  ```kotlin
  interface SwingUiDriver : AutoCloseable {
      fun click(name: String)
      fun setText(name: String, value: String)
      fun setSlider(name: String, value: Int)
      fun select(name: String, value: String)
      fun press(keyStroke: KeyStroke)
      fun requireShowing(name: String)
      fun requireEnabled(name: String, enabled: Boolean)
      fun requireText(name: String, expected: String)
      fun dismissDialog(title: String, buttonText: String)
      fun waitUntil(description: String, timeout: Duration = Duration.ofSeconds(5), condition: () -> Boolean)
  }
  ```

- [ ] Implement `AssertJSwingDriver` with `BasicRobot.robotWithNewAwtHierarchy()`, AssertJ fixtures, and AssertJ's pause/condition mechanism only for bounded polling. Keep all component matchers and AssertJ imports in this file.

- [ ] Implement `SwingTestDiagnostics.capture(frame, failure, artifactDir)` to write `failure.txt`, `component-tree.txt`, `windows.txt`, and one PNG per showing `Window`. Include component class, `name`, visible/showing/enabled state, and text where the component exposes text.

- [ ] Build `SpikeWindow` with FlatDarkLaf, a named text field, named button, keyboard action, result label, and a modal `JOptionPane`. The button must copy the field value into the result label before opening the modal.

- [ ] Write `AssertJSwingCompatibilityUiFlowIT` to prove semantic lookup, typing, clicking, a keyboard shortcut, modal discovery/dismissal, and disposal. Catch one deliberate assertion error inside the test, capture diagnostics for it, assert that the expected artifact files exist and are non-empty, then allow the overall test to pass.

- [ ] Run the spike once on Windows:

  ```powershell
  mvn -B -Pui-flow -Dit.test=AssertJSwingCompatibilityUiFlowIT verify
  ```

  Expected: one Failsafe integration test passes and the JVM exits without leaked non-daemon threads.

- [ ] Run twenty clean repetitions without retrying an individual failure:

  ```powershell
  1..20 | ForEach-Object {
      mvn -B -Pui-flow -Dit.test=AssertJSwingCompatibilityUiFlowIT verify
      if ($LASTEXITCODE -ne 0) { throw "AssertJ compatibility spike failed on run $_" }
  }
  ```

- [ ] Run the same retained test under Ubuntu Xvfb, either in a Linux environment or the CI job from Task 15:

  ```bash
  xvfb-run -a mvn -B -Pui-flow -Dit.test=AssertJSwingCompatibilityUiFlowIT verify
  ```

- [ ] Apply the decision gate. Continue with `AssertJSwingDriver` only if every spike criterion passes. If any critical criterion fails, preserve `SwingUiDriver`, diagnostics, and the spike scenario; replace `AssertJSwingDriver` with `RobotSwingDriver` implemented using `java.awt.Robot`, and rerun the same criteria before continuing.

- [ ] Commit the accepted driver:

  ```powershell
  git add src/test/kotlin/org/litvin/ui/flow
  git commit -m "test: prove Swing UI driver compatibility"
  ```

## Task 4: Add Isolated App Data and Preferences

**Files:**

- Modify: `src/main/kotlin/org/litvin/ApplicationLayout.kt`
- Modify: `src/test/kotlin/org/litvin/ApplicationLayoutResolverTest.kt`
- Create: `src/main/kotlin/org/litvin/app/AppDataPaths.kt`
- Create: `src/main/kotlin/org/litvin/app/PreferencesProvider.kt`
- Modify: `src/main/kotlin/org/litvin/projects/ProjectsRepository.kt`
- Modify: `src/main/kotlin/org/litvin/projects/RecentsProvider.kt`
- Modify: `src/test/kotlin/org/litvin/ui/tabs/projects/presenter/DefaultProjectsPresenterTest.kt`

- [ ] Add resolver tests proving precedence for app data: system property `tennis.record.appDataDir`, then environment variable `TENNIS_RECORD_APP_DATA_DIR`, then `%APPDATA%/tennis-record`, then the user-home fallback.

- [ ] Run `mvn -B -Dtest=ApplicationLayoutResolverTest test` and confirm the environment-override case fails.

- [ ] Update `resolveAppDataDirectory()` to honor `TENNIS_RECORD_APP_DATA_DIR` immediately after the existing system-property override.

- [ ] Add immutable paths rooted at one directory:

  ```kotlin
  data class AppDataPaths(val root: File) {
      val projects: File = root.resolve("projects")
      val completedRenders: File = root.resolve("completed-renders.json")
      val logs: File = root.resolve("logs")
      val temporary: File = root.resolve("tmp")

      companion object {
          fun production(): AppDataPaths = AppDataPaths(ApplicationLayout.current().appDataDirectory)
      }
  }
  ```

- [ ] Change `FileProjectsRepository` to accept its projects root in the constructor and make recents scanning root-scoped instead of relying on `user.home`. Preserve a no-argument production constructor that uses `AppDataPaths.production().projects`.

- [ ] Define `PreferencesProvider.node(key: String): Preferences`. Production maps stable keys to `Preferences.userNodeForPackage(...)`; tests will supply an in-memory implementation under test sources. Do not add test switches to production code.

- [ ] Extend repository tests to create two roots and prove projects/recents from one root cannot appear in the other.

- [ ] Run:

  ```powershell
  mvn -B -Dtest=ApplicationLayoutResolverTest,DefaultProjectsPresenterTest test
  ```

- [ ] Commit:

  ```powershell
  git add src/main/kotlin/org/litvin/ApplicationLayout.kt src/test/kotlin/org/litvin/ApplicationLayoutResolverTest.kt src/main/kotlin/org/litvin/app/AppDataPaths.kt src/main/kotlin/org/litvin/app/PreferencesProvider.kt src/main/kotlin/org/litvin/projects/ProjectsRepository.kt src/main/kotlin/org/litvin/projects/RecentsProvider.kt src/test/kotlin/org/litvin/ui/tabs/projects/presenter/DefaultProjectsPresenterTest.kt
  git commit -m "refactor: isolate application data and preferences"
  ```

## Task 5: Define External-Effect and Lifecycle Contracts

**Files:**

- Create: `src/main/kotlin/org/litvin/app/AppServices.kt`
- Create: `src/main/kotlin/org/litvin/app/ExecutorProvider.kt`
- Create: `src/main/kotlin/org/litvin/media/SwingMediaPlayer.kt`
- Create: `src/main/kotlin/org/litvin/media/MediaPlayerFactory.kt`
- Modify: `src/main/kotlin/org/litvin/media/VlcjSwingMediaPlayerAdapter.kt`
- Create: `src/main/kotlin/org/litvin/ui/commons/FilePicker.kt`
- Create: `src/main/kotlin/org/litvin/ui/commons/UserDialogService.kt`
- Create: `src/main/kotlin/org/litvin/export/RenderService.kt`
- Create: `src/main/kotlin/org/litvin/export/CompletedRendersRepository.kt`
- Modify: `src/main/kotlin/org/litvin/CompletedRendersStore.kt`
- Modify: `src/main/kotlin/org/litvin/AdjustmentsStore.kt`
- Create: `src/test/kotlin/org/litvin/app/TrackedExecutorProviderTest.kt`

- [ ] Add a failing lifecycle test that creates named executors, starts work, closes the provider twice, and asserts all owned executors terminate within five seconds and no thread with the provider's prefix remains alive.

- [ ] Implement `ExecutorProvider` and `TrackedExecutorProvider`. Return `ExecutorService`/`ScheduledExecutorService` instances with deterministic names, track every created executor, and make `close()` idempotent and bounded.

- [ ] Extract `SwingMediaPlayer` from the public operations currently used by Markup, Colors, Scoring, and Test panels. Include `component`, callbacks, load/play/pause/seek/rate/status/time/duration, preview adjustments/rotation/overlay, frame stepping, subtitle selection, activation/deactivation, and `close()`. Make `VlcjSwingMediaPlayerAdapter` implement it by delegating existing behavior and mapping `close()` to its current `dispose()` logic.

- [ ] Define `MediaPlayerFactory.create(screen: MediaScreen): SwingMediaPlayer` and `createFrameCapture(): StillFrameCaptureService`. Production creates VLCJ implementations; tests can return per-screen fakes and a fake still-frame service.

- [ ] Define `FilePicker` with source-open and export-save operations that accept parent, title, initial directory, and suggested file. Implement `SwingFilePicker` with the existing `JFileChooser` behavior.

- [ ] Define `UserDialogService` with `showInfo`, `showError`, and `confirm`. Implement `SwingUserDialogService` with `JOptionPane`, retaining current titles, messages, and option semantics.

- [ ] Define `RenderService` as the application-facing queue contract:

  ```kotlin
  interface RenderService : AutoCloseable {
      fun enqueue(job: RenderJob)
      fun cancelCurrent()
      fun cancelQueued(jobId: String): Boolean
      fun observe(observer: (ActiveQueueSnapshot) -> Unit): AutoCloseable
  }
  ```

  Implement production delegation to `RenderQueueManager`; the returned observation handle must remove exactly that observer.

- [ ] Convert completed-render persistence into an injected `CompletedRendersRepository` backed by the `AppDataPaths.completedRenders` file. Keep `CompletedRendersStore` only as the file-format implementation; remove its dependency on the global `ApplicationLayout` cache.

- [ ] Replace the process-wide adjustments singleton with an application-owned `AdjustmentsSession` that retains the existing get/set/load/save/subscribe behavior, accepts its scheduler from `ExecutorProvider`, implements `flush()` and idempotent `close()`, and can be recreated cleanly after an in-process application restart.

- [ ] Define `AppServices` with the approved boundaries and real defaults:

  ```kotlin
  data class AppServices(
      val paths: AppDataPaths,
      val preferences: PreferencesProvider,
      val executors: ExecutorProvider,
      val mediaPlayers: MediaPlayerFactory,
      val renderService: RenderService,
      val filePicker: FilePicker,
      val dialogs: UserDialogService,
      val projectsRepository: ProjectsRepository,
      val completedRenders: CompletedRendersRepository,
      val adjustments: AdjustmentsSession,
  ) : AutoCloseable
  ```

  `AppServices.production()` must use the resolved production app-data root. `close()` closes owned resources in reverse construction order and is idempotent.

- [ ] Run `mvn -B -Dtest=TrackedExecutorProviderTest test` and then `mvn -B test`.

- [ ] Commit:

  ```powershell
  git add src/main/kotlin/org/litvin/app src/main/kotlin/org/litvin/media/SwingMediaPlayer.kt src/main/kotlin/org/litvin/media/MediaPlayerFactory.kt src/main/kotlin/org/litvin/media/VlcjSwingMediaPlayerAdapter.kt src/main/kotlin/org/litvin/ui/commons/FilePicker.kt src/main/kotlin/org/litvin/ui/commons/UserDialogService.kt src/main/kotlin/org/litvin/export/RenderService.kt src/main/kotlin/org/litvin/export/CompletedRendersRepository.kt src/main/kotlin/org/litvin/CompletedRendersStore.kt src/main/kotlin/org/litvin/AdjustmentsStore.kt src/test/kotlin/org/litvin/app/TrackedExecutorProviderTest.kt
  git commit -m "refactor: define application effect boundaries"
  ```

## Task 6: Wire Boundaries Through Feature Panels

**Files:**

- Modify: `src/main/kotlin/org/litvin/ui/tabs/projects/SwingProjectsPanel.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/projects/presenter/DefaultProjectsPresenter.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/markup/AutosaveController.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/markup/SwingMarkupPanel.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/adjustments/SwingColorAdjustmentsPanel.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/crop/presenter/DefaultCropRotatePresenter.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/scoring/SwingScoringPanel.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/export/SwingExportPanel.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/export/RenderQueueList.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/export/CompletedRendersList.kt`
- Create: `src/test/kotlin/org/litvin/app/FeatureBoundaryWiringTest.kt`

- [ ] Write constructor-level tests that create each feature with fake services and assert construction does not instantiate VLC, start FFmpeg, open a native chooser, or access the production preference node.

- [ ] Inject `FilePicker` and `UserDialogService` into Projects; inject repository, preferences, and an executor supplied by `ExecutorProvider` into `DefaultProjectsPresenter`. Remove direct `JFileChooser`, `Dialogs`, `Preferences.userNodeForPackage`, and default executor creation from that feature path.

- [ ] Make `AutosaveController` `AutoCloseable`, inject its executor, track the in-flight save, and implement `flushAndClose()` so application close persists pending EDL before terminating the executor. Replace its raw `kotlin.concurrent.thread` call.

- [ ] Inject one `SwingMediaPlayer` and the application-owned `AdjustmentsSession` into each of Markup, Colors, and Scoring. Add idempotent `close()` methods that stop Swing timers, unsubscribe observers, flush pending persistence, clear callbacks, and close the player.

- [ ] Inject `StillFrameCaptureService`, the same `AdjustmentsSession`, and a scheduled executor into `DefaultCropRotatePresenter`; remove internal executor creation. Its existing `dispose()` must close the capture service, cancel scheduled work, and detach observers without closing executors it does not own.

- [ ] Inject `RenderService`, `CompletedRendersRepository`, `FilePicker`, `UserDialogService`, and preferences into Export. Replace the direct `RenderQueueManager` observer with an `AutoCloseable` subscription and close it when the panel closes. Route queue cancellation through `RenderService`.

- [ ] Replace every remaining flow-relevant `JOptionPane`/`Dialogs` call in these panels with `UserDialogService`. Keep actual `JOptionPane` construction inside `SwingUserDialogService` so deterministic tests can choose a real-modal or scripted implementation.

- [ ] Run the boundary test, all affected feature tests, and the full fast suite:

  ```powershell
  mvn -B -Dtest=FeatureBoundaryWiringTest,DefaultProjectsPresenterTest,SwingScoringPanelSmokeTest,SwingScoringPanelHotkeysTest test
  mvn -B test
  ```

- [ ] Commit:

  ```powershell
  git add src/main/kotlin/org/litvin/ui src/test/kotlin/org/litvin/app/FeatureBoundaryWiringTest.kt
  git commit -m "refactor: inject UI external effects"
  ```

## Task 7: Extract the Testable Swing Application Shell

**Files:**

- Create: `src/main/kotlin/org/litvin/app/SwingApplicationHandle.kt`
- Create: `src/main/kotlin/org/litvin/app/SwingApplicationFactory.kt`
- Modify: `src/main/kotlin/org/litvin/SwingMainApp.kt`
- Create: `src/test/kotlin/org/litvin/app/SwingApplicationFactoryTest.kt`

- [ ] Write a failing test that invokes `SwingApplicationFactory.create(testServices, show = false)` on the EDT, asserts it returns a non-visible `JFrame` configured with Projects, calls `close()` twice, and asserts all owned panels/services are closed and every application window is disposed.

- [ ] Implement `SwingApplicationHandle(frame, closeActions)` with atomic/idempotent `close()`. Execute panel close actions, help/dialog disposal, service close, and final frame disposal on the EDT. Collect close failures and throw one exception with suppressed causes after attempting all cleanup.

- [ ] Move frame, sidebar, card composition, navigation, selected-project propagation, window-state preferences, and contextual Help wiring from `SwingMainApp` into `SwingApplicationFactory`.

- [ ] Give the factory a visibility parameter defaulting to production behavior:

  ```kotlin
  fun create(
      services: AppServices,
      show: Boolean = true,
  ): SwingApplicationHandle
  ```

  Require invocation on the EDT; tests use `GuiActionRunner.execute` to call it.

- [ ] Use `JFrame.DISPOSE_ON_CLOSE`, install a window listener that calls the handle's close path, and remove lifecycle dependence on `EXIT_ON_CLOSE` or `System.exit`.

- [ ] Reduce `SwingMainApp.main()` to diagnostics handling, DPI/text/LAF/native bootstrap, production services creation, and an EDT call to the factory. Preserve current startup error behavior through the production dialog service.

- [ ] Run:

  ```powershell
  mvn -B -Dtest=SwingApplicationFactoryTest test
  mvn -B test
  ```

  Expected: both commands exit and no application-owned non-daemon thread remains.

- [ ] Commit:

  ```powershell
  git add src/main/kotlin/org/litvin/SwingMainApp.kt src/main/kotlin/org/litvin/app/SwingApplicationHandle.kt src/main/kotlin/org/litvin/app/SwingApplicationFactory.kt src/test/kotlin/org/litvin/app/SwingApplicationFactoryTest.kt
  git commit -m "refactor: extract testable Swing application shell"
  ```

## Task 8: Add Stable Semantic Component Names

**Files:**

- Modify: `src/main/kotlin/org/litvin/app/SwingApplicationFactory.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/projects/components/ProjectsHeader.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/projects/components/ProjectCard.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/markup/ui/TransportControls.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/adjustments/SwingColorAdjustmentsPanel.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/crop/CropTransformControls.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/scoring/ui/PlayerPanel.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/export/SwingExportPanel.kt`
- Create: `src/test/kotlin/org/litvin/ui/ComponentNameContractTest.kt`

- [ ] Add a fast component-tree contract test that composes the shell with fakes, enumerates non-null names, fails on duplicates, and requires all names used by initial screen objects.

- [ ] Assign these shell/project names: `app-frame`, `nav-projects`, `nav-rallies`, `nav-colors`, `nav-crop`, `nav-scoring`, `nav-export`, `projects-import-match`, `projects-current-name`, and a per-project name formed as `projects-open-` plus the manifest UUID.

- [ ] Assign these Rallies names: `rallies-video`, `rallies-play-pause`, `rallies-seek`, `rallies-current-time`, `rallies-point-start`, `rallies-point-end`, and `rallies-point-count`. Preserve dynamic favorite names already present.

- [ ] Normalize Colors/Crop names to user-intent identifiers and update existing component tests in the same commit: `colors-brightness`, `colors-contrast`, `colors-saturation`, `colors-temperature`, `colors-tint`, `colors-play-pause`, `colors-reset`, `crop-zoom`, `crop-pan-x`, `crop-pan-y`, `crop-rotation`, `crop-reset`, and `crop-seek`.

- [ ] Normalize Scoring names used by flows: `scoring-player-1-point`, `scoring-player-2-point`, `scoring-player-1-name`, `scoring-player-2-name`, and `scoring-score-summary`.

- [ ] Assign Export names: `export-preset`, `export-resolution`, `export-idle-trim`, `export-favorites-only`, `export-scoreboard`, `export-initialize`, `export-cancel`, and `export-progress`.

- [ ] Run `mvn -B -Dtest=ComponentNameContractTest test` and all existing component tests. Update tests to the stable names where they assert the old identifiers.

- [ ] Commit:

  ```powershell
  git add src/main/kotlin/org/litvin/app/SwingApplicationFactory.kt src/main/kotlin/org/litvin/ui src/test/kotlin/org/litvin/ui/ComponentNameContractTest.kt
  git commit -m "test: add stable Swing component identifiers"
  ```

## Task 9: Check In and Validate the Shared Video Fixture

**Files:**

- Create: `src/test/resources/media/ui-smoke.mp4`
- Create: `src/test/resources/media/README.md`
- Create: `src/test/kotlin/org/litvin/ui/flow/fixtures/UiSmokeMediaContractTest.kt`

- [ ] Ask the user for the approved redistributable tennis clip at implementation time, then place that exact immutable file at `src/test/resources/media/ui-smoke.mp4`. Do not synthesize or download a substitute without explicit license approval.

- [ ] Record source/provenance, copyright owner, redistribution permission/license, SHA-256, duration, dimensions, codec, container, audio presence, and byte size in `src/test/resources/media/README.md`.

- [ ] Add a fast contract test that requires the file to exist, have `.mp4` extension, be greater than zero and no more than 5 MiB, and match the documented SHA-256. Keep codec/duration validation in the PowerShell runner because ordinary fast tests must not require FFprobe.

- [ ] Validate with the packaged FFprobe or system FFprobe:

  ```powershell
  ffprobe -v error -show_entries format=duration,format_name,size -show_entries stream=codec_name,codec_type -of json src/test/resources/media/ui-smoke.mp4
  ```

  Required result: MP4 container, H.264 video, 5-15 seconds, no required audio behavior, and at most 5 MiB.

- [ ] Run `mvn -B -Dtest=UiSmokeMediaContractTest test`. Run the retained compatibility spike as well; the four product flows do not exist yet at this point in the sequence.

- [ ] Commit the binary and its provenance together:

  ```powershell
  git add src/test/resources/media/ui-smoke.mp4 src/test/resources/media/README.md src/test/kotlin/org/litvin/ui/flow/fixtures/UiSmokeMediaContractTest.kt
  git commit -m "test: add redistributable UI smoke video"
  ```

## Task 10: Build the Reusable UI-Flow Harness and Fixtures

**Files:**

- Create: `src/test/kotlin/org/litvin/ui/flow/harness/SwingUiFlowExtension.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/harness/UiFlowContext.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/harness/UiFlowArtifacts.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/fakes/FakeMediaPlayer.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/fakes/FakeRenderService.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/fakes/ScriptedFilePicker.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/fakes/ScriptedDialogService.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/fakes/InMemoryPreferencesProvider.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/fixtures/UiFlowFixtureBuilder.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/harness/SwingUiFlowExtensionTest.kt`

- [ ] Write extension tests for successful cleanup and forced-failure retention. Require successful temporary workspaces to be removed and failed workspaces to be copied below `target/ui-test-artifacts`, nested by the actual test class and method names, with the retained data in a `workspace` child directory.

- [ ] Implement fakes as observable state machines. `FakeMediaPlayer` must expose duration 10,000 ms, mutate position/status on commands, fire callbacks on the EDT, record every call with screen identity, and return a named black Swing component. `FakeRenderService` records immutable job snapshots and emits scripted queue snapshots without starting a thread or process.

- [ ] Implement picker scripts as FIFO outcomes (`Selected(file)` or `Cancelled`) and dialog scripts as either real modal display or predetermined confirmation response. Fail immediately on an unexpected call and include all recorded calls in the error.

- [ ] Implement `InMemoryPreferencesProvider` using `AbstractPreferences`, scoped to the test context, with no delegation to the platform preference store.

- [ ] Implement fixture builders for `emptyProject`, `editedProject`, `missingSourceProject`, and `exportReadyProject`. Write valid `.trproj`, `edl.json`, `adjustments.json`, and `score.json` through production IO classes. Always point source video to the immutable test resource or deliberately to a nonexistent path for the missing-source fixture.

- [ ] Implement `SwingUiFlowExtension` as a JUnit 5 `BeforeEachCallback`, `AfterTestExecutionCallback`, `AfterEachCallback`, and `ParameterResolver`. It must create the unique workspace/services, install EDT-violation and uncaught-exception capture, launch the factory with `show=true`, expose `UiFlowContext`, capture before teardown on failure, close app/driver/services, dispose every remaining window, enforce executor/thread invariants, and then clean or retain data.

- [ ] Extend diagnostics to capture screenshots, component tree, windows/dialog inventory, logs, fake media/render/picker/dialog calls, and a JVM thread dump. Use artifact names that remain valid on Windows.

- [ ] Run the extension tests and the retained spike:

  ```powershell
  mvn -B -Dtest=SwingUiFlowExtensionTest test
  mvn -B -Pui-flow -Dit.test=AssertJSwingCompatibilityUiFlowIT verify
  ```

- [ ] Commit:

  ```powershell
  git add src/test/kotlin/org/litvin/ui/flow
  git commit -m "test: add deterministic Swing flow harness"
  ```

## Task 11: Add Screen Objects

**Files:**

- Create: `src/test/kotlin/org/litvin/ui/flow/screens/ApplicationScreen.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/screens/ProjectsScreen.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/screens/RalliesScreen.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/screens/ColorsScreen.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/screens/CropScreen.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/screens/ScoringScreen.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/screens/ExportScreen.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/screens/ScreenObjectContractTest.kt`

- [ ] Write a source-level contract test that rejects `org.assertj.swing` imports from scenario and screen-object packages; only the driver/spike packages may import AssertJ Swing.

- [ ] Implement screen objects with intent-level methods. Examples: `projects.importMatch()`, `projects.openRecent(id)`, `rallies.markPoint(startMs, endMs)`, `colors.setBrightness(20)`, `crop.setRotationDegrees(15f)`, `scoring.awardPointToPlayer1()`, and `export.initialize(destination)`.

- [ ] Put all bounded waits behind `ApplicationScreen.eventually(description)`. Timeout errors must append current window inventory and fake-service call summaries.

- [ ] Expose only assertions on user-visible state or persisted fixture state. Do not return Swing components or AssertJ fixtures from screen objects.

- [ ] Run `mvn -B -Dtest=ScreenObjectContractTest test`.

- [ ] Commit:

  ```powershell
  git add src/test/kotlin/org/litvin/ui/flow/screens
  git commit -m "test: add Swing user-flow screen objects"
  ```

## Task 12: Implement Project Creation and Restart Flow

**Files:**

- Create: `src/test/kotlin/org/litvin/ui/flow/ApplicationShellUiFlowIT.kt`
- Modify: `src/test/kotlin/org/litvin/ui/flow/fixtures/UiFlowFixtureBuilder.kt`

- [ ] Write the flow first: launch with an empty root, require Projects active and non-project navigation hidden, script `ui-smoke.mp4`, click Import Match, require Rallies active and all navigation visible, and assert the created manifest points at the sample file.

- [ ] Add a harness `restartApplication()` operation that closes the current handle/driver/services, creates fresh services against the same app-data root and in-memory preference backing, then creates a fresh handle/driver. Do not reuse old service, frame, or screen-object instances.

- [ ] Complete the flow by restarting and asserting the created project appears in recents and can be opened.

- [ ] Run:

  ```powershell
  mvn -B -Pui-flow -Dit.test=ApplicationShellUiFlowIT verify
  ```

- [ ] Inspect `target/ui-test-artifacts` and ensure no directory was retained for the passing test.

- [ ] Commit:

  ```powershell
  git add src/test/kotlin/org/litvin/ui/flow/ApplicationShellUiFlowIT.kt src/test/kotlin/org/litvin/ui/flow/fixtures/UiFlowFixtureBuilder.kt
  git commit -m "test: automate project creation and restart flow"
  ```

## Task 13: Implement the Cross-Tab Editing Flow

**Files:**

- Create: `src/test/kotlin/org/litvin/ui/flow/EditingAcrossTabsUiFlowIT.kt`
- Modify: `src/test/kotlin/org/litvin/ui/flow/fakes/FakeMediaPlayer.kt`
- Modify: `src/test/kotlin/org/litvin/ui/flow/screens/RalliesScreen.kt`
- Modify: `src/test/kotlin/org/litvin/ui/flow/screens/ColorsScreen.kt`
- Modify: `src/test/kotlin/org/litvin/ui/flow/screens/CropScreen.kt`
- Modify: `src/test/kotlin/org/litvin/ui/flow/screens/ScoringScreen.kt`

- [ ] Seed an empty valid project and open it through Projects.

- [ ] In Rallies, set the fake playhead to 1,000 ms, press the visible/registered `C` shortcut, set it to 2,000 ms, press `V`, and wait for `edl.json` to contain exactly one valid point with those boundaries.

- [ ] In Colors, set brightness to UI value `20`, exercise Space to toggle fake playback, and wait for `adjustments.json` to contain `brightness=1.2` while all transform fields retain defaults.

- [ ] In Crop, set zoom to 125 and rotation to 15 degrees, navigate away to flush persistence, and assert `adjustments.json` contains `zoom=1.25` and `rotationDeg=15.0` while preserving the color edit.

- [ ] In Scoring, press the `Q` shortcut for Player 1. Wait for `score.json` to contain a Player 1 outcome for the seeded point and require the visible score summary to reflect the awarded point.

- [ ] Assert fake media events include per-screen load, play/pause, and at least one seek, and assert no VLC class was instantiated and no process was started.

- [ ] Run the flow three times to expose state leakage:

  ```powershell
  1..3 | ForEach-Object {
      mvn -B -Pui-flow -Dit.test=EditingAcrossTabsUiFlowIT verify
      if ($LASTEXITCODE -ne 0) { throw "Editing flow failed on run $_" }
  }
  ```

- [ ] Commit:

  ```powershell
  git add src/test/kotlin/org/litvin/ui/flow/EditingAcrossTabsUiFlowIT.kt src/test/kotlin/org/litvin/ui/flow/fakes/FakeMediaPlayer.kt src/test/kotlin/org/litvin/ui/flow/screens
  git commit -m "test: automate cross-tab editing flow"
  ```

## Task 14: Implement Export and Recovery Flows

**Files:**

- Create: `src/test/kotlin/org/litvin/ui/flow/ExportConfigurationUiFlowIT.kt`
- Create: `src/test/kotlin/org/litvin/ui/flow/ValidationRecoveryUiFlowIT.kt`
- Modify: `src/test/kotlin/org/litvin/ui/flow/fakes/ScriptedDialogService.kt`
- Modify: `src/test/kotlin/org/litvin/ui/flow/screens/ProjectsScreen.kt`
- Modify: `src/test/kotlin/org/litvin/ui/flow/screens/ExportScreen.kt`

- [ ] Write export configuration flow against `exportReadyProject`: choose the balanced preset and 1920x1080, enable idle trim and scoreboard, disable favorites-only, script an output path, click Initialize, dismiss the visible success dialog, and wait for exactly one fake render job.

- [ ] Assert the recorded `RenderJob` contains the seeded source path, point snapshot, preset ID, 1920x1080 dimensions, selected encoder, idle-trim/scoreboard flags, and scripted output path. Assert the output file and `.part` file do not exist, proving FFmpeg did not run.

- [ ] Write validation/recovery as independent tests in one class:

  - Cancel Import Match and verify Projects remains active and usable.
  - Open `missingSourceProject`, observe the relink request, script `ui-smoke.mp4`, and verify the manifest is repaired and Rallies opens.
  - Open an empty-EDL project with idle trim enabled, assert Initialize is disabled and its visible/accessible explanation states that the EDL is empty or invalid, then disable idle trim and assert Initialize becomes enabled.
  - Enable favorites-only on a project without favorites, assert the `Favorite export unavailable` modal, dismiss it visibly, and assert the application remains responsive.

- [ ] For dialog-under-test cases, configure `ScriptedDialogService` to delegate to real `JOptionPane`; use scripted confirmation responses only when the dialog is not itself the subject of the scenario.

- [ ] Run both classes and then the complete UI profile:

  ```powershell
  mvn -B -Pui-flow -Dit.test=ExportConfigurationUiFlowIT,ValidationRecoveryUiFlowIT verify
  mvn -B -Pui-flow verify
  ```

  Expected: all four initial scenarios pass in less than three minutes total.

- [ ] Force one local assertion failure, confirm the complete artifact bundle is present, then revert only that deliberate assertion before committing.

- [ ] Commit:

  ```powershell
  git add src/test/kotlin/org/litvin/ui/flow
  git commit -m "test: automate export and recovery flows"
  ```

## Task 15: Add the Non-Blocking Xvfb CI Observation Job

**Files:**

- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`
- Create: `docs/testing/swing-ui-flows.md`

- [ ] Add a separate `swing-ui-flow` Ubuntu job with JDK 17. Install `xvfb`, run `xvfb-run -a mvn -B -Pui-flow verify`, set `continue-on-error: true`, and upload `target/ui-test-artifacts` with `if: failure()` and `if-no-files-found: ignore`.

- [ ] Keep `build-and-test` unchanged and blocking. Do not make the observation job a required check yet.

- [ ] Document local Windows/Linux commands, layer selection, component naming rules, no-sleep/no-retry policy, artifact locations, and when packaged smoke is required.

- [ ] Add an observation log table to `docs/testing/swing-ui-flows.md` with columns run URL, commit, product assertions, lifecycle, duration, and consecutive-clean count. State that any failure resets the count and removal of `continue-on-error` happens only after 20 observed clean runs.

- [ ] Validate workflow syntax and run the local equivalent. If GitHub CLI access is available, push a branch and confirm the first job starts; otherwise record that remote observation begins on the next pushed change without weakening local verification.

- [ ] Commit:

  ```powershell
  git add .github/workflows/ci.yml README.md docs/testing/swing-ui-flows.md
  git commit -m "ci: observe deterministic Swing UI flows"
  ```

## Task 16: Add the Packaged Windows Smoke Runner and Checklist

**Files:**

- Create: `qa/windows/Run-UiSmoke.ps1`
- Create: `qa/windows/ui-smoke.md`
- Create: `qa/windows/UiSmokeRunner.Tests.ps1`
- Modify: `README.md`

- [ ] Write Pester tests for missing fixture, oversized fixture, invalid codec/duration metadata, supplied executable validation, temporary app-data setup, child-process environment injection, success cleanup, and failure retention.

- [ ] Implement `Run-UiSmoke.ps1` with parameters `-ExecutablePath`, `-KeepArtifacts`, and `-ReportPath`. Resolve all paths with `-LiteralPath`; verify destructive cleanup targets remain below the runner-created temporary directory.

- [ ] Have the runner locate and probe `ui-smoke.mp4`, create a unique QA root and artifact directory, set `TENNIS_RECORD_APP_DATA_DIR` only for the launched child process, and build via `distribution/windows/Build-AppImage.ps1` when no executable is supplied.

- [ ] Launch `target/package/app-image/Tennis Record/Tennis Record.exe` by default, print absolute paths for the scenario/report/artifacts, wait for the process, clean a passing run unless `-KeepArtifacts` is set, and always retain failure data.

- [ ] Write `ui-smoke.md` as an executable checklist with exact expected outcomes for:

  1. Projects launch state.
  2. Real chooser import of the checked-in clip.
  3. VLC play, pause, and seek.
  4. Rallies boundary marking, Colors brightness, Crop rotation, and Scoring Player 1 point.
  5. A tiny 1920x1080 FFmpeg export using the short clip.
  6. Shutdown, relaunch against the same QA root, and project presence in recents.

  Define report fields: pass/fail per step, screenshot path, reproduction steps, expected result, actual result, severity, and triage summary.

- [ ] Run Pester, then dry-run validation up to the application launch. Run the real workflow only on Windows with packaged VLC/FFmpeg dependencies available.

- [ ] Commit:

  ```powershell
  git add qa/windows README.md
  git commit -m "qa: add packaged Windows UI smoke workflow"
  ```

## Task 17: Execute and Report the First Packaged Smoke

**Files:**

- Runtime output: the runner-created timestamped report below `target/ui-smoke`
- Runtime output: the sibling `screenshots` directory printed by the runner

- [ ] Run the packaged workflow with an isolated data root:

  ```powershell
  pwsh -File qa/windows/Run-UiSmoke.ps1 -KeepArtifacts
  ```

- [ ] Use the `computer-use` skill because this step controls the packaged Windows application. Follow `qa/windows/ui-smoke.md` without substituting coordinate-only actions when accessible control targeting is available.

- [ ] Capture screenshot evidence at the initial Projects state, imported Rallies state, representative edited/scored state, export completion, and relaunched recents state.

- [ ] Save the structured report in the runner's printed report path. For any failure, include exact reproduction steps, expected/actual behavior, severity, artifact paths, and whether the defect is in product behavior, packaging/native integration, or the smoke harness.

- [ ] If the smoke passes, report the absolute report/artifact paths in the implementation handoff. Do not commit transient reports or screenshots. If it fails, leave the retained QA root untouched for diagnosis and do not claim the milestone complete.

## Final Verification

- [ ] Run the complete fast suite and confirm clean termination:

  ```powershell
  mvn -B test
  ```

- [ ] Run the complete deterministic UI suite locally:

  ```powershell
  mvn -B -Pui-flow verify
  ```

- [ ] Run the canonical Linux command in CI/Xvfb:

  ```bash
  xvfb-run -a mvn -B -Pui-flow verify
  ```

- [ ] Confirm the four scenario classes pass, the complete UI job stays below three minutes, successful workspaces are deleted, and a deliberately forced local failure produces the full diagnostic bundle.

- [ ] Confirm `ui-smoke.mp4` passes size/hash/FFprobe checks, the PowerShell runner/checklist are versioned, and the first packaged Computer Use report exists outside Git.

- [ ] Inspect `git status --short` and verify only intentional files are modified; leave `.worktrees/` and unrelated user changes untouched.

- [ ] Request code review with the approved design and this plan as review anchors. Resolve correctness findings, rerun the affected focused tests, then rerun both complete Maven commands before claiming completion.
