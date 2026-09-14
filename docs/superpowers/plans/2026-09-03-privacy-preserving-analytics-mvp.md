# Privacy-Preserving Analytics MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional, privacy-preserving product analytics to Tennis Record, with a Cloudflare Worker/D1 ingestion service, documented SQL reporting, and a manual low-cost deployment path.

**Architecture:** The Swing application composes a single, thread-safe analytics controller at startup. It exposes only a typed `Analytics.record(event)` facade to feature code and starts as a no-op until the user gives current-version consent. The enabled implementation owns an in-memory session UUID, bounded buffer, scheduler, and HTTPS transport; it never persists identifiers or blocks UI work. A TypeScript Cloudflare Worker independently validates the versioned envelope, rate-limits requests, writes valid rows idempotently to EU-jurisdiction D1, and deletes raw rows daily. SQL files are the reporting interface; there is no custom dashboard.

**Tech Stack:** Kotlin/JVM 17, Swing, JDK `HttpClient`, Jackson 2.17, Maven/JUnit 5; TypeScript, Cloudflare Workers, D1/SQLite, Wrangler, Vitest/Miniflare; GitHub Pages Markdown; Cloudflare Workers Paid ($5/month) and D1.

**Spec:** [Privacy-preserving analytics design](../specs/2026-08-29-privacy-preserving-product-analytics-design.md); [Cloudflare D1 MVP hosting decision](../specs/2026-09-03-cloudflare-d1-mvp-hosting-decision.md)

## Global Constraints

- Implement exactly the 14 approved event types: three session lifecycle events, project creation/opening, source-video result, markup add/remove, scoring, adjustment category, and four export outcomes. Do not add generic event names, free-form properties, or a persistent identifier.
- The desktop app remains fully functional, makes no analytics network object, and writes no analytics queue to disk until the effective consent state is enabled **and** a complete release configuration is present. Missing, malformed, partial, or newer-than-known configuration fails closed to `DisabledAnalytics`.
- Store only `analyticsChoice`, notice version, and a local recorded-at timestamp in Java Preferences. A session UUID, sequence counter, and unsent events live only in memory and are discarded on disable or process exit.
- Client and Worker independently allowlist schema version 1, notice version 1, names, properties, enums, numeric ranges, body depth, and sizes. Neither side may accept arbitrary maps or text.
- Do not transmit, persist, log, echo, or derive analytics from video/audio, filenames, paths, manifest or project IDs/names, player data, scores, timestamps from the client wall clock, exception text, stack traces, device/user identifiers, headers, query strings, or IP addresses.
- Analytics calls return immediately and never throw to a feature, event-dispatch, render, persistence, or shutdown caller. Network work runs on daemon-owned background work; normal exit gets at most 500 ms for one final flush.
- D1 is the deliberate MVP exception to the original PostgreSQL plan. Create it with `eu` jurisdiction and no read replicas. Document that the Worker may receive traffic outside the EU before writing EU-resident D1 data.
- Deployment stays manual: no Cloudflare API credential in the repository or GitHub Actions. Commit only an example Wrangler configuration; the actual database ID, rate-limit namespace ID, and enable flag live in an ignored local `wrangler.toml`.
- Preserve the user's existing changes in `src/main/kotlin/org/litvin/ui/tabs/scoring/ui/PointsListPanel.kt`, `src/test/kotlin/org/litvin/ui/tabs/scoring/ui/PointsListPanelTest.kt`, and `docs/try-catch-cleanup-audit.md`.

---

## Task 1: Define a single typed, shared analytics contract

**Files:**

- Create: `analytics-contract/v1/valid-batches.json`
- Create: `analytics-contract/v1/invalid-batches.json`
- Create: `analytics-contract/v1/README.md`
- Create: `src/main/kotlin/org/litvin/analytics/Analytics.kt`
- Create: `src/main/kotlin/org/litvin/analytics/AnalyticsEvent.kt`
- Create: `src/main/kotlin/org/litvin/analytics/AnalyticsEventRegistry.kt`
- Create: `src/main/kotlin/org/litvin/analytics/AnalyticsBuildConfig.kt`
- Create: `src/main/kotlin/org/litvin/analytics/AnalyticsClock.kt`
- Create: `src/main/kotlin/org/litvin/analytics/AnalyticsEnvelope.kt`
- Create: `src/test/kotlin/org/litvin/analytics/AnalyticsEventRegistryTest.kt`
- Create: `src/test/kotlin/org/litvin/analytics/AnalyticsBuildConfigTest.kt`

