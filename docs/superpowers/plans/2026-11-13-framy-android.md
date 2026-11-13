# Framy Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A light, receive-only Android app for NSU students: Canvas updates, reminders,
modules and files, instant Gmail, and a small public global chat.

**Architecture:**
- One single-module Kotlin and Compose app.
- Pure logic (parsing, dedup, reminders, mail cursor) lives in plain Kotlin objects with
  JVM unit tests. Android glue (alarm, foreground IMAP IDLE service, DownloadManager,
  notifications) is thin and checked by hand on the emulator and a phone.
- The global chat is a separate ~80-line Cloudflare Worker over Neon, in
  `chat-worker/`.

**Tech Stack:**
- Gradle 8.11.1 (already cached in `~/.gradle`), AGP 8.9.1, Kotlin 2.1.10, Compose BOM
  2025.02.00 (Material 3).
- `com.sun.mail:android-mail:1.6.7` and `com.sun.mail:android-activation:1.6.7`.
- `HttpURLConnection` and `org.json` for HTTP and JSON.
- JUnit 4.13.2. `org.json:json:20240303` as `testImplementation` only, because
  android.jar's org.json is a stub in JVM tests.
- Worker: plain JS, `@neondatabase/serverless`, `wrangler`, and `node --test`.

**Spec:** `docs/superpowers/specs/2026-11-13-android-app-design.md`. Read it before
any task.

## Global Constraints

- **Package and SDK:**
  - Package `app.framy`. App name "Framy".
  - `minSdk 26`, `compileSdk 35`, `targetSdk 35`. SDK lives at `~/Android/Sdk`, with
    platforms 35 and 36 installed.
- **Receive-only:**
  - Canvas calls are HTTP `GET` only.
  - The IMAP folder opens with `Folder.READ_ONLY`, which sends `EXAMINE`.
  - No analytics, crash reporting, update check or remote image loading.
  - The Canvas token goes only in requests to the user's Canvas host, never to a
    file-download host.
  - Global chat network calls happen only from the Global chat screen, and posts only on a
    user tap.
- **Dependencies:** none beyond the Tech Stack list. No Retrofit, OkHttp, Room,
  Hilt or Coil.
- **Polling and reminders:**
  - Canvas polls about every 10 minutes using `AlarmManager.setAndAllowWhileIdle`,
    re-armed each fire.
  - Reminders fire at 24 h and 3 h before the due time.
  - The due-soon window is 14 days.
- **Retention:** keep the last 200 Canvas items and the last 200 mail items.
- **Defaults:**
  - Canvas base URL defaults to `https://northsouth.instructure.com`.
  - IMAP host `imap.gmail.com:993` (imaps), folder `INBOX`.
- **Look:**
  - Material 3 metrics: 16dp side edges, 8dp rhythm, 64dp top bar, 72dp two-line
    rows, 80dp navigation bar, 48dp touch targets.
  - Radii 12 (cards), 8 (chips), full (pills).
  - Theme presets: slate `#5E7FA8`, sage `#6F8F7A`, teal `#5B8E8E`, sand `#A08F72`,
    rose `#A87A80`. Default is Wallpaper on Android 12+, else slate.
  - Mode is system, light or dark.
- **Files and backup:** downloads go to `Downloads/Framy/`. `android:allowBackup="false"`.
- **Notification channels:**

  | id | importance |
  |---|---|
  | `canvas` | default |
  | `mail` | high |
  | `reminders` | high |
  | `problems` | high |
  | `running` | min |

- **Size:** release APK under 5 MB.
- **CI:** `.github/workflows/ci.yml` already exists.
  - Every task's commit keeps `./gradlew testDebugUnitTest lintDebug assembleDebug`,
    `connectedDebugAndroidTest` and `cd chat-worker && npm test` green.
  - CI uses JDK 17 and an API 34 emulator.
  - Release signing reads the Gradle properties `framyStoreFile`,
    `framyStorePassword`, `framyKeyAlias` and `framyKeyPassword`. These names have no
    dots, so they can be set from `ORG_GRADLE_PROJECT_*` env vars.
- **Skills:** the user-level skills `android-development` (NowInAndroid guidance),
  `modern-jetpack-compose` (Compose review), `android-signing-config`,
  `android-release-build-setup` and `android-ci-tests` are installed.
  - Use them for review hints only.
  - This plan's dependency limits win where they disagree: no Hilt, no Room, single
    module.
- **License:** GPL-3.0 (`LICENSE` at the repo root). Add `SPDX-License-Identifier: GPL-3.0-or-later` only to the Worker's `package.json` `license` field; no per-file headers.
- **Code:** follow ponytail rules. Smallest complete change, no speculative
  abstraction, and a `shortcut:` comment on every known limit.

## Review Focus

1. **A 401/403 from one course's Files or Modules endpoint** (faculty hid the tab).
   Expected: "Files are hidden for this course, check Modules". The token-rejected
   alert must not fire. Pinned in Task 5.
2. **Activity or planner JSON with nulls** (`course_id: null`, missing `title` or
   `html_url`, `submissions: false`, `due_at: null`). Expected: the item is skipped
   or shown with a fallback, and nothing crashes. Pinned in Task 4.
3. **Coming back online after hours away,** with dozens of new Canvas items or emails
   at once. Expected: at most 5 individual notifications per source per tick, plus
   one summary ("12 new emails"), and no notification spam. Pinned in Tasks 4 and 7.
4. **HTML-only or oddly encoded email** (no text/plain part, `=?UTF-8?B?…?=`
   subject, no display name). Expected: a readable sender, subject and snippet.
   Pinned in Task 7.
5. **A due time in UTC** (`2026-12-16T17:59:59Z`). Expected: shown in the phone's
   timezone (Dhaka: 16 Dec, 11:59 PM), with reminders computed on absolute time.
   Pinned in Task 4.

---

## File Structure

