# Task 008 — adversarial break test — round 1 (static review)

## Method and its limits

**No physical device was available this round** (shared resource), so the
on-device half of the acceptance criteria — rapid/overlapping taps, rotation
spam, back-button mashing, `am send-trim-memory`, airplane-mode toggling,
killing the app mid-download, and any `logcat` excerpt — **was not
performed**. Nothing below is backed by a live repro; every finding is
derived from reading the code, and each one names the exact file:line so a
later round can drive the repro directly.

What *was* done instead, per the task's fallback instruction:

1. **Security review of the network-facing surfaces** named in the task:
   the YouTube stream-URL fetch path (`utils/YTPlayerUtils.kt`,
   `utils/cipher/`, `utils/potoken/`), the preset import path
   (`utils/preset/PresetStore.kt`), the Discord token path, the Listen
   Together client, the in-app updater, and every user-input-to-network
   entry point (deep links, search, URL paste).
2. **Secrets-in-logs sweep** — every `Timber`/`Log`/`println` call sitting
   near an auth/token/cookie variable.
3. **Bounds/size checks on parsed untrusted data** — preset import, backup
   restore, CSV/M3U playlist import, update-APK zip extraction.
4. **Robustness review for the coroutine race class already proven in this
   app** (`git show bb1049c5`): "launch one coroutine per item in a loop,
   write shared mutable state", plus unguarded index/null access in the
   navigation and state-restoration paths and missing try/catch around
   download file I/O.

Note on the preset importer specifically: per project memory
`preset-import-threat-model`, **no limit in `PresetStore.kt` was relaxed,
suggested to be relaxed, or touched**. The one preset finding below is a
*missing* check, and the recommendation tightens rather than loosens.

Findings are ordered by severity. Nothing was fixed — per this task's own
acceptance criteria, each real finding gets its own follow-up task file,
listed at the end.

---

## CRITICAL

### C1 — Zip Slip in backup restore chains into Java deserialization of attacker-controlled objects

**`app/src/main/kotlin/com/ozyern/exhale/viewmodels/BackupRestoreViewModel.kt:132-138`**

```kotlin
else -> if (entry.name.startsWith("$HOME_BACKGROUND_ZIP_DIR/")) {
    val fileName = entry.name.removePrefix("$HOME_BACKGROUND_ZIP_DIR/")
    context.filesDir.resolve(fileName).outputStream().use { outputStream ->
        inputStream.copyTo(outputStream)
    }
}
```

`fileName` is taken straight from the zip entry name with **no traversal
check, no canonical-path check, and no size cap**. An entry named
`home_background/../persistent_player_state.data` (or `../../databases/…`,
`../datastore/…`) passes the `startsWith` guard and then writes anywhere
`filesDir.resolve` can reach. This is textbook Zip Slip.

The same file's *sibling* importer — `utils/preset/PresetStore.kt:428-467` —
does this correctly: `isSafeEntryPath()` plus
`dest.canonicalPath.startsWith(canonicalRoot + File.separator)` plus entry
count/size caps. The backup importer has none of it.

**Why this is critical rather than merely high:** the app deserializes three
files out of `filesDir` with a raw `ObjectInputStream` and no
`setObjectInputFilter`:

- `app/src/main/kotlin/com/ozyern/exhale/playback/MusicService.kt:1048-1051`
  (`persistent_queue.data`)
- `app/src/main/kotlin/com/ozyern/exhale/playback/MusicService.kt:1080-1083`
  (`persistent_automix.data`)
- `app/src/main/kotlin/com/ozyern/exhale/playback/MusicService.kt:1102-1105`
  (`persistent_player_state.data`)

```kotlin
automixFile.inputStream().use { fis ->
    ObjectInputStream(fis).use { oos -> oos.readObject() as PersistQueue }
}
```

