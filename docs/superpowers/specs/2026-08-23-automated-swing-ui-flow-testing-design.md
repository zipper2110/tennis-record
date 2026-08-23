# Automated Swing UI Flow Testing Design

**Date:** 2026-08-23
**Status:** Approved design awaiting implementation planning

## Context

Tennis Record currently relies on manual verification for UI features and end-to-end user journeys. The repository already has useful domain, presenter, component, hotkey, rendering, and Swing smoke tests, but it does not have a deterministic test driver for complete Swing flows. This slows UI development, makes regressions expensive to find, and prevents coding agents from verifying their own UI changes reliably.

The existing test baseline also has two known prerequisites that must be addressed before a new UI suite becomes a merge gate:

- `VlcCropGeometryCalculatorTest` currently has two failing assertions.
- A diagnostic `mvn test` run left a test JVM alive after the normal test phase.

These baseline defects are not caused by the proposed UI harness, but they must be fixed or cleanly separated first so subsequent failures are attributable.

## Goals

- Provide deterministic, automated coverage of the application's critical Swing user flows.
- Give local developers and coding agents one repeatable command for UI verification.
- Run UI flows on pull requests without requiring VLC, FFmpeg, or an interactive Windows runner.
- Exercise the packaged Windows application with real native dependencies through a separate local smoke workflow.
- Produce enough diagnostics for an agent or developer to reproduce and triage failures without rerunning blindly.
- Keep the test scenarios independent from AssertJ Swing so the driver can be replaced if necessary.

## Non-goals

- Pixel-perfect screenshot regression testing in the first milestone.
- Full-length export correctness or performance testing.
- Packaged-app automation in hosted CI.
- Cross-platform packaged-app coverage beyond Windows in the first milestone.
- Replacing existing unit, presenter, component, or rendering tests with UI tests.
- Using Computer Use as the pull-request merge gate.

## Selected approach

Use AssertJ Swing 3.17.1 as the initial UI driver, isolated behind project-owned screen objects and a small driver layer. Add an explicit AssertJ Core 3.27.7 test dependency so the test suite does not inherit the Swing artifact's 2020-era Core version. Integrate the driver with JUnit 5 through a project-owned extension instead of depending on AssertJ Swing's JUnit 4 base classes.

AssertJ Swing is accepted conditionally because its latest release is from 2020 and several compatibility and JUnit Jupiter pull requests remain open. Implementation begins with a compatibility spike. If AssertJ cannot meet the spike's acceptance criteria, replace only the driver implementation with a project-owned `java.awt.Robot` driver; the application boundaries, screen objects, flows, fixtures, diagnostics, and CI structure remain unchanged.

References:

