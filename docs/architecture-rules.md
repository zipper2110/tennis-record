# Architecture Rules

These rules define packaging and dependency conventions to keep the codebase consistent and maintainable. All new work must follow them; existing code should be migrated opportunistically.

## Package topology (feature‑centric)
Top‑level feature and shared packages:
- `org.litvin.app` — application entry/wiring/bootstrap only (no business logic).
- `org.litvin.media` — media adapters and low‑level media services.
  - `org.litvin.media.mpv` — the libmpv preview engine (JNA bindings, core, shader/overlay plumbing).
- `org.litvin.projects` — project/session metadata and manifest handling.
- `org.litvin.adjustments` — color/geometry adjustments domain and persistence.
- `org.litvin.points` — points/EDL domain and services.
- `org.litvin.scoring` — scoring rules/engine, score timelines, score IO.
- `org.litvin.export` — export/pipeline/FFmpeg/queue/overlay writing.
- `org.litvin.analytics` — opt‑in telemetry: event registry, buffering, transport, consent state.
- `org.litvin.shared.util` — cross‑cutting utilities and primitives (timecode, debouncers, pagination, OS tweaks, etc.).
- `org.litvin.ui` — UI layer only.
  - `org.litvin.ui.tabs.{projects,points,scoring,adjustments,crop,export}` — tab UIs per feature (plus `test`, a diagnostics tab wired in only when the test flag is enabled).
  - `org.litvin.ui.commons` — shared UI widgets/components/styles with no tab dependency.
  - `org.litvin.ui.help` — help catalog and dialog.
  - `org.litvin.ui.privacy` — analytics consent and privacy dialogs.

Subpackage conventions inside a tab (`org.litvin.ui.tabs.<feature>`):
- `.presenter` — the presenter and its UI contracts (see MVP below).
- `.ui` / `.components` — widgets that belong to that tab only.

Directory layout must mirror the declared package. A file whose `package` line does not match its directory is migration debt, not a pattern to copy.

## Dependency rules
Allowed directions:
- `app` → `ui`, `projects`, feature packages, `analytics`, `shared.util`, `media`.
- `ui.tabs.*` → the corresponding feature package(s), `projects`, `export`, `media`, `shared.util`, and `ui.commons`.
- `ui.commons` → `shared.util` only (must not depend on any specific tab or feature).
- `ui.privacy` → `analytics` (the consent/privacy dialogs are the only UI allowed to touch it); `ui.help` → `shared.util` and `ui.commons`.
- Feature packages may depend on `shared.util` and other leaf services (`media`) but not on `ui` or `app`.
- `media` → `adjustments` is allowed and deliberate: the libmpv preview must apply the same crop/rotate/color transforms the FFmpeg export does, so it consumes the adjustments domain (`GeometryPlan`, `CropRect`, `AdjustmentsV1`) instead of duplicating the math.
- `analytics` must not depend on any other `org.litvin` package — it is a leaf service reached from `app` and `ui.privacy`.
- `shared.util` must not depend on any feature/ui/app.
- Cycles are forbidden.

## Third-party license boundaries
The app uses the Elastic License 2.0. A GPL component must not be part of the app process.
- Start the bundled GPL FFmpeg build only as a separate process (`ffmpeg.exe`, `ffprobe.exe`). Communicate only through command-line arguments, pipes and files.
- Do not load a GPL FFmpeg build into the app process (for example through JNA, JavaCV or bytedeco). Only an LGPL FFmpeg build, as a separate and replaceable library, can load in process.
- Load only an LGPL build of libmpv (`-Dgpl=false`). Keep `libmpv-2.dll` a separate, replaceable file.
- Before you add a dependency, check its license. Apache-2.0, MIT, BSD, EPL and LGPL (as a replaceable library) are compatible. GPL and AGPL are not compatible in process.

## UI architecture: MVP (Passive View)

This project uses MVP (Model–View–Presenter), Passive View flavor, for all Swing UI under `org.litvin.ui.*`.

Goals:
- Separate UI definition from business logic and IO.
- Keep Swing components thin and deterministic to render from immutable state.
- Make behavior testable without Swing.

### Roles and contracts
- View (in `org.litvin.ui…`):
  - Swing components (`JPanel`, dialogs, widgets) implement a small `…View` interface.
  - No business logic or domain computations; only forwards user events and renders state.
  - Required methods:
    - `fun render(state: <Feature>ViewState)` — idempotent; updates the UI from immutable state only. Enable/disable and visibility decisions belong in `ViewState`, not in the View.
    - `fun renderEffect(effect: <Feature>ViewEffect)` — optional, for one‑off UI commands (dialogs, focus, notifications). Keep these one‑shot effects out of the persistent `ViewState`.
- Presenter (in `org.litvin.ui.tabs.<feature>.presenter`):
  - Orchestrates user intents, domain use‑cases/services, and produces `ViewState`.
  - Lifecycle aware.
  - Required methods:
    - `fun attach(view: <Feature>View)` / `fun detach()`
    - `fun onIntent(intent: <Feature>Intent)`
    - `fun onActivated()` / `fun onDeactivated()` (hooked to tab lifecycle)
- Model/Domain (in feature packages e.g., `org.litvin.scoring`, `org.litvin.points`):
  - Entities, pure functions, repositories, and services. No Swing/UI dependencies.

### Data flow (unidirectional)
`User/Event` → `View` forwards `Intent` → `Presenter` runs use‑cases → computes new immutable `ViewState` → `View.render(state)`.