The `as PersistQueue` cast runs *after* `readObject()` has already
constructed the whole object graph, so a gadget chain in any class on the
app's classpath fires before the cast can reject anything. `restore()`
deletes only `PERSISTENT_QUEUE_FILE`
(`BackupRestoreViewModel.kt:154`) — `persistent_automix.data` and
`persistent_player_state.data` are left in place, so both remain reachable
targets for the traversal write.

Full chain: user is handed a `.zip` "backup" (chat, download, cloud share) →
Settings > Restore → traversal write plants a crafted
`persistent_automix.data` → `exitProcess(0)` at line 156 restarts the app →
`MusicService` deserializes attacker-controlled bytes on next playback start.

**Not verified on device.** Static read only.

Fix direction: apply the exact `isSafeEntryPath` + canonical-root check
`PresetStore.unpack` already uses, cap entry count and uncompressed bytes,
and replace `ObjectInputStream` on these three files with the app's existing
kotlinx-serialization JSON path (`playback/smart/AnalysisStore.kt:107` is the
in-repo precedent) — or at minimum install an `ObjectInputFilter` allowlist.

---

## HIGH

### H1 — Cleartext traffic permitted app-wide, in release builds

**`app/src/main/res/xml/network_security_config.xml:5`**

```xml
<!-- Allow cleartext traffic for Listen Together local servers -->
<base-config cleartextTrafficPermitted="true">
```

`base-config` applies to **every domain**, in every build type — the stated
justification ("Listen Together local servers") needs only localhost / private
ranges. Consequences that are real rather than theoretical, given the rest of
this report:

- **H2 below** (the updater) downloads and installs an APK with no scheme
  check and no signature verification. Cleartext makes an on-path downgrade
  of that download viable.
- `ListenTogetherClient.kt:467` explicitly accepts a saved `ws://` server URL,
  and `httpBase()` at line 476 rewrites it to `http://`. A user who types (or
  is socially engineered into typing) a plain `ws://` server gets the room
  code, username, and full listening activity sent in the clear — and the app
  offers no warning.
- Stream URLs returned inside the innertube `streamingData` response are never
  validated against a `googlevideo.com` allowlist before being handed to
  ExoPlayer (`utils/YTPlayerUtils.kt`, around 1190-1240 — `java.net.URL(streamUrl).host`
  at line 1215 is parsed only to *log* the host, never to check it).

Fix direction: `<base-config cleartextTrafficPermitted="false">` plus a
`<domain-config cleartextTrafficPermitted="true">` scoped to `localhost`,
`10.0.2.2`, and private ranges. The `<debug-overrides>` block below it is
correctly scoped and should stay as-is.

### H2 — Update APK is downloaded without scheme or signature verification, and extracted without bounds

**`app/src/main/kotlin/com/ozyern/exhale/exhale/updater/downloadmanager/UpdateDownloadWorker.kt:28`**

```kotlin
val url = URL(apkUrl)
val connection = url.openConnection() as HttpURLConnection
```

- **No scheme check.** `apkUrl` is a worker input string; nothing rejects
  `http://`. With H1 in force, a cleartext APK download is permitted.
- **No integrity check.** The downloaded file is never hashed, never checked
  against a signing certificate, never compared to an expected size. It goes
  straight to `DownloadNotificationManager.showDownloadComplete(...)` at line
  131, which builds an `ACTION_VIEW` install intent
  (`downloadmanager/downloadnotificationmanager.kt:180` and `:294`). The user
  gets a normal-looking "install update" prompt for whatever bytes arrived.
- **Unbounded zip extraction** at lines 94-105: `zis.copyTo(fos)` with no
  size cap and no entry cap. A zip bomb fills external storage. (The write
  target is a fixed name, so there is no traversal here — only exhaustion.)
- Line 54, `apkUrl.contains("nightly.link")`, is an unanchored substring
  match: `https://evil.example/?ref=nightly.link` selects the zip path.
- Line 56, `FileOutputStream(downloadFile)`, is not in a `use {}` block. On an
  `IOException` mid-write the outer `catch` at line 134 runs but the stream is
  never closed — a leaked fd per failed update attempt.

