# Adjustments scale alignment (VLCJ preview vs FFmpeg export)

- [x] Done

Supported user workflows
- As a user, I expect the exported video colors to match what I see in the preview.
- Out of scope for this version: geometry export (rotate/crop) scale differences; only color parameters are covered.

Solution outline
- Identify parameter scale mismatches between VLCJ preview and FFmpeg eq filter.
- Convert model (preview-oriented) values to FFmpeg scales during command generation.
- Keep filter order consistent: scale → color (eq) → subtitles. For idle-trim, apply after concat and scale.

Tasks for implementation
- T1 — Add conversion from model/VLCJ scale to FFmpeg eq scale at export time (notably brightness identity 1.0 → 0.0). [Done]
- T2 — Integrate converted parameters into FFmpegCommandBuilder for both -vf and -filter_complex paths. [Done]
- T3 — Add unit tests covering identity and extremes to guard mapping. [Done]
- T4 — Update tasks-tracker docs to record mapping decision and close task. [Done]

Acceptance criteria
- Exported command uses eq with brightness mapped as (model_brightness - 1.0) and clamps to eq boundaries.
- For identity values, either no eq filter is emitted or it uses identity parameters (0/1/1/1).
- The order of filters ensures the rendered output matches preview (post-scale, pre-subtitles), for both simple and idle-trim paths.
- Unit tests verify identity and extremes and pass with existing test suite.

Open questions
- Should temperature/tint be mapped via specialized FFmpeg filters (e.g., colorbalance/temperature) in future versions for closer parity? (Future)
