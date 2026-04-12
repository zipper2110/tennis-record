# Roadmap & Changelog

This document is the single source of truth for upcoming work (task tracker) and release history (changelog).

Conventions
- Use checkboxes to track task status. When a version ships, move the checked items to the Changelog entry for that version.
- Versioning: semantic-ish during 0.x (0.1.0, 0.2.0…). Patch versions (0.1.1) are bugfix/maintenance.
- Dates are in ISO format (YYYY-MM-DD). Current file created: 2026-03-15.

## Unreleased (Next)
Planned items that may land in the next release. Promote items to a specific version when committed.

- [x] Decide initial OS packaging priority — Windows is the priority
- [x] Define MVP export presets: Fast, Balanced, Quality (H.264 MP4; tune bitrate/quality) — see docs/export-presets.json
- [x] Draft UI wireframes for application tabs — see design drafts
  - Projects: design/projects.html
  - Markup: design/markup.html
  - Adjustments: design/adjustments.html
  - Scoring: design/scoring.html
  - Export: design/export.html

Notes: Score overlay is post‑MVP and will be planned under v0.2.0+

## v0.1.0 — MVP (TBD)
Goal: Deliver a minimal editor focused on trimming dead time and exporting a gap-free video, without scoreboard/overlay.

Workflow
- [ ] Step 0: New/Open project — choose project folder and name; or open existing `*.trproj`
- [ ] Step 1: Select a source video from filesystem (if not already selected)
- [ ] Step 2: Trim tab — manually mark and remove gaps between points
- [ ] Step 3: Render tab — export video without gaps with presets: Fast, Balanced, Quality

### Step 0 — New/Open Project: Task Breakdown
See detailed tasks with descriptions and acceptance criteria in: [v0.1.0/step-0.md](tasks-tracker/v0.1.0/projects.md)

Core
- [ ] Open a video file and play/pause/seek (VLCJ preview)
- [ ] Keyboard shortcuts (Space/J/K/L, I/O, Delete, arrows)
- [ ] Mark in/out and create "keep" segments
- [ ] Ripple delete (remove gaps between keeps)
- [ ] Keeps list UI with drag/reorder and delete
- [ ] Basic time display and transport controls

Scoreboard (post‑MVP)
- Out of scope for v0.1.0; planned under v0.2.0.

Project I/O
- [ ] Define EDL JSON schema (v1)
- [ ] Save/load project (*.trproj) with autosave

Export
See detailed tasks with descriptions and acceptance criteria in: [v0.1.0/export.md](tasks-tracker/v0.1.0/export.md)
- [ ] Build FFmpeg command(s) from EDL (no overlay in MVP)
- [ ] Implement trim + concat strategy (re-encode borders)
- [ ] Allow selecting output file name and location in the filesystem
- [ ] Provide three export presets: Fast, Balanced, Quality (H.264 MP4)
- [ ] Progress parsing and cancelation

Quality & Ops
- [ ] Basic logging setup
- [ ] Error handling for missing/broken media
- [ ] Minimal packaging via jpackage (dev build)

## v0.2.0 — Scoreboard + editing quality (TBD)
Scoreboard & Overlay
- [ ] Scoring domain model and rules (best-of-3 default)
- [ ] Toggle server and add point at playhead time
- [ ] Compute score at time t from events
- [ ] Overlay widget drawn above video in preview
- [ ] Export with scoreboard overlay via FFmpeg (drawtext or overlay)

Editing Quality & Performance
- [ ] Better scrubbing and approximate frame step
- [ ] Timeline thumbnails (ffprobe/ffmpeg extract)
- [ ] Undo/redo stack
- [ ] Export presets (refine quality/size; add HW accel options)
- [ ] Hardware-accelerated encode paths (NVENC/QSV/Videotoolbox) with detection
- [ ] Themed overlay styles and positioning presets

## v0.3.0 — Advanced media workflow (TBD)
- [ ] Proxy media generation for 4K
- [ ] Color adjustments (brightness/contrast/basic EQ)
- [ ] Rich scoreboard themes and match formats
- [ ] In-process FFmpeg (JavaCPP Presets) for thumbnails/proxy/advanced

## Backlog (Later / Nice-to-have)
- [ ] Audio waveform on timeline
- [ ] Multi-angle support (switchable cameras)
- [ ] Batch export and queueing
- [ ] Plugin/theme system

---

# Changelog

Entries appear here when versions are released.

## [v0.1.0] — TBD
Initial MVP release.
- New/Open project workflow; select source video
- Open video, preview playback
- Mark in/out, keeps, ripple delete
- Save/load EDL project
- Export gap-free video (Fast/Balanced/Quality presets) — no overlay in MVP

## [v0.2.0] — TBD
Editing quality improvements and performance enhancements.

## [v0.3.0] — TBD
Advanced media workflow and color/processing features.