- [AssertJ Swing repository and release history](https://github.com/assertj/assertj-swing)
- [OpenAI Computer Use QA workflow](https://learn.chatgpt.com/use-cases/qa-your-app-with-computer-use)

## Test layers

### 1. Fast tests

Keep the existing domain, persistence, presenter, component, hotkey, architecture, and headless renderer tests as the main source of coverage. They continue to run through `mvn test` and should contain most behavioral combinations and edge cases.

### 2. Deterministic Swing flow tests

Launch a real `JFrame` and drive visible controls using AssertJ Swing's OS-level mouse and keyboard gestures. Use real application presenters and persistence against isolated temporary data. Replace native media playback, rendering, native file selection, persistent user preferences, and uncontrolled background execution with deterministic test implementations.

Run these tests sequentially under Xvfb on Ubuntu in a dedicated Maven integration-test profile. They are the eventual pull-request UI gate.

### 3. Local packaged-app smoke test

Build or select the packaged Windows application and launch it with real VLC and FFmpeg integration. Codex Computer Use follows a versioned repository checklist, interacts with the real native file chooser, captures screenshot evidence, and returns a structured pass/fail report. This workflow runs locally on demand before releases and after significant UI, media, packaging, or native-integration changes.

## Application architecture changes

### Application composition

Separate platform bootstrapping from Swing composition:

- `SwingMainApp.main()` retains command-line handling, look-and-feel initialization, DPI/text settings, native bootstrap, and production startup.
- `SwingApplicationFactory.create(services)` constructs the complete frame on the EDT and returns a `SwingApplicationHandle`.
- `SwingApplicationHandle` exposes the root frame for the harness and an idempotent `close()` operation.
- `AppServices.production()` supplies all real implementations.
- Tests supply an `AppServices` instance whose external effects are deterministic.

`SwingApplicationHandle.close()` must stop application-owned media players, rendering work, timers, autosave work, and executors before disposing dialogs and frames. Application shutdown must not depend on `EXIT_ON_CLOSE` or `System.exit`.

### External-effect boundaries

`AppServices` owns these focused boundaries:

- `MediaPlayerFactory`: creates the media-player abstraction used by each media screen. Production creates VLCJ adapters; tests create controllable fake players.
- `RenderService`: accepts a render request and reports progress/result. Production delegates to the current queue/FFmpeg path; tests record requests without starting a process.
- `FilePicker`: selects the source video and export destination. Production uses `JFileChooser`; deterministic tests return scripted selections or cancellation.
- `UserDialogService`: shows information, error, and confirmation dialogs. Production uses Swing dialogs. Flow tests use real Swing dialogs when the visible message/choice is under test and scripted responses only when the dialog itself is outside the scenario.
- `PreferencesProvider`: supplies isolated preference nodes. Tests never read or modify the developer's actual preferences.
- `ExecutorProvider`: supplies named application executors. Tests use deterministic or explicitly tracked executors.
- `AppDataPaths`: supplies project, render-history, log, and temporary roots. Tests use one temporary root per test.

Existing feature-specific abstractions, such as `ProjectsRepository`, remain the preferred injection point when they already cover the required behavior. New boundaries should wrap external effects, not duplicate domain logic.

### Stable component identifiers

Every control used by a flow test receives a semantic Swing `name`. Use feature-prefixed kebab-case identifiers, for example:

- `projects-import-match`
- `nav-rallies`
- `colors-reset`
- `crop-rotation`
- `scoring-player-1-point`
- `export-initialize`

Identifiers describe user intent rather than layout. Tests must not locate production components by screen coordinates, child index, or container hierarchy. Text lookup is allowed only when the displayed text is the behavior being verified.

## Test harness design

### JUnit 5 extension

A project-owned `SwingUiFlowExtension` performs the complete lifecycle:

1. Create an isolated fixture workspace and services.
2. Install Swing EDT-violation detection and uncaught-exception capture.
3. Create AssertJ's Robot and launch the application on the EDT.
4. Provide screen objects to the test.
5. On failure, capture all diagnostic artifacts before teardown.
6. Close the application and AssertJ Robot.
7. Verify termination invariants and delete successful-test data.

UI flow tests execute sequentially in one Maven fork. Robot-driven tests must never run in parallel on the same display.

### Driver isolation and screen objects

Direct AssertJ fixture calls are confined to a small `AssertJSwingDriver`. Scenario tests use project-specific screen objects:

- `ProjectsScreen`
- `RalliesScreen`
- `ColorsScreen`
- `CropScreen`
- `ScoringScreen`
- `ExportScreen`

Screen objects expose user actions and visible outcomes, not internal Swing components. For example, a scenario calls `projects.importMatch()` and `rallies.assertProjectOpen(name)` rather than finding and clicking buttons directly.

If the compatibility spike rejects AssertJ, a `RobotSwingDriver` replaces `AssertJSwingDriver` without changing scenarios or application production code.

### Synchronization policy

Tests wait for observable conditions with bounded timeouts. Valid conditions include:

- A named component is showing.
- A control becomes enabled or disabled.
- Visible text or selection changes.
- A modal dialog opens or closes.
- A fake service records a specific request.
- A persisted fixture file reaches an expected state.

Arbitrary sleeps are prohibited. Every timeout message must name the expected condition and include current visible state where practical. A rerun may be used diagnostically, but automatic retries must not turn an initially failing flow into a passing CI result.

## Test data

### Checked-in video fixture

Add `src/test/resources/media/ui-smoke.mp4` to regular Git. The file must:

- Contain a 5-15 second tennis clip.
- Use H.264 video in an MP4 container.
- Be no larger than 5 MiB.
- Require no audio track for the scenarios to pass.
- Be owned by the project owner or otherwise be redistributable with this GPL-licensed repository.
- Remain immutable during every test.

Deterministic flow tests reference this path while using fake playback and rendering. The local packaged smoke workflow opens the same file through the real chooser and exercises real VLC playback and seeking.

### Per-test workspace

Each flow test creates a unique temporary app-data root containing project manifests, EDL, scores, adjustments, export history, logs, and outputs. Fixture builders create named project states such as empty project, valid edited project, missing-source project, and export-ready project.

Successful workspaces are deleted during teardown. Failed workspaces are retained under the diagnostic artifact directory.

## Initial deterministic flows

### 1. Application shell and project creation

- Launch the application and verify Projects is initially active.
- Click Import Match.
- Have the scripted picker return `ui-smoke.mp4`.
- Verify project creation, current-project state, revealed navigation, and automatic transition to Rallies.
- Restart against the same fixture workspace and verify that the project appears in recents.

### 2. Editing across tabs

- Start with a seeded project.
- Navigate through Rallies, Colors, Crop, and Scoring using sidebar clicks.
- Exercise one representative edit and relevant keyboard behavior in each screen.
- Verify visible state and persisted EDL, adjustment, crop, and score data.
- Verify fake media duration, play/pause, position, and seek interactions where applicable.

This is a short wiring test, not an exhaustive test of every edit combination. Detailed combinations remain in fast tests.

### 3. Export configuration

- Start with an export-ready project containing valid EDL and score data.
- Change representative export options and verify validation and enablement.
- Initialize export through a scripted destination selection.
- Verify that the fake `RenderService` received the expected render request.
- Do not start FFmpeg.

### 4. Validation and recovery

- Cancel source-video selection and verify the application remains usable.
- Open a missing-source project, relink it, and verify recovery.
- Exercise empty or invalid export states.
- Verify the relevant user-facing dialogs and dismiss them through visible UI actions.

These flows remain independent. Only the editing flow crosses several tabs to prove selected-project propagation and application wiring.

## Failure handling and diagnostics

Any failed assertion, timeout, uncaught exception, EDT violation, unexpected modal, or teardown invariant failure captures:

- A screenshot of every visible application window.
- A component-tree dump with component names, types, visible/enabled state, and applicable text.
- An inventory of open windows and modal dialogs.
- Application logs.
- Recorded fake-service calls and events.
- A JVM thread dump for timeouts and termination failures.
- The complete failed fixture workspace.

Store artifacts under `target/ui-test-artifacts/<test-class>/<test-method>/`. GitHub Actions uploads that directory when the UI-flow job fails.

Teardown closes application services, disposes all dialogs and frames on the EDT, shuts down tracked executors within a bounded timeout, cleans up the AssertJ Robot, and verifies that no application-owned non-daemon thread remains.

## AssertJ compatibility spike

Before refactoring the complete application shell, prove all of the following in a minimal retained test:

- The selected dependencies compile and run on JDK 17.
- The project-owned JUnit 5 extension can create and clean up AssertJ's Robot.
- FlatLaf buttons and text fields can be found by semantic name.
- Mouse clicks, text entry, and keyboard shortcuts work.
- A modal Swing dialog can be found, asserted, and dismissed.
- The test runs under Xvfb on Ubuntu.
- Screenshots and component dumps can be captured after a forced failure.
- Twenty consecutive local executions terminate cleanly without intermittent failures or leaked non-daemon threads.
- Overriding AssertJ Core to 3.27.7 does not introduce binary or runtime incompatibility.

Failure of any critical item rejects `AssertJSwingDriver` and triggers the `RobotSwingDriver` fallback. The spike is not permitted to weaken these criteria by adding retries or sleeps.

## Maven and CI workflow

- Existing fast tests remain in Surefire and run through `mvn test`.
- UI flow tests use Failsafe, the `*UiFlowIT` naming convention, and a dedicated `ui-flow` Maven profile.
- The canonical CI command is `xvfb-run -a mvn -B -Pui-flow verify` on JDK 17.
- UI tests run sequentially with one Maven fork and have a complete-job runtime target of three minutes.
- GitHub Actions adds a separate Ubuntu UI-flow job and uploads `target/ui-test-artifacts` on failure.

The new CI job initially uses `continue-on-error: true` while reliability is measured. Remove that setting and make the job a required branch-protection check after twenty consecutive observed executions in which both the product assertions and harness lifecycle pass. Any failed observed execution resets the count. A run intentionally skipped because the branch is already known to contain an unrelated failing baseline is not an observation.

## Local packaged Windows smoke workflow

Add a versioned scenario document and PowerShell runner:

- `qa/windows/ui-smoke.md`
- `qa/windows/Run-UiSmoke.ps1`

The runner:

1. Resolves `src/test/resources/media/ui-smoke.mp4` and validates its presence and size.
2. Creates an isolated temporary QA app-data root.
3. Sets `TENNIS_RECORD_APP_DATA_DIR` for the launched process; application path resolution accepts this environment override in addition to the existing system-property override.
4. Builds the Windows application image when a packaged executable path is not supplied.
5. Launches the packaged executable and prints the scenario and artifact locations.
6. Cleans successful temporary data and retains failed-run data.

Codex Computer Use follows `ui-smoke.md` and covers:

- Launch and initial Projects state.
- Importing the checked-in sample through the real file chooser.
- Real VLC play, pause, and seek.
- Representative navigation and editing across tabs.
- Representative scoring behavior.
- Export configuration and one tiny real FFmpeg export.
- Application shutdown and relaunch with the project visible in recents.

The report includes pass/fail per scenario, screenshot evidence, reproduction steps, expected and actual results, severity, and a short triage summary. The local smoke workflow is on demand and is not a CI merge gate in the first milestone.

## Rollout phases

1. **Baseline stabilization:** restore a terminating, green existing test suite.
2. **Compatibility spike:** accept AssertJ or switch only the driver to the custom Robot fallback.
3. **Testable application shell:** add composition, dependency, lifecycle, and selector boundaries without changing product behavior.
4. **Initial flows:** implement the four deterministic scenarios and their fixture builders.
5. **CI observation:** run the non-blocking UI job until twenty consecutive clean executions.
6. **Required gate:** promote the UI job to required status.
7. **Packaged smoke:** add the Windows runner, checklist, checked-in video, and first Computer Use QA report.

Each phase must leave a working, testable repository and may be reviewed independently.

## Quality rules for future UI work

- New user journeys require a deterministic flow test or an explicit explanation of why existing coverage is sufficient.
- Behavioral combinations belong in fast tests; flow tests prove integration and wiring.
- Scenario tests use screen objects, never direct AssertJ calls.
- Production controls used by flows have stable semantic names.
- Tests do not use coordinate clicks, container indices, arbitrary sleeps, or pass-producing retries.
- Native VLC and FFmpeg execution remains outside the pull-request UI job.
- Significant UI, media, packaging, or native-integration changes require the local packaged smoke workflow before release.

## Risks and mitigations

### AssertJ Swing stagnation

Mitigation: compatibility spike, direct modern AssertJ Core dependency, project-owned JUnit 5 extension, isolated driver, and a predesigned custom Robot fallback.

### Flaky asynchronous UI behavior

Mitigation: deterministic executors, semantic waits, no sleeps or retries, sequential execution, bounded timeouts, and comprehensive diagnostics.

### Test-only architecture leaking into product behavior

Mitigation: inject only external effects and lifecycle boundaries; retain production defaults in `AppServices.production()` and keep all driver/screen-object code under test sources.

### Repository growth from media

Mitigation: enforce a single immutable MP4 fixture no larger than 5 MiB and store it in regular Git.

### False confidence from faked native services

Mitigation: keep the local packaged smoke workflow with the same checked-in video, real VLC, real FFmpeg, and the packaged executable.

## Acceptance criteria

The first milestone is complete when:

- The pre-existing test suite is green and terminates cleanly.
- The AssertJ spike passes all listed criteria or the fallback driver passes equivalent criteria.
- The four deterministic flows pass locally and under Xvfb.
- Failures produce the specified artifact bundle.
- The UI-flow CI job exists and is collecting reliability history.
- `ui-smoke.mp4`, the local PowerShell runner, and the versioned smoke checklist are committed.
- Codex Computer Use completes and reports the packaged Windows smoke scenario against the checked-in video.
