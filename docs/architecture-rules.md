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
