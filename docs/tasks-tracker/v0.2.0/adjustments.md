# v0.2.0 — Adjustments Tab (Epic)

Notes
- Planning/specification for v0.2.0. Implementation is split into tasks below and cross‑referenced from Markup, Scoring, and Export.
- Use `design/adjustments.html` as visual/interaction reference for the inspector layout and transport controls; use `design/markup.html` for player/timeline integration patterns.
- Non‑destructive workflow: adjustments are stored as parameters and applied live in preview (Markup/Scoring) and during Export, without modifying source media.

## Goals and scope
- Provide a dedicated Adjustments tab to tweak video appearance and framing for a project:
  - Brightness, Contrast, Saturation
  - White balance (temperature/tint; v0.2.0 may approximate via hue/gamma; exact WB can be refined in a later version)
  - Zoom
  - Pan X / Pan Y
  - Rotation
- Changes should:
  - Apply immediately to the video preview in Markup and Scoring if technically possible without heavy operations (no full re‑encode during preview).
  - Persist per project.
  - Be applied to the exported video (re‑encode) via FFmpeg filter chain.

## User flow (expected)
1. User opens a project and switches to the Adjustments tab.
2. User tweaks sliders (Zoom, Pan X/Y, Rotation, Brightness, Contrast, Saturation) and optionally White Balance (Temp/Tint when enabled).
3. The currently open preview (Markup or Scoring) reflects changes instantly or with minimal latency; values are clamped to safe ranges.
4. Changes autosave to `adjustments.json` with a small debounce. Reloading the project restores the same visual result.
5. "Reset All" restores identity values for every parameter.
6. Export reads the same persisted settings and renders the video with the adjustments applied via a single FFmpeg filter graph.

## Technical design (high level)

### Data model (AdjustmentsV1)
- File: `adjustments.json` stored in the project directory alongside `edl.json`/`score.json`.
- Schema v1 (all fields optional; defaults are identity/no‑op):
  - `brightness`: float (relative, default 0.0) — range [-1.0, +1.0]
  - `contrast`: float (multiplier, default 1.0) — range [0.0, 3.0]
  - `saturation`: float (multiplier, default 1.0) — range [0.0, 3.0]
  - `whiteBalance` (optional):
    - `temperature`: float normalized [-1.0, +1.0] (default 0.0)
    - `tint`: float normalized [-1.0, +1.0] (default 0.0)
  - `zoom`: float (multiplier, default 1.0); zoom > 1 crops (virtual) and scales.
  - `panX`: float (normalized viewport units, default 0.0) — [-1.0, +1.0], 0 = center.
  - `panY`: float (normalized viewport units, default 0.0) — [-1.0, +1.0], 0 = center.
  - `rotationDeg`: float (degrees, default 0.0), clockwise positive. Range [-180.0, +180.0].
  - `version`: 1
- Persistence rules:
  - Autosave with debounce 300–500 ms after user changes on Adjustments tab.
  - Load on project open; use safe defaults when absent.
  - Preserve unknown fields for forward compatibility where feasible.

### Live preview (Markup and Scoring)
- Rendering stack: continue using VLCJ for playback.
- Use libVLC "adjust" video filter for low‑latency color changes where available:
  - Parameters: `brightness`, `contrast`, `saturation`, `hue`, `gamma` (map from `AdjustmentsV1`).
  - White balance in v0.2.0: approximate via hue/gamma and minor saturation shift; precise temperature/tint may be deferred or implemented via a GLSL shader if feasible.
- Geometry (zoom/pan/rotation): two implementation options; pick the simplest performant path based on feasibility:
  1) Prefer native libVLC filters if present on target platforms: `transform`, `rotate`, `crop`/`zoom` (when supported). Apply via VLCJ video filter APIs and update dynamically.
  2) Fallback: presentational transform at UI layer for preview only (e.g., draw video into a transformed surface or use an overlay layer for pan/zoom composition). Export still applies exact geometry via FFmpeg.
- Performance: apply parameter deltas without restarting playback; throttle slider updates to ≤120 Hz, coalesce bursts (leading-edge and trailing-edge updates recommended).
- Scope guard: if a parameter cannot be live‑applied on a platform, the UI still updates the model and preview may degrade gracefully (e.g., snap‑apply on slider release). Document these as known limitations in release notes.

### Export application
- Export uses FFmpeg filter chains to apply the exact adjustments in a single render pass.
- Mapping proposal (final names may vary by platform/build):
  - Brightness/Contrast/Saturation/Gamma → `eq=brightness=…:contrast=…:saturation=…:gamma=…`
  - White balance → `colorbalance` or `temperature` (or a combination of `colorchannelmixer` to simulate temp/tint)
  - Rotation → `rotate=angle=rad:fillcolor=black`
  - Zoom/Pan → either `scale` + `crop` + `pad` chain, or `zoompan` when animating (no animation in v0.2.0). For static framing: compute crop rectangle from `zoom` and `panX/panY`, then scale to output size.
- Ensure pixel formats/colorspaces are set appropriately to avoid banding (`format=yuv420p` etc.) and that audio/video sync is preserved.

