-- Opted-out users are absent; incomplete sessions may be undercounted.
SELECT event_name,
       COALESCE(json_extract(properties, '$.result'), json_extract(properties, '$.adjustment_category'),
                json_extract(properties, '$.failure_category'), json_extract(properties, '$.container'), 'none') AS category,
       COUNT(*) AS event_count
FROM analytics_event
WHERE app_version <> 'synthetic-smoke'
GROUP BY event_name, category
ORDER BY event_name, category;
