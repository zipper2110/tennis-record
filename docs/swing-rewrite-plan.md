# Tennis Record — JavaFX→Swing Rewrite Plan

This document evaluates feasibility, risks, and provides a phased task plan to transition the desktop app from JavaFX to a Swing/AWT stack (keeping Kotlin/JVM). No code changes are performed yet — this is planning only.

## 1) Executive summary (feasibility)
- Feasible: Swing/AWT has mature support for high‑performance video surfaces via VLCJ’s AWT/Swing components (Direct3D on Windows), which historically outperform JavaFX media surfaces for heavy content.
- Scope: UI layer rewrite (stages/scenes/controls → JFrame/JPanel, layout managers), media layer swap to VLCJ’s Swing embedding, and replacement of JavaFX utilities (dialogs, CSS) with Swing equivalents.
- Business logic (EDL/manifest/FFmpeg command builder, pagination, dispatcher logic) remains Kotlin and mostly UI‑agnostic; can be reused with minimal changes.
- Packaging remains via Maven/Java installer or custom launchers; no JavaFX runtime modules required.

Conclusion: A targeted Swing rewrite is practical and should improve playback/scrubbing performance. Main complexity is re‑implementing the Markup timeline/editor UI in Swing.

## 2) Current app — coupling points to JavaFX
- Application/Windowing: `MainApp` extends `javafx.application.Application`; scenes, `BorderPane`, `Stage`, `Scene`.
- Tabs/Views: `ProjectsTab`, `MarkupTab`, `ExportTab`, `VideoTestTab` — all build JavaFX node trees.
- Media layer: Abstractions exist (`org.litvin.media.AppMediaPlayer`), with adapters for JavaFX and VLCJ; `VideoTestTab` shows VLCJ test usage.
- Styling: `src/main/resources/styles/app.css` applied to JavaFX scenes.
- Dialogs/Choosers: `DialogUtils`, JavaFX `FileChooser`.

Implication: A Swing rewrite will replace UI trees and utilities while reusing domain and export code.

## 3) Key design choices for Swing
- Windowing: Single `JFrame` + `CardLayout` or custom view controller to switch between Projects/Markup/Export/VideoTest screens.
- Layouts: Prefer `BorderLayout`, `BoxLayout`/`GridBagLayout`, or MigLayout (optional dependency) for ergonomic layouting.
- Media surface: VLCJ `EmbeddedMediaPlayerComponent` (AWT Canvas under the hood) with `--avcodec-hw=d3d11va` on Windows for HW decode. Consider `CallbackVideoSurface` for advanced overlays.
- Rendering overlays/timeline: Custom `JComponent` painting on EDT for timeline; consider off‑screen buffering for large timelines.
- Threading: Strict separation between VLCJ callbacks (native threads), worker threads, and Swing EDT; marshal UI updates via `SwingUtilities.invokeLater`.
- Shortcuts: `InputMap`/`ActionMap` for global JKL/Space, I/O, etc.
- Dialogs: `JOptionPane` and `JFileChooser`.

## 4) Risks and mitigations
1) Frame‑accurate seeking
- Risk: Accurate single‑frame stepping depends on decoder and keyframe structure.
- Mitigations: Use VLCJ `EmbeddedMediaPlayer` with precise time set; accept near‑frame preview while guaranteeing export accuracy through FFmpeg; optionally add proxy generation for problematic sources.

2) High‑DPI and scaling
- Risk: Swing on Windows/macOS can render blurry if not scaled correctly.
- Mitigations: Enable HiDPI VM flags on modern JDKs; test on 125–200% scale; avoid raster icons; draw vector or HiDPI‑aware painting.

3) Event threading
- Risk: Deadlocks/jank if VLCJ callbacks touch UI off‑EDT.
- Mitigations: Wrap all UI mutations with `invokeLater`; keep heavy work off EDT.

4) Packaging/runtime
- Risk: Removing JavaFX modules changes packaging; native libraries (VLC, JNA) must be present.
- Mitigations: Document VLC installation requirement or bundle VLC; verify 32/64‑bit match; add startup checks and friendly error messages.

5) Timeline performance
- Risk: Complex painting at deep zoom can stutter.
- Mitigations: Virtualize drawing, cache tick marks/labels, throttle repaints with `Timer`/`RepaintManager`, only invalidate dirty regions.

6) Cross‑platform video output
- Risk: Direct3D path is Windows‑specific; Linux/macOS use different outputs.
- Mitigations: Detect OS and configure VLCJ accordingly; primary target Windows first, add others after parity.

## 5) Migration strategy (incremental)
- Create a parallel Swing entry point and run alongside existing JavaFX app during development (feature flag or separate main class).
- Reimplement views one by one while reusing shared Kotlin logic. Keep project files and EDL identical.
- Introduce an updated media facade implementation backed by VLCJ Swing and keep UI dependent only on the facade.

