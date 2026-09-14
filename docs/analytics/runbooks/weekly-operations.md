# Weekly analytics operations

- Check retention status age, deleted-row count, and oldest remaining receipt time.
- Check sampled Worker logs contain only status/count diagnostics.
- Review Workers and D1 usage, CPU limits, and spend.
- Audit application logs and bindings for unexpected values, headers, IP addresses, or raw event content.
- Confirm the rate limiter, daily cron, and `410` ingestion switch remain configured.

This MVP has manual weekly review; it does not promise automated alerts.
