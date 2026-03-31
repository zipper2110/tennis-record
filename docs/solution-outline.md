# Tennis Record — Technical Solution Outline (Kotlin + Compose Desktop)

This document details the architecture, components, data model, and workflows for the Tennis Record desktop application. It is referenced from the root README and serves as the living technical guide for the project.

## 1. Product goals
- Let tennis players turn full‑length match recordings into tight, watchable videos.
- Remove dead time between points quickly via convenient tooling (in/out marks, ripple delete, keyboard shortcuts).
- Track the score during editing and render a scoreboard overlay in the final export.
- Future: zoom/crop/reposition, brightness/contrast, color adjustments.

## 2. Non‑functional requirements
- Cross‑platform desktop (Windows, macOS, Linux).
- Smooth preview and responsive scrubbing for 1080p30 (target), acceptable for 4K where possible.
- Non‑destructive editing: all actions are represented by an Edit Decision List (EDL) and scoring timeline; originals are read‑only.
- Deterministic exports: same EDL yields bit‑exact outputs given same toolchain and flags.
- Packaged installers with bundled native deps (libVLC, optional FFmpeg binaries).

## 3. Tech stack
- UI framework: Compose Multiplatform Desktop (Kotlin/JVM).
- Preview engine: libVLC via VLCJ (binding). Alternative: mpv via bindings (future option).
- Export/render: FFmpeg (initially via CLI process). Future: JavaCPP‑presets FFmpeg for in‑process control.
- JSON processing: kotlinx.serialization.
- DI/State management: Kotlin coroutines + flows; simple service locator at first.
- Packaging: jpackage (MSIX/EXE, DMG, AppImage). Bundle libVLC (LGPL).
- Logging: Kotlin Logging + slf4j simple/logback.
- Tests: JUnit 5.

## 4. High‑level architecture
- app (desktop): Compose UI, windowing, actions, shortcuts
- domain: EDL and scoring models, rules (best‑of‑3/5, tiebreaks)
- media‑preview: VLCJ player wrapper, timeline thumbs (later via FFmpeg), overlay sync
- export: FFmpeg pipeline builder, concat/trim stages, overlay rendering
- persistence: project files (EDL JSON), autosave, recent projects

Data flow:
1) User loads a source video → media‑preview opens it and exposes timeline/position.
2) User marks keep segments and inputs point results → domain updates EDL + score events.
3) UI overlays the computed score for the current timestamp during preview.
4) On export, export module compiles an FFmpeg command sequence from EDL and overlay style.

## 5. Modules (proposed Gradle/Maven modules later)
- :app-desktop — Compose app entrypoint, DI wiring, resources.
- :domain — EDL, scoring rules, validation, serialization.
- :media-preview — VLCJ integration, player controls, seek/step, video surface provider.
- :export — FFmpeg command builder, temp file mgmt, progress parsing.
- :ui-widgets — reusable Compose components (timeline, scoreboard widget, transport controls).

Note: The repository currently uses Maven; we may migrate to Gradle for better Compose support. Until then, keep module plan conceptual.

## 6. Core components
- TimelineView: scroll/zoomable timeline, draggable playhead, in/out markers, segment list.
- PlayerController: play/pause, rate, step forward/back by frame (approx via VLC), seek to time.
- ScoreboardWidget: visual overlay reflecting current score from domain at time t.
- EDLManager: stores `keeps` segments, ripple logic, conflict resolution.
- ExportOrchestrator: creates temp trims as needed and performs concat + overlay.

## 7. EDL and scoring model
- Project JSON schema (versioned):
```json
{
  "version": 1,
  "source": "C:/videos/match1.mp4",
  "fps": 29.97,
  "timebase": "1000/1", 
  "keeps": [ { "startMs": 12345, "endMs": 45678 } ],
  "events": [
    { "timeMs": 12500, "type": "Point", "winner": "A" },
    { "timeMs": 30000, "type": "ChangeServer", "server": "B" }
  ],
  "matchRules": { "bestOf": 3, "tiebreakFinalSet": false },
  "overlayStyle": {
    "theme": "classic",
    "position": { "x": "right-40", "y": 40 },
    "font": "Roboto-Bold",
    "fontSize": 48,
    "boxColor": "#00000088",
    "fontColor": "#FFFFFFFF"
  }
}
```
- Score progression is computed from `events` given `matchRules` and used by preview/export.

## 8. Preview overlay strategy
- Primary: draw scoreboard as a Compose layer above the video canvas, synchronized to playhead time.
- Alternative: use VLC OSD/Marquee for simple text (limited styling).
- For final export: render via FFmpeg filtergraph (`drawtext` or pre‑rendered RGBA overlay piped to `overlay`).

## 9. Export pipeline design
- Exact‑frame cuts are best achieved by re‑encoding the necessary borders; otherwise use concat demuxer for keyframe‑aligned cuts.
- Steps (typical):
  1) For each keep segment `{start,end}` produce an intermediate file with accurate borders (NVENC/QSV/VAAPI when available).
  2) Concat intermediates in order (`-f concat`).
  3) Apply overlay filter with scoreboard text or pre‑rendered image sequence.
- Progress: parse FFmpeg stderr for `time=` to update UI progress.
- Hardware acceleration:
  - Windows: `-hwaccel d3d11va` (decode), `-c:v h264_nvenc`/`hevc_nvenc` (encode) if NVIDIA.
  - Intel: Quick Sync (`-hwaccel qsv`, `-c:v h264_qsv`).
  - macOS: `-hwaccel videotoolbox`, `-c:v h264_videotoolbox`.

## 10. Keyboard & UX basics (MVP)
- Space: play/pause
- J/K/L: shuttle backward/pause/forward
- I/O: mark in/out
- Delete/Backspace: ripple delete (remove gap between segments)
- Arrow Left/Right: small nudge seek; with Alt/Ctrl for larger steps
- S: add point for current server; Shift+S: toggle server

## 11. File I/O and project layout
- Project file: `*.trproj` (JSON) containing EDL and settings.
- Autosave to `project.autosave.trproj` every N seconds.
- Exports written to `/Exports/{projectName}/{timestamp}/`.
- Temp intermediates in a cleaned temp dir per export job.

## 12. Error handling & recovery
- Graceful handling of missing/broken media: show placeholder and allow relink.
- Persist unsaved changes warning on exit.
- Cancelable exports with cleanup of temp files.

## 13. Performance notes
- Maintain a simple index of keyframes/timestamps for faster scrubbing (later, via FFmpeg probe).
- Optionally generate proxy media for 4K sources to improve responsiveness.

## 14. Packaging & distribution
- Bundle libVLC per platform.
- Use `jpackage` for installers and include necessary run flags and JVM.
- Codesign where needed (Windows/macOS).

## 15. Licensing & third‑party components
- libVLC (LGPL) — ship unmodified, allow replacement.
- FFmpeg — prefer LGPL build to avoid GPL obligations unless GPL codecs/filters are used.
- Fonts — ensure redistribution rights (e.g., OFL fonts like Roboto).

## 16. Roadmap (high level)
- v0.1 (MVP): Open video, mark segments, simple scoreboard, save/load project, basic export with overlay.
- v0.2: Better scrubbing, thumbnails, undo/redo, presets, HW accel paths.
- v0.3: Proxy media, color adjustments, richer themes, in‑process FFmpeg.

## 17. Open questions
- Primary OS priority at launch?
- Required export formats/presets (H.264 MP4 baseline, HEVC, etc.)?
- Score rules variants (no‑ad, super tiebreak sets)?
