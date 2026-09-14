import { validateBatch } from './validation';
import { runRetention } from './retention';

export interface Env { ANALYTICS_INGESTION_ENABLED: string; ANALYTICS_DB: D1Database }

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (env.ANALYTICS_INGESTION_ENABLED !== 'true') return Response.json({ accepted: 0, rejected: 0, reasons: {} }, { status: 410 });
    if (request.method !== 'POST') return new Response(null, { status: request.method === 'GET' ? 405 : 404 });
    if (new URL(request.url).pathname !== '/v1/events/batch') return new Response(null, { status: 404 });
    if (!request.headers.get('content-type')?.toLowerCase().startsWith('application/json')) return new Response(null, { status: 415 });
    const body = await request.text();
    if (body.length > 65_536) return new Response(null, { status: 413 });
    const batch = (() => { try { return validateBatch(JSON.parse(body)); } catch { return null; } })();
    if (!batch) return Response.json({ accepted: 0, rejected: 1, reasons: { invalid: 1 } }, { status: 422 });
    try {
      const now = Date.now();
      await env.ANALYTICS_DB.batch(batch.events.map(event => env.ANALYTICS_DB.prepare('INSERT OR IGNORE INTO analytics_event (session_id,sequence_number,received_at,schema_version,notice_version,event_name,elapsed_ms,app_version,os_family,properties) VALUES (?,?,?,?,?,?,?,?,?,?)').bind(batch.session_id,event.sequence_number,now,1,1,event.name,event.elapsed_ms,batch.app_version,batch.os_family,JSON.stringify(event.properties))));
      return Response.json({ accepted: batch.events.length, rejected: 0, reasons: {} }, { status: 202 });
    } catch { return Response.json({ accepted: 0, rejected: 0, reasons: { unavailable: 1 } }, { status: 503 }); }
  },
  async scheduled(_controller: ScheduledController, env: Env): Promise<void> {
    await runRetention(env.ANALYTICS_DB);
  }
} satisfies ExportedHandler<Env>;