Fix direction: require `https`, pin the release host, verify the APK's
signing certificate (or a published SHA-256) before showing the install
notification, cap extraction size, and wrap the output stream in `use {}`.

### H3 — Auth cookie and Discord token are backed up off-device in plaintext

**`app/src/main/AndroidManifest.xml:54`** — `android:allowBackup="true"`,
with `app/src/main/res/xml/backup_rules.xml` and
`app/src/main/res/xml/data_extraction_rules.xml` excluding only
`./exoplayer`, `./download`, and `exoplayer_internal.db`.

Everything else in `filesDir` is therefore in scope for Google cloud backup,
device-transfer, and `adb backup` — including `filesDir/datastore/` which
holds, in plaintext:

- `DiscordTokenKey` (`constants/PreferenceKeys.kt:267`) — a full Discord user
  token, which is a complete account credential.
- the YouTube innertube cookie and `visitorData`/`dataSyncId` session state.
- `ListenTogetherSessionTokenKey`.

Neither the datastore directory nor the Room database is excluded. Fix
direction: exclude `domain="file" path="./datastore"` and the DB from both
rule files, or set `allowBackup="false"`, or move the credentials to
`EncryptedSharedPreferences`/Keystore.

---

## MEDIUM

### M1 — `exhale://listen` deep link joins a Listen Together room with no confirmation

**`app/src/main/kotlin/com/ozyern/exhale/MainActivity.kt:2346-2354`**, filter at
**`app/src/main/AndroidManifest.xml:117-125`**

```kotlin
val listenCode = uri.getQueryParameter("code")
    ?: uri.getQueryParameter("room")
    ?: uri.pathSegments.getOrNull(1)
val isListenLink = uri.pathSegments.firstOrNull() == "listen"
    || uri.host?.equals("listen", ignoreCase = true) == true
if (!listenCode.isNullOrBlank() && isListenLink) {
    val username = dataStore.get(ListenTogetherUsernameKey, "").ifBlank { "Guest" }
    listenTogetherManager.joinRoom(listenCode, username)
    return
}
```

The `exhale://` scheme is `BROWSABLE` with no App Links verification (the
`https` filter above it has `autoVerify="true"`; this one cannot). Any web
page, QR code, or installed app can fire
`exhale://listen/<attacker-code>` and the user is silently joined to an
attacker-controlled room — no prompt, no confirmation — after which their
username and every track they play are broadcast to that room's host.

Fix direction: show a confirmation sheet naming the room before `joinRoom`.

### M2 — Deep-link route interpolation can crash the app from any web page

**`app/src/main/kotlin/com/ozyern/exhale/MainActivity.kt:2373-2379`**

```kotlin
"browse" -> uri.lastPathSegment?.let { browseId ->
    navController.navigate("album/$browseId") { launchSingleTop = true }
}
"channel", "c" -> uri.lastPathSegment?.let { artistId ->
    navController.navigate("artist/$artistId") { launchSingleTop = true }
}
```

`browseId`/`artistId` go into the route string **unencoded**, unlike the
`search` branch two cases down (line 2383) which correctly wraps it in
`URLEncoder.encode`. `lastPathSegment` returns the *decoded* segment, so a
link ending in `%2F` yields a segment containing `/`, producing a route with
an extra path component that matches no destination.
`NavController.navigate(route)` throws `IllegalArgumentException` for an
unmatched route, and there is no try/catch here — it reaches `CrashHandler`.

Reachable from any page via the verified `music.youtube.com` App Link filter
(`AndroidManifest.xml:128-141`) and from any installed app via the exported
`MainActivity`. **Repro not run — no device.**

Fix direction: `URLEncoder.encode` these two the same way `search` already
does, and wrap `handleDeepLinkIntent`'s navigation in a `runCatching`.

### M3 — `Intent.EXTRA_TEXT` bypasses the manifest's host restrictions on deep links

**`app/src/main/kotlin/com/ozyern/exhale/MainActivity.kt:2341`**

```kotlin
val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
```