```
.gitignore                      # build/, .gradle/, local.properties, .superpowers/, canvas2telegram-main/, *.jks, chat-worker/node_modules
settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew, gradle/wrapper/*
app/build.gradle.kts, app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/java/app/framy/
  FramyApp.kt          Application: init Store, create channels, arm tick
  Models.kt            data classes shared by everything
  Store.kt             JSON-file persistence + StateFlows for UI
  Secrets.kt           Keystore AES-GCM for token/passwords/admin key
  Prefs.kt             plain settings (base URL, theme, mode, nick, tag)
  Notify.kt            channels + posting + summary capping
  Tick.kt              TickReceiver, BootReceiver, Tick.run()
  canvas/CanvasLogic.kt   pure: parse + dedup + reminders
  canvas/CanvasApi.kt     GETs, error classification
  mail/MailLogic.kt       pure: cursor, filter, snippet
  mail/MailService.kt     foreground service, IMAP IDLE loop
  Downloads.kt         DownloadManager enqueue + done tracking
  chat/ChatApi.kt
  ui/Theme.kt, ui/Palettes.kt (generated), ui/MainActivity.kt
  ui/CanvasScreen.kt, ui/CourseScreen.kt, ui/MailScreen.kt,
  ui/DetailScreen.kt, ui/ChatScreen.kt, ui/SettingsScreen.kt
app/src/test/java/app/framy/...  JVM tests
tools/gen-palettes.mjs, tools/package.json
chat-worker/src/index.js, chat-worker/test/index.test.js, wrangler.toml, package.json, README.md
```

## Shared Models (`Models.kt`, created in Task 3, used everywhere)

```kotlin
data class Course(val id: Long, val code: String, val name: String)
data class CanvasItem(val id: String, val type: String, val title: String, val courseId: Long?,
                      val createdAt: Long /* epoch ms */, val htmlUrl: String, val preview: String, val read: Boolean = false)
data class PlannerItem(val key: String /* "<plannable_type>:<plannable_id>" */, val title: String, val courseId: Long?,
                       val dueAt: Long, val submitted: Boolean, val htmlUrl: String /* absolute */)
data class MailItem(val uid: Long, val fromName: String, val fromAddr: String, val subject: String,
                    val date: Long, val snippet: String, val read: Boolean = false)
data class CanvasState(val courses: List<Course> = emptyList(), val items: List<CanvasItem> = emptyList(),
                       val planner: List<PlannerItem> = emptyList(), val seen: Set<String> = emptySet(),
                       val remindersSent: Set<String> = emptySet(), val done: Set<String> = emptySet(),
                       val downloaded: Set<Long> = emptySet(), val firstRunDone: Boolean = false, val tokenRejected: Boolean = false)
data class MailState(val uidValidity: Long = -1, val lastUid: Long = -1, val items: List<MailItem> = emptyList(),
                     val hidden: Set<String> = emptySet(), val live: Boolean = false, val authRejected: Boolean = false)
```

---

### Task 1: Project skeleton that builds and tests

**Files:**
- Create: `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
  `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`,
  `app/src/main/java/app/framy/ui/MainActivity.kt`,
  `app/src/test/java/app/framy/SmokeTest.kt`, and the wrapper files.

**Interfaces:**
- Produces: a buildable `:app` module with the Tech Stack versions and the `debug` and
  `release` build types.

- [ ] **Step 1: Init the repo.**
  Run `git init` in `/home/li/dev/framy` and write `.gitignore` with the entries
  listed in File Structure.
  - The old `canvas2telegram-main/` stays untracked. It's the owner's separate
    private repo.
- [ ] **Step 2: Generate the wrapper.**
  Run
  `~/.gradle/wrapper/dists/gradle-8.11.1-bin/*/gradle-8.11.1/bin/gradle wrapper --gradle-version 8.11.1`.
- [ ] **Step 3: Write the Gradle files.**
  - Use the plugins `com.android.application` 8.9.1, `org.jetbrains.kotlin.android`
    2.1.10 and `org.jetbrains.kotlin.plugin.compose` 2.1.10.
  - Set `buildFeatures { compose = true; buildConfig = true }`.
  - Add `buildConfigField("String", "CHAT_URL", "\"${project.findProperty("framyChatUrl") ?: ""}\"")`.
  - `versionCode` comes from the `framyVersionCode` property (default `1`) and
    `versionName` from `framyVersionName` (default `"0.1.0-dev"`).
  - `lint { abortOnError = true }`.
  - Set `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`.
  - Add these `androidTestImplementation` deps, test-only so they never ship:
    `androidx.compose.ui:ui-test-junit4` (from the BOM), `androidx.test.ext:junit:1.2.1`
    and `androidx.test:runner:1.6.2`. Also add `debugImplementation`
    `androidx.compose.ui:ui-test-manifest`.
  - Release uses `isMinifyEnabled = true` and `isShrinkResources = true`.
  - Write `local.properties` with `sdk.dir=/home/li/Android/Sdk`.
- [ ] **Step 4: Write the manifest and MainActivity.**
  - The manifest has `allowBackup="false"` and the `INTERNET` permission only.
  - `MainActivity` is a `ComponentActivity` that shows `Text("Framy")`.
- [ ] **Step 5: Write `SmokeTest.smoke()`.**
  It asserts `org.json.JSONObject("{\"a\":1}").getInt("a") == 1`. That proves the
  test classpath has the real org.json.
- [ ] **Step 6: Build and test.**
  Run `./gradlew assembleDebug testDebugUnitTest lintDebug`. That is the same command
  CI runs.
  Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 7: Commit:** `chore: android skeleton`.

### Task 2: Theme, palettes, appearance prefs

**Files:**
- Create: `tools/package.json` (devDependency
  `@material/material-color-utilities@0.3.0`) and `tools/gen-palettes.mjs`.
- Create: `app/src/main/java/app/framy/ui/Palettes.kt` (generated),
  `app/src/main/java/app/framy/ui/Theme.kt`, `app/src/main/java/app/framy/Prefs.kt`.
- Test: `app/src/test/java/app/framy/ui/PalettesTest.kt`.

**Interfaces:**
- Produces:
  - `object Palettes { val presets: Map<String, Pair<ColorScheme, ColorScheme>> /* light, dark */; fun course(preset: String, courseId: Long, dark: Boolean): Pair<Color, Color> /* container, onContainer */ }`
  - `@Composable fun FramyTheme(content: @Composable () -> Unit)`
  - `object Prefs { fun init(ctx: Context); var themeSeed: String /* "wallpaper"|"slate"|"sage"|"teal"|"sand"|"rose" */; var mode: String /* "system"|"light"|"dark" */; var canvasBaseUrl: String; var nick: String; val tag: String /* 4 hex chars, created once */ }`

- [ ] **Step 1: Write `tools/gen-palettes.mjs`.**
  - For each preset seed, build `SchemeTonalSpot(Hct.fromInt(seed), dark, 0)` for
    light and dark.
  - Emit a Kotlin `lightColorScheme(...)` / `darkColorScheme(...)` with every M3 role,
    including the `surfaceContainer*` roles.
  - Emit 8 course hues `#8E7CC3 #6F9BD1 #6FAE8C #D0A85C #C97C7C #6FB3B8 #A88FB0 #9AA16A`,
    each `Blend.harmonize`d to the seed and reduced to chroma ≤ 24. Use tone 90/10 for
    light and 30/90 for dark.
  - Output goes to `Palettes.kt`, with a header comment saying it is generated and
    naming the command.