### Settings propagation
- A lightweight in‑memory singleton or service holds current adjustments for the open project.
- Markup/Scoring players subscribe to changes and update preview filters immediately.
- Export reads from the same `AdjustmentsV1` persisted file to produce the filter chain; no duplication of configuration.

### Reset and presets
- Provide a "Reset All" action that restores identity values.
- Optional simple presets: "Neutral" (default), "Cooler", "Warmer", "High Contrast" — implemented as quick setters altering multiple fields.

## UI elements (from mock) and bindings
Use `design/adjustments.html` as the canonical mock for the Adjustments inspector on the right side panel. Implement the following controls and bind each to the `AdjustmentsV1` model with immediate preview updates and debounced autosave.

- Transform section
  - Slider: Zoom (`adj-zoom`)
    - Range UI: 0%..200% (step 1%). Display as `X.xx×`.
    - Model: `zoom` in [0.0, 2.0]; clamp to [0.1, 4.0] internally (export supports >2.0 if needed later).
    - Default: 1.0.
  - Slider: Position X (`adj-pan-x`)
    - Range UI: -100..+100 (step 1). Display normalized value `-1.00..+1.00`.
    - Model: `panX` in [-1.0, +1.0]. Default 0.0.
  - Slider: Position Y (`adj-pan-y`)
    - Same as Position X, bound to `panY`.
  - Slider: Rotation (`adj-rot`)
    - Add to UI (not shown in current HTML mock): range -180..+180 degrees (step 0.5). Display with `°`.
    - Model: `rotationDeg` clamped to [-180, +180]. Default 0.0.

- Color Grade section
  - Slider: Brightness (`adj-bright`)
    - Range UI: -100..+100 (step 1). Display as integer percentage with sign.
    - Model: `brightness` = ui/100 in [-1.0, +1.0]. Default 0.0.
  - Slider: Contrast (`adj-contrast`)
    - Range UI: 0..200 (step 1). Display as multiplier with 2 decimals (e.g., 1.10).
    - Model: `contrast` = ui/100 in [0.0, 2.0]. Default 1.0.
  - Slider: Saturation (`adj-sat`)
    - Range UI: 0..200 (step 1). Display as integer percentage.
    - Model: `saturation` = ui/100 in [0.0, 2.0]. Default 1.0.
  - Group: White Balance (add a collapsible group in Color Grade)
    - Slider: Temperature (`adj-wb-temp`) — range -100..+100 (maps to [-1.0, +1.0]).
    - Slider: Tint (`adj-wb-tint`) — range -100..+100 (maps to [-1.0, +1.0]).
    - For v0.2.0 preview, approximate via `hue`/`gamma`/`saturation` combination; exact mapping used only for export.

- Shared UI behavior
  - Each slider has a numeric readout to the right mirroring the display formats above.
  - Keyboard: ArrowLeft/Right steps one unit (with Shift = 10 units) for focused slider; Home/End go to min/max.
  - Focus/scroll behavior mirrors Markup tab inputs.
  - A "Reset" action exists at Transform level and at Color Grade level; also provide a global "Reset All" at the header of the inspector.
  - Tooltips show ranges and defaults; labels match the mock copy.

## Mapping details and formulas
- VLC (preview)
  - brightness: VLC expects [-1.0, +1.0] directly from model.
  - contrast: VLC expects [0.0, 2.0] (or 0..2+ depending on build). Use model `contrast` directly.
  - saturation: VLC expects [0.0, 2.0]. Use model `saturation` directly.
  - hue/gamma: derive from white balance as an approximation: `hue = temperature * 180` (deg), `gamma = 1.0 + tint * 0.2`, and optionally `saturation += temperature * 0.05` (clamp after sum). If unavailable, skip live WB.
- FFmpeg (export)
  - `eq` filter: `brightness=<brightness>:contrast=<contrast>:saturation=<saturation>:gamma=<gamma>`
  - WB: prefer `colorbalance` or `temperature`, else simulate via `colorchannelmixer` heuristics; unit tests validate mapping within tolerances.
  - Rotation: `rotate=<rotationRad>:fillcolor=black` with `rotationRad = rotationDeg * PI / 180`.
  - Zoom/Pan static framing (no animation):
    - Given output WxH, compute a virtual crop box. Let `z = max(zoom, 0.0001)`.
    - `cropW = round(W / z)`, `cropH = round(H / z)`; `cx = (W - cropW) / 2 + panX * (W - cropW) / 2`, `cy = (H - cropH) / 2 - panY * (H - cropH) / 2` (note Y flips).
    - Filter chain example: `crop=w=cropW:h=cropH:x=cx:y=cy,scale=W:H,rotate=rotationRad:fillcolor=black,eq=...[,wb=...]`.

## Telemetry and logging
- Log every apply for export with the exact filter graph; include model snapshot and project id.
- For preview, debug‑log parameter changes at most once per 250 ms per parameter (throttled).

## Tasks (Adjustments Tab)

