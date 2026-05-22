# v0.3.1 — Adjustments: Color Tab UX fixes

- [x] Done

## Supported user workflows
- Same as v0.3.0 Adjustments Color tab.

Out of scope (v0.3.1):
- Any new color controls; this is a small layout/visual parity update only.

## Solution outline
- Keep the left video preview + right controls layout from v0.3.0.
- Make the right controls panel fixed-width to match other tabs (Markup/Scoring) and non-resizable by the user.
- Ensure the tab uses the app’s dark theme with no white backgrounds (scroll panes, headers, content rows, split background, etc.).

## Tasks for implementation
- T1 — Fixed-width right panel (non-resizable)
  - [x] Done
  - Acceptance criteria
    - Right panel width is fixed; the JSplitPane divider is not draggable and stays at windowWidth - RIGHT_PANEL_WIDTH on resize.
    - Divider has no visible drag handle.
  - Implementation guide
    - Set min/pref/max width on the right container to `RIGHT_PANEL_WIDTH`.
    - Disable split interaction, set `dividerSize = 0`, and recalc divider on component resize.

- T2 — Theme parity (no white backgrounds anywhere)
  - [x] Done
  - Acceptance criteria
    - All surfaces are dark: split background, left header/transport, right scroll and its viewport, content rows.
    - Text is legible on dark background and matches other tabs.
  - Implementation guide
    - Use `UiStyles.DARK_BG` for surfaces; set intermediate layout panels to `isOpaque = false` where they are purely structural.

## Open topics/questions
- RIGHT_PANEL_WIDTH can be tuned if needed; currently set to 260 px to match recent tweaks.