The manifest carefully restricts `intent.data` to youtube/youtu.be hosts and
the two Listen Together origins. The `EXTRA_TEXT` fallback has no such
constraint, and `handleDeepLinkIntent` dispatches purely on `pathSegments`
(line 2356) without ever checking `uri.host` for the playlist/browse/channel/
watch branches. Any installed app can `startActivity` against the exported
`MainActivity` with an arbitrary `EXTRA_TEXT` string and drive navigation,
playback, or M1's room join.

Fix direction: validate `uri.host` against the same allowlist the manifest
declares before dispatching, regardless of which field the URI came from.

### M4 — Preset player-icon file names reach the filesystem without the traversal guard the DIY path has

**`app/src/main/kotlin/com/ozyern/exhale/ui/player/customize/PlayerIcons.kt:114`**
and **`app/src/main/kotlin/com/ozyern/exhale/utils/preset/PresetStore.kt:257-259, 269-271`**

`PlayerIconSet.fromJson` takes the `"file"` string verbatim:

```kotlin
val file = entry.optString("file").takeIf { it.isNotEmpty() } ?: return@forEach
put(slot, PlayerIconOverride(file, entry.optBoolean("tint", false)))
```

`PresetStore.apply` then uses it as a path on both ends:

```kotlin
File(assetDir, override.fileName).takeIf { it.isFile }
    ?.copyTo(File(dest, override.fileName), overwrite = true)
```

Compare the DIY sticker path, which the same codebase *does* guard —
`DiySticker.fromJson` at `ui/player/customize/DiyLayout.kt:111` calls
`isSafeFileName(source)` with an explicit comment about exactly this risk.
`PresetStore.installAsset` guards it too (`PresetStore.kt:299`). The icon path
is the one place the guard is missing.

Practical exploitability is **limited**, and that is why this is Medium and
not High: the same attacker-controlled string is used for both source and
destination, so source and destination move together and an attacker cannot
freely choose both ends. `sanitiseAssets` also deletes any asset whose
basename is not in the referenced set, so a traversal name leaves no source
file to copy in the common case. It is still parsed untrusted data reaching a
filesystem sink with a guard the neighbouring code proves was intended.

Note: `PlayerIconStore.pruneOrphans` (`PlayerIcons.kt:145-152`) matches on
`it.name !in live`, so a traversal-shaped `fileName` never matches a real
basename and the prune deletes every icon the user had — a data-loss side
effect of the same missing validation.

**No limit in `PresetStore.kt` was relaxed.** The fix is to add
`DiySticker.isSafeFileName(...)` (or `PresetStore.isSafeEntryName`) to
`PlayerIconSet.fromJson`.

### M5 — Restore wipes the saved queue and force-kills the app even when the archive contained nothing

**`app/src/main/kotlin/com/ozyern/exhale/viewmodels/BackupRestoreViewModel.kt:145-156`**

```kotlin
if (!foundAny) {
    Timber.tag("RESTORE").w("No expected entries found in archive")
}
}
}
...
context.stopService(Intent(context, MusicService::class.java))
context.filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
context.startActivity(Intent(context, MainActivity::class.java))
exitProcess(0)
```

`foundAny` is computed and then never branched on. Picking the wrong file —
any zip, a renamed `.exhalepreset`, a corrupt download — stops playback,
deletes the persistent queue, and `exitProcess(0)`s the app, with no error
shown and nothing restored. Fix direction: `if (!foundAny)` → toast and
return before the teardown.

### M6 — `AddToPlaylistDialogOnline` still dismisses itself before its own work runs, and still increments shared state from N concurrent coroutines

**`app/src/main/kotlin/com/ozyern/exhale/ui/menu/AddToPlaylistDialogOnline.kt:239-302`**
(and the identical block at **`:310-370`**)

Commit `bb1049c5` fixed the `viewStateMap` clobbering in this file. Two
sibling instances of the same bug class survived it:

