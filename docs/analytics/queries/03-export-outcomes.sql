-- Opted-out users are absent; incomplete sessions may be undercounted.
SELECT date(received_at / 1000, 'unixepoch') AS received_utc_day, app_version,
       SUM(event_name = 'export_started') AS started,
       SUM(event_name = 'export_completed') AS completed,
       SUM(event_name = 'export_failed') AS failed,
       SUM(event_name = 'export_cancelled') AS cancelled,
       AVG(CASE WHEN event_name IN ('export_completed', 'export_failed', 'export_cancelled')
           THEN json_extract(properties, '$.duration_ms') END) AS average_duration_ms
FROM analytics_event
WHERE app_version <> 'synthetic-smoke'
GROUP BY received_utc_day, app_version
ORDER BY received_utc_day;