- [ ] Write the contract fixtures first. Include one valid schema-v1 envelope for every approved event, boundary values for sequence number, elapsed duration, app version, and the four closed-enum property families. Include invalid cases for unknown keys, event names, enums, paths, email-shaped text, exception-shaped text, oversized properties, empty/oversized event arrays, duplicate sequences, malformed JSON, and deeply nested JSON. Use only synthetic UUIDs and `synthetic-smoke` app versions in fixtures.

- [ ] Define `Analytics` as the only public product-facing API:

  ```kotlin
  interface Analytics {
      fun record(event: AnalyticsEvent)
  }
  ```

  Add a singleton/stateless `DisabledAnalytics` implementation whose `record` does nothing. Keep all data-bearing types sealed or private so feature callers cannot pass names, maps, JSON, filesystem values, or arbitrary strings.

- [ ] Model every approved event as a sealed subtype of `AnalyticsEvent`. Model allowed properties as enums (`SourceVideoResult`, `AdjustmentCategory`, `ExportContainer`, `EncoderFamily`, `ExportFailureCategory`) and an integer-only `ExportDurationMs` constructor that clamps/rejects values outside `0..604800000`. Event classes must expose no field for app version, session ID, receipt time, project data, media metadata, or arbitrary properties.

- [ ] Put the one authoritative desktop registry in `AnalyticsEventRegistry`: schema/notice versions, exact names, enum string mappings, property serialization, and defensive validation. It must produce a DTO envelope with the six exact event fields from the spec and reject an accidental invalid object before it reaches the buffer.

- [ ] Add `AnalyticsBuildConfig.fromSystemProperties()` that reads only `tennis.record.analytics.endpoint`, `tennis.record.analytics.privacyUrl`, and `tennis.record.analytics.noticeVersion`. Accept configuration only when both URLs are HTTPS without user info or query/fragment, notice version equals 1, and endpoint path is exactly `/v1/events/batch`; otherwise return a disabled configuration with a safe diagnostic code. Derive broad OS family from `os.name` only.

- [ ] Add a monotonic `AnalyticsClock` abstraction (`elapsedMs()` and scheduler-friendly elapsed duration) with a production `System.nanoTime()` implementation. Do not use local wall-clock time for event payloads.

- [ ] Make registry tests load the shared fixtures and assert exact JSON serialization for each valid event, validation failures for every invalid case, absence of forbidden key names in all output, and no path-like/string property escape hatch. Test build configuration with complete, partial, HTTP, query-bearing, and unknown notice-version inputs.

- [ ] Verify the focused test suite:

  ```powershell
  mvn -B -Dtest=AnalyticsEventRegistryTest,AnalyticsBuildConfigTest test
  ```

- [ ] Commit the contract and pure desktop model separately:

  ```powershell
  git add analytics-contract src/main/kotlin/org/litvin/analytics src/test/kotlin/org/litvin/analytics
  git commit -m "feat: define privacy-preserving analytics contract"
  ```

## Task 2: Implement consent, session lifecycle, bounded delivery, and transport

**Files:**

- Create: `src/main/kotlin/org/litvin/analytics/AnalyticsPreferences.kt`
- Create: `src/main/kotlin/org/litvin/analytics/AnalyticsController.kt`
- Create: `src/main/kotlin/org/litvin/analytics/EnabledAnalytics.kt`
- Create: `src/main/kotlin/org/litvin/analytics/AnalyticsSession.kt`
- Create: `src/main/kotlin/org/litvin/analytics/AnalyticsBuffer.kt`
- Create: `src/main/kotlin/org/litvin/analytics/AnalyticsTransport.kt`
- Create: `src/main/kotlin/org/litvin/analytics/JdkAnalyticsTransport.kt`
- Create: `src/main/kotlin/org/litvin/analytics/AnalyticsLifecycle.kt`
- Create: `src/test/kotlin/org/litvin/analytics/AnalyticsPreferencesTest.kt`
- Create: `src/test/kotlin/org/litvin/analytics/AnalyticsControllerTest.kt`
- Create: `src/test/kotlin/org/litvin/analytics/AnalyticsBufferTest.kt`
- Create: `src/test/kotlin/org/litvin/analytics/AnalyticsTransportTest.kt`
- Create: `src/test/kotlin/org/litvin/analytics/AnalyticsLifecycleTest.kt`

