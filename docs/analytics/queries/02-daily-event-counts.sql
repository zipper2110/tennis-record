-- UTC day uses server receipt time. Opted-out users are absent; incomplete sessions may be undercounted.
SELECT date(received_at / 1000, 'unixepoch') AS received_utc_day, event_name, app_version, os_family,
       COUNT(*) AS event_count
FROM analytics_event
WHERE app_version <> 'synthetic-smoke'
GROUP BY received_utc_day, event_name, app_version, os_family
ORDER BY received_utc_day, event_name;
