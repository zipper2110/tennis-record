# v0.3.0 — Adjustments Tab (Spec & Task Breakdown)

## Purpose
This spec defines the reworked Adjustments experience split into two tabs — **Crop/Rotate** and **Color** — with distinct preview modes and synchronized controls. It refines v0.2.0 behavior to support frame‑based geometry editing on a canvas and normal video playback for color grading, while keeping the existing non‑destructive model and export mapping.

---

## 1) Scope & Terminology
- Non‑destructive adjustments, persisted per project, applied in export via FFmpeg.
- Two right‑panel tabs:
  - Crop/Rotate: geometry controls and canvas with a single video frame + draggable overlay frame.
  - Color: color controls and normal video player (play/pause + seek bar), updating in real time.
- “Overlay frame” denotes the visible export area rectangle drawn over the canvas; the outside area is masked/dimmed.
- Geometry parameters reflect the existing Adjustments model (`zoom`, `panX`, `panY`, `rotationDeg`). No new persistent fields are required in v0.3.0.

---

## 2) User Experience & Layout

### 2.1 Right Panel: Tabs
- Two tabs: **Crop/Rotate** and **Color**.
- Tabs are mutually exclusive; switching tabs switches the left preview mode accordingly.

### 2.2 Left Side when Crop/Rotate tab is active
- A canvas shows the current frame of the selected video at a fit scale (letterbox or pillarbox as needed).
- No playback controls (no play/pause). A seek bar underneath allows scrubbing to a different frame/time.
- Above the canvas is an overlay frame (rectangle with 4 corners and 4 edges) representing the exportable area.
  - The overlay does not rotate when the user rotates; only the image on the canvas rotates.
  - The region outside the overlay is dimmed.
- Interactions on overlay:
  - Drag any corner to resize both width and height.
  - Drag any edge to resize along one axis.
  - Drag inside the overlay to move it.
  - Rotate the content using a dedicated control:
    - Option A (recommended): a circular rotation handle near the overlay’s top‑center; dragging around pivots rotation.
    - Option B: hold a modifier key (e.g., Alt) + drag horizontally to rotate.
  - Cursor hints change on hover (nwse/nesw/ew/ns/rotate/move).
- Seek bar behavior:
  - Shows timeline progress; scrubbing updates the canvas to the corresponding frame (still image only).
  - Debounce/throttle frame requests for smooth scrubbing.
- Canvas zoom for viewing (not the model’s `zoom`): optional wheel zoom to examine details; this does not change adjustments, only the editor view.

### 2.3 Right Side Controls in Crop/Rotate tab
- Controls: **Zoom**, **Pan X**, **Pan Y**, **Rotation**.
  - Control IDs (parity with v0.2.0): `adj-zoom`, `adj-pan-x`, `adj-pan-y`, `adj-rot`.
  - UI ranges/display (for consistency):
    - Zoom: 0%..200% (step 1%), display as `X.xx×`; model `zoom` clamped internally to [0.1, 4.0], default 1.0.
    - Pan X / Pan Y: -100..+100 (step 1), display `-1.00..+1.00` normalized; model range [-1.0, +1.0], default 0.0.
    - Rotation: -180..+180° (step 0.5), display with `°`; model `rotationDeg` clamped to [-180, +180], default 0.0.
- Live synchronization:
  - Changing sliders updates overlay position/size and rotates the canvas content.
  - Dragging/moving/resizing the overlay updates the corresponding controls in real time.
  - Rotating via the rotation handle updates the **Rotation** slider.
- Reset buttons:
  - Section “Transform” Reset: resets Zoom/Pan/Rotation to identity.
  - Global "Reset All" remains available at the panel header.
- Accessibility:
  - Keyboard nudges (optional): Arrow keys move overlay by 1 px (Shift = 10 px) when the overlay is focused; Tab order reaches controls and overlay.
  - Cursor hints and tooltips include ranges/defaults; maintain high contrast for handles.