- [ ] Add tests with an isolated in-memory `Preferences` node, fake monotonic clock, fake scheduler, and recording transport before production code. Cover every consent/notice transition, including dismissal recording current-version `DISABLED`; a future stored notice version; enabled mid-process; disable during queued/retry work; and no client/transport construction before effective enablement.

- [ ] Implement `AnalyticsPreferences` with exactly `UNDECIDED`, `ENABLED`, and `DISABLED`, notice version, and local ISO-8601 decision time. Resolve old/future versions to disabled and `needsChoice=true`. Never persist session data, event data, endpoint, or privacy URL.

- [ ] Implement `AnalyticsController` as the stable `Analytics` facade injected throughout the app. Its atomic delegate begins as `DisabledAnalytics`; `enable()` records current consent, creates a new session, emits `session_started` at sequence 0, and installs the enabled delegate. `disable()` must first swap in no-op behavior, then cancel scheduling/retries, clear the queue, and destroy the in-memory session without trying to send `session_ended`.

- [ ] Implement `AnalyticsSession` using a cryptographically strong UUID v4, monotonically increasing sequence values beginning at zero, and elapsed time clamped to seven days. `AnalyticsLifecycle` schedules a heartbeat every five minutes and emits one normal-end event through a JVM shutdown hook, then permits only the specified 500 ms best-effort flush. Ensure all analytics executors use daemon threads and `close()` is idempotent for tests and shutdown.

- [ ] Implement `AnalyticsBuffer` with 100 in-memory events, normal 30-second flushes, 20-event batches, a 20-event immediate-flush threshold, and a 64 KiB serialized request cap. When full, remove the oldest non-lifecycle event; retain `session_started` and replace an older heartbeat before dropping lifecycle data. Keep sequence values fixed when a batch is retried and log only count-based diagnostics.

- [ ] Implement `JdkAnalyticsTransport` with `java.net.http.HttpClient`, short connect/response timeouts, no cookies, no redirects to a different origin, JSON content type, and sanitized logging. Classify DNS/connection/TLS/timeout, `429`, and `5xx` as retryable; bound exponential backoff with jitter from about one second to five minutes; drop all other `4xx`; and make `410` clear the queue and suppress delivery for the current process. Do not log request/response bodies, session IDs, full URLs, headers, or exception messages.

- [ ] Make transport tests prove request envelope shape, one retry retaining identical sequence numbers, bounded `Retry-After`, buffer eviction, request-size splitting/drop behavior, `410` suppression, no disk writes, daemon ownership, and that exceptions from JSON/transport/scheduler code never escape `record`.

- [ ] Run:

  ```powershell
  mvn -B -Dtest=AnalyticsPreferencesTest,AnalyticsControllerTest,AnalyticsBufferTest,AnalyticsTransportTest,AnalyticsLifecycleTest test
  ```

- [ ] Commit:

  ```powershell
  git add src/main/kotlin/org/litvin/analytics src/test/kotlin/org/litvin/analytics
  git commit -m "feat: add consented in-memory analytics delivery"
  ```

## Task 3: Add a non-coercive Privacy UI and compose analytics at startup

**Files:**

- Create: `src/main/kotlin/org/litvin/ui/privacy/AnalyticsConsentDialog.kt`
- Create: `src/main/kotlin/org/litvin/ui/privacy/PrivacySettingsDialog.kt`
- Create: `src/main/kotlin/org/litvin/ui/privacy/PrivacyLinkOpener.kt`
- Create: `src/test/kotlin/org/litvin/ui/privacy/AnalyticsConsentDialogTest.kt`
- Create: `src/test/kotlin/org/litvin/ui/privacy/PrivacySettingsDialogTest.kt`
- Modify: `src/main/kotlin/org/litvin/SwingMainApp.kt`
- Modify: `distribution/windows/Build-AppImage.ps1`
- Modify: `.github/workflows/windows-release.yml`

- [ ] First add UI tests against injected preferences/controller/link opener. Assert comparable `Enable analytics` and `No thanks` actions, modeless display, dismissal-as-disabled, no repeat for a current decision, old-version re-prompt, privacy-link failure fallback with a copyable URL, and enabling/disabling from the settings surface.