- [x] 5.1 — AdjustmentsV1 schema and persistence
  - Acceptance:
    - `adjustments.json` read/write with autosave debounce (300–500 ms).
    - Defaults on first open; unknown fields preserved if feasible.
    - Loading a project without `adjustments.json` initializes identity values and creates the file only after the first change.
  - Implementation details:
    - Kotlin data class `AdjustmentsV1` with defaults; JSON via the same library used for EDL/Scoring.
    - Use a version field (`version=1`) and preserve unknown JSON properties on read/write.
    - Implement a small debounced saver shared with EDL/Scoring facilities.

- [x] 5.2 — Inspector UI & interactions (per design)
  - Acceptance:
    - Sliders/inputs for Zoom, Pan X, Pan Y, Rotation, Brightness, Contrast, Saturation, White Balance (Temp/Tint).
    - Reset All button; per‑section Reset; numeric readouts; keyboard focus rules consistent with Markup.
    - Matches `design/adjustments.html` hierarchy and copy where practical; added Rotation and White Balance controls.
    - Values reflect current model on load; editing updates preview immediately (subject to throttling) and triggers debounced save.
  - Implementation details:
    - Assign stable IDs: `adj-zoom`, `adj-pan-x`, `adj-pan-y`, `adj-rot`, `adj-bright`, `adj-contrast`, `adj-sat`, `adj-wb-temp`, `adj-wb-tint`.
    - Provide formatting helpers for readouts (×, %, °); clamp at source (UI) and sink (model) layers.
    - Add a top‑level "Reset All" and section‑level resets using model identity constants.

- [x] 5.3 — Live preview: color adjustments
  - Acceptance:
    - Brightness/Contrast/Saturation updates reflect instantly in Markup and Scoring players without pausing.
    - Mapped to libVLC `adjust` filter (or equivalent) with safe clamping and throttling (≤120 Hz updates; coalesced).
    - If a filter is unavailable, changes are still persisted and will apply during export.
  - Implementation details:
    - Extend the VLCJ player adapter to enable/disable and update `adjust` parameters at runtime.
    - Provide a small scheduler to coalesce bursts from sliders; latest value wins.

- [ ] 5.4 — Live preview: geometry (zoom/pan/rotation)
  - Acceptance:
    - Zoom/Pan/Rotation visually affect the player viewport in Markup and Scoring.
    - Implementation may use libVLC filters when available; otherwise apply a UI‑layer transform fallback.
    - Performance target: smooth at 30–60 FPS on 1080p sources on typical dev hardware.
  - Implementation details:
    - Option A: VLC filters `transform/rotate/crop`; probe support at runtime.
    - Option B (fallback): draw into a transformable surface or overlay a transform on the player component; ensure hit‑testing and timelines remain unaffected.

- [ ] 5.5 — Cross‑tab state service
  - Acceptance:
    - Central adjustments service; subscriptions update playing views; clean up on project switch.
    - Markup and Scoring reflect changes within 100 ms on the same UI thread budget.
  - Implementation details:
    - Provide a singleton `AdjustmentsStore` with: `get()`, `set(partial)`, `subscribe(listener)`, `unsubscribe(listener)`, `reset()`, `load(path)`, `save(path)`.
    - Ensure thread‑safety with UI dispatching; avoid feedback loops by comparing previous vs new state.

- [ ] 5.6 — Export: filter chain generation
  - Acceptance:
    - Construct FFmpeg filter graph from `AdjustmentsV1` with unit tests for various combinations (identity, extremes, partials).
    - Identity model produces either an empty graph or a no‑op `null`/empty string output.
  - Implementation details:
    - Isolate mapping logic in a pure function (e.g., `AdjustmentsToFfmpeg.toFilterChain(model, outW, outH)`).
    - Include numeric stability (rounding) to avoid flicker across frames; prefer integers for crop/scale.

- [ ] 5.7 — Export: integrate into render pipeline
  - Acceptance:
    - Exported videos include adjustments; failure surfaces clear error; logging includes the exact filter chain used.
    - Regression: exports without adjustments remain unchanged performance‑wise.
  - Implementation details:
    - Inject the generated filter chain into the existing FFmpeg invocation; quote/escape safely on all platforms.

- [ ] 5.8 — QA and limits
  - Acceptance:
    - Document ranges and clamping; verify extreme values; ensure no crash on 4K sources.
    - Known limitations documented: unavailable live filters, precision differences between preview and export.
  - Implementation details:
    - Write a short release‑note section summarizing platform caveats and suggested ranges.

## Cross‑references
- Markup v0.2.0 — 3.10 “Playback reflects Adjustments” (this epic 5.3/5.4). See this file for mapping and state service.
- Scoring v0.2.0 — 4.18 “Playback reflects Adjustments” (this epic 5.3/5.4).
- Export v0.2.0 — 7.1/7.2 apply `AdjustmentsV1` via FFmpeg. See this file for filter mapping.

## Dependencies and integration notes
- Depends on: existing VLCJ player adapter; export/FFmpeg pipeline; autosave facilities used by EDL/Scoring.
- Keep adjustments separate from EDL and Scoring data to avoid coupling concerns.
- Ensure that timeline and marking logic remain unaffected by visual transforms (they are UI‑only in preview).
