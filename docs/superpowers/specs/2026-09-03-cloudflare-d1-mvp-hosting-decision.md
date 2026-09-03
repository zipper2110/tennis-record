# Cloudflare D1 MVP Hosting Decision

**Status:** Approved for the free MVP

**Date:** 2026-09-03

**Supersedes:** The deferred provider, database, scheduler, and infrastructure-as-code decisions in [the privacy-preserving analytics design](2026-08-29-privacy-preserving-product-analytics-design.md). It does not broaden the client event contract or consent model.

## Decision

Host the MVP analytics backend on Cloudflare Workers Paid with a Cloudflare D1 database. Keep the privacy notice on GitHub Pages.

The expected hosting cost is **$5 USD per month plus applicable taxes**. No custom domain, external monitoring vendor, or separate staging deployment is part of the MVP.

## Selected services

| Need | Service and configuration |
|---|---|
| Public ingestion endpoint | One Cloudflare Worker serving `POST /v1/events/batch` over HTTPS |
| Request controls | Worker request-size, content-type, JSON-depth, schema, and typed-event validation; Worker Rate Limiting binding keyed by Cloudflare's trusted client-IP header |
| Ingestion compute | The same Worker validates and inserts a bounded batch through its D1 binding |
| Database | One D1 database created with the `eu` jurisdiction and read replication disabled |
| Retention | A Cloudflare Cron Trigger invokes the Worker daily to delete raw events older than 90 days |
| Emergency shutdown | A Worker configuration flag makes the endpoint return `410` before parsing or writing data |
| Backups and restoration | D1 Time Travel; Workers Paid retains restore points for 30 days |
| Operations | Cloudflare account MFA, scoped API tokens, Workers metrics, D1 usage dashboard, and sampled Workers Logs |
| Privacy notice | GitHub Pages at the stable `/privacy/analytics/` URL |

The worker is the only component with a D1 binding. The desktop application has no database credential, API key, or other ingestion secret.

## Data location and logs

The D1 database is created with the EU jurisdiction. Its stored data and any disabled read replicas remain within that jurisdiction. Cloudflare Workers can receive requests at edge locations outside the EU before accessing the EU-restricted database; the privacy notice must state that this transient routing occurs and must not claim that all processing is EU-only.

Enable Workers Logs with a low sample rate and a seven-day retention limit. Application logs may contain only response status class, accepted/rejected counts, bounded rejection codes, coarse latency, retention deletion count, and generated correlation IDs. They must never contain request bodies, session IDs, event properties, headers, IP addresses, or query strings.

## MVP data model and reporting

D1 uses SQLite rather than PostgreSQL. Store `session_id` as validated text, `received_at` as a UTC integer timestamp, and event properties as validated JSON text. Preserve the composite `(session_id, sequence_number)` primary key for idempotency.

The MVP supports the product questions that need only simple SQL:

- Sessions, lifecycle completeness, and measured duration
- Daily event counts by event name, app version, and OS family
- Export starts, completion/failure/cancellation counts, and average duration
- Counts by the approved bounded result and failure categories

Percentile export-duration reporting, PostgreSQL-specific JSONB queries, database roles, private VPC connectivity, and externally delivered operational alerts are deferred until a paid production architecture is justified.

The daily retention job writes its completion time, deleted-row count, and oldest remaining receipt time to a small internal status table. The operator reviews this status and Cloudflare usage at least weekly during the MVP. A failed job does not affect the desktop application.

## Security boundaries

The Worker accepts no credentials from the desktop client. It processes source IP information only transiently for Cloudflare rate limiting and does not write it to D1 or application logs. The Worker rejects unsupported methods, content types, oversized bodies, malformed JSON, unrecognized fields, unrecognized event names, and out-of-range values before a database write.

D1 has no public database socket exposed to the desktop application. Operator access is through the Cloudflare dashboard or scoped API tokens protected by MFA. Production event data is not exported to development machines; query fixtures remain synthetic.

## Retention and recovery

The scheduled deletion removes rows older than 90 days. A deleted row can remain recoverable in D1 Time Travel for up to 30 additional days. If a restoration is required, the operator must immediately run the retention job before enabling ingestion or queries.

The operator may export the D1 database before a provider migration or final deletion. Deleting the D1 database removes the hosted copy; GitHub Pages content is managed separately.

## Explicit MVP exceptions

This decision intentionally relaxes the following production-oriented requirements in the earlier design:

- Managed PostgreSQL compatibility is replaced with managed SQLite/D1.
- Private database networking and separate database runtime roles are replaced with the Worker-to-D1 binding boundary.
- Automated private alerts and a separately hosted staging environment are deferred; the operator performs weekly manual checks.
- The endpoint's transient edge processing is not restricted to the EU, although D1 storage is EU-restricted.

These exceptions are acceptable only while analytics is optional, event volume is small, and the product is a free MVP. Any paid release, material growth, or expansion of analytics scope requires a new provider review before enabling the changed build.

## Release checks

Before enabling the consent UI in an MVP build:

1. Confirm the Cloudflare account uses MFA and the Worker/D1 resources are in the intended account.
2. Verify the D1 database reports the `eu` jurisdiction and no read replicas.
3. Verify rate limiting, the `410` switch, 90-day deletion, and restoration-then-retention using synthetic data.
4. Verify sampled logs contain no request values or identifiers.
5. Publish the privacy notice with Cloudflare, GitHub Pages, the EU D1 storage restriction, edge-routing qualification, 90-day raw-event retention, 30-day recovery window, and seven-day Worker log retention.
6. Configure a Workers CPU limit and review the Cloudflare usage dashboard before each MVP release.