- [ ] Build `AnalyticsConsentDialog` using the approved plain-language copy and the three required actions. Do not preselect a checkbox, block the frame, infer consent from close/focus/usage, or reopen after a current decision. `Read privacy notice` opens the configured URL through `Desktop.browse`; failures show only a copyable URL and leave consent unchanged.

- [ ] Because the application has no general Settings screen, add an always-visible `Privacy` sidebar action in `SwingMainApp` that opens `PrivacySettingsDialog`. The dialog supplies the `Send optional usage analytics` toggle, concise collected/excluded lists, notice link, and `mailto:leetvin@gmail.com`. It uses the same controller as the consent dialog, so disable has immediate effect.

- [ ] In `SwingMainApp`, construct `AnalyticsPreferences`, `AnalyticsBuildConfig`, and `AnalyticsController` before feature panels, inject the controller facade into those panels/services, start lifecycle only after an effective enablement, and register normal close handling before `EXIT_ON_CLOSE`. After the visible main window is usable, schedule the modeless choice only when release configuration is complete and preferences require it. A normal window close records `session_ended` only if analytics is still enabled.

- [ ] Extend `Build-AppImage.ps1` with optional `AnalyticsEndpoint`, `AnalyticsPrivacyUrl`, and `AnalyticsNoticeVersion` parameters. Add all three JVM properties only when all validate together; otherwise add none. In the existing release workflow, pass non-secret GitHub environment variables for those three values only after the backend/privacy release gate has been approved. Do not add Cloudflare credentials or deployment commands to GitHub Actions.

- [ ] Run focused tests and existing startup-adjacent tests:

  ```powershell
  mvn -B -Dtest=AnalyticsConsentDialogTest,PrivacySettingsDialogTest,HelpPreferencesTest test
  ```

- [ ] Commit:

  ```powershell
  git add src/main/kotlin/org/litvin/SwingMainApp.kt src/main/kotlin/org/litvin/ui/privacy src/test/kotlin/org/litvin/ui/privacy distribution/windows/Build-AppImage.ps1 .github/workflows/windows-release.yml
  git commit -m "feat: add optional analytics privacy controls"
  ```

## Task 4: Wire all approved product events at semantic success boundaries

**Files:**

- Modify: `src/main/kotlin/org/litvin/ui/tabs/projects/presenter/DefaultProjectsPresenter.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/projects/SwingProjectsPanel.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/markup/SwingMarkupPanel.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/scoring/SwingScoringPanel.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/adjustments/SwingColorAdjustmentsPanel.kt`
- Modify: `src/main/kotlin/org/litvin/ui/tabs/crop/presenter/DefaultCropRotatePresenter.kt`
- Modify: `src/main/kotlin/org/litvin/RenderQueue.kt`
- Create: `src/main/kotlin/org/litvin/analytics/RenderAnalyticsReporter.kt`
- Create: `src/test/kotlin/org/litvin/analytics/FeatureAnalyticsWiringTest.kt`
- Modify: `src/test/kotlin/org/litvin/ui/tabs/projects/presenter/DefaultProjectsPresenterTest.kt`
- Modify: `src/test/kotlin/org/litvin/MarkupDispatcherTest.kt`
- Modify: `src/test/kotlin/org/litvin/export/ExportPlannerTest.kt`

- [ ] Start with a fake `Analytics` recorder test suite. It must prove each event is emitted once only after the corresponding successful semantic action, that disabling makes every path harmless, and that captured events contain only allowed enum/numeric properties.

- [ ] Inject `Analytics` into `DefaultProjectsPresenter` (through the existing `SwingProjectsPanel` default composition). After `repository.createProject` succeeds, record `project_created` and `source_video_opened(success)`; after an existing project successfully opens, record `project_opened` and `source_video_opened(success)`. For a missing source, unreadable media, or any caught load failure, record only the bounded result (`unavailable`, `invalid_media`, or `other`) at the operation boundary—never an exception message or source path.

- [ ] In `SwingMarkupPanel`, record `markup_point_added` only after `onEndAtPlayhead` has converted a pending start into a completed point, and record `markup_point_removed` only when `MarkupDispatcher.deletePoint` returns true. Do not report start markers, invalid end attempts, edits, favorites, playback, point IDs, labels, or timestamps.

