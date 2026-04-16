# v0.1.0 — Export (Render) — Tasks

Notes

- This spec defines the MVP Export/Render tab. It follows the style and rigor of the Markup tasks file and maps closely to the UI mock: design/export.html.
- The goal is to render a single edited video (gap-free based on Markup/EDL), with simple preset control. A scoreboard overlay is available as an optional feature in v0.1.0 when "Include Scoreboard" is enabled.
- Presets source: docs/export-presets.json (Fast, Balanced, Quality — H.264 MP4).
- FFmpeg is the execution engine. Use trim+concat strategy per roadmap. Hardware encoders (NVENC/QSV/AMF) are available in v0.1.0 alongside software H.264.
- Current development build note: rendering is simulated (progress/UI only). FFmpeg is not executed and no output file is written yet.

User workflow (center of this spec)

- Select output quality/resolution (or preset → implies codec/bitrate/CRF + optional scaling).
- Select whether to remove the idle time (i.e., render only “kept” segments from EDL).
- Select the encoder engine (H.264 software or hardware acceleration: NVENC/QSV/AMF, when available).
- Click “Initialize Render”. Prompt for output folder and filename. Begin render.
- Show render progress as a card under “Active Processing” with percent, ETA, size, and Cancel.
- On completion, move to “Completed Renders” with resolution/encoder summary, file size, and an “Open folder” button.
- Active/completed render lists are global (not per project). Completed renders list is persistent across app restarts.

General findings & scope notes (review)

- Mock alignment: design/export.html shows a left settings panel and right queue (Active Processing + Completed Renders). MVP follows this structure.
- Presets vs manual controls: For v0.1.0, keep presets simple (Fast/Balanced/Quality) and allow an explicit output resolution toggle (1080p/4K) as shown in mock. Bitrate slider in mock can represent preset selection feedback; advanced free-form bitrate entry is out of scope.
- Encoders: v0.1.0 supports software H.264 (libx264) and hardware acceleration (NVENC/QSV/AMF) when available on the system.
- Global queue: Visible and identical regardless of which project is open; enqueue jobs with a snapshot of project settings and EDL at the time of submission.
- Persistence: Persist only completed jobs in MVP. Restoring/resuming in-flight jobs after app restart is out of scope.
- EDL integration: When “Remove idle time” is ON, build FFmpeg concat list from EdlV1 points (keeps). When OFF, render full source.
- Filesystem: Windows is packaging priority. Use native file dialog for Save As. “Open folder” should open Explorer at the file’s location.

- [X]  3.1 — Export tab shell (UI scaffolding)

  - Description: Implement Export tab per `design/export.html` with a left configuration panel and a right queue area (Active Processing + Completed Renders).
  - Acceptance Criteria:
    - Export tab reachable from app navigation; layout matches mock at a high level.
    - Left: resolution selector, idle-trim toggle, encoder selector, Initialize Render button.
    - Right: sections labeled “Active Processing” and “Completed Renders” with list/card containers.
  - Implementation Guide:
    - UI toolkit: JavaFX (match app). Mirror class structure of MarkupTab; create `ExportTab` view and wire basic button events to a dispatcher.
    - Reuse existing sidebar styling from Projects/Markup for a consistent left navigation.
- [X]  3.2 — Presets loading and binding

  - Description: Read presets from `docs/export-presets.json` and bind to UI (Fast/Balanced/Quality) including target bitrate/CRF and default resolution.
  - Acceptance Criteria:
    - Presets are displayed; selecting a preset updates the resolution and bitrate/quality display accordingly.
    - Default preset selected on first load (Balanced).
  - Implementation Guide:
    - Reuse JSON library already in use (Jackson/kotlinx). Place lightweight DTOs and a loader utility.
- [X]  3.3 — Output resolution selector

  - Description: Provide explicit resolution choices per mock (e.g., 1080p, 4K). Selecting a resolution updates the command plan (scale filter) and estimated bitrate if preset dictates.
  - Acceptance Criteria:
    - Visual selection state; resolution included in job summary and completed card.
    - If source < selected res, upscale allowed (basic scale) with note; no smart scaling in MVP.
- [X]  3.4 — Idle time removal toggle (EDL integration)

  - Description: Toggle determines whether to render only kept intervals (from EdlV1 points) or the entire source.
  - Acceptance Criteria:
    - When ON: command builder uses trim+concat over keep intervals in temporal order.
    - When OFF: full-duration render with no cuts.
  - Implementation Guide:
    - Read EDL from project. Ensure validation (sorted, half-open intervals) is honored. Re-encode borders as per roadmap.