- [ ] **Step 2: Generate.**
  Run `cd tools && npm i && node gen-palettes.mjs > ../app/src/main/java/app/framy/ui/Palettes.kt`.
- [ ] **Step 3: Write the failing test `PalettesTest`.**
  - `presets.keys == setOf("slate","sage","teal","sand","rose")`.
  - `course("slate", 7, false) == course("slate", 7, false)`, i.e. stable per course.
  - `course("slate", 7, false) != course("slate", 8, false)`.
- [ ] **Step 4: Implement `FramyTheme`.**
  - `themeSeed == "wallpaper"` on API 31+ uses `dynamicLightColorScheme` /
    `dynamicDarkColorScheme`. Otherwise use the preset, falling back to slate.
  - Mode comes from `Prefs.mode`, using `isSystemInDarkTheme()` when it's "system".
  - Expose the course colours via a `CompositionLocal`, using slate's set when on
    wallpaper. Add a `shortcut:` comment saying wallpaper mode doesn't harmonise course
    hues.
- [ ] **Step 5: Run the test.**
  Run `./gradlew testDebugUnitTest --tests '*PalettesTest'`.
  Expected: PASS.
- [ ] **Step 6: Commit:** `feat: M3 theme with soft presets and wallpaper colour`.

### Task 3: Models, Store, Secrets

**Files:**
- Create: `Models.kt` (exactly as Shared Models above), `Store.kt`, `Secrets.kt`.
- Test: `app/src/test/java/app/framy/StoreJsonTest.kt`.

**Interfaces:**
- Produces:
  - `object Store { fun init(dir: File); val canvas: StateFlow<CanvasState>; val mail: StateFlow<MailState>; fun updateCanvas(f: (CanvasState) -> CanvasState); fun updateMail(f: (MailState) -> MailState) }`
    - Each update writes atomically: write a temp file, then rename.
    - Items are capped at 200, newest first.
  - `object StoreJson { fun canvasToJson(s: CanvasState): String; fun canvasFromJson(j: String): CanvasState; fun mailToJson(s: MailState): String; fun mailFromJson(j: String): MailState }`
    - Pure, so it can be tested.
  - `object Secrets { fun init(ctx: Context); fun get(key: String): String?; fun put(key: String, value: String?) }`
    - Keys: `"canvasToken"`, `"mailAddress"`, `"mailPassword"`, `"adminKey"`.
    - Uses an AES/GCM/NoPadding key aliased `framy` in `AndroidKeyStore`, stored base64
      as iv+ciphertext in private prefs `secrets`.

- [ ] **Step 1: Write the failing test `StoreJsonTest.roundTrip()`.**
  A `CanvasState` and a `MailState` with one of every field survive
  `fromJson(toJson(x)) == x`.
- [ ] **Step 2: Write the failing test `StoreJsonTest.corruptFileGivesEmptyState()`.**
  `canvasFromJson("{oops") == CanvasState()`, so a corrupt file never crashes the app.
- [ ] **Step 3: Implement `StoreJson`, `Store` and `Secrets`.**
  Secrets is not JVM-testable; it's checked in Task 12's manual pass.
- [ ] **Step 4: Run the tests.**
  Run `./gradlew testDebugUnitTest --tests '*StoreJsonTest'`.
  Expected: PASS.
- [ ] **Step 5: Commit:** `feat: json store and keystore secrets`.

### Task 4: Canvas logic (pure)

**Files:**
- Create: `app/src/main/java/app/framy/canvas/CanvasLogic.kt`.
- Test: `app/src/test/java/app/framy/canvas/CanvasLogicTest.kt`.

**Interfaces:**
- Produces, all in `object CanvasLogic`:
  - `fun parseCourses(json: String): List<Course>`
    - `course_code` falls back to `name`, then to `"course <id>"`.
  - `fun parseActivity(json: String): List<CanvasItem>`
    - `id` is the stream item id as a string.
    - `title` falls back to the type.
    - `preview` is the first 140 chars of `message` with HTML stripped.
    - Missing `html_url` becomes `""`.
  - `fun parsePlanner(json: String, baseUrl: String): List<PlannerItem>`
    - Keep only items whose `plannable.due_at` (or `plannable_date`, for type
      `assignment`) is non-null and whose `plannable_type` is in
      `assignment, quiz, discussion_topic`.
    - `submitted = submissions is object && (submitted || graded)`.
    - Relative `html_url` is prefixed with `baseUrl`.
  - `fun toNotify(fetched: List<CanvasItem>, seen: Set<String>, activeCourseIds: Set<Long>, firstRun: Boolean): List<CanvasItem>`
    - Keeps unseen items whose `courseId` is null or active.
    - Returns empty on first run.
  - `fun dueReminders(items: List<PlannerItem>, sent: Set<String>, done: Set<String>, now: Long): List<Pair<PlannerItem, String /* "24h"|"3h" */>>`
  - `fun parseIso(ts: String): Long`
