/*
 * Everything the page says, in one place.
 *
 * Nothing here is aspirational. Every feature below is a string, a module or a
 * settings screen that exists today (strings.xml, kizzy/, lastfm/, lrclib/,
 * innertube/). Shipped goes here; planned doesn't.
 */

export const REPO = 'https://github.com/ozyern/exhale'
export const RELEASES = `${REPO}/releases/latest`
export const APK_URL = 'https://github.com/ozyern/exhale/releases/download/v1.0.506/Exhale-1.0.506.apk'
export const WINDOWS_URL = 'https://github.com/ozyern/exhale/releases/download/v1.0.506/Exhale-Setup-1.0.506.exe'
export const VERSION = '1.0.506'

/**
 * Palettes. `rose` is the app's DefaultThemeColor; the other two are what
 * the drifting backdrop mixes with.
 */
export const ALBUMS = [
  {
    id: 'exhale',
    title: 'Slow Exhale',
    artist: 'Nightwork',
    rose: '#ed5564',
    ember: '#ff8a6b',
    iris: '#7b6cff',
  },
  {
    id: 'blue',
    title: 'Blue Hour',
    artist: 'Marin',
    rose: '#4a8bff',
    ember: '#3ad6c4',
    iris: '#6c5cff',
  },
  {
    id: 'citrus',
    title: 'Citrus, Later',
    artist: 'Ovoid',
    rose: '#f5a524',
    ember: '#ffd166',
    iris: '#ff6b6b',
  },
  {
    id: 'moss',
    title: 'Moss Garden',
    artist: 'Kalyna',
    rose: '#46bf7b',
    ember: '#a8e063',
    iris: '#2fa3a0',
  },
]

export const artFor = (album, angle = 145) =>
  `linear-gradient(${angle}deg, ${album.ember} 0%, ${album.rose} 46%, ${album.iris} 100%)`

/** The app's own tagline, off its About screen. */
export const TAGLINE = 'Music, exhaled.'

export const MAINTAINER = { name: 'Aditya Jha', role: 'Lead developer', handle: '@ozyern' }
export const TELEGRAM = 'https://t.me/ozyern'
/** The project's announcement channel: releases, test builds and news. */
export const TELEGRAM_CHANNEL = 'https://t.me/exhalemusic'

/**
 * Real captures of the shipping build, in the order the hero cycles them.
 * Alt text says what each screen does, not what it looks like.
 */
export const SHOTS = [
  { src: '/shots/home.jpg', alt: 'The Home screen: rows of recommendations under a liquid-glass dock.' },
  { src: '/shots/player.jpg', alt: 'The full-screen player, tinted by the cover art behind it.' },
  { src: '/shots/lyrics.jpg', alt: 'Synced lyrics, with the current line lit and the rest falling away.' },
  { src: '/shots/artist.jpg', alt: 'An artist page: portrait, biography, and top songs.' },
  { src: '/shots/about.jpg', alt: 'The About screen, showing version and build architecture.' },
]

export const SHOT = { home: 0, player: 1, lyrics: 2, artist: 3, about: 4 }

/**
 * Captures from the 1.0.203 build, kept apart from [SHOTS].
 *
 * The gallery on the landing page is about the app; these are about a release, and they date the
 * moment they stop being the current one. Keeping them in their own list means a future release
 * adds a set rather than editing the app's own screenshots out from under the home page.
 */
export const SHOTS_203 = [
  { src: '/shots/203-home.jpg', alt: 'Home in 1.0.203: the large title, shortcut tiles, and the dock as a tab strip.' },
  { src: '/shots/203-about.jpg', alt: 'The About screen in 1.0.203, carrying the artwork color through the large title.' },
  { src: '/shots/203-menu.jpg', alt: 'The rebuilt player menu: five round actions over one grouped list.' },
]

export const SHOT203 = { home: 0, about: 1, menu: 2 }

