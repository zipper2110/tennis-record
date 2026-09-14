export async function runRetention(db: D1Database, now = Date.now()): Promise<void> {
  const cutoff = now - 90 * 24 * 60 * 60 * 1000;
  const deleted = await db.prepare('DELETE FROM analytics_event WHERE received_at < ?').bind(cutoff).run();
  const oldest = await db.prepare('SELECT MIN(received_at) AS oldest FROM analytics_event').first<{ oldest: number | null }>();
  await db.prepare('INSERT INTO analytics_retention_status (id,ran_at,deleted_count,oldest_received_at,consecutive_failures) VALUES (1,?,?,?,0) ON CONFLICT(id) DO UPDATE SET ran_at=excluded.ran_at, deleted_count=excluded.deleted_count, oldest_received_at=excluded.oldest_received_at, consecutive_failures=0').bind(now, deleted.meta.changes, oldest?.oldest ?? null).run();
}