- Notification capping is shared and lives in `Notify.kt` (Task 6). Its pure part is
  defined here so both sources use it:
  `fun <T> capped(list: List<T>, max: Int = 5): Pair<List<T>, Int /* overflow count */>`.

Reminder rule (the spec's "nearest only"):

```
for each item not submitted, not in done, dueAt > now:
  left = dueAt - now
  if left <= 3h and "${key}:3h" !in sent  -> emit (item,"3h")        // also suppresses 24h
  else if left <= 24h and "${key}:24h" !in sent and "${key}:3h" !in sent -> emit (item,"24h")
```

- [ ] **Step 1: Write failing tests with literal JSON fixtures in the test file.**
  - `parseActivitySkipsNothingAndSurvivesNulls`: an item with `course_id:null`, no
    `title`, no `html_url` parses with `courseId == null`, `title == type`,
    `htmlUrl == ""`.
  - `parsePlannerDropsUndatedAndUnsubmittable`: `submissions:false` gives
    `submitted == false`; `due_at:null` is dropped; a `planner_note` is dropped;
    relative `/courses/1/assignments/2` becomes `https://x.instructure.com/courses/1/assignments/2`.
  - `toNotifyFiltersInactiveAndSeen`: inactive course dropped, `courseId == null` kept,
    seen dropped, `firstRun = true` gives empty.
  - `reminderNearestOnly`:
    - `left = 2h` with nothing sent gives `[3h]`.
    - `left = 20h` gives `[24h]`.
    - `left = 20h` with `24h` already sent gives `[]`.
    - Submitted gives `[]`, and done gives `[]`.
  - `isoUtcToDhaka`: `parseIso("2026-12-16T17:59:59Z") == 1797443999000L`, and
    formatting that in `Asia/Dhaka` gives `16 Dec, 11:59 PM`.
  - `cappedFive`: 12 items give 5 items plus overflow 7, and 3 items give 3 plus 0.
- [ ] **Step 2: Run the tests and check they fail.**
  Run `./gradlew testDebugUnitTest --tests '*CanvasLogicTest'`.
  Expected: FAIL, unresolved references.
- [ ] **Step 3: Implement `CanvasLogic` and `capped`.**
  `parseIso` uses `java.time.Instant.parse`, which is available from minSdk 26.
- [ ] **Step 4: Run the tests again.**
  Expected: PASS.
- [ ] **Step 5: Commit:** `feat: canvas parsing, dedup and reminder rules`.

### Task 5: Canvas HTTP client

**Files:**
- Create: `app/src/main/java/app/framy/canvas/CanvasApi.kt`.
- Test: `app/src/test/java/app/framy/canvas/CanvasApiTest.kt`.

**Interfaces:**
- Consumes: `CanvasLogic.parse*` (Task 4), `Secrets.get("canvasToken")`,
  `Prefs.canvasBaseUrl`.
- Produces:
  - `sealed interface Fail { object TokenRejected : Fail; object Hidden : Fail; data class Other(val code: Int) : Fail }`
    and `class CanvasException(val fail: Fail) : Exception()`.
  - `fun classify(code: Int, path: String): Fail?`
    - `null` for 2xx.
    - 401/403 on paths starting with `/api/v1/users/self` gives `TokenRejected`.
    - 401/403 anywhere else gives `Hidden`.
    - Anything else gives `Other(code)`.
  - `object CanvasApi`:
    - `fun courses(): List<Course>`
    - `fun activity(): List<CanvasItem>`
    - `fun planner(now: Long): List<PlannerItem>`
    - `fun modules(courseId: Long): JSONArray`
    - `fun folders(courseId: Long): JSONArray`
    - `fun files(folderId: Long): JSONArray`
    - `fun fileDownloadUrl(fileApiUrl: String): Pair<String, String> /* url, display_name */`
    - `fun topic(courseId: Long, topicId: Long): JSONObject`
    - `fun conversation(id: Long): JSONObject`
    - `fun validate(baseUrl: String, token: String): Boolean`
    - All of them are GET, with a 20 s connect and read timeout, and they throw
      `CanvasException`.
    - The endpoints and query strings are exactly as in the spec's "Canvas polling,
      per tick" and Courses sections. `conversation` adds `auto_mark_as_read=false`.
    - Follow the `Link: rel="next"` header for modules and files, up to 5 pages, with a
      `shortcut:` comment noting the cap.

- [ ] **Step 1: Write failing tests `classifyHiddenVsRejected`.**
  - `classify(401,"/api/v1/courses/5/folders") == Hidden`. This is Review Focus 1.
  - `classify(401,"/api/v1/users/self/activity_stream") == TokenRejected`.
  - `classify(200, "...") == null`.
  - `classify(500,"/api/v1/courses/5/modules") == Other(500)`.
- [ ] **Step 2: Run them and check they fail, then implement.**
- [ ] **Step 3: Run `./gradlew testDebugUnitTest --tests '*CanvasApiTest'`.**
  Expected: PASS.
- [ ] **Step 4: Commit:** `feat: canvas GET client with hidden-vs-rejected errors`.

### Task 6: Notifications and the 10-minute tick

**Files:**
- Create: `Notify.kt`, `Tick.kt`, `FramyApp.kt`.
- Modify: `AndroidManifest.xml`:
  - permissions `POST_NOTIFICATIONS`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`;
  - `TickReceiver` and `BootReceiver`;
  - `android:name=".FramyApp"`.

**Interfaces:**
- Consumes: Tasks 3, 4 and 5.
- Produces:
  - `object Notify`:
    - `fun channels(ctx: Context)`
    - `fun canvas(ctx: Context, items: List<CanvasItem>)`, which uses `capped` and
      posts a summary "N more Canvas updates" for the overflow.
    - `fun mail(ctx: Context, items: List<MailItem>)`, the same with "N more emails".
    - `fun reminder(ctx: Context, item: PlannerItem, which: String)`, with text
      "Due in 24 hours" or "Due in 3 hours".
    - `fun problem(ctx: Context, id: Int, text: String)`
    - `fun runningNotification(ctx: Context): Notification`
    - Every tap opens `MainActivity` with extra `open = "canvas:<id>" | "mail:<uid>" | "planner:<key>"`.
  - `object Tick`:
    - `fun arm(ctx: Context)`, which calls
      `setAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, now+10min, pendingIntent(TickReceiver))`.
    - `fun run(ctx: Context)`, which is blocking and runs on a background thread.
  - `TickReceiver` uses `goAsync()` and a thread holding a 60 s timed partial wakelock.
    It calls `Tick.run` and then `Tick.arm`.
  - `BootReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`) calls `Tick.arm` and
    starts `MailService` if mail is configured (Task 8 adds that line).

`Tick.run` order:
1. Call `MailService.renew(ctx)`. Task 8 adds this line.
2. If a Canvas token is set and not rejected: `courses` → `activity` → `planner`.
3. Update `Store.canvas`: courses, `items` (merged by id), `planner`, `seen` += fetched
   ids, `firstRunDone = true`.
4. Call `Notify.canvas` on `toNotify(...)`.
5. Fire `dueReminders`, then add their keys to `remindersSent`.
6. On `TokenRejected`: set `tokenRejected = true` and send one `problem` notification
   with the text "Canvas token rejected. Paste a new one in Settings".
7. On `IOException`: do nothing. The next tick retries.

- [ ] **Step 1: Implement the four files and the manifest entries.**
- [ ] **Step 2: Build and install on the emulator, with a real token in Secrets.**
  - Set the token through a temporary debug-only intent extra, or wait for Task 12.
  - Run `./gradlew installDebug`, then
    `adb shell cmd alarm` / `adb shell dumpsys alarm | grep framy`.
  - Expected: one framy alarm, about 10 min out.
- [ ] **Step 3: Force a tick.**
  Run `adb shell am broadcast -n app.framy/.TickReceiver`.
  Expected:
  - logcat shows the courses, activity and planner calls;
  - no notification on the first run;
  - a second broadcast after a new announcement notifies exactly once.
- [ ] **Step 4: Check the rejected-token path.**
  Set a bad token and broadcast. Expected: exactly one problem notification. A second
  broadcast posts no repeat.
- [ ] **Step 5: Commit:** `feat: notification channels and 10-minute tick`.

### Task 7: Mail logic (pure)

**Files:**
- Create: `app/src/main/java/app/framy/mail/MailLogic.kt`.
- Test: `app/src/test/java/app/framy/mail/MailLogicTest.kt`.

**Interfaces:**
- Produces, in `object MailLogic`:
  - `data class CursorStep(val fetchFromUid: Long? /* null = fetch nothing */, val resetTo: Long? /* set lastUid to this, no notify */)`.
  - `fun cursor(stateValidity: Long, stateLastUid: Long, serverValidity: Long, serverMaxUid: Long): CursorStep`
    - First run (`stateLastUid == -1`) or a validity change gives
      `CursorStep(null, serverMaxUid)`.
    - Otherwise, if `serverMaxUid > stateLastUid`, give
      `CursorStep(stateLastUid + 1, null)`; if not, `CursorStep(null, null)`.
  - `fun visible(items: List<MailItem>, hidden: Set<String>): List<MailItem>`.
    Matching is a case-insensitive exact address match.
  - `fun snippet(plain: String?, html: String?): String`
    - Uses the plain part if present, else strips the HTML (tags, `&nbsp;`, entities via
      a small map, collapsed whitespace).
    - First 120 chars.
  - `fun sender(personal: String?, address: String): String`, which returns `personal`
    when it's non-blank, else the address.

- [ ] **Step 1: Write failing tests.**
  - `cursorFirstRunNoBackfill`: `cursor(-1,-1,7,3000)` gives `CursorStep(null,3000)`.
  - `cursorValidityChangeResets`: `cursor(7,2990,8,15)` gives `CursorStep(null,15)`.
  - `cursorNormal`: `cursor(7,2990,7,3000)` gives `CursorStep(2991,null)`.
  - `hiddenCaseInsensitive`: hidden `promo@shop.example` hides `Promo@Shop.example`.
  - `htmlOnlySnippet`: `snippet(null, "<p>Hi&nbsp;<b>there</b></p>") == "Hi there"`.
    This is Review Focus 4.
  - `senderFallsBackToAddress`: `sender(null, "a@b.c") == "a@b.c"`.
  - Subject decoding is javax.mail's `getSubject()`. Add one check in Task 8's manual
    pass with a Bengali-subject email.
- [ ] **Step 2: Run them, check they fail, then implement.**
- [ ] **Step 3: Run `./gradlew testDebugUnitTest --tests '*MailLogicTest'`.**
  Expected: PASS.
- [ ] **Step 4: Commit:** `feat: mail cursor, hidden filter and snippets`.

### Task 8: Instant mail service (IMAP IDLE)

**Files:**
- Create: `app/src/main/java/app/framy/mail/MailService.kt`.
- Modify:
  - `Tick.kt` (add `MailService.renew(ctx)` as the first line of `run`), and
    `BootReceiver`.
  - `AndroidManifest.xml`:
    - `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE`;
    - `<service android:name=".mail.MailService" android:foregroundServiceType="specialUse">`
      with
      `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Keeps one read-only IMAP IDLE connection so new email notifies instantly"/>`.
  - `app/proguard-rules.pro`: keep `com.sun.mail.**`, `javax.mail.**`,
    `javax.activation.**`, and `-dontwarn` for the `java.awt.**` and `java.beans.**`
    references.

