CREATE TABLE IF NOT EXISTS analytics_event (
  session_id TEXT NOT NULL, sequence_number INTEGER NOT NULL, received_at INTEGER NOT NULL,
  schema_version INTEGER NOT NULL, notice_version INTEGER NOT NULL, event_name TEXT NOT NULL,
  elapsed_ms INTEGER NOT NULL, app_version TEXT NOT NULL, os_family TEXT NOT NULL, properties TEXT NOT NULL,
  PRIMARY KEY (session_id, sequence_number),
  CHECK (sequence_number BETWEEN 0 AND 1000000), CHECK (elapsed_ms BETWEEN 0 AND 604800000),
  CHECK (length(properties) <= 2048), CHECK (json_valid(properties))
);
CREATE INDEX IF NOT EXISTS analytics_event_received_at_idx ON analytics_event(received_at);
CREATE INDEX IF NOT EXISTS analytics_event_name_received_at_idx ON analytics_event(event_name, received_at);
CREATE TABLE IF NOT EXISTS analytics_retention_status (
  id INTEGER PRIMARY KEY CHECK (id = 1), ran_at INTEGER NOT NULL, deleted_count INTEGER NOT NULL,
  oldest_received_at INTEGER, consecutive_failures INTEGER NOT NULL DEFAULT 0
);
