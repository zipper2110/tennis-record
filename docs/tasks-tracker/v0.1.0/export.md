# v0.1.0 — Export (Render) — Tasks

Notes
- This spec defines the MVP Export/Render tab. It follows the style and rigor of the Markup tasks file and maps closely to the UI mock: design/export.html.
- The goal is to render a single edited video (gap-free based on Markup/EDL), with simple preset control. No scoreboard/overlay in v0.1.0 (see roadmap).
- Presets source: docs/export-presets.json (Fast, Balanced, Quality — H.264 MP4).
- FFmpeg is the execution engine. Use trim+concat strategy per roadmap. Hardware encoders are post‑MVP.

User workflow (center of this spec)
- Select output quality/resolution (or preset → implies codec/bitrate/CRF + optional scaling).
- Select whether to remove the idle time (i.e., render only “kept” segments from EDL).
- Select the encoder engine (MVP: H.264 software path; HW accel options shown but disabled/placeholder).
- Click “Initialize Render”. Prompt for output folder and filename. Begin render.
- Show render progress as a card under “Active Processing” with percent, ETA, size, and Cancel.
- On completion, move to “Completed Renders” with resolution/encoder summary, file size, and an “Open folder” button.
- Active/completed render lists are global (not per project). Completed renders list is persistent across app restarts.

General findings & scope notes (review)
- Mock alignment: design/export.html shows a left settings panel and right queue (Active Processing + Completed Renders). MVP follows this structure.
- Presets vs manual controls: For v0.1.0, keep presets simple (Fast/Balanced/Quality) and allow an explicit output resolution toggle (1080p/4K) as shown in mock. Bitrate slider in mock can represent preset selection feedback; advanced free-form bitrate entry is out of scope.
- Encoders: MVP ships with software H.264 (libx264). Hardware acceleration (NVENC/QSV/AMF) will be planned for v0.2.0.
- Global queue: Visible and identical regardless of which project is open; enqueue jobs with a snapshot of project settings and EDL at the time of submission.
- Persistence: Persist only completed jobs in MVP. Restoring/resuming in-flight jobs after app restart is out of scope.
- EDL integration: When “Remove idle time” is ON, build FFmpeg concat list from EdlV1 points (keeps). When OFF, render full source.
- Filesystem: Windows is packaging priority. Use native file dialog for Save As. “Open folder” should open Explorer at the file’s location.

- [x] 3.1 — Export tab shell (UI scaffolding)
  - Description: Implement Export tab per `design/export.html` with a left configuration panel and a right queue area (Active Processing + Completed Renders).
  - Acceptance Criteria:
    - Export tab reachable from app navigation; layout matches mock at a high level.
    - Left: resolution selector, idle-trim toggle, encoder selector, Initialize Render button.
    - Right: sections labeled “Active Processing” and “Completed Renders” with list/card containers.
  - Implementation Guide:
    - UI toolkit: JavaFX (match app). Mirror class structure of MarkupTab; create `ExportTab` view and wire basic button events to a dispatcher.
    - Reuse existing sidebar styling from Projects/Markup for a consistent left navigation.

- [x] 3.2 — Presets loading and binding
  - Description: Read presets from `docs/export-presets.json` and bind to UI (Fast/Balanced/Quality) including target bitrate/CRF and default resolution.
  - Acceptance Criteria:
    - Presets are displayed; selecting a preset updates the resolution and bitrate/quality display accordingly.
    - Default preset selected on first load (Balanced).
  - Implementation Guide:
    - Reuse JSON library already in use (Jackson/kotlinx). Place lightweight DTOs and a loader utility.

- [x] 3.3 — Output resolution selector
  - Description: Provide explicit resolution choices per mock (e.g., 1080p, 4K). Selecting a resolution updates the command plan (scale filter) and estimated bitrate if preset dictates.
  - Acceptance Criteria:
    - Visual selection state; resolution included in job summary and completed card.
    - If source < selected res, upscale allowed (basic scale) with note; no smart scaling in MVP.

- [x] 3.4 — Idle time removal toggle (EDL integration)
  - Description: Toggle determines whether to render only kept intervals (from EdlV1 points) or the entire source.
  - Acceptance Criteria:
    - When ON: command builder uses trim+concat over keep intervals in temporal order.
    - When OFF: full-duration render with no cuts.
  - Implementation Guide:
    - Read EDL from project. Ensure validation (sorted, half-open intervals) is honored. Re-encode borders as per roadmap.

- [ ] 3.5 — Encoder engine selector (MVP scope)
  - Description: Encoder options listed; only software H.264 path is available in v0.1.0. Hardware options are displayed but disabled.
  - Acceptance Criteria:
    - UI clearly indicates availability. Selected encoder stored in job config and shown in summaries.

