const eventNames = new Set(['session_started','session_heartbeat','session_ended','project_created','project_opened','source_video_opened','markup_point_added','markup_point_removed','score_point_recorded','adjustment_changed','export_started','export_completed','export_failed','export_cancelled']);
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const appVersion = /^[0-9A-Za-z.+-]{1,32}$/;
const noProperties = new Set(['session_started','session_heartbeat','session_ended','project_created','project_opened','markup_point_added','markup_point_removed','score_point_recorded']);

export type ValidatedEvent = { sequence_number: number; name: string; elapsed_ms: number; properties: Record<string, unknown> };
export type ValidatedBatch = { session_id: string; app_version: string; os_family: string; events: ValidatedEvent[] };

export function validateBatch(value: unknown): ValidatedBatch | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const batch = value as Record<string, unknown>;
  if (!exact(batch, ['schema_version','notice_version','session_id','app_version','os_family','events']) || batch.schema_version !== 1 || batch.notice_version !== 1) return null;
  if (typeof batch.session_id !== 'string' || !uuid.test(batch.session_id) || typeof batch.app_version !== 'string' || !appVersion.test(batch.app_version)) return null;
  if (!['windows','macos','linux','other'].includes(batch.os_family as string) || !Array.isArray(batch.events) || batch.events.length < 1 || batch.events.length > 20) return null;
  const seen = new Set<number>();
  const events: ValidatedEvent[] = [];
  for (const raw of batch.events) {
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null;
    const event = raw as Record<string, unknown>;
    if (!exact(event, ['sequence_number','name','elapsed_ms','properties']) || typeof event.sequence_number !== 'number' || !Number.isInteger(event.sequence_number) || event.sequence_number < 0 || event.sequence_number > 1_000_000 || seen.has(event.sequence_number) || typeof event.name !== 'string' || !eventNames.has(event.name) || typeof event.elapsed_ms !== 'number' || !Number.isInteger(event.elapsed_ms) || event.elapsed_ms < 0 || event.elapsed_ms > 604_800_000 || !event.properties || typeof event.properties !== 'object' || Array.isArray(event.properties)) return null;
    if (!validProperties(event.name, event.properties as Record<string, unknown>)) return null;
    seen.add(event.sequence_number); events.push(event as ValidatedEvent);
  }
  return { session_id: batch.session_id, app_version: batch.app_version, os_family: batch.os_family as string, events };
}
function exact(value: Record<string, unknown>, keys: string[]) { const actual = Object.keys(value); return actual.length === keys.length && keys.every(key => Object.hasOwn(value, key)); }
function validProperties(name: string, p: Record<string, unknown>) {
  if (JSON.stringify(p).length > 2048) return false;
  if (noProperties.has(name)) return exact(p, []);
  const is = (key: string, values: string[]) => typeof p[key] === 'string' && values.includes(p[key] as string);
  const duration = () => typeof p.duration_ms === 'number' && Number.isInteger(p.duration_ms) && p.duration_ms >= 0 && p.duration_ms <= 604_800_000;
  if (name === 'source_video_opened') return exact(p,['result']) && is('result',['success','unsupported','invalid_media','unavailable','other']);
  if (name === 'adjustment_changed') return exact(p,['adjustment_category']) && is('adjustment_category',['crop_rotate','color','scale','other']);
  if (name === 'export_started') return exact(p,['container','encoder_family']) && is('container',['mp4','mov','mkv','other']) && is('encoder_family',['software','nvidia','intel','amd','apple','other']);
  if (name === 'export_completed') return exact(p,['container','encoder_family','duration_ms']) && is('container',['mp4','mov','mkv','other']) && is('encoder_family',['software','nvidia','intel','amd','apple','other']) && duration();
  if (name === 'export_failed') return exact(p,['failure_category','duration_ms']) && is('failure_category',['validation','dependency_unavailable','encoder_unavailable','process_start','processing','output_write','other']) && duration();
  return name === 'export_cancelled' && exact(p,['duration_ms']) && duration();
}
