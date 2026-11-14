# Framy setup, from zero

Do one part at a time. Tick each box. Stop between parts if you like; nothing breaks
if you leave a part half done.

**Total: about 1 hour.** You need: this PC, your Android phone, a GitHub account.
Everything is free, no card needed.

Keep a notes file open. You'll save 5 things in it:

| # | Thing | From part |
|---|---|---|
| 1 | Neon connection string | B |
| 2 | Developer key | C |
| 3 | Chat URL | C |
| 4 | Keystore password | D |
| 5 | Cloudflare API token + Account ID | E |

---

## Part A. Put the code on GitHub (5 min)

- [ ] 1. Open <https://github.com/new>.
- [ ] 2. Repository name: `framy`. Choose **Public**.
- [ ] 3. Leave README, .gitignore and license **unticked**. Click **Create repository**.
- [ ] 4. In a terminal on this PC, run these one at a time (replace `YOU` with your GitHub username):

```bash
cd ~/dev/framy
```

```bash
git checkout main && git merge feat/android-app
```

```bash
git remote add origin https://github.com/YOU/framy.git
```

```bash
git push -u origin main
```

✅ **Done when:** you refresh the GitHub page and see the code. A yellow "CI" dot
appears under **Actions**. Let it run (about 12 min); it should turn green.

---

## Part B. Create the chat database on Neon (5 min)

- [ ] 1. Open <https://neon.tech> and sign up (GitHub login is fine).
- [ ] 2. Create a project. Name: `framy`. Pick the region closest to you.
- [ ] 3. On the dashboard, click **Connect**. Copy the connection string
  (starts with `postgresql://`). **Save it as #1.**
- [ ] 4. In the left menu, open **SQL Editor**.
- [ ] 5. Open `chat-worker/schema.sql` on this PC, copy everything, paste it into the editor, click **Run**.

✅ **Done when:** the editor says the query succeeded.

---

## Part C. Put the chat online with Cloudflare (10 min)

- [ ] 1. Sign up at <https://dash.cloudflare.com/sign-up> (free plan).
- [ ] 2. In the terminal:

```bash
cd ~/dev/framy/chat-worker && npm ci
```

```bash
npx wrangler login
```

  A browser opens. Click **Allow**.

- [ ] 3. Make your developer key. Run this and **save the output as #2**:

```bash
openssl rand -hex 32
```

- [ ] 4. Give the Worker its two secrets. Each command asks you to paste a value:

```bash
npx wrangler secret put DATABASE_URL
```

  Paste **#1** and press Enter.

```bash
npx wrangler secret put ADMIN_KEY
```

  Paste **#2** and press Enter.

- [ ] 5. Deploy:

```bash
npx wrangler deploy
```

- [ ] 6. It prints a URL like `https://framy-chat.something.workers.dev`. **Save it as #3.**

✅ **Done when:** opening `#3/comments` in a browser shows `[]`.

---

## Part D. Make your signing key (5 min)

This key proves every update comes from you. **If you lose it, nobody can update
their installed app.** Back it up.

- [ ] 1. Run this. It asks for a password: pick one and **save it as #4**. For the
  other questions, press Enter (or type anything):

```bash
keytool -genkeypair -v -keystore ~/framy-release.jks -alias framy -keyalg RSA -keysize 2048 -validity 10000
```

  If it asks for a separate key password, use the same password as #4.

- [ ] 2. Copy `~/framy-release.jks` to a safe place (Google Drive, a USB stick, or both).

✅ **Done when:** the file `~/framy-release.jks` exists and you have a backup.

---

## Part E. Give GitHub the secrets (10 min)

First, get two values from Cloudflare:

- [ ] 1. Open <https://dash.cloudflare.com/profile/api-tokens> → **Create Token** →
  the **Edit Cloudflare Workers** template → **Continue to summary** → **Create Token**.
  Copy the token. **Save it as #5.**
- [ ] 2. Open <https://dash.cloudflare.com> → **Workers & Pages**. Copy the
  **Account ID** on the right. Save it next to #5.

Now get the keystore as text:

- [ ] 3. Run this. It prints a long line of text; copy all of it:

```bash
base64 -w0 ~/framy-release.jks
```

Now add them on GitHub: repo → **Settings** → **Secrets and variables** → **Actions**.

- [ ] 4. On the **Secrets** tab, click **New repository secret** six times:

| Name | Value |
|---|---|
| `FRAMY_KEYSTORE_BASE64` | the long text from step 3 |
| `FRAMY_STORE_PASSWORD` | #4 |
| `FRAMY_KEY_ALIAS` | `framy` |
| `FRAMY_KEY_PASSWORD` | #4 |
| `CLOUDFLARE_API_TOKEN` | #5 token |
| `CLOUDFLARE_ACCOUNT_ID` | #5 Account ID |

- [ ] 5. Switch to the **Variables** tab → **New repository variable**:

| Name | Value |
|---|---|
| `CHAT_URL` | #3 |

✅ **Done when:** you see 6 secrets and 1 variable.

---

## Part F. Protect `main` (2 min)

- [ ] 1. Repo → **Settings** → **Branches** → **Add branch ruleset** (or "Add rule").
- [ ] 2. Target: `main`. Tick **Require status checks to pass**, add the three CI checks
  (they appear after CI has run once). Save.

✅ **Done when:** the rule shows as active.

---

## Part G. Make the first release (15 min, mostly waiting)

- [ ] 1. Run:

```bash
cd ~/dev/framy && git tag v0.1.0 && git push origin v0.1.0
```

- [ ] 2. Open the repo → **Actions** → the **Release** run. Wait for green (about 15 min).
- [ ] 3. Open the repo → **Releases** → `v0.1.0`. Download `framy-v0.1.0.apk`.

✅ **Done when:** the Release page has the `.apk` and `.apk.sha256` files.

If it fails: click the red step and read the first error line. The usual cause is a
misspelled secret name in Part E.

---

## Part H. Install and set up on your phone (10 min)

Your friends do only this part.

- [ ] 1. Send the APK to yourself on Telegram. Tap it → **Install**. If Android asks,
  allow **Install unknown apps** for Telegram.
- [ ] 2. Open Framy. Tap **Allow** for notifications. It opens on Settings.

**Canvas:**

- [ ] 3. In a browser, log in to Canvas → **Account** → **Settings** → **+ New Access Token**.
  Purpose: `Framy`. Leave the expiry empty. Copy the token.
- [ ] 4. In Framy: check the Canvas address, paste the token, tap **Save token**.
  Wait for "Saved."

**Gmail:**

- [ ] 5. Turn on 2-Step Verification for your Google account if it's off.
- [ ] 6. Open <https://myaccount.google.com/apppasswords>. Name it `Framy`, tap
  **Create**, copy the 16-letter password.
- [ ] 7. In Framy: type your Gmail address, paste the app password, tap **Save**.
  Wait for "Connected."

**Battery:**

- [ ] 8. Scroll to **Keep mail instant** → **Allow background** → **Allow**.

✅ **Done when:** the Canvas tab shows your courses and the Mail tab shows a green
**Live** pill. Send yourself an email: it should notify within about 10 seconds.

---

## Part I. Turn on developer mode (1 min, only you)

- [ ] 1. Settings → scroll to the bottom → tap the version line **7 times**.
- [ ] 2. Paste **#2** into **Developer key** → **Save key**.

✅ **Done when:** your Global chat posts show the **Developer** badge and every post
has a **Delete** button.

---

## Part J. Share with friends

- [ ] 1. Send them `framy-v0.1.0.apk` on Telegram.
- [ ] 2. Send them Part H of this file.

Their token and password stay on their own phone. You never see them.

---

## Later: releasing an update

1. Make your change, and push it to `main` (CI must be green).
2. Run `git tag v0.1.1 && git push origin v0.1.1`. Use a higher number each time.
3. Download the new APK from Releases and send it. It installs over the old one and
   keeps everyone's settings.

Finally, run the real-phone checklist in [qa-2026-11.md](qa-2026-11.md).
