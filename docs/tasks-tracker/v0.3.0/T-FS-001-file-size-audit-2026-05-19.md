# T-FS-001: Kotlin file size audit (500+ lines)

- [ ] Done

Date: 2026-05-19 20:47 (local)

Purpose
- Identify Kotlin source files exceeding the 500-line guideline and flag them for breakdown or explicit justification per `docs/architecture-rules.md`.

Criteria
- Kotlin `.kt` files with more than 500 lines.
- Default action: split into smaller focused components unless a clear file-level KDoc justifies keeping the file large.

Findings (to be broken down or explicitly justified)
- [ ] src\\main\\kotlin\\org\\litvin\\ui\\tabs\\scoring\\SwingScoringPanel.kt — 1465 lines  (ref: see E-SC-001 for componentization plan and status)
- [ ] src\\main\\kotlin\\org\\litvin\\ui\\tabs\\markup\\SwingMarkupPanel.kt — 1047 lines
- [ ] src\\main\\kotlin\\org\\litvin\\ui\\tabs\\export\\SwingExportPanel.kt — 573 lines
- [ ] src\\main\\kotlin\\org\\litvin\\ui\\tabs\\projects\\SwingProjectsPanel.kt — 540 lines
- [ ] src\\main\\kotlin\\org\\litvin\\RenderQueue.kt — 508 lines

Next steps
- For each item above:
  - Either split into smaller components aligned with feature boundaries, or
  - Add a top-of-file KDoc explaining why a large file is preferred and the trade-offs considered.

Notes
- This audit reflects the repository state at the timestamp above. Re-run the audit after major refactors or feature additions.