**Interfaces:**
- Consumes: `MailLogic` (Task 7), `Store.updateMail`, `Notify.mail`, `Notify.problem`,
  `Secrets` (`mailAddress`, `mailPassword`).
- Produces:
  - `MailService.start(ctx)`, `MailService.stop(ctx)`, `MailService.renew(ctx)`. All are
    no-ops if mail isn't configured.
  - `fun MailService.Companion.fetchBody(uid: Long): Pair<String /* text or html */, List<Pair<String, Long>> /* attachment name, size */>`
  - `fun MailService.Companion.saveAttachment(uid: Long, name: String, dest: File)`
  - These two open their own short read-only connection, used by the detail screen.

Loop, on one dedicated thread:
1. Connect `imaps` with these properties: `mail.imaps.connectiontimeout=20000`,
   `mail.imaps.timeout=0` (IDLE needs an unbounded read; renew breaks it), and
   `mail.imaps.ssl.checkserveridentity=true`.
2. Open `INBOX` with `Folder.READ_ONLY`.
3. Apply `MailLogic.cursor(...)` using `folder.uidValidity` and the UID of the last
   message.
4. Fetch `ENVELOPE` plus the text parts for UIDs from `fetchFromUid`, build
   `MailItem`s, and update the Store (the newest first).
5. Call `Notify.mail(visible(new, hidden))`, unless `resetTo` was set.
6. Set `live = true`, then call `folder.idle(true)` and loop back to step 3 when it
   returns.