- [ ] 3.6 — Initialize Render → Save As dialog
  - Description: On click, show file save dialog with suggested filename and last-used folder. On confirm, enqueue a render job.
  - Acceptance Criteria:
    - User can pick folder and name. Reject overwrite unless confirmed. Job appears under Active Processing immediately.
  - Implementation Guide:
    - Use existing DialogUtils or platform file chooser. Remember last save directory in app prefs.

- [ ] 3.7 — FFmpeg command builder (v1)
  - Description: Build the final command line from: source file, EDL keep list (optional), encoder, preset, resolution.
  - Acceptance Criteria:
    - H.264 (libx264) output MP4; applies `-vf scale` when needed; sets CRF/bitrate/encoder tune from preset.
    - For idle-trim ON: uses concat demuxer or filtergraph with accurate re-encode at segment borders.
    - Command string preview visible in dev logs for debugging.

- [ ] 3.8 — Render job model and global queue manager
  - Description: Introduce `RenderJob` data model and a process-managed queue. Queue is global across projects within app session.
  - Acceptance Criteria:
    - Jobs hold: id, projectId (optional), source path, edl snapshot, preset id, resolution, encoder, idleTrim flag, outputPath, status, progress, eta, bytesWritten.
    - Dispatcher/manager exposes flows/observers to refresh UI on state changes.
    - Multiple jobs can run sequentially (single-worker) in MVP; parallelism off by default.

- [ ] 3.9 — Progress parsing and UI card (Active Processing)
  - Description: Parse ffmpeg stderr for time/size/speed to estimate percent and ETA. Update a progress bar and stats on the active card.
  - Acceptance Criteria:
    - Show percent, ETA, and written size on the card. Updates at least 2×/sec while running.
    - Card displays filename and a stable Job ID.

- [ ] 3.10 — Cancel render (terminate and cleanup)
  - Description: Provide a Cancel button on the active job card that stops the ffmpeg process and cleans up partial output (tmp or zero-length rules).
  - Acceptance Criteria:
    - After Cancel: status = CANCELED; card removed from Active and not added to Completed. Partial file is deleted (or moved to .part).

- [ ] 3.11 — Completed Renders list (persistent)
  - Description: On successful completion, append a record to the Completed list and persist it to disk.
  - Acceptance Criteria:
    - Each item shows: file name, encoder + resolution summary (e.g., H.264 / 1080p), final file size, and an “Open folder” button.
    - Completed list persists across app restarts (JSON in app data). “Clear Finished” removes all persisted entries after confirm.

- [ ] 3.12 — Global visibility and state retention
  - Description: Export tab shows the same Active/Completed lists regardless of project. Submitting a job from any project adds to the one global queue.
  - Acceptance Criteria:
    - Switching projects does not affect queue visibility. Completed persists; Active does not resume on restart in MVP.

- [ ] 3.13 — Error handling and user notifications
  - Description: Surface errors: missing source, ffmpeg not found, write-permission issues, disk full, invalid EDL, command failure.
  - Acceptance Criteria:
    - Active card shows FAILED status and basic reason. Log contains raw ffmpeg tail.
    - User-visible toast/dialog for critical failures (e.g., cannot start).

- [ ] 3.14 — Unit tests (command builder, persistence, parsing)
  - Description: Add tests to ensure stable command generation, JSON persistence of Completed, and stderr parsing to progress.
  - Acceptance Criteria:
    - Given inputs, command builder produces expected args. Completed JSON round-trip works. Regex/parser extracts time/size reliably on sample logs.

- [ ] 3.15 — Integration with navigation and project context
  - Description: Ensure navigation to Export is consistent; initialize with current project manifest/EDL when present.
  - Acceptance Criteria:
    - From Projects/Markup, navigating to Export reflects current project context and enables Initialize button only if prerequisites are met (source exists, EDL valid when idle-trim ON).

- [ ] 3.16 — Minimal logging hooks
  - Description: Add lightweight logging for queue lifecycle, command execution, state transitions, and errors.
  - Acceptance Criteria:
    - Logs are on by default at INFO; ffmpeg command lines logged at DEBUG.

Out of scope for v0.1.0 → v0.2.0 candidates
- Hardware-accelerated encode paths (NVENC/QSV/AMF) with device detection and fallbacks.
- Resume in-progress jobs after app restart; durable Active queue; crash-safe checkpoints.
- Batch preset management UI (create/edit/delete presets); more codecs (HEVC, ProRes), per-preset advanced flags.
- Concurrent renders (parallel workers) with device-aware scheduling.
- Rich bitrate/CRF tuning UI, 2‑pass encode path for quality mode.
- Scoreboard/overlay burn‑in during export.
- Detailed per-segment re-timing and audio crossfades between cuts.
