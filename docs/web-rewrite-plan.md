# Tennis Record — Desktop-to-Web Rewrite Plan

This document evaluates feasibility, risks, and provides a phased task plan to transition Tennis Record from a JavaFX desktop app (Kotlin/JVM) to a web-based stack.

## 1) Executive summary (feasibility)
- Feasible with a hybrid desktop-web approach using a modern web UI plus a desktop shell (Electron or Tauri) to retain local file access and FFmpeg integration.
- Browser-only (pure PWA) is not recommended initially due to limited access to local files, codecs, and process spawning for exports; possible later with server-side processing.
- Video playback and scrubbing performance can meet or exceed current JavaFX/VLCJ using native decoders via:
  - Desktop shell’s OS decoders in a `<video>` element where codecs are supported (H.264/H.265/VP9/AV1 with hardware acceleration as available), or
  - Specialized players (mpv+IPC via Node addon) if precise frame stepping is mandatory (advanced option), or
  - WebCodecs API (experimental, Chrome/Edge) for custom demux/decoding (higher effort, future path).
- Export pipeline can be preserved by invoking local FFmpeg from the desktop shell (Node/Tauri Command API). Parity with current CLI-based approach is straightforward.

Conclusion: A desktop-web hybrid (Electron or Tauri) is the fastest path to parity and improved video UX. A pure browser app is possible later as a separate deployment mode if a server component is introduced.

## 2) Key requirements to preserve
- Local-first projects: open video from disk, write project manifests/EDL, autosave.
- Responsive timeline + accurate seeking; smooth preview at 1080p30 (target), workable at 4K with proxies.
- Export via FFmpeg with overlays and optional HW acceleration.
- Cross-platform: Windows primary; macOS secondary; Linux if feasible.

## 3) Risks and mitigations
1. Frame-accurate seeking in the player
   - Risk: HTML `<video>` exposes keyframe-seek; exact frame step is inconsistent across browsers.
   - Mitigations:
     - Accept near-frame accuracy in preview; guarantee export accuracy with FFmpeg (current approach).
     - Use keyframe index + binary seeks + frame-time interpolation to improve perceived accuracy.
     - For strict frame stepping, consider mpv embedded (Electron native module) in a later phase.

2. Codec/format support variability
   - Risk: System/browser support differs (HEVC on Windows 10 may require extra codecs).
   - Mitigations:
     - Prefer H.264/MP4 in demos and docs; fall back with proxy generation (FFmpeg transcode) when unsupported.
     - Detect support and guide the user to install codecs (Windows HEVC extension) or generate proxies.

3. Hardware acceleration consistency
   - Risk: HW decode/encode varies by GPU/driver.
   - Mitigations:
     - Keep preview simple via `<video>` where possible.
     - Expose FFmpeg presets for NVENC/QSV/VTB; auto-detect support.

4. Large files and local file access in browser context
   - Risk: Pure browser apps cannot freely read arbitrary local paths.
   - Mitigations:
     - Use desktop shell (Electron/Tauri) with file system APIs.
     - If future browser-only mode is needed, add a local helper or server upload flow.

5. Packaging and updates
   - Risk: Shipping Node/Electron size; signing and updater complexity.
   - Mitigations:
     - Prefer Tauri (Rust shell) for smaller footprint if team skills permit.
     - Start with manual releases; add auto-update later.

6. Timeline UI performance
   - Risk: High-DPI, long duration videos, deep zoom causing re-render cost.
   - Mitigations:
     - Virtualization, canvas-based rendering for tracks, requestAnimationFrame throttling.
     - Separate state store from render, memoized drawing of spans/ruler.

7. Team skill shift (Kotlin→TypeScript/Rust)
   - Mitigations:
     - Keep domain models and EDL logic portable; document schema.
     - Start with TypeScript + React/Svelte; consider Kotlin/JS only if strong preference.

## 4) Architecture options
- A) Electron + React/Svelte + Node (Recommended starter)
  - Pros: Mature ecosystem, rich IPC, easy spawning of FFmpeg, wide examples.
  - Cons: Larger runtime size (~80–100 MB+).
- B) Tauri + React/Svelte (Rust backend)
  - Pros: Very small footprint, fast, secure permission model.
  - Cons: Requires Rust for native commands, smaller community for media-native integrations.
- C) Pure Web (PWA)
  - Pros: Zero install, easiest distribution.
  - Cons: Limited local file/process access; would need server-side FFmpeg and uploads.

Recommendation: Start with B) Tauri if team is okay with Rust; otherwise A) Electron. Keep the web UI framework choice decoupled from the shell to allow future PWA exploration.

