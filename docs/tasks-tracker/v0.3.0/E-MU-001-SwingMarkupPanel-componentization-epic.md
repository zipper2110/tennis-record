# E-MU-001: SwingMarkupPanel componentization epic

- [ ] Done

Date: 2026-05-20 00:08 (local)

Context
- `src\main\kotlin\org\litvin\ui\tabs\markup\SwingMarkupPanel.kt` is ~1048 lines (see T-FS-001 audit, 2026-05-19). It mixes layout/rendering for point cards and table, transport controls, selection, context menus, edit dialogs, autosave, and keybindings.
- Goal: decompose into cohesive Swing components with clear responsibilities and minimal public APIs, aligned with architecture rules.

References
- Size audit: `docs\tasks-tracker\v0.3.0\T-FS-001-file-size-audit-2026-05-19.md` (item for SwingMarkupPanel).
- Similar pattern: `E-SC-001: SwingScoringPanel componentization epic`.

Supported user workflows (in scope)
- Browse and select markup points as cards and as a table.
- Create/edit/delete a markup point.
- Navigate playback and jump to point start/end; toggle play/pause.
- Set point start/end at current playhead.
- Auto-activation of points based on playhead, and ability to jump to selected.
- Keyboard shortcuts for the above operations.
- Autosave and explicit save of markup.

Out of scope (for this epic)
- Scoring tab UI and logic (separate epic E-SC-001).
- Export/Projects tabs.
- Media decoding/playback engine internals beyond the existing UI-facing APIs.

Solution outline
- Extract leaf Swing components with dedicated purpose and a narrow public API. Suggested slices:
  - `MarkupToolbar` — top-level actions (save now, maybe reload), autosave indicator.
  - `TransportControls` — play/pause, jump to selected, start/end at playhead, time display.
  - `PointsCardsView` — vertical list/grid of point "cards" with selection visuals and scroll-to-selected.
  - `PointsTableView` — table view of points with a dedicated table model and cell renderers/editors.
  - `EditPointDialog` — dialog for creating/editing a single point.
  - `ContextMenuBuilder` — builds the table/cards context popup(s).
  - `Keybindings` — installs/uninstalls all key bindings for the Markup tab.
  - `AutosaveController` — debounced scheduling and save triggering (no EDT blocking).
- Define narrow interfaces for data and commands:
  - View-to-domain/container: `MarkupActions` (play/pause, seek, select, create/edit/delete, setStart/EndAtPlayhead, save, etc.).
  - Domain/container-to-view: `MarkupViewState` (immutable snapshot: playback status/time, selection, pending draft start, list of points DTOs, autosave status). Components consume only the slice they require.

Component map (v0.3.0)
- Container: `org.litvin.ui.tabs.markup.SwingMarkupPanel` — composes leaf components and wires to services.
- Contracts: `org.litvin.ui.tabs.markup.MarkupActions`, `org.litvin.ui.tabs.markup.MarkupViewState` (+ DTOs: `PointDto`, `PointPatch`, `AutosaveState`).
- Toolbar: `org.litvin.ui.tabs.markup.toolbar.MarkupToolbar` — top-level actions, autosave indicator.
- Transport: `org.litvin.ui.tabs.markup.transport.TransportControls` — playback/time/seek controls.
- Cards view: `org.litvin.ui.tabs.markup.cards.PointsCardsView` — card list and selection.
- Table view: `org.litvin.ui.tabs.markup.table.PointsTableView` — table + model/renderers/editors.
- Edit dialog: `org.litvin.ui.tabs.markup.dialog.EditPointDialog` — create/edit single point.
- Context menus: `org.litvin.ui.tabs.markup.menu.ContextMenuBuilder` — popups for views.
- Keymap: `org.litvin.ui.tabs.markup.keymap.Keybindings` — installs/uninstalls all tab shortcuts.
- Autosave: `org.litvin.ui.tabs.markup.autosave.AutosaveController` — debounce + save orchestration.
- Wiring rules:
  - `SwingMarkupPanel` becomes a thin container composing the components, binding them to existing services (e.g., `MarkupDispatcher`, media/playhead controller, persistence) via `MarkupActions` and pushing `MarkupViewState` snapshots.
  - No cross-component coupling; communication flows through the container and the contracts.
- Performance/UX constraints
  - Keep UI responsive; avoid blocking the EDT (IO and heavy computations off-EDT).
  - Card and table repaint paths should remain smooth with hundreds of points.
  - Preserve existing interactions (mouse + keyboard) and visual cues (selection, hover, pending draft).

Architecture compliance
- Place components under `org.litvin.ui.tabs.markup.*` subpackages (e.g., `markup.toolbar`, `markup.transport`, `markup.cards`, `markup.table`, `markup.dialog`, `markup.menu`, `markup.keymap`, `markup.autosave`).
- Container (`SwingMarkupPanel`) may depend on domain services; leaf components depend only on `MarkupViewState`/`MarkupActions` and UI commons.

Major invariants
- Component APIs do not leak domain types unnecessarily; use simple DTOs/state snapshots.
- Event handling remains deterministic; no duplicate dispatching on refactor.
- Functional parity with current `SwingMarkupPanel`.