1. **The root-cause-#4 shape is still here.** `coroutineScope` is this
   composable's `rememberCoroutineScope()` (line 87). Line 240 calls
   `onDismiss()` — which the caller wires to hide this dialog — and *then*
   lines 244-300 launch one coroutine per song **on that same scope**. If
   `onDismiss()` removes the composable, the scope is cancelled, and every
   per-song `YouTube.search(...)` (a suspension point) dies. This is
   precisely the mechanism `automation/progress/004-playlist-add-songs-bug.md`
   documents, in the file that commit was editing.

2. **`songsIdx` is shared mutable state written from N concurrent coroutines**
   (lines 282, 286, 349, 353). `songsIdx += 1` is a non-atomic
   read-modify-write, and the completion test at line 289
   (`if (songsIdx.toInt() == songsTot.toInt() - 1) onProgressStart(false)`)
   tests for one exact value. Under concurrency that value can be skipped
   entirely, leaving `onProgressStart(false)` never called and the progress
   indicator stuck on forever. The percentage at line 292 is likewise
   computed from a torn counter.

**Not verified on device.** Fix direction: `coroutineScope.launch { songs.map { async { … } }.awaitAll(); onProgressStart(false); onDismiss() }` — collect the results, dismiss last.

### M7 — Debug builds write full poTokens and the account cookie into a user-shareable log file

**`app/src/main/kotlin/com/ozyern/exhale/utils/potoken/PoTokenWebView.kt:239`
and `:247`**

```kotlin
Timber.tag(TAG).d("Generated poToken (before decoding): identifier=$identifier poTokenU8=$poTokenU8")
Timber.tag(TAG).d("Generated poToken: identifier=$identifier poToken=$poToken")
```

**`app/src/main/kotlin/com/ozyern/exhale/App.kt:421` and `:425`**

```kotlin
Timber.d("forgetAccount: Before - cookie=${YouTube.cookie?.take(50)}, visitorData=…")
Timber.d("forgetAccount: After - cookie=${YouTube.cookie}, visitorData=${YouTube.visitorData}, dataSyncId=${YouTube.dataSyncId}")
```

Line 425 has no `take()` at all. Also `PoTokenWebView.kt:153` logs
`integrityToken.take(50)`.

**Partial mitigation, verified:** `App.kt:117-120` plants Timber trees only
under `if (BuildConfig.DEBUG)`, so with no tree planted these are no-ops in
release. The finding stands for debug builds, because the second planted tree
is `DebugLogs.tree(this)` — `utils/DebugLogs.kt:26` mirrors **every** line to
a file, and `DebugLogs.share()` at `:124-137` hands that file to an arbitrary
app through `ACTION_SEND`. A tester who taps "share debug log" to report a bug
ships their live Google session cookie and poTokens to whoever receives it.

Fix direction: redact in the log call sites rather than relying on build type
— these are exactly the strings that must never enter a shareable artifact.

---

## LOW

### L1 — `println` survives into release logcat, including a private-upload stream URL

**`app/src/main/kotlin/com/ozyern/exhale/utils/YTPlayerUtils.kt:1322`**

```kotlin
println("[PLAYBACK_DEBUG] SUCCESS: Got playback data for uploaded track - format=${format.mimeType}, streamUrl=${streamUrl?.take(100)}...")
```

`println` writes to `System.out`, which Android routes to logcat
unconditionally — it is not gated by `BuildConfig.DEBUG` the way the Timber
calls in M7 are. 27 such calls exist across 6 files (11 in `YTPlayerUtils.kt`
alone, plus `ui/player/Player.kt`, `viewmodels/OnlineSearchSuggestionViewModel.kt:95`,
`ui/player/Thumbnail.kt`, `eq/data/`). Severity is Low because the URL is
truncated to 100 characters, which cuts before the signature — but it is a
private-track URL logged on the release path.

Fix direction: convert to `Timber.tag(...).d(...)` so the debug gate applies.

### L2 — Remote YouTube player JS runs in a WebView with file-URL read access and a Java bridge