7. Handle failures:
   - `AuthenticationFailedException`: set `authRejected = true`, send one problem
     notification ("Gmail rejected the app password. Update it in Settings"), stop
     the service.
   - Any other exception: set `live = false`, close quietly, and wait until `renew()`
     or the network-available callback signals.

`renew()` hands this to a helper thread: run `folder.getMessageCount()`, which interrupts
IDLE. If the call doesn't return within 30 s, `store.close()` so the loop reconnects.

- [ ] **Step 1: Implement the service, the manifest, the ProGuard rules and the
  Tick/Boot hooks.**
- [ ] **Step 2: Manual check on the emulator with the owner's own app password.**
  - Start the service. Expected: the "Live" state, and no backfill notifications.
  - Send yourself an email. Expected: a notification within 10 s.
  - Send one with a Bengali subject. Expected: readable.
  - Hide that sender in the store and send again. Expected: no notification.
- [ ] **Step 3: Check sleep behaviour.**
  Run `adb shell dumpsys deviceidle force-idle`, send an email, then
  `adb shell dumpsys deviceidle unforce`.
  Expected: the notification arrives, or arrives within one tick of waking.
  Record which in the commit message.
- [ ] **Step 4: Check offline recovery.**
  Turn airplane mode on for 2 min, then off. Expected: logcat shows no reconnect spin
  while offline, and it reconnects within seconds of the network returning.
- [ ] **Step 5: Commit:** `feat: instant read-only gmail via IMAP IDLE`.

### Task 9: App shell and Canvas Updates screen

**Files:**
- Modify: `ui/MainActivity.kt`.
- Create: `ui/CanvasScreen.kt` and `ui/DetailScreen.kt`.

**Interfaces:**
- Consumes: `Store.canvas`, `FramyTheme`, the course colour local, `Tick.run`,
  `CanvasApi.topic` and `conversation`.
- Produces:
  - `@Composable fun CanvasScreen(onOpen: (CanvasItem) -> Unit, onOpenPlanner: (PlannerItem) -> Unit, onOpenCourse: (Course) -> Unit)`
  - `@Composable fun CanvasDetail(item: CanvasItem, onBack: () -> Unit)`
  - `@Composable fun PlannerDetail(item: PlannerItem, onBack: () -> Unit)`
  - The local "Done" button adds the key to `done`.
  - Navigation is a tiny sealed `Screen` held in `rememberSaveable` state with
    `BackHandler`. No navigation library.

Layout, matching the approved mockup `m3-system-v2.html` exactly:
- Top bar: "Canvas", with refresh and settings icons.
- `SingleChoiceSegmentedButtonRow` with Updates and Courses.
- "Due soon": two-column cards with a course chip, the title, and the time left.
  "Tomorrow" or less uses `colorScheme.error`.
- "Today" and "Earlier": 72dp rows with a course-code avatar and an unread dot.
- `NavigationBar` with Canvas (badge = unread count) and Mail only.
- A chat-bubble `IconButton` in the top bar of both tabs opens `ChatScreen`. It has
  no badge and is deliberately low-key.
- Refresh runs `Tick.run` on `Dispatchers.IO` and shows a `LinearProgressIndicator`
  meanwhile.
- The detail screen renders HTML with `HtmlCompat.fromHtml(..., FROM_HTML_MODE_COMPACT)`
  with **no ImageGetter**, so no remote images load. Links are clickable through
  `LinkAnnotation`.
- Detail has "Open in Canvas", which starts an `ACTION_VIEW` intent.

- [ ] **Step 1: Implement.**
- [ ] **Step 2: Run on the emulator at 360×800 in light and dark mode, and screenshot.**
  Expected: it matches the mockup's spacing (16dp edges, 72dp rows, 80dp bar).
- [ ] **Step 3: Tap a notification.** Expected: it opens that item's detail.
- [ ] **Step 4: Write the emulator smoke test.**
  Create `app/src/androidTest/java/app/framy/SmokeTest.kt` with
  `launchAndOpenEveryScreen()`, using `createAndroidComposeRule<MainActivity>()`.
  Write it now and extend it in Tasks 10–12 and 14 as screens land, so that by
  Task 14 it covers every screen:
  - Fresh install: Settings shows. Press back and "Canvas" is displayed.
  - Tap the "Courses" segment, the "Mail" nav item, then the chat icon. "Global chat"
    is displayed.
  - With no credentials and an empty `CHAT_URL`, nothing crashes.
  - Run `./gradlew connectedDebugAndroidTest` on the local emulator (system image
    `android-36` is installed; create an AVD with `avdmanager` if none exists).
    Expected: PASS.
- [ ] **Step 5: Commit:** `feat: canvas updates screen and details`.

### Task 10: Courses, Modules, Files, downloads

**Files:**
- Create: `ui/CourseScreen.kt` and `Downloads.kt`.
- Modify: `ui/CanvasScreen.kt` (the Courses segment lists `Store.canvas.courses`).

**Interfaces:**
- Consumes: `CanvasApi.modules`, `folders`, `files`, `fileDownloadUrl`, and `Fail.Hidden`.
- Produces:
  - `@Composable fun CourseScreen(course: Course, onBack: () -> Unit)`
  - `object Downloads { fun enqueue(ctx: Context, fileId: Long, url: String, name: String) }`
    - It uses `DownloadManager.Request` with
      `setDestinationInExternalPublicDir(DIRECTORY_DOWNLOADS, "Framy/$name")`,
      `VISIBILITY_VISIBLE_NOTIFY_COMPLETED`, and **no Authorization header**.
    - It adds `fileId` to `Store.canvas.downloaded`.

