# Projects Tab Architecture Findings

Date: 2026-06-01

Scope: `src/main/kotlin/org/litvin/ui/tabs/projects/SwingProjectsPanel.kt` checked against `docs/architecture-rules.md`.

## Findings

1. Status: Resolved
   Severity: High
   Rule: UI architecture: MVP (Passive View)
   Finding: `SwingProjectsPanel` mixes Swing rendering with project creation, manifest reads/writes, recents scanning, source-video selection flow, and pagination state.
   Remediation: Added Projects MVP contracts and `DefaultProjectsPresenter`. `SwingProjectsPanel` now forwards intents, renders immutable view state, and handles one-off UI effects only.

2. Status: Resolved
   Severity: High
   Rule: Package topology and dependency rules
   Finding: Project-domain objects (`ManifestIO`, `ProjectManifestV1`, `RecentsProvider`) live in the legacy `org.litvin` root package instead of `org.litvin.projects`.
   Remediation: Rehomed project manifest and recents services under `org.litvin.projects`, added `ProjectsRepository`, and updated UI imports to use the feature package explicitly.

3. Status: Resolved
   Severity: Medium
   Rule: Threading/EDT rules
   Finding: Disk operations for scanning, reading, creating, and writing project manifests run directly from Swing event handlers.
   Remediation: Moved those operations into `DefaultProjectsPresenter`; IO runs through an executor and view rendering/effects are marshalled back to the EDT.

4. Status: Resolved
   Severity: Medium
   Rule: Encapsulate business logic or visual style in focused components with clear APIs
   Finding: The panel contains persistent UI decisions and derived state, including current project summary, secondary-line resolution, page boundaries, page counts, and button enablement.
   Remediation: Persistent state is computed in `ProjectsViewState`; `SwingProjectsPanel.render(state)` applies it idempotently.

5. Status: Resolved
   Severity: Low
   Rule: File size and structure
   Finding: `SwingProjectsPanel.kt` is 531 lines, above the discouraged 500-line threshold, without a file-level exception note.
   Remediation: Split presenter/contracts/domain services out of the panel. `SwingProjectsPanel.kt` is now 317 lines.