- [X]  3.5 — Encoder engine selector (MVP scope)

  - Description: Encoder options listed; software H.264 and hardware acceleration (NVENC/QSV/AMF) are available in v0.1.0.
  - Acceptance Criteria:
    - UI allows selecting among available software and hardware encoders; unavailable ones are hidden or clearly marked unsupported.
    - Selected encoder stored in job config and shown in summaries.
- [X]  3.6 — Initialize Render → Save As dialog

  - Description: On click, show file save dialog with suggested filename and last-used folder. On confirm, enqueue a render job.
  - Acceptance Criteria:
    - User can pick folder and name. Reject overwrite unless confirmed. Job appears under Active Processing immediately.
  - Implementation Guide:
    - Use existing DialogUtils or platform file chooser. Remember last save directory in app prefs.
- [X]  3.7 — FFmpeg command builder (v1)

  - Description: Build the final command line from: source file, EDL keep list (optional), encoder, preset, resolution.
  - Acceptance Criteria:
    - H.264 (libx264) output MP4; applies `-vf scale` when needed; sets CRF/bitrate/encoder tune from preset.
    - For idle-trim ON: uses concat demuxer or filtergraph with accurate re-encode at segment borders.
    - Command string preview visible in dev logs for debugging.
- [X]  3.8 — Render job model and global queue manager

  - Description: Introduce `RenderJob` data model and a process-managed queue. Queue is global across projects within app session.
  - Acceptance Criteria:
    - Jobs hold: id, projectId (optional), source path, edl snapshot, preset id, resolution, encoder, idleTrim flag, outputPath, status, progress, eta, bytesWritten.
    - Dispatcher/manager exposes flows/observers to refresh UI on state changes.
    - Multiple jobs can run sequentially (single-worker) in MVP; parallelism off by default.
- [X]  3.8.1 — Actual rendering engine (ffmpeg execution)

  - Description: Replace the simulation loop with invoking ffmpeg using `FFmpegCommandBuilder` args to produce a real output file.
  - Acceptance Criteria:
    - When job status switches to RUNNING, spawn an ffmpeg process with built args; output is written to the chosen path (use a .part temp name and rename on success).
    - Job status reflects process result: COMPLETED on exit code 0; FAILED otherwise.
    - Stdout/stderr are captured; last stderr lines kept for diagnostics; full command logged at DEBUG.
  - Implementation Guide:
    - Use ProcessBuilder to launch ffmpeg; read stderr asynchronously (parsing comes in 3.9).
    - Ensure output directory exists; overwrite behavior already handled by Save As confirmation.
    - Clean up partial output on failure/cancel (finalize deletion rules in 3.10).
- [X]  3.9 — Progress parsing and UI card (Active Processing)

  - Description: Parse ffmpeg stderr for time/size/speed to estimate percent and ETA. Update a progress bar and stats on the active card.
  - Acceptance Criteria:
    - Show percent, ETA, and written size on the card. Updates at least 2×/sec while running.
    - Card displays filename and a stable Job ID.
- [X]  3.10 — Cancel render (terminate and cleanup)

  - Description: Provide a Cancel button on the active job card that stops the ffmpeg process and cleans up partial output (tmp or zero-length rules).
  - Acceptance Criteria:
    - After Cancel: status = CANCELED; card removed from Active and not added to Completed. Partial file is deleted (or moved to .part).
- [x]  3.11 — Completed Renders list (persistent)

  - Description: On successful completion, append a record to the Completed list and persist it to disk.
  - Acceptance Criteria:
    - Each item shows: file name, encoder + resolution summary (e.g., H.264 / 1080p), final file size, and an “Open folder” button.
    - Completed list persists across app restarts (JSON in app data). “Clear Finished” removes all persisted entries after confirm.
- [x]  3.12 — Global visibility and state retention

  - Description: Export tab shows the same Active/Completed lists regardless of project. Submitting a job from any project adds to the one global queue.
  - Acceptance Criteria:
    - Switching projects does not affect queue visibility. Completed persists; Active does not resume on restart in MVP.
- [x]  3.13 — Error handling and user notifications

  - Description: Surface errors: missing source, ffmpeg not found, write-permission issues, disk full, invalid EDL, command failure.
  - Acceptance Criteria:
    - Active card shows FAILED status and basic reason. Log contains raw ffmpeg tail.
    - User-visible toast/dialog for critical failures (e.g., cannot start).
- [x]  3.14 — Unit tests (command builder, persistence, parsing)

  - Description: Add tests to ensure stable command generation, JSON persistence of Completed, and stderr parsing to progress.
  - Acceptance Criteria:
    - Given inputs, command builder produces expected args. Completed JSON round-trip works. Regex/parser extracts time/size reliably on sample logs.