### 2.4 Left Side when Color tab is active
- Normal VLCJ‑based player with:
  - Video viewport.
  - Play/Pause button.
  - Seek bar (as currently used in Markup/Scoring paradigms).
- All color adjustments apply live during playback.

### 2.5 Right Side Controls in Color tab
- Brightness, Contrast, Saturation, White Balance (Temperature, Tint), and optional Shadows/Highlights controls.
- Reset buttons:
  - Section “Color Grade” Reset.
  - Global “Reset All” at the panel header remains available.

---

## 3) Behavior & Synchronization Rules
- Model: continue to use `AdjustmentsV1` fields and ranges from v0.2.0:
  - `zoom: Float`, `panX: Float`, `panY: Float`, `rotationDeg: Float`
  - `brightness: Float`, `contrast: Float`, `saturation: Float`, `whiteBalance.temperature|tint`
- Synchronization:
  - Any change from controls or overlay updates the model; vice versa, model updates update UI.
  - Debounce autosave to `adjustments.json` (300–500 ms) on changes.
  - Cross‑tab store (`AdjustmentsStore`) remains single source of truth; both tabs subscribe to updates.
  - Avoid feedback loops: compare previous vs new values before publishing; coalesce rapid updates.
- Tab‑specific preview:
  - Crop/Rotate: show still frame image; apply geometry transforms in the canvas rendering pipeline only.
  - Color: show normal video playback; apply color filters via VLCJ `adjust` filter (or equivalents) as in v0.2.0.
- Fallbacks & errors:
  - If libVLC adjust/color filters are unavailable, controls still update the model; preview may degrade (apply on slider release or show a notice), export remains authoritative.
  - If frame fetch for Crop/Rotate fails or is slow, show a temporary placeholder (e.g., last good frame or low‑res thumbnail) and retry with debounce; never auto‑start playback while seeking.
  - No disk caching of frames in v0.3.0; in‑memory only, bounded by a small LRU if implemented.
- Export overlay rectangle is authoritative for how geometry maps to `zoom/pan`:
  - If the user manipulates the overlay, recompute `zoom/panX/panY` from the resulting rectangle.
  - Rotation is independent; rotating the canvas does not deform the overlay rectangle.

---

## 4) Geometry/Math Details

### 4.1 Mapping between overlay rectangle and model
- Let the visible canvas present the source frame with a known pixel size `SrcW × SrcH` in the canvas coordinate space (before rotation transform), scaled to fit within `CanvasW × CanvasH` with letterboxing.
- Maintain a virtual output size `OutW × OutH` (the export target). Default to project output resolution.
- Model mapping formulas (consistent with v0.2.0):
  - Given `zoom = z` (>0), compute crop box dimensions in output space: `cropW = round(OutW / z)`, `cropH = round(OutH / z)`.
  - Let overlay center in output coordinates be `(cx, cy)` with pan normalization:
    - `cx = (OutW - cropW)/2 + panX * (OutW - cropW)/2`
    - `cy = (OutH - cropH)/2 - panY * (OutH - cropH)/2` (note Y flip)
- For overlay‑>model inversion:
  - From overlay width/height in output space, compute `z = OutW / overlayW` (aspect fixed = `OutW:OutH`).
  - From overlay top‑left `(x, y)` compute normalized pans:
    - `panX = clamp(((2*x + cropW - OutW) / (OutW - cropW)), -1, +1)` when `cropW < OutW`, else 0
    - `panY = clamp(((OutH - (2*y + cropH)) / (OutH - cropH)), -1, +1)` when `cropH < OutH`, else 0
- Rotation:
  - `rotationDeg` is applied as a 2D rotation of the source image around the canvas center before overlay render.
  - Overlay rectangle remains axis‑aligned in output space; it does not rotate.

### 4.2 Canvas rendering order (Crop/Rotate)
1) Draw background (checker/black) over the letterboxed area.
2) Draw the source frame, transformed by `rotationDeg`, scaled to fit.
3) Draw the dimmed mask outside the overlay rectangle.
4) Draw the overlay rectangle stroke and corner/edge handles.
5) Draw rotation handle and guide if hovered/dragging.

