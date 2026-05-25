# v0.3.0 — Adjustments: Crop/Rotate Tab (Spec & Tasks)

## Supported user workflows
- Configure geometry for export using a still-frame canvas with overlay.
- Scrub the timeline via seek bar to pick a frame for precise geometry tuning; no playback.
- Adjust Zoom, Pan X/Y, and Rotation via sliders and by manipulating the overlay and rotation handle.
- Reset Transform section or Reset All.

Out of scope (v0.3.0):
- Continuous playback in this tab (must remain paused/still-image only).
- Disk-based frame cache; only in-memory buffering may be used.
- Free-aspect crops; overlay aspect is locked to output aspect.

## Solution outline
- Left preview: canvas rendering a single video frame acquired at the current seek position. No play/pause.
- Right controls: Zoom, Pan X, Pan Y, Rotation; synchronized both ways with the model.
- Overlay: axis-aligned rectangle representing output crop; image content rotates, overlay remains unrotated.
- Interaction: drag corners/edges to resize; drag inside to move; rotation via dedicated handle; keyboard nudges optional.
- Data: continue using `AdjustmentsV1` (`zoom`, `panX`, `panY`, `rotationDeg`). Persist via `AdjustmentsStore` with debounced autosave.
- Frame source: use VLCJ to step/seek and capture a frame without starting playback; coalesce requests on scrubbing.

Constraints and invariants
- Never auto-start playback in this tab; seeking updates only the still frame.
- Maintain overlay aspect equal to output aspect; clamp overlay to rotated-image inscribed bounds.
- Rotation is independent of overlay orientation; the overlay stays axis-aligned in output space.

## Tasks for implementation (granular)
- T1 — Layout: split panel scaffolding (left canvas + right controls)
  - [x] Done
  - Acceptance criteria
    - A container with two regions: Left (preview stack) and Right (controls column), resizable; minimum sizes defined (Left ≥ 640×360; Right ≥ 280 px width).
    - Component IDs: `adj-cr-root`, `adj-cr-left`, `adj-cr-right`.
  - Implementation guide
    - Use `BorderLayout` or `JSplitPane` with divider at ~68%/32%. Persist last divider location in prefs.

- T2 — Left preview stack: header + canvas + seek bar
  - [x] Done
  - Acceptance criteria
    - Stack contains: `header` row (title + Reset), `canvas` area (opaque), `seek` row (JSlider with tooltip and labels hidden).
    - Component IDs: `adj-cr-left-header`, `adj-cr-canvas`, `adj-cr-seek`.
  - Implementation guide
    - Use `BorderLayout` within left region; seek at SOUTH, header at NORTH, canvas at CENTER.

- T3 — Canvas baseline and frame capture plumbing (no overlay yet)
  - Acceptance criteria
    - When seek position changes, the canvas shows the corresponding frame; playback never starts.
    - Debounced scrubbing (≥100 ms) with last-value-wins.
    - Placeholder (last-good frame or neutral background) shown while fetching.
  - Implementation guide
    - Keep a paused VLC instance; implement `requestFrameAt(ms)` that schedules capture on a single-thread executor and repaints on EDT.

- T4 — Draw pipeline and double buffering
  - Acceptance criteria
    - Rendering order: background → rotated image (rotationDeg) → dim mask (outside overlay) → overlay/handles → rotation guide.
    - No tearing/flicker during rapid seeks.
  - Implementation guide
    - Use a back buffer `BufferedImage` if necessary; clip to letterbox area; scale-to-fit with aspect preserved.

- T5 — Overlay visuals (static)
  - Acceptance criteria
    - Draw export-aspect-aligned rectangle; draw 4 corner + 4 edge handles; dim outside area with configurable opacity.
    - Visual constants documented (stroke widths, colors, handle sizes in px, hover/active colors).
  - Implementation guide
    - Provide constants in the component; ensure high-DPI scaling uses device scale factor.

- T6 — Hit-testing and cursors
  - Acceptance criteria
    - Hovering shows appropriate cursors: `nwse`, `nesw`, `ew`, `ns`, `move`, `rotate`.
    - Interior, edges, and corners are distinguishable; rotation handle hover works.
  - Implementation guide
    - Compute hit regions; update cursor on `mouseMoved`.

