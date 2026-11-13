# CI/CD

There are two workflows in `.github/workflows/`.

| Workflow | Runs on | Does |
|---|---|---|
| `ci.yml` | every push to `main`, every pull request | unit tests, Android lint, debug build, Global chat Worker tests, then boots an API 34 emulator and runs the smoke test that opens every screen |
| `release.yml` | pushing a tag like `v0.1.0` | runs all of `ci.yml` first, then builds the signed APK (fails if over 5 MB), attaches it with its SHA-256 to a GitHub Release, records a build-provenance attestation, and deploys the Global chat Worker |

`dependabot.yml` opens monthly PRs to update GitHub Actions, Gradle and npm
dependencies. CI tests each PR before you merge it.

## One-time setup (owner)

### 1. Create the signing key

Run this once, then back the file up somewhere safe. If you lose it, nobody's
installed app can ever be updated.

```bash
keytool -genkeypair -v -keystore ~/framy-release.jks -alias framy -keyalg RSA -keysize 2048 -validity 10000
```

### 2. Add the repository secrets

Go to GitHub → repo → Settings → Secrets and variables → Actions.

| Name | Kind | Value |
|---|---|---|
| `FRAMY_KEYSTORE_BASE64` | secret | output of `base64 -w0 ~/framy-release.jks` |
| `FRAMY_STORE_PASSWORD` | secret | keystore password |
| `FRAMY_KEY_ALIAS` | secret | `framy` |
| `FRAMY_KEY_PASSWORD` | secret | key password |
| `CLOUDFLARE_API_TOKEN` | secret | Cloudflare dashboard → My Profile → API Tokens → "Edit Cloudflare Workers" template |
| `CLOUDFLARE_ACCOUNT_ID` | secret | Cloudflare dashboard → Workers & Pages → Account ID |
| `CHAT_URL` | **variable** (not secret) | the Worker URL, e.g. `https://framy-chat.<you>.workers.dev` |

The Worker's own secrets (`DATABASE_URL`, `ADMIN_KEY`) are set once with
`npx wrangler secret put` (`chat-worker/README.md`). They never go in GitHub.

### 3. Protect `main` (recommended for a public repo)

Go to Settings → Branches and add a rule for `main`: require the CI checks to pass
before merging.

## Releasing

```bash
git tag v0.1.0
git push origin v0.1.0
```

When the run is green, download `framy-v0.1.0.apk` from the Release page and send it
on Telegram.

## How anyone can verify an APK

This is how anyone can check that an APK came from this public code:

```bash
sha256sum framy-v0.1.0.apk                       # compare with the .sha256 on the Release
gh attestation verify framy-v0.1.0.apk --repo <owner>/framy
```

The attestation proves GitHub Actions built that exact file from the tagged commit,
and not on someone's laptop.

## Notes

- Pull requests from forks get no secrets. `ci.yml` uses none, and only a tag you push
  can trigger a signed release.
- Public repos get unlimited free Actions minutes. A full CI run takes about
  12 minutes, most of it the emulator.