Modules tab:
- Collapsible cards: week title, item count, and a chevron.
- File rows have a download button that becomes a tick once `fileId` is in
  `downloaded`.
- Page, Quiz, Assignment, ExternalUrl and Discussion rows have an "open externally"
  arrow that starts an `ACTION_VIEW` on `html_url`.

Files tab:
- A folder tree: tap a folder to drill in, and back to go up.
- On `Hidden`, show "Files are hidden for this course, check Modules".
- Both tabs keep the last result in memory for the session. There's no background
  polling.

- [ ] **Step 1: Implement.**
- [ ] **Step 2: Manual check on a real course.**
  - Download a PDF from Modules. Expected: the system download notification, the file
    in `Downloads/Framy/`, and the row tick.
  - Open the Files tab on a course with hidden files. Expected: the message, and no
    problem notification.
- [ ] **Step 3: Commit:** `feat: course modules, files and downloads`.

### Task 11: Mail screens and hide sender

**Files:**
- Create: `ui/MailScreen.kt`.
- Modify: `ui/DetailScreen.kt` (add `MailDetail`).

**Interfaces:**
- Consumes: `Store.mail`, `MailLogic.visible`, `MailService.fetchBody`,
  `MailService.saveAttachment`.
- Produces:
  - `@Composable fun MailScreen(onOpen: (MailItem) -> Unit)`
  - `@Composable fun MailDetail(item: MailItem, onBack: () -> Unit)`

Behaviour:
- Top bar: "Mail", a "Live" pill shown while `live`, and the same chat-bubble icon.
- Rows are grouped Today, Yesterday and Earlier, with unread rows in bold.
- Long-press opens a bottom sheet with "Hide mail from <address>". It adds the address
  to `hidden` and shows a snackbar "Hidden mail from <address>" with an Undo that
  removes it again.
- The detail screen fetches the body on open (plain text, or HTML via
  `HtmlCompat` with no ImageGetter).
- Attachment chips save to `cacheDir/att/` and open through a `FileProvider`
  `ACTION_VIEW`. Add the `<provider>` and `res/xml/file_paths.xml`.
- A `shortcut:` comment notes that the cache dir is cleared by the OS under storage
  pressure.

- [ ] **Step 1: Implement.**
- [ ] **Step 2: Manual check.**
  - Hide a sender. Expected: their rows disappear, and Undo restores them.
  - Open a PDF attachment. Expected: it opens in the system viewer.
- [ ] **Step 3: Commit:** `feat: mail list, detail and hide sender`.

### Task 12: Settings and first launch

**Files:**
- Create: `ui/SettingsScreen.kt`.
- Modify:
  - `ui/MainActivity.kt`: show Settings as the first screen when nothing is
    configured, and request `POST_NOTIFICATIONS` on API 33+.
  - Manifest: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

**Interfaces:**
- Consumes: `Secrets`, `Prefs`, `CanvasApi.validate`, `MailService.start`/`stop`,
  `Tick.arm`.
- Produces: `@Composable fun SettingsScreen(onBack: () -> Unit)`.

Sections, in this order:

1. **Canvas**
   - Base URL and token fields. The token field uses a password transformation.
   - Hint: "Canvas → Account → Settings → New access token".
   - Save validates with `GET /api/v1/users/self`. Errors show under the field.
   - Success clears `tokenRejected`.
2. **Gmail**
   - Address and app-password fields.
   - A link to `https://myaccount.google.com/apppasswords`, opened externally.
   - Save does a test IMAP login, then calls `MailService.start`. Success clears
     `authRejected`.
   - "Remove" clears the secrets and stops the service.
3. **Hidden senders**: tap one to unhide it.
4. **Appearance**
   - Colour: Wallpaper (only on API 31+) plus five swatches.
   - Mode: System, Light or Dark.