- [ ] In `SwingScoringPanel`, record `score_point_recorded` only after a valid selected point receives an outcome and the score update succeeds. It must not include the winner, score, point ID, player name, or position.

- [ ] In color and crop/rotate UI paths, emit `adjustment_changed(color)` or `adjustment_changed(crop_rotate)` only when a user-originated input changes `AdjustmentsStore` state. Avoid event emission from store subscriptions, initial project loads, programmatic UI synchronization, preview recreation, and no-op changes.

- [ ] Add a narrow `RenderAnalyticsReporter` interface that receives sanitized render lifecycle DTOs, not `RenderJob`. Compose its analytics implementation in `SwingMainApp`. Have `RenderQueueManager` emit `export_started` when a job becomes `RUNNING`, and exactly one terminal event from the worker-thread state transition: completed carries only container/encoder family/duration; failed carries only failure category/duration; cancellation carries duration. Map known container and encoder labels to approved enums and map each internal failure branch to the closed categories without passing output paths, FFmpeg command lines, stderr, exception classes, or text. Queued jobs cancelled before running do not produce an export outcome because no export attempt started.

- [ ] Verify `RenderQueueManager` does not use its UI observer to drive analytics (observers can replay snapshots). Make duplicate terminal notifications impossible and calculate duration from the in-memory attempt monotonic start time.

- [ ] Run:

  ```powershell
  mvn -B -Dtest=FeatureAnalyticsWiringTest,DefaultProjectsPresenterTest,MarkupDispatcherTest,ExportPlannerTest test
  ```

- [ ] Commit:

  ```powershell
  git add src/main/kotlin/org/litvin/ui/tabs src/main/kotlin/org/litvin/RenderQueue.kt src/main/kotlin/org/litvin/analytics src/test/kotlin/org/litvin
  git commit -m "feat: record approved product analytics events"
  ```

## Task 5: Build the Cloudflare Worker and D1 validation/storage boundary

**Files:**

- Create: `analytics-worker/package.json`
- Create: `analytics-worker/package-lock.json`
- Create: `analytics-worker/tsconfig.json`
- Create: `analytics-worker/vitest.config.ts`
- Create: `analytics-worker/wrangler.toml.example`
- Create: `analytics-worker/src/index.ts`
- Create: `analytics-worker/src/contract.ts`
- Create: `analytics-worker/src/validation.ts`
- Create: `analytics-worker/src/retention.ts`
- Create: `analytics-worker/src/logging.ts`
- Create: `analytics-worker/migrations/0001_initial.sql`
- Create: `analytics-worker/test/ingestion.test.ts`
- Create: `analytics-worker/test/retention.test.ts`
- Create: `analytics-worker/README.md`
- Modify: `.gitignore`

- [ ] Create TypeScript tests using a fake D1 binding before handler implementation. Load the same root `analytics-contract/v1` fixtures used by Kotlin. Assert all status semantics, exact bounded response bodies, partial acceptance, duplicate primary-key conflict ignore, server-owned receipt timestamps, no IP/header/database values in bindings or logs, kill switch behavior, and database-unavailable `503`.

- [ ] Implement `POST /v1/events/batch` only. Reject unsupported routes/methods with `404`/`405`, non-JSON with `415`, bodies larger than 64 KiB with `413`, malformed/invalid envelopes with `400`, and a fully invalid batch with `422`. Return `202` and only `{ accepted, rejected, reasons }` for a processed batch. Do not set CORS headers or accept cookies/credentials.

- [ ] Implement bounded JSON parsing/validation in `validation.ts`, independently of desktop types: depth and collection checks; exact envelope/event key sets; UUID v4; allowed app version/OS; event count and sequence/elapsed limits; exact event property sets; 2 KiB serialized property limit; closed enum values. Allow partial valid batches, aggregating only bounded reason-code/count pairs. Never include submitted values in a response.

- [ ] Add Worker Rate Limiting binding use keyed only from Cloudflare's trusted connecting-IP header and keep that header out of all other code paths. The first operation in the handler checks `ANALYTICS_INGESTION_ENABLED`; `false` returns `410` before reading/parsing/writing a request.

