# Architecture Rules

These rules define packaging and dependency conventions to keep the codebase consistent and maintainable. All new work must follow them; existing code should be migrated opportunistically.

## Package topology (feature‑centric)
Top‑level feature and shared packages:
- `org.litvin.app` — application entry/wiring/bootstrap only (no business logic).
- `org.litvin.media` — media adapters (e.g., VLCJ) and low‑level media services.
- `org.litvin.projects` — project/session metadata and manifest handling.
- `org.litvin.adjustments` — color/geometry adjustments domain and persistence.
- `org.litvin.markup` — markup/EDL domain and services.
- `org.litvin.scoring` — scoring rules/engine, score timelines, score IO.
- `org.litvin.export` — export/pipeline/FFmpeg/queue/overlay writing.
- `org.litvin.shared.util` — cross‑cutting utilities and primitives (timecode, debouncers, pagination, OS tweaks, etc.).
- `org.litvin.ui` — UI layer only.
  - `org.litvin.ui.tabs.{projects,markup,scoring,adjustments,export}` — tab UIs per feature.
  - `org.litvin.ui.commons` — shared UI widgets/components/styles with no tab dependency.

## Dependency rules
Allowed directions (subset order):
- `app` → `ui`, `projects`, feature packages, `shared.util`, `media`.
- `ui.tabs.*` → the corresponding feature package(s), `projects`, `export`, `media`, `shared.util`, and `ui.commons`.
- `ui.commons` → `shared.util` only (must not depend on any specific tab or feature).
- Feature packages may depend on `shared.util` and other leaf services (`media`) but not on `ui` or `app`.
- `shared.util` must not depend on any feature/ui/app.
- Cycles are forbidden.

## UI component conventions
- Encapsulate business logic or visual style in focused components with clear APIs.
- Prefer extracting repeated or cohesive UI widgets into separate component classes instead of keeping them as builder methods inside a tab panel.
- Keep theme-level colors in `UiStyles`; UI components should consume shared style constants instead of defining local palettes.
- Favor composition over inheritance; keep components testable.
- Prefer immutable value objects for inputs/outputs where practical.

## Naming
- Names must reflect intent and feature (e.g., `FFmpegCommandBuilder`, `RenderQueue`, `RulesEngine`).
- Avoid ambiguous names like `Utils`/`Helper`; prefer precise nouns.

## Testing
- Pure logic (e.g., rules engine, timecode, geometry math) belongs in feature or `shared.util` and should have unit tests.

## Migration guidance
- When touching legacy code in `org.litvin` that mixes concerns, rehome it into the appropriate feature package or `shared.util`/`ui.commons`.
- Keep public APIs stable; refactor internals behind components.

## File size and structure
- Kotlin source files longer than 500 lines are discouraged.
- If a file exceeds 500 lines:
  - Prefer splitting it into smaller, focused components (classes, files, or top‑level functions) that align with the feature/package boundaries above.
  - If keeping it as a single file is deliberate, add a clear file‑level KDoc at the top that explains why this size is preferred and what trade‑offs were considered.
- This is a guideline, not a hard limit; exceptions must be justified as above.

# Architecture Rules

These rules define packaging and dependency conventions to keep the codebase consistent and maintainable. All new work must follow them; existing code should be migrated opportunistically.

## Package topology (feature‑centric)
Top‑level feature and shared packages:
- `org.litvin.app` — application entry/wiring/bootstrap only (no business logic).
- `org.litvin.media` — media adapters (e.g., VLCJ) and low‑level media services.
- `org.litvin.projects` — project/session metadata and manifest handling.
- `org.litvin.adjustments` — color/geometry adjustments domain and persistence.
- `org.litvin.markup` — markup/EDL domain and services.
- `org.litvin.scoring` — scoring rules/engine, score timelines, score IO.
- `org.litvin.export` — export/pipeline/FFmpeg/queue/overlay writing.
- `org.litvin.shared.util` — cross‑cutting utilities and primitives (timecode, debouncers, pagination, OS tweaks, etc.).
- `org.litvin.ui` — UI layer only.
  - `org.litvin.ui.tabs.{projects,markup,scoring,adjustments,export}` — tab UIs per feature.
  - `org.litvin.ui.commons` — shared UI widgets/components/styles with no tab dependency.

## Dependency rules
Allowed directions (subset order):
- `app` → `ui`, `projects`, feature packages, `shared.util`, `media`.
- `ui.tabs.*` → the corresponding feature package(s), `projects`, `export`, `media`, `shared.util`, and `ui.commons`.
- `ui.commons` → `shared.util` only (must not depend on any specific tab or feature).
- Feature packages may depend on `shared.util` and other leaf services (`media`) but not on `ui` or `app`.
- `shared.util` must not depend on any feature/ui/app.
- Cycles are forbidden.

## UI component conventions
- Encapsulate business logic or visual style in focused components with clear APIs.
- Prefer extracting repeated or cohesive UI widgets into separate component classes instead of keeping them as builder methods inside a tab panel.
- Keep theme-level colors in `UiStyles`; UI components should consume shared style constants instead of defining local palettes.
- Favor composition over inheritance; keep components testable.
- Prefer immutable value objects for inputs/outputs where practical.

