# v0.2.0 — Export — Tasks

Notes
- Planning/specification only for v0.2.0.
- This version integrates the Adjustments epic into the export pipeline so that exported videos reflect project adjustments.
- See `docs/tasks-tracker/v0.2.0/adjustments.md` for the canonical data model and FFmpeg mapping guidance.

- [ ] 7.1 — Read `AdjustmentsV1` and build FFmpeg filter chain
  - Description: Load `adjustments.json` from the project and translate values into a deterministic FFmpeg filter graph string.
  - Acceptance Criteria:
    - Identity values produce either an empty filter graph or a no-op equivalent; non-identity values map as per Adjustments epic (eq/colorbalance/rotate/crop/scale/pad).
    - Clamp/sanitize values into safe ranges.
    - Unit tests cover representative combinations: color only, geometry only, both; extreme but valid ranges; identity.
  - Cross-reference: See Adjustments v0.2.0 5.6 for filter chain generation specifics.

- [ ] 7.2 — Integrate filter chain into render pipeline
  - Description: When exporting, append the generated filter graph to the command used by `RenderQueue`/FFmpeg invocation.
  - Acceptance Criteria:
    - Exported file visually matches the Adjustments preview within expected differences (WB approx).
    - Logs include the exact filter graph used.
    - Errors in filter graph construction fail fast with a clear message.
  - Cross-reference: See Adjustments v0.2.0 5.7.

- [ ] 7.3 — Golden export verification (optional in v0.2.0)
  - Description: Add a small integration test (or manual golden instructions) that exports a 2–3s clip with known adjustments and verifies basic properties (e.g., non-identity histogram, rotated dimensions when rotation applied).
  - Acceptance Criteria:
    - Documented steps or CI test; may be deferred to v0.3.0 if CI video compare is heavy.

Dependencies and integration notes
- Depends on: existing `RenderQueue`/FFmpeg path; ensure pixel formats and color spaces are stable (e.g., `-pix_fmt yuv420p`).
- Keep compatibility with current export options; Adjustments are an additive pass.