### 4.3 Constraints & UX rules
- Maintain overlay aspect ratio equal to output aspect (prevent non‑uniform crops).
- Coverage and clamping policy:
  - The overlay rectangle must remain entirely within the valid image area after rotation. Compute the maximal axis‑aligned inscribed rectangle of the rotated source and clamp overlay moves/resizes to this region.
  - If current `zoom`/`rotationDeg` would expose uncovered areas, prefer clamping movement/size over allowing exposure. If exposure is unavoidable (edge cases while rotating interactively), fill uncovered pixels with black in preview to match export behavior.
- Minimum/maximums:
  - Minimum overlay size corresponds to maximum `zoom` (respect internal clamps, e.g., [0.1, 4.0]).
  - Maximum overlay size is bounded by the inscribed rectangle for the current rotation.
- Keyboard nudges when overlay is focused: arrows move by 1 px (Shift: 10 px). Optional; if enabled, obey clamping above.

---

## 5) Color Grading Details (Color tab)
- Live preview via libVLC `adjust` filter for brightness/contrast/saturation.
- White balance approximation as in v0.2.0 (hue/gamma and slight saturation skew) for preview.
- Shadows/Highlights (new UI, optional in v0.3.0):
  - Preview: approximation via gamma/curve if supported; otherwise, preview‑off with model persistence only.
  - Export: optional; if not included, denote as “export‑only later” in Known Limitations.

---

## 6) Performance & Throttling
- Controls coalesce updates to ≤120 Hz; last‑value‑wins throttling for sliders and overlay drags.
- Seek bar scrubbing on Crop/Rotate tab uses a low‑latency frame fetch path with debounce to avoid request storms.
- Canvas rendering should target 60 FPS on 1080p; degrade gracefully to 30 FPS on lower hardware.
- Budgets (guidelines on typical dev laptop with integrated GPU):
  - Crop/Rotate canvas render time ≤ 8 ms/frame at 1080p; color tab playback additional adjust filters ≤ 2 ms/frame.
  - CPU utilization while idle on Adjustments tab ≤ 5%; during active drag ≤ 1 full core.
  - Memory for frame buffering capped to ≤ 64 MB for in‑memory LRU (configurable), no disk caching in v0.3.0.
- Instrumentation:
  - Add lightweight timing scopes around frame fetch and canvas paint; log aggregated percentiles in debug builds.
  - Throttle logs to avoid IO overhead; see Telemetry section.

---

## 7) Technical Implementation

### 7.1 Data & State
- Keep `AdjustmentsV1` as the persisted schema (no changes to JSON fields or ranges).
- `AdjustmentsStore` remains the pub/sub source. Both tabs:
  - Subscribe to store changes and update UI.
  - Publish partial updates on user interactions.
- No additional persistent state for the Crop/Rotate seek position; it’s purely UI state.

### 7.2 Crop/Rotate Canvas: Frame Source
- Use VLCJ to obtain still frames without continuous playback:
  - Option A (preferred): open media paused; use position/time set commands and capture a snapshot or use direct rendering callbacks to acquire the current frame buffer which is drawn into the canvas.
  - Option B: if VLCJ callbacks are heavy, use the existing player hidden instance to step to time and copy the current frame into a `BufferedImage` (no playback).
  - Requirements:
    - No auto‑play; position jumps should not start playback.
    - Frame availability events routed back to the canvas component.

### 7.3 Overlay Interaction Component
- Implement a `GeometryViewportPanel`‑like component or extend the existing one to:
  - Render rotated image content + overlay in the order described.
  - Hit‑test corners, edges, interior, and rotation handle.
  - Convert drag deltas to overlay rectangle changes, and then to model values using formulas in Section 4.1.
  - Keep aspect ratio locked; clamp movement and min/max sizes.