- [x]  3.15 — Integration with navigation and project context

  - Description: Ensure navigation to Export is consistent; initialize with current project manifest/EDL when present.
  - Acceptance Criteria:
    - From Projects/Markup, navigating to Export reflects current project context and enables Initialize button only if prerequisites are met (source exists, EDL valid when idle-trim ON).
- [ ]  3.16 — Minimal logging hooks

  - Description: Add lightweight logging for queue lifecycle, command execution, state transitions, and errors.
  - Acceptance Criteria:
    - Logs are on by default at INFO; ffmpeg command lines logged at DEBUG.
Design reference — Scoreboard overlay visual

- Canonical image: design/scoreboard-design.png (Windows absolute: C:\w\tennis-record\design\scoreboard-design.png). All visual requirements in 3.17–3.20 reference this image.

- [x]  3.17 — Scoreboard overlay: UI checkbox (Include Scoreboard)

  - Description: Add an "Include Scoreboard" checkbox to the left settings panel of Export. When enabled, the rendered video includes a score overlay styled like the modern card shown in the design reference image (design/scoreboard-design.png) — dark rounded panel with header and two player rows — and updates after each point.
  - Acceptance Criteria:
    - Checkbox is placed directly below the existing "Remove idle time" checkbox, with label "Include Scoreboard" and a fixed-English tooltip explaining behavior.
    - Default state rules: Checked by default if the current project has at least 1 scored point; otherwise unchecked. If there is no project loaded, default is unchecked.
    - The checkbox state is captured into the `RenderJob` config (e.g., `includeScoreboard: Boolean`).
    - The setting is reflected in the job summary and the Completed Renders card.
  - Implementation Guide:
    - Read scoring snapshot from current project using existing scoring model (e.g., `ScoreV1` via `ScoreIO`). Count scored points to decide the default when the Export tab initializes or when project context changes.
    - Do not persist the checkbox choice globally in MVP; rely on project-based default + user adjustment per job.
- [x]  3.18 — Scoreboard overlay: timeline generation

  - Description: Build a scoreboard timeline that maps the evolving match score to output-render time, accounting for Idle-trim ON/OFF and EDL keeps.
  - Acceptance Criteria:
    - For Idle-trim ON: overlay updates exactly at point boundaries in the concatenated output (no drift). For Idle-trim OFF: overlay updates at original source times.
    - Scoring data is authoritative for values (points/games/sets); EDL only determines timing/alignment of updates.
    - If some points are not scored, display the last known score until the next completed/scored point (carry-forward behavior).
    - Supports at least: current game point score (0/15/30/40/Ad), games per set, sets per match for two players; no tiebreaks in v0.1.x; for multi-set, display each set’s game score; formatting matches Scoring tab values.
    - Correctly initializes the first overlay state at the start of the export and advances after each completed point (point boundaries only).
  - Implementation Guide:
    - Derive a sequence of point intervals from EDL/Markup and align them with scoring outcomes from `ScoreV1`.
    - Produce an intermediate representation (e.g., list of `{startMs, endMs, text}` for the overlay) in output time domain. This IR will be used by the render path to burn in the overlay.
- [x]  3.19 — Scoreboard overlay: ffmpeg composition (burn-in)

  - Description: When `includeScoreboard` is true, augment the ffmpeg command to burn in a styled scoreboard overlay that matches the design reference image layout and feel (design/scoreboard-design.png).
  - Acceptance Criteria (visuals at 1080p; scale proportionally for 4K using `scale` or ASS `PlayRes`):
    - Position: top-left safe area with 48 px margin from top/left at 1080p.
    - Container: rounded rectangle 900×260 px, background #0E1116 at 88% opacity, 12 px radius, soft shadow (simulate with blurred translucent rectangle if using image overlay; otherwise omit shadow if not feasible).
    - Header row:
      - Left leading dot: 16 px circle, neon green #C4FF4D.
      - Title text: "BATUMI RAKETO LEAGUE" uppercase, Inter/Semibold fallback, 28 px, letter spacing +2, color #C4FF4D.
      - Header gradient optional; solid background acceptable in MVP.
    - Two player rows:
      - Left icon squares: 24 px, Player 1 = blue #4DA3FF, Player 2 = red #FF6B6B.
      - Name labels: use the user-provided Player 1/Player 2 names from scoring (ScoreV1), styled uppercase at render time for visual consistency, 30 px, color #E7ECEF at 92% opacity.
      - Per-set game cells: show last two sets as numbers (e.g., 6, 4 / 3, 6), 44 px wide each, centered, 30 px font, color #AAB4BF.
      - Current point score cell at right: 120 px wide, bold 64 px; winner-leading emphasis with bright green #C4FF4D; trailing player’s point score dimmed to 50% opacity. No animations.
    - Spacing: 28 px vertical between rows; 36 px gutters between name and game cells; 22 px between cells.
    - Behavior: updates instantly at point boundaries; no fades.
    - Legibility: all text remains readable at 1080p and 4K; safe-area margins preserved.
    - If fonts are unavailable, use system sans-serif with similar weights.
  - Implementation Guide (choose one practical approach):
    - ASS subtitles skin: Pre-render static panel as semi-transparent PNG, and generate an `.ass` file for all texts (title, names, set/game numbers, point scores) positioned within the panel. Update only changing texts per interval. Composite using `-vf "[0:v][bg]overlay,subtitles=score.ass"` or `subtitles` alone if the panel is drawn via ASS `\\bord`/`\\shad` boxes.
    - Drawtext pipeline: Use `-vf drawbox,drawtext,...` with `sendcmd` to switch texts at boundaries. Pre-render panel as PNG for simpler visuals and overlay it, then draw text on top.
    - Image sequence: Pre-render per-state PNGs (panel + texts) and switch with `overlay=enable='between(t,...)'`. Accepts larger command but simplest to match exact look.
    - Ensure the filtergraph integrates with existing trim/concat/scale correctly and that all Windows paths are quoted/escaped.