- T7 — Drag behaviors: move/resize with constraints
  - Acceptance criteria
    - Drag inside moves overlay; edges/corners resize while preserving output aspect; overlay stays within inscribed bounds.
    - Movements/resizes update model (`zoom`, `panX`, `panY`) in real time.
  - Implementation guide
    - Convert drag deltas → overlay rect → model via formulas in Geometry math; clamp and coalesce to ≤120 Hz.

- T8 — Rotation handle interaction
  - Acceptance criteria
    - Rotation handle placed ~24 px above overlay top-center; dragging updates `rotationDeg` continuously.
    - Snaps to 0/90/180/270° when within 2° or when holding Shift.
  - Implementation guide
    - Angle via `atan2` around overlay center; keep overlay axis-aligned; repaint throttle ≤120 Hz.

- T9 — Right controls: sliders and labels
  - Acceptance criteria
    - Controls present with labels, tooltips, and IDs: `adj-zoom`, `adj-pan-x`, `adj-pan-y`, `adj-rot`.
    - Ranges: Zoom UI 0–200% (maps to model clamp [0.1, 4.0]); Pan X/Y -100..+100; Rotation -180..+180° (0.5° step).
    - Section header "Transform" with a Reset button `adj-transform-reset`.
  - Implementation guide
    - Use `JSlider` + `JTextField` readouts; show current numeric value near each slider.

- T10 — Two-way binding and autosave
  - Acceptance criteria
    - Slider changes update canvas/overlay immediately; overlay/rotation drags update sliders instantly.
    - Changes propagate to `AdjustmentsStore`; writes debounced 300–500 ms.
  - Implementation guide
    - Compare old/new before publishing to avoid loops; use a simple guard flag while reflecting UI updates.

- T11 — Seek bar behavior (still image only)
  - Acceptance criteria
    - Seek slider scrubs time; canvas updates; play/pause control is absent/disabled in this tab.
  - Implementation guide
    - Debounce while dragging; final seek on release triggers an immediate frame request.

- T12 — Keyboard focus and optional nudges
  - Acceptance criteria
    - Tab order reaches overlay and controls; Arrow=1 px, Shift+Arrow=10 px when overlay focused.
  - Implementation guide
    - Add focus ring on overlay; key bindings gated by a feature flag if needed.

- T13 — Error handling and fallbacks
  - Acceptance criteria
    - On repeated frame failures, show a non-blocking banner; canvas keeps last-good frame.
  - Implementation guide
    - Centralize capability detection and retry with backoff; throttle logs.

- T14 — Performance instrumentation
  - Acceptance criteria
    - Record p50/p95 for seek→frame and paint in debug logs; idle CPU ≤ 5%, active drag ≤ 1 core.
  - Implementation guide
    - Lightweight timers around capture and paint paths; developer toggle to enable logs.

- T15 — Unit tests (pure math)
  - Acceptance criteria
    - Overlay↔model round-trip within 1 px/0.5° on typical sizes; clamping edge cases covered.
  - Implementation guide
    - Test utilities for mapping and inscribed-rect calculations; no Swing in tests.

## Geometry math (reference)
- For output size `OutW × OutH`:
  - `cropW = round(OutW / zoom)`, `cropH = round(OutH / zoom)`.
  - From overlay top-left `(x, y)` in output space, with `cropW/H`:
    - `panX = clamp(((2*x + cropW - OutW) / (OutW - cropW)), -1, +1)` when `cropW < OutW`, else 0
    - `panY = clamp(((OutH - (2*y + cropH)) / (OutH - cropH)), -1, +1)` when `cropH < OutH`, else 0
  - Inversion for slider -> overlay: `zoom = OutW / overlayW`; compute center and then `(x,y)` accordingly.
- Rendering order: background → rotated image → dim mask → overlay/handles → rotation guides.

## Open topics/questions
- Confirm default output aspect/source used for overlay constraints when project has not fixed export size.
- Decide whether keyboard nudges ship in v0.3.0 or are deferred.
- Cross-platform check for snapshot/callback stability and latency.