**`app/src/main/kotlin/com/ozyern/exhale/utils/cipher/CipherWebView.kt:39-45`
and `:186-195`**; same shape at
**`app/src/main/kotlin/com/ozyern/exhale/utils/sabr/EjsNTransformSolver.kt:118-124`**

```kotlin
settings.javaScriptEnabled = true
settings.allowFileAccess = true
settings.allowFileAccessFromFileURLs = true
settings.blockNetworkLoads = true
webView.addJavascriptInterface(this, JS_INTERFACE)
...
webView.loadDataWithBaseURL("file://${cacheDir.absolutePath}/", html, "text/html", "utf-8", null)
```

The page `<script src="player.js">` (line 186) loads YouTube's player
JavaScript, fetched from the network by `PlayerJsFetcher`, from a `file://`
base URL with `allowFileAccessFromFileURLs = true` — so that remote script can
XHR any file the app can read, and can call every `@JavascriptInterface`
method on the bridge.

**Mitigated in practice by `blockNetworkLoads = true`** on the same object,
which is why this is Low: the script has no route to send what it reads back
out. It is still a wider grant than the design needs.

Fix direction: inline the fetched player JS into the HTML string (it is
already in memory) and set `allowFileAccess = false` /
`allowFileAccessFromFileURLs = false`, or serve it through
`WebViewAssetLoader`.

### L3 — Discord login WebView accepts a token from any origin that fires a JS alert

**`app/src/main/kotlin/com/ozyern/exhale/ui/screens/settings/DiscordLoginScreen.kt:87-119`**

```kotlin
override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
    if (url.endsWith("/app")) { view.stopLoading(); view.loadUrl(JS_SNIPPET); … }
    return false          // every other navigation is allowed
}
...
override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
    if (message.isNotBlank() && message != "null" && message != "undefined") {
        discordTokenPref.value = message      // url is never checked
    }
```

`onJsAlert` receives the originating `url` as a parameter and ignores it. Any
page the WebView can reach — reachable because `shouldOverrideUrlLoading`
returns `false` for everything, and third-party cookies are enabled at line
79 for the 2FA flow — that calls `alert("…")` has its message stored as the
user's Discord token. Low because it requires steering the login WebView
off-origin first, but the check is one line and the parameter is already
there.

Fix direction: `if (!url.startsWith("https://discord.com/")) { result.confirm(); return true }`.

### L4 — Playlist/CSV/M3U import reads the whole file into memory with no size cap

**`app/src/main/kotlin/com/ozyern/exhale/viewmodels/BackupRestoreViewModel.kt:169, 199, 301`**

```kotlin
val lines = stream.bufferedReader().readLines()
```

Three separate importers `readLines()` an arbitrary user-picked URI with no
length check and no line-count cap. A large file OOMs the process. Each call
site is inside a `runCatching`, but `OutOfMemoryError` is an `Error`, not an
`Exception` — `runCatching` catches `Throwable` and so will swallow it, which
means the likely outcome is a silently failed import on a wedged heap rather
than a clean crash. The preset importer caps at 20 MB
(`PresetStore.kt:71`); these do not.

Fix direction: check `openAssetFileDescriptor(uri, "r")?.length` first, as
`PresetStore.import` (`:383-388`) already does, and stream line-by-line
instead of materialising the list.

### L5 — Preset-bundled fonts skip revalidation

**`app/src/main/kotlin/com/ozyern/exhale/utils/preset/PresetStore.kt:510`**

```kotlin
"ttf", "otf" -> Unit // Fonts are handed to the platform loader, not decoded here.
```

Every other asset in an imported preset is re-decoded and re-encoded by
`MediaImport`, or SVG-sanitised. Fonts are passed through and later handed to
the platform font loader, historically a rich parser-bug surface. The size cap
(`MAX_ENTRY_BYTES`, 25 MB) still applies.

This is documented as a deliberate choice and is genuinely hard to do better
without a font parser. Recording it as a known residual, not a defect.
**No existing preset limit should be relaxed here** — if anything is changed
it should be a tighter per-font byte cap.