## 5) Proposed stack (initial)
- UI: React (TypeScript) or SvelteKit; component library kept minimal; custom timeline via Canvas/WebGL.
- State: Zustand or Redux Toolkit (React) / Svelte stores (SvelteKit).
- Player: Native `<video>` for MVP; investigate `ShakaPlayer`/`hls.js` if HLS/streaming becomes relevant.
- Timeline rendering: HTML Canvas 2D (first), consider WebGL for very large timelines.
- IPC: 
  - Electron: `ipcMain`/`ipcRenderer` + `child_process` for FFmpeg.
  - Tauri: `tauri::command` for filesystem and FFmpeg invocation.
- Packaging: Electron Builder or Tauri bundler (MSI/EXE, DMG). Codesign later.
- Tests: Vitest/Jest for UI logic; Playwright for E2E smoke.

## 6) Data & compatibility
- Preserve current manifest/EDL schemas. Keep JSON compatible or provide a one-time upgrader.
- File naming and project folder structure should remain as in docs/solution-outline.md.
- Maintain deterministic export flags where possible.

## 7) Export strategy in the web-hybrid app
- Continue building FFmpeg commands from EDL as today.
- Execute FFmpeg locally via shell APIs (Electron/Tauri). Parse stderr for progress.
- Hardware encode presets exposed via UI (NVENC/QSV/VTB); probe availability at app start.

## 8) Security & licensing
- Electron/Tauri permissions: restrict fs access to user-approved paths.
- Bundle FFmpeg under LGPL (or document requirement to install). Audit third-party licenses.
- No telemetry in MVP; local logging only.

---

## 9) Phased rewrite plan (tasks)

### Phase 0 — Foundations and Decision Record
- [ ] 0.1 Choose shell (Electron vs Tauri) and web framework (React vs Svelte); record ADR.
- [ ] 0.2 Set up monorepo or two-repo structure (app shell + web ui). Define build scripts.
- [ ] 0.3 Port domain models and manifest/EDL schemas to TypeScript; add JSON validators.
- [ ] 0.4 Implement settings storage, recent projects scanning, and project open/create flows (filesystem APIs).

Acceptance for Phase 0:
- [ ] Can create/open a project folder, read/write manifest JSON, show project in a basic UI.

### Phase 1 — Media preview MVP
- [ ] 1.1 Player surface with `<video>`: open local file, play/pause/seek, basic rate control.
- [ ] 1.2 Timeline (canvas) with playhead, click-to-seek, wheel nudge.
- [ ] 1.3 Keyboard: JKL, I/O, Space; map to state and player.
- [ ] 1.4 Simple EDL: keep segments list, ripple delete, undo/redo (basic).
- [ ] 1.5 State autosave to manifest; error dialogs.

Acceptance for Phase 1:
- [ ] Smooth playback at 1080p sources; seeking behaves responsively; EDL persists.

### Phase 2 — Parity features
- [ ] 2.1 Zooming/panning timeline with deep zoom; fixed-position playhead.
- [ ] 2.2 Score model and overlay layer rendered over video.
- [ ] 2.3 Proxy media pipeline for heavy sources (optional toggle).
- [ ] 2.4 Import existing desktop project and confirm parity.

Acceptance for Phase 2:
- [ ] Editing UX matches current app capabilities; legacy project opens and works.

### Phase 3 — Export pipeline
- [ ] 3.1 FFmpeg invocation from shell, progress parsing.
- [ ] 3.2 Overlay rendering via filtergraph; presets for HW encoders.
- [ ] 3.3 Export job queue with cancel/resume; temp dir management.

Acceptance for Phase 3:
- [ ] Exports succeed for typical inputs; results match desktop outputs given same EDL.

### Phase 4 — Packaging & QA
- [ ] 4.1 Installers for Windows (primary), macOS (secondary).
- [ ] 4.2 Smoke E2E (Playwright) for new/open/edit/export flows.
- [ ] 4.3 Crash/error reporting (local logs); optional auto-updater.

Acceptance for Phase 4:
- [ ] Signed builds install and run; basic E2E suite green.

---

## 10) Milestones & timeline (rough)
- Phase 0: 1–2 weeks
- Phase 1: 2–4 weeks
- Phase 2: 3–5 weeks
- Phase 3: 2–3 weeks
- Phase 4: 1–2 weeks

Total: 9–16 weeks depending on shell choice and proxy media complexity.

## 11) Open questions
- Primary deployment target at launch: Windows only or include macOS?
- Accept near-frame preview accuracy if export is exact? (Recommended yes for MVP)
- Electron vs Tauri: team preference and skills?
- Keep Kotlin/JVM modules for export helpers or fully move to Node/Rust?

## 12) References
- Existing desktop design docs: `docs/solution-outline.md`, `design/*.html`.
- Current media/export Kotlin components for logic parity: `src/main/kotlin/org/litvin/FFmpegCommandBuilder.kt`, `RenderQueue.kt`, `MarkupDispatcher.kt`.