### 7.4 Rotation UX
- Place a rotation handle at a small offset above the overlay’s top‑center (e.g., ~24 px gap). Cursor changes to rotate icon on hover.
- Drag in an arc around the overlay center. Compute angle by `atan2` of the pointer vector relative to overlay center. Update `rotationDeg` continuously.
- Snap to common angles (0°, 90°, 180°, 270°) when near or when holding Shift.

### 7.5 Colors Tab Player Integration
- Use `VlcjSwingMediaPlayerAdapter` as today for normal playback.
- Apply color settings via VLC adjust filters in real time; continue debounce/throttle behavior.
- When switching from Crop/Rotate to Color, release any frame‑capture resources and activate normal player.

### 7.6 Export
- Export pipeline remains the same as v0.2.0:
  - Geometry via `crop` + `scale` + `rotate` (or equivalent), computed from the model.
  - Colors via `eq` + WB mapping (and optional extra filters if S/H implemented for export).

### 7.7 Accessibility & Usability
- Provide tooltips with ranges/defaults (reuse v0.2.0 patterns).
- Ensure high‑contrast handles and keyboard operability for overlay manipulation where feasible.

### 7.8 Telemetry & Logging
- Export telemetry:
  - Log parameter applications on export including the exact filter graph and output resolution; include a model snapshot hash to avoid leaking full values repeatedly.
  - Do not log frame pixels or media paths in verbose mode; redact PII.
- Preview/debug telemetry:
  - Debug‑log overlay interactions at a throttled cadence (≤4/s) including derived model snapshots (zoom/pan/rotation) and overlay rectangle in output space.
  - Frame fetch timings: log p50/p95 for seek→frame and paint duration when debug mode is enabled.
  - Throttle logs to avoid IO overhead; disable entirely in production builds unless explicitly enabled.
- Event schema (suggested):
  - `adj.overlayDrag { cx, cy, w, h, rotationDeg, ts }`
  - `adj.seekFrame { positionMs, fetchMs, paintMs, success, ts }`
  - `adj.exportFilters { ffmpeg, outW, outH, modelHash, ts }`

---

## 8) Acceptance Criteria (High‑Level)
- Crop/Rotate tab:
  - Displays a still frame on a canvas with a draggable overlay frame and a seek bar (no play/pause).
  - Overlay drag/resize/move updates Zoom/Pan; rotation handle updates Rotation; the overlay itself does not rotate.
  - Sliders update overlay rectangle and canvas rotation in real time.
  - Geometry control IDs and ranges match v0.2.0: `adj-zoom` (0–200% UI, model clamp [0.1, 4.0]), `adj-pan-x`/`adj-pan-y` (-100..+100 UI → [-1.0, +1.0]), `adj-rot` (-180..+180°, step 0.5°).
  - Aspect ratio of overlay equals output aspect; overlay clamped within rotated-image inscribed rectangle; uncovered areas preview as black.
  - Optional keyboard nudges (if enabled): Arrow=1 px, Shift+Arrow=10 px; snapping to 0/90/180/270° on rotation when near or with Shift.
- Color tab:
  - Normal player with play/pause + seek.
  - Color adjustments apply live during playback using VLC adjust filters when available; graceful degradation when unavailable.
- Persistence and export behave as in v0.2.0; no schema change required; autosave debounce 300–500 ms.
- Tab switch reliability: no resource leaks; instant preview mode switch (<150 ms perceived).
- Performance: Crop/Rotate canvas ~60 FPS on 1080p typical hardware; scrubbing responsive with debounced frame fetch; UI updates coalesced to ≤120 Hz.
- Telemetry (debug builds): throttled overlay interaction logs and frame timing metrics available; disabled by default in production.

---

## 9) Detailed Task Breakdown

### T1 — Create v0.3.0 spec document and wire into docs navigation
- Acceptance:
  - A new `docs/tasks-tracker/v0.3.0/adjustments.md` with this spec and tasks.
  - Cross‑references to v0.2.0 Adjustments and existing Markup/Scoring docs.