### Threading/EDT rules
- Never block the EDT. Long‑running work is initiated by the Presenter off‑EDT.
- View updates happen on the EDT (`EventQueue.invokeLater { … }`). Presenters ensure this when calling `view.render(…)`.
- Domain/services remain UI‑agnostic; they do not touch Swing.

### Package placement and dependencies
- Views: `org.litvin.ui.tabs.<feature>` and subpackages.
- Presenters and UI contracts: `org.litvin.ui.tabs.<feature>.presenter`.
- Domain/services: the corresponding feature packages (`org.litvin.scoring`, `org.litvin.points`, etc.).
- Dependency direction: View → Presenter(contract) → Domain/Services. Domain must not depend on UI.

### Naming conventions
For a feature `Foo` (e.g., Scoring, Points):
- Interfaces/classes:
  - `FooView`, `FooPresenter`, `FooViewState`, `FooIntent`, `FooViewEffect` (optional).
- Presenter methods:
  - `attach`, `detach`, `onIntent`, `onActivated`, `onDeactivated`.
- State is immutable (`data class FooViewState(…)`). Avoid mutable UI‑driven state outside this object.

### Reference implementation
`projects` is the worked example; `crop` is the same shape in miniature. Read those rather than a sketch:
- Contracts — [`ProjectsContracts.kt`](../src/main/kotlin/org/litvin/ui/tabs/projects/presenter/ProjectsContracts.kt), [`CropRotateContracts.kt`](../src/main/kotlin/org/litvin/ui/tabs/crop/presenter/CropRotateContracts.kt)
- Presenter — [`DefaultProjectsPresenter.kt`](../src/main/kotlin/org/litvin/ui/tabs/projects/presenter/DefaultProjectsPresenter.kt)
- View wiring (`attach`/`detach` from `addNotify`/`removeNotify`) — [`SwingProjectsPanel.kt`](../src/main/kotlin/org/litvin/ui/tabs/projects/SwingProjectsPanel.kt)
- Presenter test against a fake view — [`DefaultProjectsPresenterTest.kt`](../src/test/kotlin/org/litvin/ui/tabs/projects/presenter/DefaultProjectsPresenterTest.kt)

## UI component conventions
- Encapsulate business logic or visual style in focused components with clear APIs.
- Prefer extracting repeated or cohesive UI widgets into separate component classes instead of keeping them as builder methods inside a tab panel.
- Keep theme-level colors in `UiStyles`; UI components should consume shared style constants instead of defining local palettes.
- Favor composition over inheritance; keep components testable.
- Prefer immutable value objects for inputs/outputs where practical.

## Naming
- Names must reflect intent and feature (e.g., `FFmpegCommandBuilder`, `RenderQueue`, `ScoringEngine`).
- Avoid ambiguous names like `Utils`/`Helper`; prefer precise nouns.

## Testing
- Pure logic (e.g., rules engine, timecode, geometry math) belongs in feature or `shared.util` and should have unit tests.
- Presenter tests: send `Intent`s, assert emitted `ViewState`/`Effect`s. No Swing involved.
- Domain tests: verify pure logic in `org.litvin.<feature>` packages.
- End‑to‑end Swing behavior belongs in the UI‑flow ITs under `org.litvin.ui.flow`, which drive the real shell through a headless harness. Use them for cross‑tab wiring, not for logic a presenter test can cover.

## File size and structure
- Kotlin source files longer than 500 lines are discouraged.
- If a file exceeds 500 lines:
  - Prefer splitting it into smaller, focused components (classes, files, or top‑level functions) that align with the feature/package boundaries above.
  - If keeping it as a single file is deliberate, add a clear file‑level KDoc at the top that explains why this size is preferred and what trade‑offs were considered.
- This is a guideline, not a hard limit; exceptions must be justified as above.

## Migration guidance
- When touching legacy code in `org.litvin` that mixes concerns, rehome it into the appropriate feature package or `shared.util`/`ui.commons`.

Known debt, to be paid down opportunistically rather than in a single sweep:
- The root `org.litvin` package still holds ~20 classes that belong in feature packages — FFmpeg/render/overlay pieces (`export`), scoreboard styling and timeline (`scoring`), `Pagination`/`JsonFileIO`/`SessionSettings` (`shared.util`), `GeometryViewportPanel` (`ui`).
- Several files already declare a feature package while still sitting in the `org/litvin/` directory (e.g. `Timecode.kt`, `EdlIO.kt`, `ScoreIO.kt`, `PointsDispatcher.kt`). Move the file when you next touch it.
- Only `projects` and `crop` are on MVP. `points` and `scoring` use an older container pattern — action interfaces (`PointsActions`, `ScoringActions`) plus a `ViewState` snapshot pushed to leaf components, with no presenter — and their contracts sit in the tab package rather than `.presenter`. `adjustments` and `export` have no contracts at all. New tabs follow the `projects`/`crop` shape.
- `RulesEngine` is superseded by `scoring.ScoringEngine` and is now referenced only from tests; fold the remaining coverage into `ScoringEngineTest` and delete it.

### Migration checklist (per tab)
1) Introduce `…View`, `…Presenter`, `…ViewState`, `…Intent` contracts.
2) Move logic from Swing components into the Presenter, step by step.
3) Extract reusable computations to the corresponding feature packages.
4) Ensure `render(state)` is the only way the View changes persistent UI state.
5) Preserve threading rules: heavy work off‑EDT; rendering on EDT.
