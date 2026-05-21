# Scoring Tab — Help button and popup dialog (v0.3.1)

- [x] Done

## Supported user workflows
- Open contextual Help while on Scoring tab via a dedicated Help button.
- Read concise guidance on the overall scoring flow:
  - Input player names for the scoreboard.
  - Review points created on the Markup tab.
  - For each point, decide which player gets the point (or mark as no point).
  - Generate the scoreboard on video export (Export tab).
- Review keyboard shortcuts relevant to Scoring (seek, play/pause, assign outcomes, next point), presented within the Help popup instead of an always-visible panel.

### Out of scope (v0.3.1)
- Changing the actual scoring rules/engine.
- Export UI/logic changes beyond linking/mentioning where the scoreboard is generated.
- Hotkeys reconfiguration (remain as-is; dialog just reflects current shortcuts).
- Comprehensive scoring tutorial; keep content short and task-oriented.

## Solution outline
- Replace the inline `HotkeysHelpPanel` on the Scoring tab with a compact Help entry point:
  - Add a Help button (icon + text "Help") on the Scoring tab UI (preferred: within `ControlsToolbar`, right-aligned, or in `MatchHeaderPanel` secondary actions). Exact placement to be decided (see Open questions).
  - Clicking the Help button opens a modal, resizable dialog with two sections:
    1) "What to do here" — bullet list describing the scoring flow.
    2) "Hotkeys" — the same table of shortcuts previously shown inline.
- Create a simple leaf component `ScoringHelpDialog` (Swing) that:
  - Accepts a supplier of the hotkeys map to keep it consistent with key bindings.
  - Uses existing styles from `UiStyles` where possible.
  - Is self-contained; no domain/service calls.
- Update `SwingScoringPanel` to:
  - Remove/stop composing `HotkeysHelpPanel` in the layout.
  - Provide the Help button and show the dialog on click.
  - Keep existing key bindings unchanged.
- Keep architecture compliance: container (`SwingScoringPanel`) wires UI actions; leaf dialog remains passive.

## Tasks
- T1 — Help button placement and wiring
  - Add a Help button (icon tooltip: "F1 — Help").
  - Wire action to open the new dialog (modal, centered over Scoring tab window).
- T2 — Implement `ScoringHelpDialog`
  - Title: "Scoring — Help".
  - Left panel: "What to do" guidance using compact bullets.
  - Right panel: Hotkeys table (reuse logic from `HotkeysHelpPanel` or embed it).
  - Make dialog resizable and remember last size/position per session (optional nice-to-have).
- T3 — Migrate from inline `HotkeysHelpPanel`
  - Remove the `HotkeysHelpPanel` from `SwingScoringPanel` layout.
  - Ensure no empty gaps remain; compact the vertical spacing.
- T4 — Keyboard shortcut for Help
  - Bind `F1` (when Scoring tab is active and focus not in text field) to open the Help dialog.
  - Avoid global conflicts with other tabs.
- T5 — Content finalization
  - Fill the guidance section with the following bullets (dynamic names allowed):
    - "Input player names for the scoreboard" (left list footer fields).
    - "Review points created on the Markup tab" (left timeline list).
    - "For each point, choose: Player 1, No point, Player 2".
    - "Scoreboard overlays are produced during Video export".
  - Include a miniature legend about what “scored” checkmark means in the list.
- T6 — Styling and UX polish
  - Apply dark theme colors and borders consistent with existing UI (`UiStyles`).
  - Ensure dialog content scrolls if smaller than content.
  - Set minimum size to prevent cramped layout.
- T7 — Cleanup and references
  - Update any in-code comments referencing the inline hotkeys panel.
  - Keep `HotkeysHelpPanel` class for potential reuse elsewhere (no deletion in this task).
  - Cross-reference in dialog: add a link-like hint "Edit points in Markup tab" (non-clickable text is OK for now).

## Acceptance criteria
- A Help button is visible on the Scoring tab and opens a modal dialog titled "Scoring — Help".
- The inline hotkeys panel is no longer shown on the Scoring tab body.
- The dialog shows two clear sections:
  - What to do: the four bullets listed above.
  - Hotkeys: table with the existing shortcuts (Q/W/E, R, Space, Arrow keys, Shift+Arrows, Up/Down).
- `F1` opens the dialog when the Scoring tab is active (and does not interfere with text editing fields).
- Dialog is resizable, thematically consistent, and content is readable with scrolling when needed.
- No regressions to scoring actions, hotkeys, or persistence of outcomes/names.

## Implementation guide
- Placement:
  - Prefer adding the Help control into `ControlsToolbar` as a rightmost action (icon `?`), or as a small button in `MatchHeaderPanel` if space is constrained. Keep consistent with existing toolbar styling.
- Dialog:
  - New class `org.litvin.ui.tabs.scoring.ui.ScoringHelpDialog` extending `JDialog` or `JOptionPane`-like wrapper.
  - Compose two panels side by side for guidance and hotkeys (use `JSplitPane` or `GridLayout(1,2)` with scroll panes).
  - Accept `Supplier<Map<String,String>>` for hotkeys (reuse supplier from `SwingScoringPanel`).
  - Consider reusing `HotkeysHelpPanel` inside the dialog to avoid duplicate table code.
- Wiring:
  - In `SwingScoringPanel.buildCenterControls()` or toolbar construction, create Help button and action listener that instantiates the dialog on demand (cache optional).
  - Add a key binding for `F1` in `installKeyBindings()` guarded by `isTextEditingFocus()`.
- Removal:
  - Remove `hotkeysHelpPanel` field usage and composition. Keep class `HotkeysHelpPanel` source intact for reuse.
- Tests/verification:
  - Manual UI check: Help button visible, opens dialog, content correct, hotkeys accurate, `F1` works.

## Open questions
- Exact placement: ControlsToolbar vs Header panel — which aligns better with current design guidelines?
- Should the dialog remember its bounds across app sessions (persisted in settings) or just within the session?
- Should we expose a direct "Go to Markup" button inside the dialog (would cross tabs) — keep out of scope for now?
- Do we want a dedicated Help icon asset, or is a text button acceptable for v0.3.1?

## Traceability
- Issue: Replace inline hotkeys panel with a Help button opening a popup containing both workflow guidance and hotkeys.
- Related components: `SwingScoringPanel`, `ControlsToolbar`, `MatchHeaderPanel`, `HotkeysHelpPanel`, `UiStyles`.
- Architecture: Must comply with `docs/architecture-rules.md`.
