# Retention and recovery

The daily scheduled Worker deletes raw events older than 90 days and records only status counts and oldest remaining receipt time. Review the status table after any failed cron run.

If a D1 Time Travel restore is necessary, immediately rerun retention before reopening ingestion or running queries. Confirm the emergency `410` switch is active until retention completes. Recovery tests use synthetic data only.
