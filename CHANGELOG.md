# Changelog

All notable changes to Exhale are recorded here. Versions follow [semantic versioning](https://semver.org),
and the format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

Release notes for the build you are running are also bundled into the app itself, under
**Settings → Updates → Changelog**. Every later version's notes are pulled from this repository's
[GitHub releases](https://github.com/ozyern/Exhale/releases).

## [1.0.304] — 2026-09-26

The whole app in Apple Music's shape, music that plays without a network, updates that install
themselves, and a long list of things that had been quietly wrong.

### Added

**Offline, properly** — downloads that behave like files you own.

- Downloaded songs play with no network at all, and the queue skips to what is actually on the
  phone instead of stalling on the first track that is not.
- **Save to device** writes a tagged `.m4a` into `Music/Exhale` with its cover art and synced
  lyrics embedded, so the song is a file every other player on the phone can read.
- **Prefer lossless files** plays a FLAC, WAV or AIFF of the same song from your own storage
  instead of streaming it, when there is one. Settings → Player and audio.

**Updates without leaving the app.** Settings → Updates checks, downloads and hands the APK
straight to the installer, with real byte progress. The page states the pending release and the
build you are on; it no longer sends you to a browser to fetch a file by hand.

**The live artwork background from the desktop build**, on every page that has a subject — home,
library, settings, the player and the lyrics, drifting behind the glass rather than sitting still.

**App language (Beta)** — Settings → Appearance → Display. Twenty languages, named in their own
scripts, applied through Android's per-app locale API. Beta because several of those translations
are partial: choosing one gets a translated app with English where the strings have not landed.

**Spatial audio stages** — Natural, Wide and Cinema, with Cinema as the default. A phone has two
speakers, so the 5.1/7.1 switches other players show have nothing to drive; this is the width of
the stage, which is the thing they are reaching for.

**Music Together over a hotspot.** Two phones, one host, no router in between — a QR invite and a
screen built like the rest of the app rather than like a debug panel.

**A Sabrina Carpenter theme** — Settings → Appearance → Theme.

- Blush, butter and lilac over warm cream. In dark mode the surfaces go warm near-black rather
  than blue-grey.
- It is the only palette in the app that spends all four theme seeds. Every other entry sets
  primary, secondary, tertiary and neutral to one colour, which turns a four-seed system into an
  accent swap — the buttons change and the app underneath stays the grey it was. This one puts a
  separate warm seed on `neutral`, the seed Material builds every surface tier from, so the whole
  app moves.
- **Charms**: ribbon bows, hearts and four-point sparkles drifting *behind* the app, on the layer
  every frosted surface samples — so they are refracted through the top bar, the dock, the
  mini-player pill and every sheet. Vector paths, not images: no APK weight, sharp at any density.
- Colours and drawings only. Nothing shipped in the APK but four hex values and three vector paths.

**Small things that were missing.** Swipe a song left to queue it, right to play it next. A sync
pill on the lyrics that puts you back on the line being sung. Interlude dots that count an
instrumental down. Tempo and pitch, with semitones or a multiplier. A sleep timer with a countdown
dial. Hold the card on the About page and it turns over.

### Changed

**The Apple Music pass.** Home leads with Top Picks for You, learned from what you actually listen
to, then Recently Played, at Apple's type sizes and shelf rhythm. The queue, the menus, the artist
page, the charts, the account sheet, the search pages and every settings screen were rebuilt to
the same rules: grouped tables on glass plates, hairlines inset to the label, headers set quiet,
and a coloured glyph rather than a coloured tile on every row.

**Album and Downloads** open on their cover, edge to edge under the status bar, dissolving into
the page — then the title, one grey line of facts, and Play and Shuffle as glass capsules.

**About and Updates** are one release poster: the gold key art, the Exhale wordmark, the build, and
whether you are current. The update action sits under the facts rather than on top of the artwork.

**The player's panels** — equalizer, tempo and pitch, sleep timer and details — are the app's own
sliders, capsules and segmented controls instead of Material's, on the same plates as the settings
pages. The equalizer's section names moved outside their cards, its presets wrap so none is sliced
off, and its three effects became one group.

**Outputs** is an AirPlay-style picker that names devices the way their makers do — "OnePlus 13",
not "CPH2649" — with a volume bar you can drag.

**Option menus** open as iOS pull-downs: 44pt rows, 17pt labels, no rules between items, sized to
their longest option.

**Motion.** Tab changes are a 150ms fade with a hair of scale rather than a slide. A tapped
expansion of the player uses a softer spring than a flung one, because a tap starts from nothing
and has the whole screen to cover. The dock fades out over the first half of that expansion instead
of sliding under a player that has already arrived.

**Lyrics** use Linotte and the staggered line motion from the Windows build, and only the line
being sung reads the clock — the rest of the column no longer recomposes sixty times a second.

### Fixed

- **Songs stopped at about 0:29**, then resumed. The first range requested from the server was
  512 KB, which put a chunk boundary right there; a range's size does not gate playback start, so
  the small first chunk bought nothing and cost a stall.
- **Lyrics ran a line ahead** on fast songs. The highlight was given a fixed 450ms lead, which is
  most of a line when lines are a second apart. The lead is smaller now and capped at a quarter of
  the current line's own length.
- **Lyrics flickered on every line change.** Two causes: the lit copy of each word was added to and
  removed from the tree as it was sung, and the auto-scroll jumped the whole column instantly and
  caught it a frame later. Both are gone.
- **The artist's name was missing** in the mini player and the full player when a song was played
  from search rather than from an artist page.
- **The dock's "Mood & Genres" label** ran into the selection capsule. The labels are now fitted to
  the slot they sit in.
- **Opening Library crashed** on devices with a runtime shader: two in-content chips were sampling
  the backdrop they were drawn into.
- **The settings search field floated a mini player's height above the mini player**, having been
  lifted twice by the same inset.
- **System audio effects opened MusicFX** instead of the phone's own panel. Handlers are ranked
  now, vendor first.
- **Discord showed "Playing Exhale"** with a stranger's artwork. The presence is fixed to
  Listening, the song, the artist and the cover.
- **The Updates page could open without its title bar**, having inherited a collapsed app bar from
  the page that linked to it.
- The standard player slider never committed a seek. The update sheet offered older releases as
  updates. Treat network as metered defaults to off.

## [1.0.203] — 2026-09-05

The interface at your size, a library shaped like a library, a sleep timer you can read half
asleep, and glass that bends the pixels actually behind it.

### Added

**Interface scale** — Exhale's own copy of Android's Display Size, for this app only.

- Ten steps, a scale model of a real screen that redraws as you drag, and an explicit Apply, so a
  size you are still deciding on never becomes the size you have to read the Apply button at.
- Settings → Appearance → Display.

**App icon packs**

- Gold — the gold mark lit on black, and what the app now ships wearing — and Classic, the black
  mark on a gold tile.
- The pack also supplies the disc in the app bar and the mark the boot splash opens on, so the
  choice carries past the home screen instead of stopping there.
- Selected by enabling one of two activity-aliases, incoming first: a moment with no enabled
  launcher component is a moment some launchers use to forget the app exists. The icon still
  visibly disappears and comes back while it changes, which is inherent to the technique and is
  why the picker says so before it does it.

**A sleep timer you can reach**

- It lives in the player menu now, reachable from every player design, and counts songs as well as
  minutes.
- Every option carries the clock time it lands on, because "45 minutes" is a subtraction you should
  not have to do at midnight.
- The ring drains rather than fills. A timer that fills is measuring how long you have been awake.

**Refracting in-content glass**

- The round controls are cut from the search bar's own material. Press one and the pane deforms
  toward your finger, with a specular bloom tracking the touch.

**A welcome screen, once per update**

- The first launch after an update opens on the build number assembling itself out of a scattered
  star field — the same figure the announcement page is headed by — which holds, comes apart, and
  leaves the colour it explodes into behind.
- Then the word is written: a script authored as one unbroken cursive stroke and drawn on by arc
  length, rather than set in a face the phone may not have. `FontFamily.Cursive` resolves to
  Dancing Script on AOSP and to nothing at all on a good number of OEM builds.
- Shown once per update and never on a fresh install — that one is already being walked through
  Song Preferences, and being welcomed *back* to an app you have never opened is worse than not
  being welcomed. A tap skips ahead to the part worth seeing.

**exhale.ozyern.me has a release page** — a long-form announcement for each version, with the
build number rendered as a field of stars that assembles itself, keeps moving once it has landed,
and reads the same on a phone as on a laptop.

### Changed

**The Library, rebuilt.**

A large title, an inset list of destinations, pinned collections in the same list, and Recently
Added as an artwork grid. The filter chips became real pages with a working back gesture: a
selected chip and an unselected chip differed by a fill colour, which is not enough to say where
you are.

**The player menu is one level deep.**

Five round actions on top, one grouped list of places underneath. It used to be six nested
containers before it reached a verb.

**Lyrics keep up.**

Blur is forward-only, the word swell peaks when you hear it rather than after, and the scroll runs
220 ms ahead of the highlight so a line arrives at the anchor about when it lights.

**Updates finish inside the app.**

"Update Now" goes to the real transfer instead of handing you to the system browser, and the prompt
is a proper Software Update sheet with a decline action it never had.

**The page is lit rather than painted.**

The artwork glow drifts a lap every thirty-odd seconds. Home arrives shelf by shelf, the shortcut
tiles cascade, and the large title shrinks toward the compact one as it leaves.

**The dock changes shape like one object.**

The tab strip folds along its length into exactly the 64dp the home circle occupies, the circle
grows in place there, and the pill slides out from behind it. Nothing arrives from off-screen any
more, because nothing was ever off-screen. Both fades are matched linear ramps so the two sheets of
glass sum to one plate at every instant — an eased pair dips in the middle, and on a translucent
surface a dip in coverage is a flash of the page. The collapse threshold is a band rather than a
line, so the bar stops morphing back and forth wherever the thumb happens to rest.

**Home says what it is doing.**

The shortcut tiles show which of them is playing and take the same long-press menu as every other
row in the app. A feed that comes back empty is now a page that says so and offers your library,
rather than the word "Home" on a black sheet — which is what the app looked like on a plane.

### Fixed

**Downloads ran at about the speed of playback.**

`OkHttpDataSource` only sends a `Range` header when the DataSpec says where it ends, and the
download manager's does not — so every song was one open-ended GET, which is the shape googlevideo
serves slowly on purpose. Playback has bounded its requests all along; this path never did. It now
asks for the exact content length from the player response, five downloads run at once instead of
three, and each byte is written to disk once rather than twice.

**The Library tab could strand you on a sub-page.**

Opening Songs or Albums wrote the "default library page" setting, so browsing silently rewrote a
choice the user had made, the tab reopened there forever, and its own dock button could not get
back to the front page because a tap on the tab you are already on is a re-tap. Worse, the sub-view
arms a back handler: once that state survived across sessions, the back gesture from what looked
like the Library tab went up a level *inside* Library instead of home, on a screen that gave no
sign there was a level to go up from.

**A tap on Home could do nothing at all.**

The dock's tab strip folded "no tab is selected" (-1) into "Home is selected" (0) by clamping, so
on any route showing the dock without a selected tab, a tap on Home reported itself as a re-tap and
the host answered by scrolling the page instead of navigating.

**Settings bars cut the artwork off dead.**

They were a flat fill of the page colour, slicing the ambient wash along the bar's bottom edge.
Every settings page now takes the same ground as the page it sits on — About and the two
large-title pages that were still painting a black slab included, since a large title is most of
what you see when you open one.

**The player's seek bar was stuck.**

`LiquidSlider` watched a plain parameter through `snapshotFlow`, which reads no snapshot state and
so never re-emitted.

**"Update Now" crashed the app.**

The prompt was resolving `navigate()` against a `lateinit` property nothing ever assigned. It
compiled, it looked right, and it threw on press.

**The launcher icon was wrong under a square mask.**

Its background layer carried the source artwork's own edge vignette — invisible under a circle,
glaring under a square. The in-app mark was the unfixed copy.

### Removed

- Three quarters of the launch animation: two shockwave rings, a ten-degree entrance tilt and a
  diagonal sheen sweep. Four things competing for attention inside one second is what a splash
  looks like when it is trying to impress you.
- The duplicate playlist list. The Playlists tab already owned it, and that is the one with
  reordering.
- A second account circle, which briefly sat inside Home's large title directly under the app bar
  that already had one.

## [1.0.102] — 2026-08-26

First public release.

### Added

**Your Sound Chem** — a monthly listening capsule, replacing the old statistics table.

- Month-by-month only: the month is the page title, and a pair of chevrons steps through the months
  you have history for. The mode dropdown (Continuous / Weeks / Months / Years) and the period chip
  rail are gone — a capsule is a period, so the period is no longer something you configure.
- Total listening time counts up on every period change, with an hours line beneath the minutes.
- Top artist and top song as a two-up spotlight row, each tinted with its own accent.
- Share of listening: the dominant artist at poster size with their percentage, shown only when one
  artist is actually ≥5% of the month.
- Listening breakdown: the top five artists as proportional bars that grow from empty. This replaces
  a pie chart made of cropped artist photographs.
- Share button emits a plain-text summary of the month.

**Lock screen lyrics on OxygenOS / ColorOS ("Live Space")**

- Timed lyrics are published to the active media session under the OPlus `lyricInfo` key, so the
  lock screen's music focus mode shows real lyrics instead of "No lyrics".
- Lyrics already in the cache are attached to the next few queue items *before* they start, so the
  track's very first metadata push already carries them. This is what the OPlus spec asks for, and
  it avoids the debounce that eats a payload patched on a few milliseconds late.
- When lyrics only resolve after playback has started there is nowhere to hide, so the payload is
  forced through — at most twice, with the retry window the spec allows. Position updates,
  pause/resume and notification refreshes never rewrite it.
- Forcing works by also changing `requestMetadata.mediaUri`, the one field Media3's session layer
  compares that this app does not use. Both `ExoPlayerImpl` and `MediaSessionLegacyStub` decide
  whether metadata changed with `MediaMetadata.equals`, which compares `extras` only by whether it
  is null — so a change that lives *only* in a lyric payload is invisible to both, and every item
  here already carries extras. Without this the payload was written into a void.
- TTML lyrics are flattened to LRC rather than dropped. The word-synced providers return TTML, so
  the format the lyrics screen looks best with was the one the lock screen never saw.
- Word timings, where a provider supplies them, are published as `rawLyric` for ROMs that
  highlight per syllable.
- Unsynced lyrics are deliberately never published — the lock screen cannot scroll them, and a
  payload it cannot use displaces the fallback it would otherwise show.
- Manifest opt-in for the OPlus media-history stack, so the ColorOS media card survives the app
  being fully stopped.
- Toggle under **Settings → Content → Lock screen lyrics**. Inert on every other ROM.
  `adb logcat -s LiveLyrics` reports which path each track took.

**In-app updates**

- Updates download and install inside the app instead of handing you off to a browser: real byte
  progress, a spring-eased transfer bar, and a direct handoff to the system installer.
- Release notes for the installed build are compiled in, so they are readable offline, on first
  launch, and before any release exists on GitHub. They fold out of the changelog card and
  disappear once you are past the version they describe.
- Software Update page rebuilt: a breathing glass hero whose glyph spins while a check runs, a
  status line that slides between states, and three glass groups.

**Home**

- Spotify-style shortcut grid at the top: two columns of squat tiles, artwork flush to the left
  edge, six destinations on screen at once.
- Apple Music large title — the weekday over a heavy display-size heading, inside the scroll content
  so it passes under the compact chrome as you scroll.

**About**

- Hidden breathing pacer behind seven taps on the app mark: an orb that swells over four seconds
  and empties over six, inside a field of a hundred and fifty motes that the inhale draws in and
  the exhale throws back out. Three counter-rotating sweeps cross the field, a ring closes once per
  half-breath, and touching anywhere pushes a ripple out from your finger. The whole thing is one
  canvas driven by one clock read inside the draw pass, so a frame costs a draw and nothing else.
  The app mark visibly winds up from the fourth tap onward.

**Recommendations**

- `Accept-Language` is now sent on every Innertube request. The `hl`/`gl` in the JSON context were
  the only statement of language we made, and the surrounding Google frontend does not read them —
  which is how a request that said "English" could still be answered out of the caller's IP region.
- The cached visitor id is re-minted when the content language or country changes. It used to be
  minted once on first launch and kept forever, so a user who installed in one region and *then*
  chose a language kept introducing themselves with the identity from before the choice. Skipped
  when a cookie or poToken is present, since a poToken is bound to the visitor id it was made for.
- Picking a language during onboarding now always takes effect. Two different "unset" sentinels had
  been written to the content-language preference over the app's history, and onboarding only
  recognised one of them, so anyone who had ever chosen "System default" in Settings could pick a
  language in onboarding and have it silently ignored.
- The feed filter now recognises romanized titles. It could only judge a title by its script, which
  passes "Kesariya" and "Laung Laachi" at 100% Latin — the actual shape of the leak on an Indian
  connection, rather than an edge case. A deliberately small vocabulary of distinctive whole words
  now marks those. It narrows the leak; a one-word title that is simply a name stays
  unclassifiable from the client.

**Output picker** — rebuilt on the app's own frosted sheet, following the Connect-panel shape: the
device you are on is a hero card carrying its live bitrate, the track it is playing and its own
volume slider; the devices you are not on are a quiet list underneath. It replaces a plain Material
dialog, the last surface in the app that did not look like the app. It also stops pretending to be
able to switch outputs — Android routes audio system-wide, so the other-device rows open the system
picker rather than doing nothing convincingly.

**exhale.ozyern.me** — a site for the app.

- Built from the shipping build's own screenshots rather than a recreation, so it cannot drift from
  what the app looks like. The one part rebuilt in the browser is the dock, because that is the one
  part you can put your hands on: its capsule stretches into a move and overshoots before it
  settles, exactly as the real one does.
- The hero is a light ribbon drawn on canvas — five lens-shaped bands weaving through one another
  on periods that share no factors, so the color order keeps inverting and the shape never repeats
  where anyone can catch it. It stops drawing entirely when it scrolls off screen or is paused.
- The feature gallery is a rail of large cards that runs itself, and each card holds a working
  miniature of what it names — the equalizer card is an equalizer, the sleep-timer card is a dial —
  rather than an abstract wash with a caption on it. It stops when you stop it.
- The build facts are cards too, each showing the thing the number came out of: the three SDK
  levels, a Compose snippet, the licence header, the five ABI flavours, the current release. Every
  figure is read off `app/build.gradle.kts`, so the page cannot claim a build that is not shipping.
- Deployed to GitHub Pages from `website/` on any push that touches it.

### Changed

**Design system**

- New shared liquid-glass surface: gradient fill, diagonal sheen, hairline rim, and an optional
  accent tint. Used by the Sound Chem deck, the Updates page, the account sheet and About, so they
  can no longer drift apart.
- New animated aurora backdrop — three color blobs drifting on a 26-second cycle, driven entirely
  through `graphicsLayer` so a frame of drift costs no recomposition and no layout.

**Accounts sheet** — redesigned.

- One identity block instead of two. The sheet used to open with a large centred avatar and then
  repeat the same avatar, name and email in a card directly beneath it.
- The four things people open the sheet for — Sound Chem, History, Settings, Sign in/out — are now
  tiles under the identity card rather than rows scattered across three captioned groups.
- Groups are real glass rather than a flat 5% ink wash, which on a translucent sheet read as a grey
  smudge.

**Settings**

- A visible search field under the large title, replacing a magnifier in the app bar.
- Every group is labelled. The quick actions and integrations were previously bare cards with
  nothing naming them.
- Divider indent now derives from row geometry (16 + 36 + 14 = 66dp). At 60dp every hairline in
  Settings stopped 6dp short of the text it aligned to.
- Group headers sit 20dp from the screen edge instead of 36dp, where they lined up with nothing.
- Rows fill with a neutral grey on press instead of shrinking to 98% and tinting themselves with the
  brand color — that is button behaviour, and a grouped table is a list of destinations.
- The header plate is now a real destination: it opens About.

**About** — rebuilt as an inset grouped table on the same ground as the rest of Settings, replacing a
stack of elevated cards with shadows, shimmer sweeps and fake hover states.

- Maintainer credited as Aditya Jha, avatar served from the GitHub profile.
- Social links reduced to GitHub, Telegram and Instagram.
- App information group: version and build.

**Home**

- Section headers end in a small grey chevron rather than a full-size filled forward arrow.
- Keep Listening became the shortcut grid and moved to the top of the page.

**The dock is one material again.**

- State A and State B were drawing glass at different strengths, so the collapse read as a
  cross-fade between two panes rather than one pane changing shape. Both now take the same tint and
  blur, raised to stay legible over album art — the dock is the one surface that is never over a
  background of its own choosing.
- The A/B morph no longer animates the container at all. Every piece moves itself: the tab strip
  folds into its left edge, the home circle and the mini-player pill arrive from the outside of the
  bar, and the search circle — same place, same size in both states — simply stays put instead of
  cross-fading against itself.
- In the collapsed pill, artwork, title and artist change as one movement rather than three
  independent glitches on three different frames, and the play/pause glyph pops in from small so
  the command visibly takes.
- Circle presses went underdamped and stiff, to match the capsule's physics rather than sitting
  next to it with a lift button's.

**Sheets can be thrown away.**

- Every frosted sheet now asks the compositor to blur the whole screen behind its window. A sheet
  lives in its own window, where the dock's trick is unavailable; this is the same effect, done by
  the system. Where the platform refuses — low-end devices, battery saver, a developer setting —
  the surface falls back to the opacity it had before.
- Dim and blur ride the sheet's own position, so dragging it halfway down lightens the room by
  half rather than holding a hard scrim until the window is torn down.
- The grab handle is a control now: drag or flick it to dismiss, with the sheet springing back if
  you change your mind. It drew that affordance for months and did nothing with it.
- Sheets animate out. They used to rise on a spring and then vanish on a single frame.

**Spelling is American throughout.** "Colours follow the album art" is now "Colors", and the site
and these notes match — one spelling, everywhere the app writes a word.

**Releases are signed, named, and built by tag.** Pushing `v1.0.103` builds the app, signs it
with the release key and publishes it, with the notes taken from this file so the release page,
the site and the in-app changelog cannot disagree.

One APK per release — `Exhale-1.0.103.apk`, the universal build that runs on every
architecture. Splitting it four ways saved about a megabyte and cost a decision nobody outside
this repository can make: a download page that asks whether you are arm64, armeabi-v7a, x86 or
x86_64 is a page most people close. The filename carries the version, so a file sitting in a
Downloads folder says which build it is.

### Fixed

**Update notifications could point backwards.**

- The background check asked whether the newest release *differed* from the installed build,
  not whether it was newer. Anyone running a build ahead of the newest release — every
  developer, and anyone on a pre-release — was told to "update" to an older version, and any
  tag outside the 1.x line could trigger the same. Both the worker and the notification gate
  now use the same strictly-newer, same-major comparison the Updates screen already used.

- Release builds were never signed at all: the signing config existed but nothing referenced
  it, so `assembleRelease` produced an unsigned APK that no device would install.

- "Update check failed: no releases found" is gone. An empty release list is not a failure —
  it means there is nothing newer — so the Updates screen now says Exhale is up to date, which
  is what anyone who installed before the first release was seeing an error for.

**The bottom dock was never actually glass.**

- It refracted `LocalAppBackdrop`, which is `rememberDefaultBackdrop()` — an *empty* passthrough
  canvas. Blurring nothing produces nothing, so the only pixels the dock ever painted were its own
  34%-opacity tint film. That is the transparency people were seeing. The dock, the search capsule
  and the new player shelf now blur the real content layer, at half resolution so a blur running
  under a scrolling list stays cheap.

**Search stopped stuttering.**

- Suggestion requests are debounced. Every keystroke used to fire its own network round trip:
  `flatMapLatest` cancelled the previous collector but the request had already gone out, so typing
  a seven-letter query opened seven connections to discard six.
- The results list no longer blanks and reappears while you type. Two collectors were both writing
  the whole view state, and the history one rebuilt it from scratch — silently resetting
  suggestions and results to empty on every database emission.
- Opening the search overlay no longer re-lays-out the entire result list on every frame. The
  surface animates from the collapsed bar to full screen, and the list was measured against that
  animating height 60 times a second, starting from one pixel tall. It is now measured once at its
  final size and revealed by the growing window.
- The expansion runs on a critically-damped spring instead of a fixed 300ms tween.

**Max audio quality now means the best stream available, not the first one that answered.**

- The stream loop stopped at the first client that validated, so fidelity was decided by which
  client won the race. They genuinely differ: ANDROID_VR typically tops out at Opus itag 251
  (~160 kbps) while TVHTML5 and IOS are the ones that surface AAC itag 141 (~256 kbps). MAX now
  keeps probing and takes the best of everything it saw.
- Stated plainly: the stopping bar is set at 500 kbps and YouTube's music catalogue has nothing
  that high, so in practice this means "probe every client, keep the best". No client flag can
  conjure a bitrate the server does not hold. `adb logcat` reports the winning client and bitrate.

**Player transitions.**

- Expanding and collapsing no longer share one spring. Opening answers a tap and now arrives on a
  critically-damped 520-stiffness spring instead of taking ~600ms; collapsing keeps the softer
  curve, which is the direction the liquid tail is actually visible in.
- The shrinking player and the growing pill now cross-dissolve at a single shared point. Their
  fades used to disagree — the player was solid by 12% of travel while the pill lingered to 25% —
  so an eighth of every transition showed both at once, one drawn through the other.
- The vertical squash is gentler. At 18% the controls were folded like an accordion rather than
  compressed into the pill's band.

**The expanded player has liquid glass for the first time.**

- Every control surface on it was a flat alpha box over the cover, with a gradient scrim doing all
  the legibility work — it was the one screen not built out of the app's own material. The
  transport now sits on a real frosted shelf that blurs the artwork behind it, with a hairline rim
  and an interior sheen. The bottom scrim drops from 0.62 to 0.44 as a result, so the lower third
  of a cover is no longer crushed to near-black.

**Smaller.**

- Settings glyph plates are one shared definition instead of seven copies of the same flat 12%
  wash, and they now catch light: a vertical gradient with a hairline rim.
- The Updates page's pulsing hero no longer recomposes the whole header 60 times a second forever;
  the phase is read in the draw phase, and the loading glyph only spins while something is loading.
- The Updates notification switch is the app's glass toggle, not the last stock Material `Switch`.
- Grid artwork carries a soft drop shadow, so shelves read as covers lifted off the page.
- Home shortcut tiles and the account sheet's tiles are gradient plates with rims and the app's tap
  physics, rather than flat ink washes with a Material ripple washing across the glass.

**Some songs showed another song's lyrics.**

LRCLIB's search endpoint is fuzzy full-text, and the result was being chosen on duration alone —
so any track of about the same length could win. Matching now scores title and artist together and
refuses anything below a floor, with duration demoted to a tie-breaker.

**The Home screen could crash on open.**

A YouTube Music feed can return the same browse id for more than one section, and that id was the
row's key. A repeat is fatal to `LazyColumn` during measure, which took the whole screen down.
Repeats now get an occurrence suffix; first occurrences keep the key they always had, so an
ordinary append-only feed rebinds nothing.

### Removed

- Update channel picker. Only one channel is published, so the choice was between one real option
  and one that does not exist.
- The Development section (raw commit feed) from the Updates page — it answered a question nobody
  standing on an update screen is asking. Full history remains under Changelog.
- Contributors section from About.
- Website, PayPal and Facebook links.
- The pie chart of cropped artist photographs from the statistics screen.

[1.0.203]: https://github.com/ozyern/Exhale/releases/tag/v1.0.203
[1.0.102]: https://github.com/ozyern/Exhale/releases/tag/v1.0.102
