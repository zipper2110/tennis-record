## Instructions and user input

When a command is given, assess it and see if one of the following is required:

- answering a question
- task creation/edit
- task implementation
- bugfix

If explicitly not asked to implement/work on a task/fix, do not start implementation.

Task definitions are stored in .md files and are used as specification and source of truth. If you spot any additional significant task-related info during work, feel free to update the task definition. We don't want discrepancies between tasks definitions and how things actually work.

There is no need to output the implementation plan for review if was not asked. Do what's asked in the task directly.

## Reporting

When the work is done, keep the report as short as possible.

- no need to say which files were changed, it's already displayed for the user
- no need to say what was changed overall, unless it's something unexpected
- no need to describe all changes if they were preagreed
- no need to describe how did you verify the changes
- in the beginning of the report shortly state if the work is done in full as expected or not.

## Plans & choices

Plans are reserved only for non-trivial or risky changes. For trivial edits and micro-fixes (≈1–3 lines, single file, no side-effects), implement immediately without drafting a plan; just do the change and report succinctly.

Do not produce any plans unless explicitly asked by the user or the change is non-trivial/risky. If you believe a feature should be planned before implementing, suggest making a plan first.

When producing a plan, make it short and concise. Define the goal in 1-2 compact sentences. Define sequence of steps to achieve it. And in 1-2 short sentences define the end result.

When a choice is to be made, instead of making a plan present the choice options first and align on decisions with the user.

If a plan is too complex to follow this guide, change the goal to something more achievable so that it doesn't require a complex plan.

If a complex solution outline/spec is required for some features, consider suggesting making a spec for it in a separate file.

## Task status convention

- Each task in specs/epics must include a status checkbox line right under the task header: `- [ ] Done`.
- When the task is fully completed, check it: `- [x] Done`.
- Keep subtasks and acceptance criteria as-is; use them to reflect progress details if helpful.
- When you complete a task during your work, also mark its checkbox as done in the corresponding spec/epic file.

## Specs/Epics authoring rules

Based on the existing specs/epics under `docs/tasks-tracker`, all new and updated specs must follow these rules.

- Where to place specs
  - Store specs/epics under `docs/tasks-tracker` in versioned folders (e.g., `v0.3.0`).
  - Do not edit historical specs retroactively; create a new version when scope or decisions change.

- Spec/Epic contents (required)
  - Supported user workflows
    - List concrete user scenarios the feature must support.
    - Explicitly call out out-of-scope workflows for this version.
  - Solution outline
    - High-level approach: core components, data flow, state, and major interactions.
    - Reference existing modules/classes where applicable; keep it consistent with code terminology.
    - Note performance/UX constraints and critical invariants.
  - List of tasks for implementation
    - Break down into small, testable tasks (e.g., `T1`, `T2`, …) with clear scope.
  - Open topics/questions
    - General-level open questions affecting the whole solution.
    - Per-task open questions; each task should also track its own open points.

- Per-task contents (required)
  - Acceptance criteria
    - Bullet list describing the observable end result, including UX/behavioral details, edge cases, and performance/error-handling expectations where relevant.
  - Implementation guide
    - Suggested approach, touched modules/classes, implementation steps, key algorithms/data structures, and test guidance.
    - Keep guides pragmatic; they inform, but implementation details can evolve if acceptance is preserved.
  - Open questions
    - Ambiguities and decisions needed before/during implementation.

- Cross-cutting rules
  - Architecture compliance: the overall solution and every task must respect the architecture rules (see reference below). If a conflict exists, update the spec or seek clarification before coding.
  - Consistency: use consistent terminology with existing specs and code.
  - Traceability: reference task IDs in commit messages and PR titles/descriptions.
  - Assets/links: when relevant, link to design assets under `design/` and to previous specs/decisions.

## Architecture rules reference

The architecture rules are authoritative for packaging, components, and dependencies. All tasks/specs and changes must conform to them. If a task/spec conflicts with these rules, update the spec accordingly or request clarification before implementing.

See: [docs/architecture-rules.md](../docs/architecture-rules.md)
