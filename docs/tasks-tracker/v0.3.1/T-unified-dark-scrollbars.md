### T: Unified dark scrollbars across the app
- [x] Done

#### Supported user workflows
- Scrolling in all tabs and dialogs uses a consistent dark scrollbar style.
- Hover/drag visual feedback on scrollbar thumb is visible and subtle.
- Horizontal scrollbars (when present) match the same style.

Out of scope (this version)
- Global LAF changes and non-Swing platforms.

#### Solution outline
- Centralize styling in `org.litvin.ui.commons.ScrollHelper`:
  - `applyDarkScrollbar(scroll: JScrollPane, background: Color? = null)`
  - `applyDarkScrollbars(root: Container)` for recursive application when needed.
- Visuals: track `#202020`, thumb `#333333` (hover/drag `#444444`), 10 px width, rounded 8 px, no arrow buttons; non-opaque blending with parents; `unitIncrement=16`.
- Apply helper to all `JScrollPane` instances across tabs/components.

#### Tasks for implementation
- T1: Add helper functions to `ScrollHelper`. ✓
- T2: Refactor `PointsListPanel` to use the helper. ✓
- T3: Apply helper in Markup (`PointsCardsView`). ✓
- T4: Apply helper in Projects (`SwingProjectsPanel.wrapIntoTransparent`). ✓
- T5: Apply helper in Scoring (`HotkeysHelpPanel`, `ScoringHelpDialog`). ✓
- T6: Apply helper in Adjustments (`SwingAdjustmentsPanel`). ✓
- T7: Apply helper in Export (`CompletedRendersList`). ✓
- T8: Quick audit for other `JScrollPane` usages. ✓

#### Acceptance criteria
- All scrollbars in the app match the dark theme (track `#202020`, thumb `#333`, hover/drag `#444`).
- Scrollbar width is 10 px, with rounded thumb and no arrow buttons.
- Scroll components and viewports are non-opaque and visually blend with surrounding backgrounds.
- Mouse wheel and keyboard scroll behavior remains unchanged.
- No duplicated inline scrollbar styling remains where the helper is used.

#### Implementation guide
- Place helper in `org.litvin.ui.commons.ScrollHelper` alongside `scrollIntoView()`.
- Import and invoke `applyDarkScrollbar(...)` immediately after creating `JScrollPane` instances.
- Pass container background color where helpful to ensure seamless blending.
- Keep horizontal bars styled similarly or disable them explicitly where not needed.

#### Open questions
- None for this version; adjust colors/widths centrally if the design system evolves.