Contracts (draft)
- `org.litvin.ui.tabs.markup.MarkupActions`
  - `togglePlayPause()`
  - `seekTo(ms: Long)`
  - `jumpToSelected()`
  - `setStartAtPlayhead()`
  - `setEndAtPlayhead()`
  - `createPointAt(ms: Long)`
  - `editPoint(id: String, patch: PointPatch)`
  - `deletePoint(id: String)`
  - `selectByVisualIndex(index: Int)`
  - `saveNow()`
- `org.litvin.ui.tabs.markup.MarkupViewState`
  - `isPlaying: Boolean`
  - `currentTimeMs: Long`
  - `selectedVisualIndex: Int?`
  - `pendingDraftStartMs: Long?`
  - `points: List<PointDto>` (id, time bounds, label, flags)
  - `autosave: AutosaveState` (pending: Boolean, lastSavedAt: Long?)

Tasks

## T1: Define component map and contracts
- [x] Done

Acceptance criteria
- A concise component map documented here and referenced from `SwingMarkupPanel` KDoc.
- Kotlin interfaces `MarkupActions` and `MarkupViewState` drafted next to the container and referenced by new components.
- No functional change yet; compile passes.

Implementation guide
- Identify minimum command and state surfaces based on current handlers in `SwingMarkupPanel.kt` (keybindings, menu items, buttons).
- Draft `PointDto`, `PointPatch`, and `AutosaveState` simple data classes in markup package if needed.

Open questions
- Keep a single `MarkupViewState` or split per component? Default: single snapshot with sub-views.

## T2: Extract PointsTableView (model/renderers/editors)
- [x] Done

Acceptance criteria
- A new `PointsTableView` encapsulates `JTable` setup with its `PointsTableModel`, column names, cell renderers, and action button editor.
- No direct domain references; communicates via `MarkupActions` and receives a slice of `MarkupViewState`.

Implementation guide
- Move `PointsTableModel`, `ActionButtonRenderer`, `ActionButtonEditor`, and table init code.
- Keep column order, editors, and shortcuts behavior the same.

Open questions
- Confirm which table operations create side-effects vs pure selection changes.

## T3: Extract PointsCardsView
- [x] Done

Acceptance criteria
- A new `PointsCardsView` renders cards (including the "pending" card), handles selection visuals and click-to-select.
- Provides `scrollToVisualIndex(index)` API to help the container keep selection in view.

Implementation guide
- Move `buildPendingCard`, `buildPointCard`, `applyCardSelectionStyle`, `setSelectedVisual`, `scrollCardIntoView` and related helpers.

Open questions
- Confirm exact card sizing behavior and any dynamic style rules.

## T4: Extract TransportControls
- [x] Done

Acceptance criteria
- A `TransportControls` component surfaces play/pause button, time display, and actions: start/end at playhead, jump to selected.
- Updates reflect `isPlaying` and `currentTimeMs`.

Implementation guide
- Move `togglePlayPause`, `updatePlayPauseButton`, `updateTimeUI`, `jumpToSelected`, `onStartAtPlayhead`, `onEndAtPlayhead`, and listeners.

Open questions
- Time formatting utilities location (reuse existing `Timecode` utilities if present).

## T5: Extract MarkupToolbar
- [x] Done

Acceptance criteria
- A toolbar hosts top-level actions (save now; show autosave pending state).
- Emits callbacks through `MarkupActions` only.

Implementation guide
- Port button creation and autosave indicator. Preserve icons/styles.

Open questions
- Which actions belong here vs in context menus?

## T6: Extract EditPointDialog
- [x] Done

Acceptance criteria
- `EditPointDialog` shows/edit fields for a single point; supports confirm and delete actions.
- No long-running work on EDT.

Implementation guide
- Move `showEditDialog` and related form-row builder(s).

Open questions
- Validate fields and error states; confirm modality.

## T7: Extract ContextMenuBuilder
- [x] Done

Acceptance criteria
- Context menus for table/cards are built by a small helper; actions wired to `MarkupActions`.

Implementation guide
- Move `buildTableContextMenu` and any other popup construction code.

Open questions
- Ensure menu item enable/disable logic mirrors current selection rules.

## T8: Consolidate Keybindings
- [x] Done

Acceptance criteria
- A dedicated `Keybindings` helper installs/uninstalls all key bindings for the Markup tab.

Implementation guide
- Move `installKeyBindings` and helpers; ensure conditions (e.g., ignore while text editing focus) preserved.

Open questions
- Confirm all shortcuts are documented somewhere in UI (consider a small help panel later).

## T9: AutosaveController
- [x] Done

Acceptance criteria
- Debounced autosave scheduling and immediate save behavior extracted; no EDT blocking; integrates with toolbar indicator and persistence.

Implementation guide
- Move `scheduleAutosave`, `autosaveNow`, and `saveNow` into a controller; expose callbacks to container.

Open questions
- Decide debounce delay and persistence feedback UX.

## T10: Make SwingMarkupPanel a thin container
- [x] Done

Acceptance criteria
- `SwingMarkupPanel` composes the extracted components; holds minimal layout and binding glue.
- No large nested classes remain; file size well under 500 lines.

Implementation guide
- Wire `MarkupActions` implementations to existing services (`MarkupDispatcher`, playback/playhead controller, persistence), and publish `MarkupViewState` snapshots to the leaves.

Open questions
- Consider paging/virtualization if point count grows very large.

Notes
- Re-run the file size audit (T-FS-001) after refactor; either remove the item or justify if still >500 with a KDoc.
