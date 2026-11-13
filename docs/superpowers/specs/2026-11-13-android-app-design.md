# Framy (Android) — design

Date: 2026-11-13. Status: complete, waiting for the owner's final approval.

## Why

NSU students use the official Canvas app. It logs them out every ~12 hours and its
notifications are unreliable, so people miss announcements. The owner ran a personal
fix (`canvas2telegram-main/`: Python scripts on GitHub Actions pushing to Telegram).
It never worked well: GitHub ran the 10-minute schedule hours late, and the AI
summaries almost always fell back to raw text. It also couldn't be shared without the
owner holding friends' Canvas tokens and Gmail passwords, which the owner refuses to do.

Framy replaces it with a small Android app. Each student signs in on their own phone,
their credentials stay on that phone, and the app only receives: Canvas activity and
due-date reminders, plus instant email.

## Goals

- Canvas tab: announcements, inbox messages, discussion and submission comments from
  **actively enrolled courses**. Checked automatically about every 10 minutes, plus a
  manual refresh button.
- Canvas due-date reminders for upcoming assignments.
- Canvas **Modules and Files** per active course, so materials faculty share are easy to browse and download.
- Mail tab: **one** Gmail / NSU Google Workspace account (owner's decision; more accounts later would mean restructuring the mail service). New mail arrives **within seconds**. The user
  can hide senders, and hidden senders never show up or notify again (until unhidden).
- Notifications that actually arrive, and keep arriving with the phone asleep.
- Very light: small APK, low battery use, minimal dependencies.
- Shareable with any number of students at zero cost to the owner.
- **Global chat**: a public in-app comment wall where users report issues and the owner reads and answers them.

## Non-goals (this version)

- iOS. It comes later, and iOS can't run 10-minute checks or hold a mail connection,
  so it will need its own design.
- AI summaries, link shortening, Telegram.
- Grades, calendar, planner sync, multiple mail accounts.

## Anti-goals (a failure even if it "works")

- **The app sends anything out.** It only reads. Concretely:
  - Canvas: HTTP `GET` only.
  - IMAP: the folder is opened read-only (`EXAMINE`), so nothing gets marked read,
    moved or deleted.
  - No analytics, crash reporting, update checks or remote images (tracking pixels),
    and no server run by the owner.
  - Network traffic goes only to the user's Canvas host, Canvas's own file-storage host (file downloads redirect there), and `imap.gmail.com`. The Canvas token is never sent to anything but the Canvas host.
  - Logging in obviously sends the credentials to those two hosts.
  - **The one deliberate exception is the Global chat screen.** It talks to the owner's
    Worker only while that screen is open: it loads comments, and it posts only when
    the user taps Post. Nothing is ever sent automatically or in the background.
- The owner (or anyone) ever seeing a user's token or password.
- Noticeable battery drain, a crash loop, or a retry storm when offline.
- Missing a notification silently. If a login stops working, the user is told.

## Constraints

- Canvas gives students no real-time push (Live Events and push channels are
  admin-only), so Canvas is polled. Polling is per user, from their own phone, so it
  stays well under Canvas's per-token rate limit.
- Canvas auth is a **personal access token** the user pastes in. Canvas's API policy
  prefers OAuth for multi-user apps, which needs an NSU admin-issued key. The owner
  accepted this grey area because tokens never leave the user's device.
- Mail auth is a **Gmail app password** over IMAP. There's no Google Cloud project, no
  OAuth, no 100-user cap and no cost. Each user needs 2-Step Verification. NSU or Google
  could disable app passwords in future; if so, the app shows a "login rejected" alert.
- Android only lets background work run so often while the phone sleeps (Doze), so
  "every 10 minutes" means about 10, sometimes a bit more.

## Approaches considered

| | Approach | Trade-off |
|---|---|---|
| **A (picked)** | Native Kotlin. IMAP IDLE held in a foreground service for instant mail. One ~10-min wake-up both renews IDLE and polls Canvas. | Instant mail, small app. Needs a permanent low-priority "Framy is running" notification. |
| B | Native Kotlin, poll both every 15 min with WorkManager. | Lightest, no permanent notification, but mail can be 15 min late. Fails the "instant" requirement. |
| C | Flutter (shared with future iOS). | ~4× bigger APK, flakier Android background plugins, and iOS needs a different background design anyway. |

A gets instant mail without any Google API registration. That last point settled it
when the owner worried about Google's 100-user limit, which only applies to the Gmail
API with OAuth, not to IMAP with app passwords.

## The design

### Stack

- Kotlin and Jetpack Compose (Material 3). minSdk 26, target the current SDK.
- Dependencies, kept to what's needed:
  - AndroidX core and Compose.
  - Jakarta/Angus Mail for Android, for IMAP, IDLE and MIME parsing. Hand-rolling
    these is where bugs live.
- Canvas HTTP: `HttpURLConnection` and `org.json`, both built into Android. No
  Retrofit, OkHttp or Room.
- R8 minify and resource shrink on release. Target an APK under ~5 MB.

### Visual system (approved from mockups, 2026-11-13)

The approved mockup is at `.superpowers/brainstorm/*/content/m3-system-v2.html`.

- **Material 3, by the book.** Proportion comes from the platform grid, not by eye:
  - 16dp side edges and an 8dp spacing rhythm.
  - Standard heights: 64dp top bar, 72dp two-line list rows, 80dp navigation bar,
    48dp minimum touch target, 40dp segmented button.
  - The M3 type scale on Roboto, Android's own font, so no font ships in the APK:
    22 title, 16 body, 14 supporting, 12 label.
  - Corner radii: 12 for cards and modules, 8 for chips and file icons, full for pills.
  - One outline icon style.
- **Navigation:**
  - Bottom bar: Canvas and Mail. The Canvas icon shows an unread badge.
  - **Global chat is discreet:**
    - It's a small chat-bubble icon in the top bar of both tabs, next to refresh and
      settings, and it opens a full-screen "Global chat".
    - There's no bottom tab for it and no badge or notification for new messages.
  - The Canvas tab has a segmented control for Updates and Courses.
  - A course opens with a back arrow and tabs for Modules and Files.
  - The Mail top bar shows a "Live" pill while the instant connection is up, and a
    snackbar with Undo after "Hide this sender".
- **Canvas Updates:**
  - "Due soon" is a two-column card grid. Each card has a course chip, the title and
    the time left; "Tomorrow" or less turns error-red.
  - "Today" is a list with course-code avatars and unread dots.
- **Modules:**
  - Collapsible week cards. File rows have a download button that turns into a tick
    once downloaded.
  - Pages, quizzes and links show an "open externally" arrow.
- **Colour: soft, not vibrant.** The owner found the mockup seeds too vivid. The
  palette uses low-chroma seeds with M3 tonal-spot schemes.
  - **Course colours:** each course gets a stable hue (picked from its id), harmonised
    to the theme and kept to low chroma. Containers are tone 90 in light and tone 30
    in dark.
  - **Settings → Appearance:**
    - Theme colour: **Wallpaper** (Material You, Android 12+, the default there), or
      one of five soft presets: Slate blue `#5E7FA8` (default below Android 12), Sage
      `#6F8F7A`, Dusty teal `#5B8E8E`, Sand `#A08F72`, Rose `#A87A80`.
    - Mode: System, Light or Dark.
  - Preset schemes are generated **once, offline**, with Google's
    material-color-utilities and checked in as Kotlin colour constants. That means no
    colour library in the APK.

- **First launch / Settings:**
  - Canvas: base URL (prefilled `https://northsouth.instructure.com`, editable) and
    access token, with a one-line "how to get a token" hint.
  - Mail: Gmail address and app password, with a hint linking to the app-password
    page.
  - Either section can be left empty. A user can run Canvas-only or mail-only.
  - Also in Settings: the hidden-senders list (tap to unhide), and a button that opens
    the system "don't optimise battery for Framy" prompt. That exemption is needed for
    mail to stay instant while the phone sleeps.
  - Credentials are checked once on save, with a GET to `/api/v1/users/self` and an
    IMAP login.
- **Canvas tab:**
  - "Due soon": unsubmitted planner items due in the next 14 days.
  - "Activity": newest first.
  - Tapping an item opens a detail screen with the full text (HTML rendered as plain
    styled text, no remote images), attachment links, and "Open in Canvas" (an
    external browser intent, which the user triggers).
- **Courses (inside the Canvas tab):**
  - A third section, or a segmented control at the top: Updates and Courses.
  - It lists the active courses. Tapping one opens two tabs:
    - **Modules:** `GET /api/v1/courses/:id/modules?include[]=items&per_page=100`. Modules are collapsible. File items show a download button, and every other item type (page, quiz, link) opens in the browser.
    - **Files:** `GET /api/v1/courses/:id/folders` and `.../files`, as a folder tree with a download button per file.
  - Many faculty hide the Files tab, and Canvas then returns 401 or 403 for that course. Show "Files are hidden for this course, check Modules" there. This must **not** trigger the "token rejected" alert: only a 401 on `/users/self` or the activity stream counts as a rejected token.
  - Both lists load on open and are cached. They aren't polled in the background.
- **Mail tab:**
  - List of From, Subject, time and a one-line snippet.
  - Tap opens the detail: full text, tappable links, and attachments that download on
    tap and open via the system viewer.
  - Long-press any mail and choose "Hide this sender". That removes all their mail from
    the list and stops their notifications. Matching is on the exact address.

### Background behaviour

- **Mail-only "service":**
  - Started at login and on boot (if mail is configured), shown as a silent,
    minimum-priority ongoing notification.
  - Holds one IMAP connection to `INBOX` (read-only) in IDLE.
  - On new mail: fetch headers and body for new UIDs above the stored cursor, filter
    hidden senders, save, notify.
  - Fetches attachment bodies only when the user taps them.
- **Tick (~10 min):**
  - One `AlarmManager.setAndAllowWhileIdle` alarm. It needs no exact-alarm permission
    and is allowed in Doze, and it is re-armed each time it fires.
  - Each tick holds a short wakelock while it:
    1. renews the IDLE (Gmail drops idle connections, so re-issue before it does), or
       reconnects if the connection died;
    2. polls Canvas;
    3. fires any due-date reminders.
  - Canvas-only users get the same alarm without the service.
- **Offline:** if there's no network, do nothing and wait for the connectivity
  callback or the next tick. No retry loops. Back off on repeated failures (cap of one
  attempt per tick).
- **Manual refresh:** the ⟳ button runs the same tick logic immediately.

### Canvas polling, per tick

1. `GET /api/v1/users/self/courses?enrollment_state=active&per_page=100` gives the
   active course ids and course codes.
2. `GET /api/v1/users/self/activity_stream?per_page=50` gives new items. Keep those
   whose course is active, or that have no course (conversations). Dedup by item id,
   and on first run notify nothing (store only), like the old script.
3. `GET /api/v1/planner/items?start_date=<now>&end_date=<now+14d>` gives the due-soon
   list. Each item carries its submission status, so there are no per-assignment
   calls.

The full message for the detail screen is fetched when the user opens it (topic or
conversation endpoint, as `notify.py` did), not on every poll.

### Reminders

- For each unsubmitted planner item, notify once at **24 h** and once at **3 h** before
  it's due.
- They're checked on each tick, so a reminder fires within about 10 minutes of its
  threshold.
- The detail screen has a local **"Done"** button that stops reminders for that item.
  It isn't sent to Canvas (read-only).
- Submitted items stop reminding automatically.

### Errors that must reach the user

- Canvas returns 401 or 403: one "Canvas token rejected — paste a new one in Settings"
  notification, and polling pauses until the token changes.
- IMAP login fails with an auth error: the same, for mail.
- Any other error is logged locally only and retried on the next tick.

### Storage

- Credentials are encrypted with an AES-GCM key held in Android Keystore and stored in
  private SharedPreferences. Never logged, and excluded from backup
  (`allowBackup=false`).
- Feed data goes in small JSON files in app-private storage:
  - last 200 Canvas items, plus planner items;
  - last 200 mail headers and snippets;
  - seen ids and the mail UID cursor;
  - hidden senders, and reminders already sent.
  - Older items are dropped.

### Notifications

Channels:
- Canvas activity (default importance)
- Mail (high: the instant one)
- Reminders (high)
- Problems (high)
- Running (min, silent)

Tapping a notification opens that item's detail.

### Distribution

A release APK signed with the owner's key, sent to friends to install. Updates are new
APKs sent the same way. There's no in-app update check, because it would send
requests.

## Global chat

### Shape

```
Android app ──HTTPS──► Cloudflare Worker (free) ──► Neon Postgres (free)
 (Global chat screen)      validates, rate-limits,       one table: comments
                        daily cleanup cron
```

- **Why not Prisma:** it's heavy inside a Worker. Neon's own serverless driver
  (`@neondatabase/serverless`) plus 3 SQL statements does the job.
- **Why the Worker:** database credentials can't ship in the APK, because anyone could
  unpack it and wipe the table. The Worker holds the Neon connection string as a
  Cloudflare secret.
- **Table:** `comments(id serial, nick text, tag text, body text, is_dev bool,
  created_at timestamptz)`.
- **Endpoints:**
  - `GET /comments`: newest 100.
  - `POST /comments`: `{nick, tag, body}`.
  - `DELETE /comments/:id`: requires the admin key.
- **Cleanup:** a daily Cron Trigger deletes comments older than 60 days and keeps at
  most the newest 1,000.

### In the app

- Reached from the small chat-bubble icon in the top bar (not a bottom tab), titled
  **Global chat**.
- A flat list (newest first) and a compose box.
- It never notifies.
- **Nickname:**
  - Optional. Default is "Anonymous".
  - Every post shows a short tag (e.g. `#a3f2`) generated once per install, so
    regulars are recognisable.
  - No accounts, no email, no Canvas identity attached.
- **Developer mode (owner only):**
  - Tap the version number 7 times, then paste the admin key, which is stored
    encrypted on the owner's phone and never in the APK.
  - The owner's posts get a **Developer** badge, and a delete button appears on every
    comment.

### Pros, cons and fixes

| Pros | |
|---|---|
| Free | Cloudflare Workers free plan (100k requests/day) and Neon free tier. Comments are tiny text, so storage will never be the limit. |
| No Google | No Google Cloud project or verification. |
| No surprise bills | Neither account needs a card. Hitting a free limit makes requests fail; it never charges. |
| Public, owner-controlled | Everyone sees the same wall, and the owner moderates from their own phone. |
| Small app cost | Nothing to add to the app: built-in `HttpURLConnection` and `org.json` again. |

| Cons | Fix |
|---|---|
| The Worker URL is in the APK, so anyone can script spam at it. | Cloudflare's free rate-limit binding (3 posts per 60 s per IP; the binding only supports 10 s or 60 s periods), a 1,000-char body cap, a 30-char nick cap, and the owner's delete button. If it gets bad later, add Cloudflare Turnstile (a free invisible captcha). |
| Anonymous posts mean offensive content is possible. | The owner deletes from the app. The 60-day auto-cleanup bounds anything missed. |
| A nick and tag can be faked via the API. | Accepted for a global chat. Only the Developer badge is trustworthy, because only the admin key can set it. |
| Neon's free database sleeps when idle, so the first load after a quiet spell can take 1–3 s. | Show a loading shimmer. The screen loads on demand only, so nothing waits in the background. |
| It breaks the pure receive-only rule. | Scoped exception: Global chat screen only, user-initiated, no IPs or identities stored. Stated in Settings → Privacy. |
| The owner maintains one more thing (two free accounts, one deploy). | The Worker is ~80 lines in `chat-worker/` with a 5-step setup README (`wrangler deploy`, two secrets). |
| If either free plan changes, the board breaks. | Only the Global chat shows "Global chat unavailable". Canvas and Mail are untouched. |

## Testing

- Unit tests for the pure logic:
  - activity filtering and dedup;
  - the reminder threshold calculation;
  - the hidden-sender filter;
  - the UID cursor and first-run behaviour;
  - HTML-to-text conversion.
- Worker: a test for validation (empty or oversize body rejected), for the admin-key
  check, and for the cleanup query.
- Manual check on a real phone:
  - login;
  - receive a test email within seconds with the screen off;
  - Canvas refresh;
  - a reminder;
  - airplane-mode recovery;
  - an overnight battery reading.

## Calls made during review (builder check)

- **Mail first run:** set the cursor to the newest UID and notify nothing. Keep
  UIDVALIDITY too; if it changes, reset the cursor without notifying.
- **Canvas conversation detail:** use
  `GET /api/v1/conversations/:id?auto_mark_as_read=false`, because a plain GET marks
  the conversation read on the server.
- **Canvas attachments and files:** downloaded with Android's built-in `DownloadManager` into `Downloads/Framy/` (system progress notification, no extra code). This replaces the earlier "open in browser" call now that Modules/Files exist.
- **Mail service type:** `specialUse`, which isn't time-limited the way `dataSync` is
  on Android 15. Handle the Android 14+ limits on starting a foreground service at
  boot.
- **Reminder items:** only submittable planner items that have a due date. If an item
  is first seen already inside a threshold, fire only the nearest reminder.
- **Gmail Promotions and Social:** they arrive in INBOX and do notify. Hiding senders
  is the answer to that noise for now.
- **Token expiry:** the old date-based expiry warning is dropped. The 401 "rejected"
  alert covers it.

## Left to the builder

- Package name (proposed `app.framy`) and icon.
- Exact IDLE renew timing and back-off numbers.
- Compose structure. Keep it to a handful of files.
- How mail attachments are cached (app cache dir, cleared on low storage).