- [ ] Create a single D1 `batch()` transaction that inserts validated rows with `INSERT OR IGNORE`. Schema columns are `session_id`, `sequence_number`, `received_at` UTC milliseconds, `schema_version`, `notice_version`, `event_name`, `elapsed_ms`, `app_version`, `os_family`, and validated JSON-text `properties`; use `(session_id, sequence_number)` primary key and indexed receipt/name columns. Add SQLite checks for numeric bounds, JSON validity/size, and current known event names as a second boundary.

- [ ] Create `analytics_retention_status` with one internal status row and a scheduled Worker path that deletes `received_at` values older than 90 days, reads the oldest remaining receipt time, and records only run timestamp, deleted-row count, oldest remaining time, and consecutive failure count. The cron handler must authenticate by Worker scheduling context, never be exposed as a public route, and emit safe count/status logs only.

- [ ] Make `wrangler.toml.example` bind D1, a rate limiter, and a daily cron, with `ANALYTICS_INGESTION_ENABLED=false` by default. Add ignored `analytics-worker/wrangler.toml`, `.dev.vars`, `.wrangler/`, and `node_modules/`; no real account/database/namespace IDs enter Git history. README must document copying the example to the ignored file.

- [ ] Run Worker verification:

  ```powershell
  Push-Location analytics-worker
  npm ci
  npm run typecheck
  npm test
  Pop-Location
  ```

- [ ] Commit:

  ```powershell
  git add analytics-worker analytics-contract .gitignore
  git commit -m "feat: add D1 analytics ingestion worker"
  ```

## Task 6: Add query files, privacy notice, and operational documentation

**Files:**

- Create: `docs/analytics/queries/01-session-summary.sql`
- Create: `docs/analytics/queries/02-daily-event-counts.sql`
- Create: `docs/analytics/queries/03-export-outcomes.sql`
- Create: `docs/analytics/queries/04-bounded-categories.sql`
- Create: `docs/analytics/queries/README.md`
- Create: `docs/analytics/runbooks/manual-deploy.md`
- Create: `docs/analytics/runbooks/retention-and-recovery.md`
- Create: `docs/analytics/runbooks/weekly-operations.md`
- Create: `docs/analytics/release-checklist.md`
- Create: `docs/privacy/analytics.md`
- Create: `docs/_config.yml`
- Create: `analytics-worker/test/query-fixtures.sql`
- Create: `analytics-worker/test/query-files.test.ts`

- [ ] Seed only synthetic rows in `query-fixtures.sql`, then write query tests that execute each committed SQL file against an isolated SQLite/D1-compatible test database. Cover complete/incomplete sessions, max elapsed duration, UTC day grouping, duplicates, export denominators, bounded category counts, and exclusion of `app_version = 'synthetic-smoke'`.

- [ ] Implement the four versioned SQL files. Every query filters synthetic smoke rows, derives time/day from server `received_at`, and says that opted-out users are absent and incomplete sessions may be undercounted. Use SQLite JSON functions only; do not write PostgreSQL JSONB syntax or create a dashboard.

- [ ] Add the query README with the manual command shape (`npx wrangler d1 execute <database-name> --remote --file <query-file>`), an explicit reminder not to export production raw events to a development machine, and the product question each query answers.

- [ ] Create `docs/privacy/analytics.md` with Jekyll front matter and stable `/privacy/analytics/` permalink. State notice version 1 and effective date; optional explicit consent; exact collected and excluded data; Cloudflare Worker/D1 and GitHub Pages roles; EU D1 storage/no replicas; transient non-EU edge routing qualification; seven-day sampled Worker log retention; 90-day event deletion plus up-to-30-day Time Travel recovery; no sales/ads; withdrawal behavior; `leetvin@gmail.com`; relevant Cloudflare/GitHub privacy links; and a change history. Keep the page tracker-, script-, form-, and external-font-free.

- [ ] Write manual runbooks that require MFA, `wrangler d1 create --jurisdiction=eu`, no read replicas, a low sampled seven-day Workers Log configuration, CPU/spend review, a tested Rate Limiting binding, cron retention, and an exercised `410` switch. The recovery runbook must require rerunning retention before reopening ingestion or queries after a D1 Time Travel restore. The weekly checklist covers status-table age, logs, Worker/D1 usage, budget, and log-value audit; it does not promise automated alerts.

