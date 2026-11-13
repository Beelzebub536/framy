import { test } from 'node:test';
import assert from 'node:assert/strict';
import { handle, cleanup } from '../src/index.js';

// Fake Neon tagged-template sql: records each query's text and values, returns `rows`.
function fakeSql(rows = []) {
  const calls = [];
  const sql = (strings, ...values) => { calls.push({ text: strings.join('?'), values }); return Promise.resolve(rows); };
  sql.calls = calls;
  return sql;
}
const env = (allow = true) => ({ ADMIN_KEY: 'k3y', POST_LIMIT: { limit: async () => ({ success: allow }) } });
const post = (body, headers = {}) => new Request('https://w/comments', { method: 'POST', body: JSON.stringify(body), headers: { 'content-type': 'application/json', ...headers } });

test('listReturnsNewest100', async () => {
  const sql = fakeSql([{ id: 1 }]);
  const res = await handle(new Request('https://w/comments'), env(), sql);
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), [{ id: 1 }]);
  assert.match(sql.calls[0].text, /ORDER BY id DESC LIMIT 100/);
});

test('emptyBodyRejected', async () => {
  assert.equal((await handle(post({ nick: 'a', tag: 'abcd', body: '   ' }), env(), fakeSql())).status, 400);
});

test('oversizeBodyRejected', async () => {
  assert.equal((await handle(post({ tag: 'abcd', body: 'x'.repeat(1001) }), env(), fakeSql())).status, 400);
});

test('badTagRejected', async () => {
  assert.equal((await handle(post({ tag: 'ZZZZ', body: 'hi' }), env(), fakeSql())).status, 400);
});

test('notJsonRejected', async () => {
  const req = new Request('https://w/comments', { method: 'POST', body: '{oops' });
  assert.equal((await handle(req, env(), fakeSql())).status, 400);
});

test('postOk', async () => {
  const sql = fakeSql([{ id: 9 }]);
  const res = await handle(post({ nick: '  Rafi  ', tag: 'a3f2', body: '  Mail is late  ' }), env(), sql);
  assert.equal(res.status, 201);
  assert.deepEqual(await res.json(), { id: 9 });
  assert.deepEqual(sql.calls[0].values, ['Rafi', 'a3f2', 'Mail is late', false]);
});

test('blankNickBecomesAnonymousAndLongNickCut', async () => {
  const sql = fakeSql([{ id: 1 }]);
  await handle(post({ nick: ' ', tag: 'a3f2', body: 'hi' }), env(), sql);
  await handle(post({ nick: 'n'.repeat(50), tag: 'a3f2', body: 'hi' }), env(), sql);
  assert.equal(sql.calls[0].values[0], 'Anonymous');
  assert.equal(sql.calls[1].values[0].length, 30);
});

test('adminPostIsDev', async () => {
  const sql = fakeSql([{ id: 2 }]);
  await handle(post({ tag: 'a3f2', body: 'Fixed in v0.2' }, { 'x-admin-key': 'k3y' }), env(), sql);
  assert.equal(sql.calls[0].values[3], true);
});

test('wrongAdminKeyIsNotDev', async () => {
  const sql = fakeSql([{ id: 2 }]);
  await handle(post({ tag: 'a3f2', body: 'hi' }, { 'x-admin-key': 'nope' }), env(), sql);
  assert.equal(sql.calls[0].values[3], false);
});

test('deleteNeedsAdminKey', async () => {
  const del = (h) => new Request('https://w/comments/5', { method: 'DELETE', headers: h });
  assert.equal((await handle(del({}), env(), fakeSql())).status, 401);
  const sql = fakeSql();
  assert.equal((await handle(del({ 'x-admin-key': 'k3y' }), env(), sql)).status, 204);
  assert.deepEqual(sql.calls[0].values, [5]);
});

test('rateLimited', async () => {
  const sql = fakeSql();
  assert.equal((await handle(post({ tag: 'a3f2', body: 'hi' }), env(false), sql)).status, 429);
  assert.equal(sql.calls.length, 0);
});

test('unknownRouteIs404', async () => {
  assert.equal((await handle(new Request('https://w/admin'), env(), fakeSql())).status, 404);
});

test('cleanupQuery', async () => {
  const sql = fakeSql();
  await cleanup(sql);
  assert.match(sql.calls[0].text, /DELETE FROM comments WHERE created_at < now\(\) - interval '60 days' OR id NOT IN \(SELECT id FROM comments ORDER BY id DESC LIMIT 1000\)/);
});
