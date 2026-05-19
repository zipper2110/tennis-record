# v0.3.0 — Adjustments: Color Tab (Spec & Tasks)

## Supported user workflows
- Preview video with normal playback (play/pause) and seek while adjusting color parameters.
- Adjust Brightness, Contrast, Saturation, and White Balance (Temperature, Tint) in real time.
- Optional Shadows/Highlights controls, if feasible in preview; otherwise, persist-only for future export support.
- Reset Color Grade section or Reset All.

Out of scope (v0.3.0):
- Geometry editing (crop/rotate) — handled in the separate Crop/Rotate tab.
- Per-frame keyframing or time-varying color grades.

## Solution outline
- Left preview: VLCJ-based player viewport with full playback and seek bar.
- Right controls: Brightness, Contrast, Saturation, White Balance; synchronized both ways with the model.
- Processing: apply color changes live using libVLC adjust filters (or existing preview mapping used in v0.2.0).
- Data: continue using `AdjustmentsV1` fields; persist via `AdjustmentsStore` with debounced autosave.

Constraints and invariants
- Playback must remain smooth; color changes should apply without stutter on typical hardware.
- If an effect is not available in live preview, controls still update the model; preview may apply on slider release or be disabled with notice.

## Tasks for implementation (granular)
- T1 — Layout: split panel scaffolding (left player + right controls)
  - Acceptance criteria
    - A container with two regions: Left (player stack) and Right (controls column), resizable; minimum sizes defined (Left ≥ 640×360; Right ≥ 280 px width).
    - Component IDs: `adj-color-root`, `adj-color-left`, `adj-color-right`.
  - Implementation guide
    - Use `JSplitPane` with divider ~68%/32%. Persist divider location in prefs.

- T2 — Left player stack: header + viewport + transport bar
  - Acceptance criteria
    - Stack contains: `header` row (title + Reset), `viewport` area (opaque), `transport` row with Play/Pause and seek slider and time labels.
    - Component IDs: `adj-color-left-header`, `adj-color-viewport`, `adj-color-transport`.
  - Implementation guide
    - `BorderLayout` within left region; transport at SOUTH, header at NORTH, viewport at CENTER.

- T3 — Player integration and transport wiring
  - Acceptance criteria
    - Video is fully playable/seekable; SPACE toggles play/pause; seek slider works; current/duration labels update.
    - Seek while dragging updates time label; final release seeks accurately.
  - Implementation guide
    - Use `VlcjSwingMediaPlayerAdapter`; mirror seek/transport patterns from Markup/Scoring where applicable.

- T4 — Color controls panel: sliders and labels
  - Acceptance criteria
    - Controls present with labels, tooltips, and IDs:
      - `adj-brightness`, `adj-contrast`, `adj-saturation`, `adj-wb-temp`, `adj-wb-tint`.
    - Ranges (UI → model):
      - Brightness [-100..+100], Contrast [-100..+100], Saturation [-100..+100].
      - Temperature [-100..+100], Tint [-100..+100].
    - Section header "Color Grade" with a Reset button `adj-color-reset`.
  - Implementation guide
    - Use `JSlider` + value readouts; tooltips show ranges/defaults; organize into collapsible sections if needed.

- T5 — Live preview plumbing and capability detection
  - Acceptance criteria
    - Adjusting color controls updates the preview in real time with low latency.
    - If a capability is unavailable, control remains functional for model persistence and shows a tooltip/notice.
  - Implementation guide
    - Apply via libVLC `adjust` filter; coalesce slider events to ≤120 Hz; last-value-wins.

- T6 — Reset and persistence
  - Acceptance criteria
    - Section Reset restores defaults; Global Reset All remains available.
    - Changes persist to `adjustments.json` with debounce 300–500 ms.
  - Implementation guide
    - Use `AdjustmentsStore` pub/sub to broadcast changes and update UI; guard against feedback loops.

- T7 — Performance & UX
  - Acceptance criteria
    - Playback is smooth at 1080p; color updates do not cause noticeable drops; seek responsiveness matches other panels.
  - Implementation guide
    - Minimal logging; throttle debug to avoid IO overhead.

- T8 — Shadows/Highlights (optional)
  - Acceptance criteria
    - If implemented, provide a reasonable preview approximation; otherwise, omit for v0.3.0 or keep disabled with tooltip.
  - Implementation guide
    - Consider gamma/curve approximation if available; otherwise persist-only fields without preview.

## Notes on export
- Export pipeline remains unchanged from v0.2.0: use FFmpeg filters for color adjustments.
- If some preview effects aren’t available, export still applies the model values where supported.

## Open topics/questions
- Final list of color controls for v0.3.0 — confirm inclusion of Shadows/Highlights.
- Ranges and UI scaling parity with existing panels; confirm defaults.
- Verify libVLC filter availability on all target OSes and packaged versions.