- [ ] Write the release checklist so analytics stays disabled until the privacy URL/version, actual provider configuration, D1 jurisdiction, no-replica state, synthetic smoke, rate-limit, `410`, retention/recovery, logs, and packaging properties are verified. Include the agreed rollout: disabled backend, local synthetic smoke, small pre-release consent, then general release.

- [ ] Run:

  ```powershell
  Push-Location analytics-worker
  npm test -- --run query-files.test.ts
  Pop-Location
  ```

- [ ] Commit:

  ```powershell
  git add docs/analytics docs/privacy docs/_config.yml analytics-worker/test
  git commit -m "docs: add analytics queries and operating runbooks"
  ```

## Task 7: Perform manual Cloudflare provisioning and a synthetic smoke exercise

**Files:**

- Modify: `analytics-worker/wrangler.toml.example` only if the reviewed account configuration requires a documented non-secret adjustment
- Modify: `docs/analytics/runbooks/manual-deploy.md` with the completed deployment date and non-sensitive resource names; never commit resource IDs or tokens

- [ ] This task requires the operator to authenticate interactively to the intended Cloudflare account with MFA. Confirm Workers Paid is enabled, then create exactly one D1 database with `--jurisdiction=eu`, confirm its dashboard reports EU jurisdiction, and leave read replication disabled.

- [ ] Copy `wrangler.toml.example` to ignored `wrangler.toml`, fill local D1/rate-limit binding IDs, leave the ingestion flag `false`, and verify the local file is ignored with `git status --ignored`. Do not place an API token, database credential, or desktop secret in the client, workflow, or repository.

- [ ] Apply the reviewed migration manually, deploy the Worker with Wrangler, and confirm public `POST /v1/events/batch` returns `410` while disabled. Configure the Worker CPU limit, rate limiter, daily cron, low-sampled seven-day Workers Logs, and account spend review.

- [ ] With the synthetic fixture only, temporarily enable ingestion, prove a valid event gives `202`, malformed/oversized/rate-limited traffic is rejected, D1 contains no IP/header fields, and logs contain only the approved bounded metrics. Set the flag back to `false` after the exercise.

- [ ] Exercise retention with synthetic old data, inspect the status table, perform a Time Travel restore only if safe in the synthetic database, immediately rerun retention, and document the verification result without copying raw events into the repository.

- [ ] Publish and open the GitHub Pages privacy URL. Verify HTTPS, notice version 1, contact link, no external trackers, and the exact Cloudflare qualifications. Only then set the three non-secret release build variables and enable a pre-release desktop build.

- [ ] Commit only non-sensitive runbook completion evidence, if any:

  ```powershell
  git add docs/analytics/runbooks/manual-deploy.md
  git commit -m "docs: record analytics MVP deployment verification"
  ```

## Task 8: Run the release-quality verification suite and review the diff

**Files:**

- Modify only files needed to correct failures discovered below.

- [ ] Run all desktop tests and require no test regression:

  ```powershell
  mvn -B test
  ```

- [ ] Run all Worker checks from a clean dependency install:

  ```powershell
  Push-Location analytics-worker
  npm ci
  npm run typecheck
  npm test
  Pop-Location
  ```

- [ ] Inspect the final diff specifically for forbidden persistence/logging/network fields and untracked sensitive configuration:

  ```powershell
  rg -n "session_id|sourceVideo|outputPath|failureReason|stderr|absolutePath|headers|cf-connecting-ip" src/main/kotlin/org/litvin/analytics analytics-worker
  git status --short
  git diff --check
  ```

  Require that any matching production analytics code is either a validator rejecting the value or a carefully documented, non-logging transport boundary. Require no tracked `wrangler.toml`, `.dev.vars`, tokens, database IDs, or production event exports.

- [ ] Manually verify desktop behavior with no configuration (no consent UI/network), with a fake local HTTPS endpoint (enable/record/disable), and after a `410` response. Verify project editing, saving, preview, and export are unchanged even when the endpoint is unreachable.

- [ ] Review the final implementation against both approved documents: all 14 event mappings, consent mechanics, client/server allowlists, retention/recovery, D1 explicit exceptions, manual Wrangler deployment, query-only reporting, and privacy-page disclosures. Resolve failures before claiming readiness.

- [ ] Do not make a mechanical final verification commit. Keep every fix in the task-specific commit where it was introduced, then confirm the only remaining uncommitted files are the user's pre-existing scoring changes and audit document.