## Naming
- Names must reflect intent and feature (e.g., `FFmpegCommandBuilder`, `RenderQueue`, `RulesEngine`).
- Avoid ambiguous names like `Utils`/`Helper`; prefer precise nouns.

## Testing
- Pure logic (e.g., rules engine, timecode, geometry math) belongs in feature or `shared.util` and should have unit tests.

## Migration guidance
- When touching legacy code in `org.litvin` that mixes concerns, rehome it into the appropriate feature package or `shared.util`/`ui.commons`.
- Keep public APIs stable; refactor internals behind components.

## File size and structure
- Kotlin source files longer than 500 lines are discouraged.
- If a file exceeds 500 lines:
  - Prefer splitting it into smaller, focused components (classes, files, or top‑level functions) that align with the feature/package boundaries above.
  - If keeping it as a single file is deliberate, add a clear file‑level KDoc at the top that explains why this size is preferred and what trade‑offs were considered.
- This is a guideline, not a hard limit; exceptions must be justified as above.

---

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
    - `fun render(state: <Feature>ViewState)` — idempotent; updates the UI from immutable state only.
    - `fun renderEffect(effect: <Feature>ViewEffect)` — optional, for one‑off UI commands (dialogs, focus, notifications).
- Presenter (in `org.litvin.ui.tabs.<feature>.presenter`):
  - Orchestrates user intents, domain use‑cases/services, and produces `ViewState`.
  - Lifecycle aware.
  - Required methods:
    - `fun attach(view: <Feature>View)` / `fun detach()`
    - `fun onIntent(intent: <Feature>Intent)`
    - `fun onActivated()` / `fun onDeactivated()` (hooked to tab lifecycle)
- Model/Domain (in feature packages e.g., `org.litvin.scoring`, `org.litvin.markup`):
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
- Domain/services: the corresponding feature packages (`org.litvin.scoring`, `org.litvin.markup`, etc.).
- Dependency direction: View → Presenter(contract) → Domain/Services. Domain must not depend on UI.

### Naming conventions
For a feature `Foo` (e.g., Scoring, Markup):
- Interfaces/classes:
  - `FooView`, `FooPresenter`, `FooViewState`, `FooIntent`, `FooViewEffect` (optional).
- Presenter methods:
  - `attach`, `detach`, `onIntent`, `onActivated`, `onDeactivated`.
- State is immutable (`data class FooViewState(…)`). Avoid mutable UI‑driven state outside this object.

### Do / Don’t
Do:
- Keep Views free of business logic; only translate widgets to intents and render state.
- Encapsulate all enable/disable visibility decisions in `ViewState`.
- Extract reusable rules to feature packages with unit tests.
- Keep effects (one‑shot commands) separate from persistent `ViewState`.

Don’t:
- Don’t call Swing from domain/services.
- Don’t keep hidden UI state that affects logic (single source of truth = `ViewState`).
- Don’t make Presenters depend on concrete Swing classes; depend on `…View` interfaces.

### Minimal example (Scoring)
```kotlin
// ui/tabs/scoring/presenter/ScoringContracts.kt
interface ScoringView {
    fun render(state: ScoringViewState)
    fun renderEffect(effect: ScoringViewEffect) {} // optional default no‑op
}

interface ScoringPresenter {
    fun attach(view: ScoringView)
    fun detach()
    fun onActivated()
    fun onDeactivated()
    fun onIntent(intent: ScoringIntent)
}

data class ScoringViewState(
    val p1Name: String,
    val p2Name: String,
    val scoreText: String,
    val timeline: List<String>,
    val isPlaying: Boolean,
    val speed: Float,
    val autosavePending: Boolean,
)

sealed class ScoringIntent {
    data class PointWon(val side: Side): ScoringIntent()
    data object Undo: ScoringIntent()
    data class SeekBy(val ms: Long): ScoringIntent()
    data class ChangeSpeed(val multiplier: Float): ScoringIntent()
    data object PlayPause: ScoringIntent()
}

sealed class ScoringViewEffect {
    data class ShowError(val message: String): ScoringViewEffect()
}
```

View wiring sketch (Swing):
```kotlin
class SwingScoringPanel(private val presenter: ScoringPresenter): JPanel(), ScoringView {
    override fun addNotify() {
        super.addNotify()
        presenter.attach(this)
        presenter.onActivated()
    }
    override fun removeNotify() {
        presenter.onDeactivated()
        presenter.detach()
        super.removeNotify()
    }
    override fun render(state: ScoringViewState) { /* update labels/buttons/lists */ }
}
```

### Testing guidance
- Presenter tests: send `Intent`s, assert emitted `ViewState`/`Effect`s. No Swing involved.
- Domain tests: verify pure logic in `org.litvin.<feature>` packages.

### Migration checklist (per tab)
1) Introduce `…View`, `…Presenter`, `…ViewState`, `…Intent` contracts.
2) Move logic from Swing components into the Presenter, step by step.
3) Extract reusable computations to the corresponding feature packages.
4) Ensure `render(state)` is the only way the View changes persistent UI state.
5) Preserve threading rules: heavy work off‑EDT; rendering on EDT.
