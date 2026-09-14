-- Opted-out users are absent; incomplete sessions may be undercounted.
WITH lifecycle AS (
  SELECT session_id, MAX(elapsed_ms) AS measured_duration_ms,
         MAX(CASE WHEN event_name = 'session_ended' THEN 1 ELSE 0 END) AS completed
  FROM analytics_event
  WHERE app_version <> 'synthetic-smoke'
  GROUP BY session_id
)
SELECT completed, COUNT(*) AS sessions, AVG(measured_duration_ms) AS average_measured_duration_ms
FROM lifecycle GROUP BY completed;