- Implementation guide:
  - Copy structure from v0.2.0 doc; add tab split, canvas overlay behavior, and tasks below.
- Open questions:
  - None.

### T2 — Right Panel UI: Split Adjustments into two tabs
- Acceptance:
  - Tabs labeled `Crop/Rotate` and `Color` exist and are selectable.
  - Right‑panel controls render per active tab.
- Implementation guide:
  - Update `SwingAdjustmentsPanel` (or equivalent) to host a tabbed UI (JTabbedPane or custom header).
  - Maintain control IDs from v0.2.0 where applicable.
- Open questions:
  - Need product copy for tooltips and labels beyond existing mock?

### T3 — Crop/Rotate: Canvas component for still frame with overlay
- Acceptance:
  - A canvas shows a still image of the selected frame.
  - Overlay frame with 4 corner handles, 4 edge handles, draggable interior, dimmed outside.
  - Rotation handle implemented; rotating spins only the image.
- Implementation guide:
  - Extend `GeometryViewportPanel` or introduce `CropRotateCanvas`.
  - Render order and hit‑testing per Section 4.2.
  - Maintain output aspect ratio; compute overlay rectangle in output space.
- Open questions:
  - Do we support keyboard nudging and angle snapping UX by default?

### T4 — Crop/Rotate: Seek bar without playback
- Acceptance:
  - Seek bar moves to any time; canvas updates to corresponding frame; no playback occurs.
  - Scrubbing is responsive; low‑latency fetch with basic debounce.
- Implementation guide:
  - Use VLCJ to set position/time and fetch the current frame (snapshot/direct render).
  - Ensure commands do not auto‑start playback; pause state retained.
- Open questions:
  - If frame fetch is slow on long GOP sources, do we show a low‑res placeholder while decoding?

### T5 — Geometry math + synchronization with controls
- Acceptance:
  - Dragging overlay updates Zoom/Pan/Rotation controls and model in real time.
  - Adjusting controls updates overlay and canvas.
  - Clamping and aspect constraints enforced.
- Implementation guide:
  - Implement mapping functions overlay<->model (Section 4.1) as pure utilities with tests.
  - Debounce store writes; throttle visual updates to ≤120 Hz.
- Open questions:
  - Behavior when `zoom` is so small that overlay exceeds source bounds after rotation; confirm clamping policy.

### T6 — Color tab: player, controls, and live color filters
- Acceptance:
  - Normal playback with play/pause + seek.
  - Brightness/Contrast/Saturation/WB sliders update live; optional S/H preview per capability.
- Implementation guide:
  - Use `VlcjSwingMediaPlayerAdapter` and existing v0.2.0 color filter plumbing.
  - Add Shadows/Highlights sliders as UI first; preview if feasible.
- Open questions:
  - Confirm whether S/H are in scope for export in 0.3.0; if not, label as “Preview‑only: Off” or remove.

### T7 — AdjustmentsStore integration and tab switching lifecycle
- Acceptance:
  - Both tabs subscribe to `AdjustmentsStore`; updates propagate within 100 ms.
  - Switching tabs releases unneeded resources (frame capture vs playback) without flicker or leaks.
- Implementation guide:
  - Add lifecycle hooks to activate/deactivate frame source or player.
  - Compare old vs new state to avoid feedback loops during synchronization.
- Open questions:
  - Any multi‑source projects where different clips need different adjustments in the future?

### T8 — Unit tests for geometry mapping and export invariants
- Acceptance:
  - Tests for overlay<->model round‑trip within 1 px/0.5° error on typical sizes.
  - Export chain generation unchanged from v0.2.0 for equivalent settings.
- Implementation guide:
  - Add pure tests for mapping utilities and export chain builder.
- Open questions:
  - Pixel rounding strategy: round or floor at which stage for stability?

### T9 — QA pass & polish
- Acceptance:
  - Smooth interaction at 60 FPS target on 1080p.
  - Tooltips, cursor hints, snapping, and reset flows verified.
  - Known limitations documented.
