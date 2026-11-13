# Framy Global chat Worker

A tiny public chat wall for the Framy app: a Cloudflare Worker in front of a Neon
Postgres table. Both are on free plans and need no card. It stores no IP addresses.

## Setup (once)

1. Create a free project at neon.tech, open its SQL editor, and run `schema.sql`.
2. Run `npx wrangler login`.
3. Run `npx wrangler secret put DATABASE_URL` and paste Neon's connection string.
   Then run `npx wrangler secret put ADMIN_KEY` and type a long random string. That's
   your developer key for the app.
4. Run `npx wrangler deploy` and copy the `https://framy-chat.<you>.workers.dev` URL.
5. Add `framyChatUrl=<that url>` to `~/.gradle/gradle.properties`, or the `CHAT_URL`
   repo variable for CI, then rebuild the app.

After that, every tagged release redeploys this Worker automatically (see `docs/ci.md`).

## API

| Method | Path | Notes |
|---|---|---|
| GET | `/comments` | newest 100 |
| POST | `/comments` | `{nick, tag, body}`: body 1–1000 chars, tag 4 hex chars. 3 per minute per IP. |
| DELETE | `/comments/:id` | needs header `X-Admin-Key` |

A POST with a valid `X-Admin-Key` is marked `is_dev` and shows a Developer badge in
the app.

## Test

```bash
npm ci && npm test
```
