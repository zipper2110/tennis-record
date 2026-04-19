# v0.2.0 — Scoring tab: Points list shows Set/Game and in‑game score — Tasks

Context

- Builds on v0.1.0 Scoring tab (4.x series), rules engine (4.8), and persistence (ScoreV1).
- This task refines the left points list to show compact match context per point.

5.1 — Points list: show current Set/Game and in‑game score for each point

- Description: In the Scoring tab’s left points list, each point row should display the match context at that point: current set index, current game index within the set, and the current score within that game, formatted as: "set {S} game {G} P1-P2". Example: "set 1 game 2 40-0".

- Acceptance Criteria:
  - Each point row includes a right‑aligned (or otherwise secondary text) label with: "set {S} game {G} {P1}-{P2}".
  - The score within the game is shown with Player 1 first, then Player 2, e.g., "40-15", "30-30", "Ad-40".
  - The values represent the state BEFORE applying that point’s outcome (i.e., the state at the start of the point segment).
  - Unscored points still show a correct context derived from prior outcomes.
  - Tiebreaks:
    - At 6–6 (win‑by‑2 tiebreak), the game label remains "game {G}" (tiebreak counts as a game), and the in‑game score shows numeric tiebreak points (e.g., "5-4").
  - If the point immediately completes a game or set, the label still reflects the pre‑point state (no special suffixes in the label).
  - Formatting is exactly: lowercase words, single spaces, no punctuation: "set {S} game {G} {P1}-{P2}".

- Implementation Guide:
  - Source state from the existing rules engine timeline (v0.1.0 4.8). For each point i, compute the state at i (before applying point i), not at i+1.
  - Map regular game points using the same labels used elsewhere (0, 15, 30, 40, Ad). For pre‑deuce sequences, use numeric labels accordingly.
  - During tiebreak, display raw numeric points for P1 and P2.
  - Integrate with the existing list cell renderer used in Scoring; keep layout compact (fit within 140 px list width context—may truncate on small windows with an end ellipsis if needed).
  - Recompute and update labels live when outcomes change (same recomputation triggers as 4.8 and 4.11). Cache interim results to avoid O(n²) recomputation while typing or bulk edits.
  - Respect player name ordering but do not include names in this compact label; the score order is strictly P1 first, then P2.

- Open Questions:
  - Localization: For v0.2.0, keep English lowercase "set" and "game" literals; future versions may localize.
  - Very long matches: If game index exceeds two digits, allow natural wrapping or truncation per standard list cell behavior; no special compacting in v0.2.0.

- Out of Scope for this task:
  - Any changes to the scoreboard overlay (covered by v0.2.0 overlay task).
  - Changes to persistence format.
  - Hotkeys or navigation behavior.