## 6) Phased task plan

### Phase 0 — Setup and skeleton
- [ ] 0.1 Add Swing app module or new main class (e.g., `org.litvin.SwingMainApp`) with `JFrame`, menu/sidebar, and view switching (CardLayout).
- [ ] 0.2 Wire dependency updates in `pom.xml`: ensure VLCJ + JNA present; drop JavaFX runtime plugins for Swing build profile (keep JavaFX for legacy build until cut‑over).
- [ ] 0.3 Add a Swing media facade implementation using VLCJ `EmbeddedMediaPlayerComponent`; expose as `AppMediaPlayer` implementation.
- [ ] 0.4 Establish base theming (UIManager settings) and HiDPI settings; global error dialog helper.

Acceptance for Phase 0:
- [ ] Swing app starts, navigates between placeholder screens, and shows a placeholder media surface.

### Phase 1 — Media preview MVP (Swing)
- [ ] 1.1 Implement `VideoTest` screen in Swing: open file via `JFileChooser`, play/pause/seek, time display.
- [ ] 1.2 Keyboard controls J/K/L, Space, left/right small seeks; mouse wheel seek.
- [ ] 1.3 Proper resource management: release player on close; handle device change errors gracefully.

Acceptance for Phase 1:
- [ ] Smooth playback at 1080p; stable seeking; no EDT freezes; CPU/GPU usage acceptable.

### Phase 2 — Projects and basic flows
- [ ] 2.1 Rebuild Projects screen (recent projects list, open/create dialogs) in Swing; reuse `ManifestIO`, `RecentsProvider`.
- [ ] 2.2 Implement “Select Source Video” flow using `JFileChooser`; update manifest and navigate to Markup.
- [ ] 2.3 Persist window state and MRU paths.

Acceptance for Phase 2:
- [ ] Can create/open a project, pick a video, and persist manifest without JavaFX.

### Phase 3 — Markup editor (core UI)
- [ ] 3.1 Implement timeline track component (custom `JComponent`): playhead, zoom/pan, selection spans.
- [ ] 3.2 Bind timeline to media time via `AppMediaPlayer` callbacks; smooth playhead updates.
- [ ] 3.3 Port EDL interactions (add/remove segments, ripple delete, undo/redo) reusing `MarkupDispatcher` logic.
- [ ] 3.4 Keyboard mappings and context menus; basic overlays preview (score layer drawn in a separate layer or in timeline panel).

Acceptance for Phase 3:
- [ ] Editing UX matches current app capabilities; timeline remains responsive at practical zoom levels.

### Phase 4 — Export pipeline
- [ ] 4.1 Port Export screen UI (presets, progress output) to Swing.
- [ ] 4.2 Invoke FFmpeg as before; show progress via stderr parsing; allow cancel/resume.
- [ ] 4.3 Validate output parity vs JavaFX app for sample projects.

Acceptance for Phase 4:
- [ ] Exports succeed; outputs match given same EDL and presets.

### Phase 5 — Cut‑over & cleanup
- [ ] 5.1 Remove JavaFX app code paths or guard them behind a legacy profile.
- [ ] 5.2 Update README and docs; provide migration notes.
- [ ] 5.3 Packaging for Windows (MSI/EXE) and smoke tests.

Acceptance for Phase 5:
- [ ] Swing app is the default build; installers created; basic QA passed.

## 7) Engineering spikes (recommended before full commit)
- [ ] S1: Prototype VLCJ `EmbeddedMediaPlayerComponent` with D3D11 on representative 4K/60 and 1080p/60 files; measure CPU/GPU and seek latency.
- [ ] S2: Prototype timeline painting at deep zoom with virtualization; ensure < 4 ms paint time for typical frames.
- [ ] S3: Verify HiDPI rendering and font metrics across 100%, 150%, 200% scale.

## 8) Dependency and config notes
- VLCJ requires compatible VLC installation (or native libraries next to app). Ensure 64‑bit JDK + 64‑bit VLC.
- Add startup diagnostics to locate VLC and print selected video output and HW decode mode.
- Consider a `-Pswing` Maven profile that excludes JavaFX modules and starts `SwingMainApp`.

## 9) Timeline (rough)
- Phase 0: 3–5 days
- Phase 1: 3–5 days
- Phase 2: 3–6 days
- Phase 3: 2–3 weeks (largest scope)
- Phase 4: 3–5 days
- Phase 5: 2–4 days

Total: ~5–7 weeks focused effort, assuming strong familiarity with Swing and reuse of existing logic. Add 1–2 weeks buffer for spikes and packaging.

## 10) Open questions
- Bundle VLC or require system VLC? What versions do we target?
- Keep JavaFX build around during transition, or hard switch after Phase 3?
- Do we need Linux/macOS parity now or after Windows cut‑over?
- Any licensing implications for bundled codecs or presets?