/**
 * Captures from the 1.0.304 build.
 *
 * Same rule as the 203 set: a release's screenshots belong to that release. The landing page's
 * own shots are replaced when the app changes; these stay as the record of what this version
 * looked like on the day it shipped.
 */
export const SHOTS_304 = [
  { src: '/shots/304-home.jpg', alt: 'Home in 1.0.304: Top Picks for You, learned from what you listen to, over the glass dock.' },
  { src: '/shots/304-player.jpg', alt: 'The player in 1.0.304, the cover filling the screen behind the controls.' },
  { src: '/shots/304-lyrics.jpg', alt: 'Lyrics lighting word by word, the lines further away falling out of focus.' },
  { src: '/shots/304-downloads.jpg', alt: 'Downloaded songs: the cover edge to edge, then Play and Shuffle as glass capsules.' },
  { src: '/shots/304-settings.jpg', alt: 'Settings as grouped glass tables with coloured glyphs on every row.' },
  { src: '/shots/304-about.jpg', alt: 'About: the gold release poster, the Exhale wordmark and the build you are on.' },
]

export const SHOT304 = { home: 0, player: 1, lyrics: 2, downloads: 3, settings: 4, about: 5 }

/** Screen capture, re-encoded for the web: 30fps, no audio track, ~230KB. */
export const LYRICS_VIDEO = {
  src: '/media/lyrics.mp4',
  poster: '/media/lyrics-poster.jpg',
}

/** Placeholder words, written for the demo — nobody's lyrics but ours. */
export const LYRICS = [
  'and the room goes quiet',
  'the way a room does',
  'when the light gets low enough to lean on',
  'I can hear it coming back',
  'slow, and then all at once',
  'hold the note until it lets go',
  'one more turn around the dark',
  'breathe out',
]

/**
 * The grid under the tour: everything that is real but is not a headline.
 *
 * One line each, because a feature that needs a paragraph to explain is a
 * feature that should have been a tile above. `glyph` names a drawing in
 * `Features.jsx`. Only what ships in the current build goes here.
 */
export const FEATURES = [
  { glyph: 'update', title: 'Updates itself', body: 'Checks, downloads and installs new builds without a browser.' },
  { glyph: 'lossless', title: 'Your lossless files', body: 'A FLAC, WAV or AIFF you already own plays instead of the stream.' },
  { glyph: 'save', title: 'Save to device', body: 'A tagged .m4a with cover art and synced lyrics, for any player.' },
  { glyph: 'spatial', title: 'Spatial stages', body: 'Natural, Wide and Cinema — the width of the stage, on two speakers.' },
  { glyph: 'eq', title: 'Equalizer', body: 'Full-band EQ and presets, with tempo and pitch that stay put.' },
  { glyph: 'moon', title: 'Sleep timer', body: 'A countdown dial, in minutes or in songs, that fades rather than cuts.' },
  { glyph: 'together', title: 'Music Together', body: 'Everyone hears the same second — now over a phone hotspot too.' },
  { glyph: 'car', title: 'Android Auto', body: 'Liked songs, downloads and playlists on the dashboard.' },
  { glyph: 'scrobble', title: 'Last.fm and ListenBrainz', body: 'Scrobble to either or both, so your history stays yours.' },
  { glyph: 'discord', title: 'Discord presence', body: 'Listening to the song, the artist and the real cover.' },
  { glyph: 'language', title: 'Twenty languages', body: 'Per-app language, named in each one’s own script. Beta.' },
  { glyph: 'backup', title: 'Backup and restore', body: 'Library, playlists and settings to a file and back again.' },
]

/**
 * The open-source half of the download panel. Numbers off
 * app/build.gradle.kts; if the build changes and this doesn't, the page is
 * wrong.
 */
export const OPEN_FACTS = [
  { value: 'Android 13+', body: 'compileSdk 37, targetSdk 36, minSdk 33' },
  { value: '100% Kotlin', body: 'Jetpack Compose, not one XML layout' },
  { value: 'GPL-3.0', body: 'Read it, build it, fork it, pass it on' },
  { value: 'One APK', body: 'arm64, arm32 and x86 in a single file' },
]

