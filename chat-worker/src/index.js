// Framy Global chat: a tiny public wall. Validates, rate-limits, stores in Neon. Never stores IPs.
import { neon } from '@neondatabase/serverless';

const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
const empty = (status) => new Response(null, { status });

function isAdmin(given, expected) {
  if (!given || !expected || given.length !== expected.length) return false;
  let diff = 0; // constant-time compare
  for (let i = 0; i < given.length; i++) diff |= given.charCodeAt(i) ^ expected.charCodeAt(i);
  return diff === 0;
}

export async function handle(request, env, sql) {
  const path = new URL(request.url).pathname.replace(/\/+$/, '');
  const admin = isAdmin(request.headers.get('x-admin-key'), env.ADMIN_KEY);

  if (path === '/comments' && request.method === 'GET') {
    return json(await sql`SELECT id, nick, tag, body, is_dev, created_at FROM comments ORDER BY id DESC LIMIT 100`);
  }

  if (path === '/comments' && request.method === 'POST') {
    if (!admin) {
      const { success } = await env.POST_LIMIT.limit({ key: request.headers.get('cf-connecting-ip') || 'unknown' });
      if (!success) return json({ error: 'slow down' }, 429);
    }
    let input;
    try { input = await request.json(); } catch { return json({ error: 'invalid json' }, 400); }
    const body = typeof input?.body === 'string' ? input.body.trim() : '';
    const nick = (typeof input?.nick === 'string' ? input.nick.trim() : '').slice(0, 30) || 'Anonymous';
    const tag = typeof input?.tag === 'string' ? input.tag : '';
    if (body.length < 1 || body.length > 1000 || !/^[0-9a-f]{4}$/.test(tag)) return json({ error: 'invalid comment' }, 400);
    const [row] = await sql`INSERT INTO comments (nick, tag, body, is_dev) VALUES (${nick}, ${tag}, ${body}, ${admin}) RETURNING id`;
    return json({ id: row.id }, 201);
  }

  const del = path.match(/^\/comments\/(\d+)$/);
  if (del && request.method === 'DELETE') {
    if (!admin) return empty(401);
    await sql`DELETE FROM comments WHERE id = ${Number(del[1])}`;
    return empty(204);
  }

  return empty(404);
}

export async function cleanup(sql) {
  await sql`DELETE FROM comments WHERE created_at < now() - interval '60 days' OR id NOT IN (SELECT id FROM comments ORDER BY id DESC LIMIT 1000)`;
}

export default {
  fetch: (request, env) => handle(request, env, neon(env.DATABASE_URL)).catch(() => json({ error: 'unavailable' }, 503)),
  scheduled: (event, env, ctx) => ctx.waitUntil(cleanup(neon(env.DATABASE_URL))),
};