### L6 — Over-broad FileProvider path

**`app/src/main/res/xml/provider_paths.xml:3-5`** — `<external-path
name="external_files" path="." />` exposes the entire external storage root
through the provider.

The provider itself is `android:exported="false"`
(`AndroidManifest.xml:383`), so only this app can mint URIs and grants are
per-URI, which is why this is Low rather than a live hole. It is still a
larger surface than the app's actual uses (debug logs in `cacheDir`, shared
presets in `cacheDir`, update APKs in the external files dir) require.

### L7 — Unbounded coroutine fan-out during composition in the lyrics romanizer

**`app/src/main/kotlin/com/ozyern/exhale/ui/component/Lyrics.kt:280-355`**
(and the plain-text branch at **`:368-390`**)

Inside `remember(lyrics, scope) { … }`, one `scope.launch` is started per
lyric line per enabled romanization language. A long lyric document starts
hundreds of concurrent coroutines from inside composition.

Unlike the `bb1049c5` bug, each launch writes to its **own**
`newEntry.romanizedTextFlow`, so there is no cross-item clobbering — with one
exception: lines 283 and 289 are two separate `if` statements (not
`else if`, unlike every branch after them), so a line detected as both
Japanese and Korean gets two coroutines racing on the same flow, last writer
wins. Cosmetic, but it is the same class.

---

## Explicitly checked and found clean

Recording these so a later round does not re-derive them:

- **`PresetStore.unpack` (`:428-467`)** — entry-name allowlist, canonical-root
  containment check, entry count cap, per-entry and total uncompressed byte
  caps, archive size pre-check, manifest type-vs-declared-type check, asset
  re-encoding through `MediaImport`, orphan deletion. This is the model the
  backup importer (C1) should copy.
- **`DiyLayout.fromJson` / `DiySticker.fromJson`
  (`ui/player/customize/DiyLayout.kt:104-158`)** — sticker count capped at
  `DIY_MAX_STICKERS`, file names guarded by `isSafeFileName`, every numeric
  field `coerceIn`'d. Correct.