/** What's worth knowing before tapping Download. Read off the release. */
export const ABOUT_FACTS = [
  { label: 'Version', value: VERSION },
  { label: 'Architecture', value: 'Universal' },
  { label: 'Size', value: '41 MB' },
  { label: 'Requires', value: 'Android 13' },
]

/**
 * The release notes, written for someone deciding whether to update.
 *
 * CHANGELOG.md is the same release said properly — every fix, with the reason
 * it broke. That is the right document for anyone reading the diff and the
 * wrong one for anyone standing at a download button, so this is the short
 * version: four groups, a sentence each. Newest first; the sheet reads
 * `RELEASE_NOTES[0]` as current.
 */
export const RELEASE_NOTES = [
  {
    version: '1.0.405',
    date: 'October 11, 2026',
    tag: 'Current release',
    summary:
      'Ultra fluid. Knows your music. Liquid glass in every page, a player that flies out of the dock, music videos over the song’s own sound, and a search that finds a song from a link or a line of it.',
    groups: [
      {
        title: 'New',
        items: [
          {
            title: 'Music video in the player',
            body: 'The artist’s own video, picked over fan uploads, plays over the song’s own stream, with a full-screen landscape player in liquid glass.',
          },
          {
            title: 'Horizontal lyrics',
            body: 'Turn the phone on its side from the lyrics’ own buttons: the cover large on the left, the lyrics lighting a word at a time on the right.',
          },
          {
            title: 'Search that knows your music',
            body: 'Paste a Spotify, Apple Music, Deezer, Tidal, SoundCloud or YouTube link into search, or type a line of a song to find it by its lyrics.',
          },
          {
            title: 'Artist pages, rebuilt',
            body: 'Apple Music’s photograph and title logo for the artist, the latest release on its own card, twenty top songs, and the story at the end of the page.',
          },
          {
            title: '8D audio & Sound presets',
            body: '8D audio that passes behind your head, and one-tap Sound presets: Sped up, Nightcore, Slowed, Daycore and Vaporwave.',
          },
          {
            title: 'Chromecast',
            body: 'Cast to the big screen straight from the device sheet with full playback, seek and volume control.',
          },
          {
            title: 'Offline mode',
            body: 'With no internet, the app automatically switches to what is saved on the phone, ready to play and shuffle.',
          },
          {
            title: 'Settings search',
            body: 'Search lands on the setting itself — even below the fold — and lights the row so the eye goes straight to it.',
          },
          {
            title: 'Jelly dock style',
            body: 'A fluid dock with labels, compact and touch-glow options that stretches between tabs and folds as you scroll.',
          },
          {
            title: 'AMOLED black theme',
            body: 'A true black theme tailored for OLED displays across every screen and surface.',
          },
          {
            title: 'Always-on display lyrics',
            body: 'Word-by-word synchronized lyrics displayed cleanly on the always-on display.',
          },
          {
            title: 'Voice control',
            body: '“Play … on Exhale” from Google Gemini or Google Assistant finds the song, artist, album or playlist.',
          },
        ],
      },
      {
        title: 'Improved',
        items: [
          {
            title: 'Liquid glass everywhere',
            body: 'Real refracting liquid glass in pages, not only the dock; the mini player and search bar are clear glass too.',
          },
          {
            title: 'The dock folds as you scroll',
            body: 'The tab strip folds along its length into a circle as you scroll and unfolds back into the bar, the active tab flying smoothly between them.',
          },
          {
            title: 'The cover flies from the dock',
            body: 'It lifts out of the mini player on a curve while the player rises in behind it, and flies home when you close it.',
          },
          {
            title: 'The Library, redesigned',
            body: 'A Liked hero card with Play, glass collection tiles, and one destination list for pinned and recent music.',
          },
          {
            title: 'Under a second to start',
            body: 'Songs you have never played before start in under a second, at the best quality available.',
          },
          {
            title: 'Menus grow from touch',
            body: 'Long-press menus open out of the held point over a blurred page; pop-ups grow directly from their button.',
          },
          {
            title: 'Moving artwork at 30 fps',
            body: 'The ambient artwork runs at 30 frames a second and only while music plays, sleeping when playback pauses.',
          },
          {
            title: 'Mini player progress ring',
            body: 'The mini player is the same slim size on every tab, with play and pause hosted inside a ring tracking song progress.',
          },
        ],
      },
      {
        title: 'Fixed',
        items: [
          {
            title: 'Queue was never saved',
            body: 'The saved queue was never actually written to disk. Now it is, so after Android closes the app your song is still there at the second you left it.',
          },
          {
            title: 'Crash on resume',
            body: 'Resuming a song no longer crashes on a spring animation that dipped below zero, and streams with missing codecs no longer fail.',
          },
          {
            title: 'Songs sat at 00:00',
            body: 'Resolved issues where playback sat at 00:00 or stopped in runs when stream sessions expired.',
          },
          {
            title: 'Official video prioritized',
            body: 'The artist’s own official video is picked over fan lyric videos, and landscape video no longer drops out on exit.',
          },
          {
            title: 'Lyrics sync and drift',
            body: 'Fixed lyrics running ahead, reading lines twice, or stopping loading after a single provider failed.',
          },
          {
            title: 'Pasted link search error',
            body: 'A pasted song or album link no longer displays “No results found” under its own resolved match.',
          },
          {
            title: 'Black band across inner pages',
            body: 'Eliminated an unwanted black strip across the top of inner screens under the status bar.',
          },
          {
            title: 'Namesake lyrics mismatch',
            body: 'Lyrics sources must now match both title and artist, preventing incorrect lyrics from another artist being cached.',
          },
          {
            title: 'Automix tempo stretch',
            body: 'Automix tempo adjustment is now strictly capped within 6% instead of stretching a song by up to 15%.',
          },
        ],
      },
    ],
  },
  {
    version: '1.0.304',
    date: 'September 26, 2026',
    tag: 'Previous release',
    summary:
      'The whole app in Apple Music’s shape, music that plays without a network, updates that install themselves, and a long list of things that had been quietly wrong.',
    groups: [
      {
        title: 'New',
        items: [
          {
            title: 'Offline, properly',
            body: 'Downloaded songs play with no network at all, and the queue skips to what is actually on the phone instead of stalling on the first track that is not.',
          },
          {
            title: 'Save to device',
            body: 'A tagged .m4a in Music/Exhale, with its cover art and synced lyrics embedded, so the song is a file every other player on the phone can read.',
          },
          {
            title: 'Prefer lossless files',
            body: 'When a FLAC, WAV or AIFF of the same song is already on your storage, Exhale plays that instead of the stream. Settings → Player and audio.',
          },
          {
            title: 'Updates without leaving the app',
            body: 'Settings → Updates checks, downloads and hands the APK straight to the installer, with real byte progress. No browser, no fetching a file by hand.',
          },
          {
            title: 'Spatial audio stages',
            body: 'Natural, Wide and Cinema, with Cinema as the default. A phone has two speakers, so this is the width of the stage rather than a 5.1 switch with nothing to drive.',
          },
          {
            title: 'App language (Beta)',
            body: 'Twenty languages, named in their own scripts, through Android’s per-app locale API. Beta because several translations are partial and fall back to English. Settings → Appearance → Display.',
          },
          {
            title: 'Music Together over a hotspot',
            body: 'Two phones, one host, no router in between — a QR invite and a screen built like the rest of the app.',
          },
          {
            title: 'A Sabrina Carpenter theme',
            body: 'Blush, butter and lilac over warm cream, and the only palette that moves the surfaces as well as the buttons. Ribbon bows, hearts and sparkles drift behind the glass and are refracted through it.',
          },
        ],
      },
      {
        title: 'Improved',
        items: [
          {
            title: 'The Apple Music pass',
            body: 'Home leads with Top Picks for You, then Recently Played. The queue, menus, artist page, charts, account sheet, search and every settings screen are grouped tables on glass plates, with a coloured glyph on every row.',
          },
          {
            title: 'Album and Downloads open on their cover',
            body: 'Edge to edge under the status bar, dissolving into the page — then the title, one grey line of facts, and Play and Shuffle as glass capsules.',
          },
          {
            title: 'The player’s panels',
            body: 'Equalizer, tempo and pitch, sleep timer and details use the app’s own sliders, capsules and segmented controls instead of Material’s.',
          },
          {
            title: 'Outputs, like AirPlay',
            body: 'Devices are named the way their makers name them — “OnePlus 13”, not “CPH2649” — with a volume bar you can drag.',
          },
          {
            title: 'Lyrics',
            body: 'Linotte and a staggered line motion, and only the line being sung reads the clock — the rest of the column no longer recomposes sixty times a second.',
          },
          {
            title: 'Motion',
            body: 'Tab changes are a 150ms fade. A tapped player expansion uses a softer spring than a flung one, and the dock fades out over the first half of it.',
          },
        ],
      },
      {
        title: 'Fixed',
        items: [
          {
            title: 'Songs stopped at about 0:29',
            body: 'The first range asked for was 512 KB, which put a chunk boundary right there. A small first chunk does not start playback any sooner, so it bought nothing and cost a stall.',
          },
          {
            title: 'Lyrics ran a line ahead on fast songs',
            body: 'The highlight had a fixed 450ms lead, most of a line when lines are a second apart. It is smaller now, and capped at a quarter of the current line’s length.',
          },
          {
            title: 'Lyrics flickered on every line change',
            body: 'The lit copy of each word was added to and removed from the tree as it was sung, and the auto-scroll jumped the column and caught it a frame later. Both are gone.',
          },
          {
            title: 'Opening Library crashed',
            body: 'On devices with a runtime shader, two in-content chips were sampling the backdrop they were drawn into.',
          },
          {
            title: 'Discord showed “Playing Exhale”',
            body: 'With a stranger’s artwork. The presence is fixed to Listening, the song, the artist and the cover.',
          },
          {
            title: 'The artist’s name went missing',
            body: 'In the mini player and the full player, when a song was played from search rather than from an artist page.',
          },
        ],
      },
    ],
  },
  {
    version: '1.0.203',
    date: 'September 5, 2026',
    tag: 'Earlier release',
    summary:
      'Everything that moved since the first public release: the interface at your size, a library shaped like a library, a sleep timer you can read half asleep, and glass that bends the pixels actually behind it.',
    groups: [
      {
        title: 'New',
        items: [
          {
            title: 'Interface scale',
            body: 'Exhale’s own copy of Android’s Display Size, for this app only. Ten steps, a scale model of a real screen that redraws as you drag, and an explicit Apply. Settings → Appearance → Display.',
          },
          {
            title: 'A welcome, once',
            body: 'The first launch after an update opens on the build number assembling itself out of a scattered star field — the same figure the announcement page is headed by — which holds, comes apart, and leaves the colour it explodes into behind. Then the word is written, in a script drawn as one unbroken stroke rather than set in a face the phone may not have. Shown once per update, never on a fresh install, and a tap skips to the part worth seeing.',
          },
          {
            title: 'App icon packs',
            body: 'Gold (a gold mark lit on black, and what the app now ships wearing) and Classic (the black mark on gold). The pack also supplies the app-bar disc and the boot splash mark, so the choice carries past the home screen.',
          },
          {
            title: 'A sleep timer you can reach',
            body: 'It lives in the player menu now, reachable from every player design, and counts songs as well as minutes. Every option carries the clock time it lands on, and the ring drains rather than fills.',
          },
          {
            title: 'Refracting in-content glass',
            body: 'The round controls are cut from the search bar’s own material. Press one and the pane deforms toward your finger, with a specular bloom tracking the touch.',
          },
        ],
      },
      {
        title: 'Improved',
        items: [
          {
            title: 'The Library, rebuilt',
            body: 'A large title, an inset list of destinations, pinned collections in the same list, and Recently Added as an artwork grid. Filter chips became real pages with a working back gesture.',
          },
          {
            title: 'The player menu is one level deep',
            body: 'Five round actions on top, one grouped list of places underneath. It used to be six nested containers before it reached a verb.',
          },
          {
            title: 'Lyrics keep up',
            body: 'Blur is forward-only, the word swell peaks when you hear it rather than after, and the scroll runs 220ms ahead of the highlight so a line arrives at the anchor about when it lights.',
          },
          {
            title: 'Updates finish inside the app',
            body: '“Update Now” goes to the real transfer instead of the system browser, and the prompt is a proper Software Update sheet with a decline action it never had.',
          },
          {
            title: 'The dock changes shape like one object',
            body: 'The tab strip folds along its length into exactly the 64dp the home circle occupies, the circle grows in place there, and the pill slides out from behind it. Nothing arrives from off-screen any more, the two states hand the glass over without the bar flashing, and the collapse has a threshold band so it stops flipping under your thumb.',
          },
          {
            title: 'Home says what it is doing',
            body: 'The shortcut tiles show which of them is playing, and take the same long-press menu as every other row in the app. A feed that comes back empty is now a page that says so and offers your library, rather than the word “Home” on a black sheet.',
          },
          {
            title: 'The page is lit rather than painted',
            body: 'The artwork glow drifts a lap every thirty-odd seconds. Home arrives shelf by shelf, the shortcut tiles cascade, and the large title shrinks toward the compact one as it leaves.',
          },
        ],
      },
      {
        title: 'Fixed',
        items: [
          {
            title: 'The player’s seek bar was stuck',
            body: 'LiquidSlider watched a plain parameter through snapshotFlow, which reads no snapshot state and so never re-emitted.',
          },
          {
            title: '“Update Now” crashed the app',
            body: 'The prompt was resolving navigate() against a lateinit property nothing ever assigned. It compiled, it looked right, and it threw on press.',
          },
          {
            title: 'Settings bars cut the artwork off dead',
            body: 'They were a flat fill of the page color, slicing the ambient wash along the bar’s bottom edge. Every settings page now takes the same ground as the page it sits on — About and the two large-title pages that were still painting a black slab included, since a large title is most of what you see when you open one.',
          },
          {
            title: 'The launcher icon was wrong under a square mask',
            body: 'Its background layer carried the source artwork’s own edge vignette — invisible under a circle, glaring under a square. The in-app mark was the unfixed copy.',
          },
          {
            title: 'The Library tab could strand you on a sub-page',
            body: 'Opening Songs or Albums wrote the “default library page” setting, so the tab reopened there forever, its own button could not get you back to it, and the back gesture silently went up a level inside Library instead of home. Where you are and what you prefer are two different things now.',
          },
          {
            title: 'Downloads ran at about the speed of playback',
            body: 'Every download asked for the whole file in one open-ended request, which is the shape YouTube serves slowly on purpose — playback has always asked for ranges, and downloads never did. They now ask for a range, five run at once instead of three, and each byte is written to disk once rather than twice.',
          },
        ],
      },
      {
        title: 'Removed',
        items: [
          {
            title: 'Three quarters of the launch animation',
            body: 'Two shockwave rings, a ten-degree entrance tilt and a diagonal sheen sweep. Four things competing for attention inside one second is what a splash looks like when it is trying to impress you.',
          },
          {
            title: 'The duplicate playlist list',
            body: 'The Playlists tab already owned it, and that is the one with reordering.',
          },
          {
            title: 'A second account circle',
            body: 'It briefly sat inside Home’s large title, directly under the app bar that already had one.',
          },
        ],
      },
    ],
  },
  {
    version: '1.0.102',
    date: 'August 26, 2026',
    tag: 'First public release',
    summary:
      'Everything below shipped in one go, so the list is longer than a release note usually is. From here on each one covers only what moved since the last.',
    groups: [
      {
        title: 'New',
        items: [
          {
            title: 'Your Sound Chem',
            body: 'A listening capsule for each month: total time, your top artist and song, and the five artists that took up most of it. Step through the months with the arrows.',
          },
          {
            title: 'Lyrics on the lock screen',
            body: 'On OnePlus and Oppo phones the lock screen music panel shows real synced lyrics instead of "No lyrics". Off by default, under Settings → Content.',
          },
          {
            title: 'Updates inside the app',
            body: 'Exhale checks GitHub for newer versions, downloads them with real progress, and hands them to the installer. The notes for the build you are on are compiled in, so they work offline.',
          },
          {
            title: 'A new Home screen',
            body: 'Six shortcuts in a grid at the top, and a large title that slides under the toolbar as you scroll.',
          },
          {
            title: 'Android Auto, Discord, scrobbling',
            body: 'Your library on the dashboard, what you are playing on your Discord profile, and scrobbles to Last.fm, ListenBrainz or both.',
          },
          {
            title: 'A breathing pacer, hidden',
            body: 'Tap the app mark in About seven times. Four seconds in, six seconds out, and a hundred and fifty motes that move with it.',
          },
        ],
      },
      {
        title: 'Improved',
        items: [
          {
            title: 'One glass, everywhere',
            body: 'The dock, the sheets, the search field and the player are all built from the same material now, so nothing on screen looks like it came from a different app.',
          },
          {
            title: 'The dock moves as one object',
            body: 'Opening and closing it is one shape changing rather than two panels fading through each other, and the selected tab stretches into the move before it settles.',
          },
          {
            title: 'Sheets can be thrown away',
            body: 'Drag the handle to dismiss one. The room behind it lightens as you drag, and the sheet springs back if you change your mind.',
          },
          {
            title: 'Settings you can find things in',
            body: 'A search field under the title, a label on every group, and dividers that line up with the text instead of stopping six pixels short.',
          },
          {
            title: 'Highest quality means highest',
            body: 'Max audio quality used to take the first source that answered. It now checks them all and keeps the best one.',
          },
          {
            title: 'Recommendations in your language',
            body: 'Picking a language during setup now actually takes effect, and the feed stops leaking songs in a language you did not ask for.',
          },
        ],
      },
      {
        title: 'Fixed',
        items: [
          {
            title: 'Update alerts could point backwards',
            body: 'Anyone running a build newer than the latest release was told to "update" to an older one.',
          },
          {
            title: 'Release builds were never signed',
            body: 'Which meant no phone would install them. They are signed, named and built from a tag now.',
          },
          {
            title: 'The dock was not really glass',
            body: 'It was blurring an empty layer, so the only thing it ever drew was its own grey film. It blurs the real screen behind it now.',
          },
          {
            title: 'Search stuttered while you typed',
            body: 'Every keystroke opened its own connection and the results blanked between them. Seven letters used to mean seven requests to throw six away.',
          },
          {
            title: 'Some songs showed another song\u2019s lyrics',
            body: 'Lyrics were being matched on track length alone, so any song of about the same length could win. Title and artist decide it now.',
          },
          {
            title: 'Home could crash when you opened it',
            body: 'Two sections arriving with the same id was enough to take the whole screen down.',
          },
        ],
      },
      {
        title: 'Removed',
        items: [
          {
            title: 'The update channel picker',
            body: 'There is one channel, so the choice was between one real option and one that does not exist.',
          },
          {
            title: 'The commit feed on the Updates page',
            body: 'Nobody standing on an update screen is asking what the last thirty commits were. The full history is still in the changelog.',
          },
          {
            title: 'The pie chart of cropped artist photos',
            body: 'Replaced by bars you can actually read a proportion off.',
          },
        ],
      },
    ],
  },
]