- [x]  3.20 — Scoreboard overlay: unit tests and golden samples

  - Description: Add tests for timeline generation and for ffmpeg command augmentation when `includeScoreboard` is enabled.
  - Acceptance Criteria:
    - Given a small synthetic scoring + EDL input, the generated overlay timeline matches expected output-time intervals (golden test).
    - Command builder includes the correct filters (ASS/drawtext/overlay) and orders them properly with scale and concat; visual golden for one frame/state is produced and compared by hash/size in test resources.
    - Provide 1–2 short stderr log samples (fixtures) to ensure progress parsing (3.9) remains compatible when overlay filters are present.
    - Timeline generation honors scoring-as-authoritative when counts differ between scoring and EDL (EDL only drives timing).
    - Carry-forward behavior is applied when some points are not scored (last known score is shown until next completed/scored point).
    - Overlay text uses fixed English labels (e.g., "PLAYER 1", "PLAYER 2", "Ad").

Decisions — Scoreboard overlay (v0.1.0)

- Keep overlay super simple.
- Player labels: come from scoring (ScoreV1) player names; default to "Player 1" and "Player 2" when not set.
- Show points, games, and sets; no tiebreaks in v0.1.x.
- For multi-set matches, display each set’s game score.
- No fade-ins/outs; instantaneous updates at point boundaries only.
- Checkbox placement: put "Include Scoreboard" directly below the "Remove idle time" (idle-trim) checkbox.
- Data authority: use scoring data for values; EDL is only used to time updates.
- If some points are not scored, display the last known score until the next completed/scored point.
- Language: fixed English in v0.1.x (e.g., labels like "Player 1", "Player 2", "Ad").
- Guardrails: none; if the checkbox is selected, the overlay is always rendered.

Open questions — Scoreboard overlay

- Is there a keyboard shortcut for toggling "Include Scoreboard"?

Out of scope for v0.1.0 → v0.2.0 candidates

- Advanced hardware encode features only (automatic device detection, intelligent fallbacks, per-device tuning/profiles).
- Resume in-progress jobs after app restart; durable Active queue; crash-safe checkpoints.
- Batch preset management UI (create/edit/delete presets); more codecs (HEVC, ProRes), per-preset advanced flags.
- Concurrent renders (parallel workers) with device-aware scheduling.
- Rich bitrate/CRF tuning UI, 2‑pass encode path for quality mode.
- Advanced overlay animations and alternate themes (beyond the v0.1.0 visual)
- Detailed per-segment re-timing and audio crossfades between cuts.

- [x]  3.21 — Use player names from scoring in overlay

  - Description: When the scoreboard overlay is enabled, use the user-provided player names from `score.json` (ScoreV1) instead of fixed placeholders.
  - Acceptance Criteria:
    - Overlay name fields reflect `ScoreV1.player1Name` and `ScoreV1.player2Name` with fallback to "Player 1" / "Player 2" when empty/missing.
    - Names update correctly in the generated overlay timeline and in the final rendered video.
    - Uppercasing for style is applied at render time only; persisted names remain as entered by the user.
  - Implementation Guide:
    - Thread the names through the overlay timeline builder and `AssOverlayWriter`/drawtext pipeline.
    - Sanitize/control max visual length (e.g., ellipsis) to avoid layout overflow; do not truncate in persistence.
  - Decisions:
    - Language remains fixed-English; only names are user-provided. Fonts/weights per 3.19 visual requirements.