- **`MusicService` persistence writes (`:3664-3695`)** and
  **`playback/smart/AnalysisStore.kt:104-110`** — all file I/O in
  `runCatching { … use { … } }`, atomic write-aside-and-rename. Correct.
  (The *read* side is C1's deserialization problem, not an I/O problem.)
- **`loadM3UOnline` (`BackupRestoreViewModel.kt:302`)** — `lines.first()` on
  an empty file throws `NoSuchElementException`, but it is inside the
  `runCatching` at line 299 and degrades to an empty list. Not a crash.
- **`YTPlayerUtils` stream URL logging (`:1215-1216`)** — logs host and
  poToken *length* only, not the token. Correct.
- **`PoTokenGenerator` (`:74, :96`)** — consistently `take(20)`-truncates.
  Correct.
- **`<debug-overrides>` in `network_security_config.xml:10-18`** — correctly
  scoped and correctly commented; only honoured for debuggable builds.
- **`ListenTogetherActionReceiver`** — `android:exported="false"`
  (`AndroidManifest.xml:424-426`), so its unvalidated `getStringExtra` reads
  are not an external surface.

---

## Not covered this round

- Every live-device acceptance criterion (tap/rotation/back-button fuzzing,
  trim-memory, airplane mode, kill-mid-download, ANR and stuck-state hunting,
  `logcat` excerpts). **These still need a device round.**
- Tidal (`utils/tidal/TidalService.kt`) and Saavn were not reviewed in depth.
  Saavn has no module under `app/` on this branch — the only hits are in a
  detached worktree (`.claude/worktrees/functional-beaming-matsumoto/jiosaavn/`)
  and Gradle cache artifacts, so there is nothing on this branch to probe.
- The Discord *bot* (Hermes/ExhaleBot) integration — only the in-app Discord
  RPC token path was reviewed.
- `utils/potoken/PoTokenWebView.kt`'s JS bridge was read for logging only, not
  audited as an attack surface the way `CipherWebView` was.

---

## Follow-up task files to generate

Named per this task's acceptance criteria — one per real finding, to be
looped worker/verifier independently. None of these were fixed here.

| File | Goal |
|---|---|
| `automation/tasks/009-fix-backup-restore-zip-slip.md` | Add traversal + canonical-root + size/count checks to `BackupRestoreViewModel.restore` using `PresetStore.unpack`'s existing pattern, and replace the three `ObjectInputStream` reads in `MusicService` with a non-deserializing format. |
| `automation/tasks/010-fix-cleartext-traffic-config.md` | Scope `cleartextTrafficPermitted` to localhost/private ranges instead of `base-config`, and reject `ws://` Listen Together URLs (or warn) at `ListenTogetherClient.getServerUrl`. |
| `automation/tasks/011-fix-updater-apk-verification.md` | Require `https`, pin the release host, verify the downloaded APK's signing certificate before the install prompt, cap zip extraction, and close the output stream in `use {}`. |
| `automation/tasks/012-fix-credential-backup-exposure.md` | Exclude `filesDir/datastore` and the Room DB from `backup_rules.xml` / `data_extraction_rules.xml`, or move the Discord token and innertube cookie into Keystore-backed storage. |
| `automation/tasks/013-fix-deep-link-hardening.md` | Confirm before `joinRoom` on a `exhale://listen` link, URL-encode the `browse`/`channel` route segments, wrap `handleDeepLinkIntent` navigation in `runCatching`, and host-validate URIs that arrive via `EXTRA_TEXT`. |
| `automation/tasks/014-fix-preset-icon-filename-validation.md` | Apply `isSafeFileName` to `PlayerIconSet.fromJson`'s `"file"` field, matching the guard `DiySticker.fromJson` already has. Tighten only — relax no existing preset limit. |
| `automation/tasks/015-fix-restore-no-valid-entries.md` | Branch on `foundAny` before stopping the service, deleting the persistent queue, and calling `exitProcess(0)`. |
| `automation/tasks/016-fix-addtoplaylist-online-race.md` | Await the per-song coroutines (`async`/`awaitAll`) before dismissing, move `onDismiss()` to after completion, and replace the shared `songsIdx` counter — both blocks in `AddToPlaylistDialogOnline.kt`. |
| `automation/tasks/017-fix-token-redaction-in-logs.md` | Redact poTokens, the innertube cookie, `visitorData`, and `dataSyncId` at their log call sites (`PoTokenWebView.kt:153/239/247`, `App.kt:421/425`) so they cannot reach a shared `DebugLogs` file; convert release-surviving `println` calls to `Timber`. |
| `automation/tasks/018-fix-import-size-caps.md` | Cap file size before `readLines()` in the CSV and M3U importers, reusing `PresetStore.import`'s `openAssetFileDescriptor` length pre-check. |
| `automation/tasks/019-fix-cipher-webview-file-access.md` | Inline the fetched player JS instead of loading it from a `file://` base URL, and turn off `allowFileAccess` / `allowFileAccessFromFileURLs` in `CipherWebView` and `EjsNTransformSolver`. |
| `automation/tasks/020-fix-discord-webview-origin-check.md` | Check the `url` parameter's origin in `onJsAlert` before writing `discordTokenPref`. |
| `automation/tasks/021-device-fuzzing-round.md` | Run the live-device half of 008 that this round could not: tap/rotation/back-button fuzzing, trim-memory, airplane mode, kill-mid-download, with `logcat` evidence. Re-verify M2 and M6 as on-device repros. |

## Status

Round 1 complete for the static-review half; **the device half of the
acceptance criteria is outstanding**, so this task must not be marked done on
the strength of this report alone. 20 findings: 1 critical, 3 high, 7 medium,
7 low, plus 8 surfaces confirmed clean. Nothing was fixed and nothing was
committed, per this task's report-only criteria.