5. **Keep mail instant**: a button that fires
   `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. It shows a tick when
   `PowerManager.isIgnoringBatteryOptimizations` is true.
6. **Privacy**: fixed copy:
   > "Framy only reads. Your Canvas token and Gmail app password are encrypted on this phone and are sent only to Canvas and Gmail. The Global chat is the one place that sends anything, and only what you post."
7. **Version**: tapping it 7 times opens an admin-key field (`Secrets "adminKey"`).

- [ ] **Step 1: Implement.**
- [ ] **Step 2: Manual first-launch pass on a fresh emulator.**
  - Expected: the notification permission prompt appears, a bad token shows an inline
    error, and a good token plus a good app password land on the Canvas tab with
    "Live" on the Mail tab.
  - Check that the token is not in plain text:
    `adb shell run-as app.framy cat shared_prefs/secrets.xml` should show no plain
    token.
- [ ] **Step 3: Commit:** `feat: settings, onboarding and appearance`.

### Task 13: Global chat Worker

**Files:**
- Create in `chat-worker/`:
  - `package.json`: dependency `@neondatabase/serverless`, devDependency `wrangler`,
    script `"test": "node --test"`. Commit `package-lock.json`, because CI runs
    `npm ci`.
  - `wrangler.toml`.
  - `src/index.js`.
  - `test/index.test.js`.
  - `schema.sql`.
  - `README.md`.

**Interfaces:**
- Produces HTTP:
  - `GET /comments` returns `[{id,nick,tag,body,is_dev,created_at}]`, the newest 100.
  - `POST /comments` with `{nick,tag,body}` returns 201 `{id}`.
  - `DELETE /comments/:id` with header `X-Admin-Key` returns 204.
  - A `POST` with a valid `X-Admin-Key` sets `is_dev = true`.
- Produces JS: `export function handle(request, env, sql)`, which is testable with a
  fake `sql`. The `export default { fetch, scheduled }` wires the real `neon(env.DATABASE_URL)`.

Values:
- `body` is trimmed, 1–1000 chars. `nick` is trimmed, ≤ 30 chars, default
  "Anonymous". `tag` matches `/^[0-9a-f]{4}$/`. Anything else gives 400.
- Rate limit: Workers rate-limit binding `POST_LIMIT`, `{limit = 3, period = 60}`,
  keyed by `CF-Connecting-IP`. The spec said 5 per 10 min, but the binding only
  supports 10 s or 60 s periods.
- The IP is never stored.
- Cron `0 3 * * *` runs
  `DELETE FROM comments WHERE created_at < now() - interval '60 days' OR id NOT IN (SELECT id FROM comments ORDER BY id DESC LIMIT 1000)`.
- CORS isn't needed, because native clients don't send it.

- [ ] **Step 1: Write failing tests with a fake `sql` that records queries and a fake
  limiter.**
  - `emptyBodyRejected` → 400.
  - `oversizeBodyRejected` (1001 chars) → 400.
  - `badTagRejected` → 400.
  - `postOk` → 201, and the insert gets the trimmed values.
  - `deleteNeedsAdminKey` → 401 without it, 204 with it.
  - `adminPostIsDev` → the insert has `is_dev = true`.
  - `rateLimited` → 429.
  - `cleanupQuery`: `scheduled` issues the DELETE above.
- [ ] **Step 2: Run them and check they fail.**
  Run `cd chat-worker && npm i && npm test`. Expected: FAIL.
- [ ] **Step 3: Implement `src/index.js`, `schema.sql` and `wrangler.toml`.**
  - `wrangler.toml` sets the cron and the rate-limit binding.
  - Secrets `DATABASE_URL` and `ADMIN_KEY` are set with `wrangler secret put`.
- [ ] **Step 4: Run `npm test`.** Expected: all pass.
- [ ] **Step 5: Write `README.md` with five owner steps.**
  1. Create a Neon project and run `schema.sql`.
  2. Run `npx wrangler login`.
  3. Run `npx wrangler secret put DATABASE_URL`, then `ADMIN_KEY`.
  4. Run `npx wrangler deploy` and copy the URL.
  5. Add `framyChatUrl=<url>` to `~/.gradle/gradle.properties` and rebuild.
- [ ] **Step 6: Commit:** `feat: chat worker on cloudflare + neon`.

### Task 14: Global chat screen

**Files:**
- Create: `chat/ChatApi.kt` and `ui/ChatScreen.kt`.

**Interfaces:**
- Consumes: `BuildConfig.CHAT_URL`, `Prefs.nick`, `Prefs.tag`, and
  `Secrets.get("adminKey")`.
- Produces:
  - `object ChatApi`:
    - `data class Comment(val id: Long, val nick: String, val tag: String, val body: String, val isDev: Boolean, val createdAt: Long)`
    - `fun list(): List<Comment>`
    - `fun post(nick: String, tag: String, body: String, adminKey: String?): Long`
    - `fun delete(id: Long, adminKey: String)`
    - Timeouts are 10 s connect and 15 s read, because Neon may be waking up.
  - `@Composable fun ChatScreen()`

Behaviour:
- Load only when the tab opens. Show a shimmer meanwhile.
- The list is newest first, showing nick, `#tag`, relative time, a "Developer" badge
  when `isDev`, and the body.
- The compose box sits at the bottom, with a 1000-character counter and a Post button.
  Post is disabled while the box is empty.
- Delete icons appear only when an admin key is set.
- An empty `CHAT_URL` or any failure shows "Global chat is unavailable right now"
  with a Retry button. The rest of the app is unaffected.
- On 429, show "Slow down a little, try again in a minute".

- [ ] **Step 1: Implement.**
- [ ] **Step 2: Manual check against the deployed Worker.**
  - Post: it appears.
  - The fourth post within a minute shows the slow-down message.
  - In admin mode: the post shows the badge, and delete works.
  - Airplane mode shows the unavailable state, and the Canvas and Mail tabs still work.
- [ ] **Step 3: Commit:** `feat: global chat screen`.

### Task 15: Release build and full QA pass

**Files:**
- Modify: `app/build.gradle.kts` to add the release `signingConfig`.
  - It reads the Gradle properties `framyStoreFile`, `framyStorePassword`,
    `framyKeyAlias` and `framyKeyPassword`, locally from `~/.gradle/gradle.properties`
    and in CI from `ORG_GRADLE_PROJECT_*` env vars.
  - If `framyStoreFile` is unset, the release build stays unsigned instead of failing,
    so `assembleRelease` still works for contributors.
  - Never commit a keystore or a password.
- Modify: `app/proguard-rules.pro` if R8 strips anything the release run needs.

- [ ] **Step 1: Create the keystore.**
  The owner runs
  `keytool -genkeypair -v -keystore ~/framy-release.jks -alias framy -keyalg RSA -keysize 2048 -validity 10000`
  and adds the four properties.
  - **Back the keystore up somewhere safe.** If it's lost, existing installs can never
    be updated.
  - Then follow `docs/ci.md` to add the GitHub secrets.
- [ ] **Step 2: Build the release.**
  Run `./gradlew assembleRelease && ls -l app/build/outputs/apk/release/`.
  Expected: an APK under 5 MB.
- [ ] **Step 3: Run the full test suite.**
  Run `./gradlew testDebugUnitTest && (cd chat-worker && npm test)`.
  Expected: all pass.
- [ ] **Step 4: Run the spec's manual checklist on a real phone, using the release
  APK.**
  - login;
  - email within seconds with the screen off;
  - Canvas refresh;
  - a reminder (temporarily create a planner item due in 3 h, or use a test course);
  - airplane-mode recovery;
  - an overnight battery reading from Settings → Battery.
  - Record the results in `docs/qa-2026-11.md`.
- [ ] **Step 5: Check traffic stays receive-only.**
  With `adb shell dumpsys netstats` or a short `tcpdump` on the emulator, confirm the
  only hosts are the Canvas host, its file host on downloads, `imap.gmail.com`, and the
  Worker host (only after opening Global chat).
- [ ] **Step 6: Commit:** `chore: release signing and QA notes`.