- Implementation guide:
  - Record performance metrics; adjust throttling.
- Open questions:
  - Do we gate high‑DPI assets or scale handle sizes automatically?

### T10 — Documentation & release notes
- Acceptance:
  - v0.3.0 doc updated; release notes include tab split and limitations.
- Implementation guide:
  - Cross‑link to v0.2.0 and update `design/adjustments.html` if needed.
- Open questions:
  - Include short tutorial GIFs/screens?

### T11 — Telemetry & Logging wiring (debug‑only)
- Acceptance:
  - Export includes a single log line with the exact FFmpeg filter chain and output WxH.
  - Debug builds emit throttled overlay and frame timing events as per Section 7.8; production builds have telemetry disabled by default.
- Implementation guide:
  - Introduce a lightweight logger facade or reuse existing; guard with a debug flag.
- Open questions:
  - Do we persist any aggregated metrics to a dev diagnostics file, or console only?

### T12 — Accessibility & Keyboard Ops
- Acceptance:
  - Overlay handles have sufficient contrast; cursor changes reflect interaction type.
  - Keyboard focus can reach the overlay; Arrow keys nudge 1 px (Shift=10 px) if enabled.
  - Tooltips for controls show ranges and defaults.
- Implementation guide:
  - Reuse v0.2.0 tooltip patterns; add focus ring/highlight for overlay when focused.
- Open questions:
  - Finalize copy for tooltips; verify high‑DPI scaling of handles/cursors.

### T13 — Error handling & Fallbacks
- Acceptance:
  - Absence of libVLC adjust filters degrades gracefully: model updates persist; preview either applies on slider release or shows a non‑blocking notice.
  - Crop/Rotate frame fetch failures surface a subtle placeholder and retry; seek never auto‑starts playback.
- Implementation guide:
  - Centralize capability detection; provide small LRU for in‑memory frames if implemented (≤64 MB cap).
- Open questions:
  - Do we need a user‑visible banner for repeated frame failures, or keep it to debug logs only?

### T14 — Performance profiling & budgets
- Acceptance:
  - Crop/Rotate canvas meets target FPS on 1080p; profiling report captured on reference hardware.
  - UI updates are throttled to ≤120 Hz; no jank on slider drags.
- Implementation guide:
  - Add simple timing scopes and a developer toggle to print p50/p95 for frame fetch and paint.
- Open questions:
  - Any additional GPU pipeline optimizations needed on low‑end iGPUs?

---

## 10) Open Questions & Concerns
- Performance of frame fetching during Crop/Rotate scrubbing: if too slow on certain files, consider caching N previous/next frames or showing a debounced preview.
- Shadows/Highlights scope: UI only, preview, and/or export support? If deferred for export, mark clearly.
- Rounding policy for crop coordinates to ensure export stability vs preview display; prefer integer math for crop and scale.
- Multi‑monitor/high‑DPI: verify cursor hit‑testing scales correctly.

---

## 11) Deliverables
- New doc file for v0.3.0 with this spec and task breakdown: `docs/tasks-tracker/v0.3.0/adjustments.md`.
- Updates to `design/adjustments.html` if the rotation handle and tabbed layout require visual refinement.

---

## 12) Non‑Goals for v0.3.0
- Animation of geometry over time (keyframed zoom/pan/rotate) — out of scope.
- Per‑clip adjustments for multi‑clip timelines — out of scope for now.

---

## 13) References
- v0.2.0 Adjustments epic (persisted model, live color preview, export mapping): `docs/tasks-tracker/v0.2.0/adjustments.md`
- Existing classes to leverage:
  - `AdjustmentsStore` — state service and pub/sub
  - `VlcjSwingMediaPlayerAdapter` — color preview and Color tab player
  - `GeometryViewportPanel` — basis for canvas/overlay behaviors
  - `SwingAdjustmentsPanel` — right panel UI host
