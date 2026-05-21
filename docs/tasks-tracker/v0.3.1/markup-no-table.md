# v0.3.1 — Markup panel: remove table view

- [x] Done

Supported user workflows
- Browse and manage markup points via the cards list only.
- Out of scope: any table/grid presentation of points for this version.

Solution outline
- `SwingMarkupPanel` composes the right-side panel with `PointsCardsView` only.
- The previous `JTabbedPane` (Cards/Table) is removed; no `Table` tab is present.
- All state propagation (`pushTableState`) and references to `PointsTableView` are removed from the container.
- Domain logic and autosave remain unchanged; only presentation is affected.

Tasks for implementation
- T1 — Remove table tab from `SwingMarkupPanel` right panel.
- T2 — Remove all `pushTableState()` calls and the `tableView` field.
- T3 — Ensure dispatcher callbacks only update cards/toolbar/timeline states.
- T4 — Verify build passes and runtime shows only the cards list with count badge.

Acceptance criteria
- The Markup panel shows no "Table" tab; only the cards view is visible.
- No runtime errors related to `PointsTableView`.
- Build is green.

Open topics/questions
- If the table view becomes unnecessary long-term, consider removing `PointsTableView` and related assets in a later cleanup task.
