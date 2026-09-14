# Manual analytics Worker deployment

Manual deployment requires an operator authenticated to the intended Cloudflare account with MFA. Do not add Cloudflare credentials, D1 IDs, rate-limit namespace IDs, or `wrangler.toml` to this repository.

1. Enable Workers Paid and create one database with `wrangler d1 create <name> --jurisdiction=eu`.
2. Confirm the dashboard reports EU jurisdiction and read replicas are disabled.
3. Copy `analytics-worker/wrangler.toml.example` to ignored `analytics-worker/wrangler.toml`; fill only local binding IDs and keep ingestion disabled.
4. Apply the reviewed migration, configure a rate-limit binding, daily cron, low sampled seven-day Workers Logs, CPU limit, and spend review.
5. Deploy with Wrangler. While disabled, `POST /v1/events/batch` must return `410` before parsing or writing data.
6. Use synthetic data only for a temporary smoke test. Restore the disabled switch afterwards.

Never export production raw events to a development machine.
