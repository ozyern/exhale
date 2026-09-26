/*
 * The 1.0.304 announcement, as a document.
 *
 * Same genre as `release.js`: a story, not an essay. Chapters carry one idea
 * and at most two sentences, screenshots are real captures from the signed
 * build, and everything the edit cut is kept at the bottom as one plain list
 * rather than quietly dropped.
 *
 * What is different is the subject. 1.0.203 was a handful of features; this is
 * the release where the whole app was rebuilt to one set of rules and then made
 * to work without a network. So the story is told in materials and behaviours —
 * what it is made of, what it does when the signal goes — rather than as a tour
 * of new switches.
 *
 * Everything below is a commit. Nothing here is a plan.
 */

import { SHOTS_304, SHOT304 } from './content.js'

export const RELEASE_304 = {
  version: '1.0.304',
  code: 304,
  date: 'September 26, 2026',
  dateISO: '2026-09-26',
  kicker: 'Release',
  read: '4 min read',

  /** Which hero the page draws. See `GlassField`. */
  field: 'glass',

  /**
   * The page's palette. This release is the gold one — the key art, the wordmark
   * and the gold mark on its About screen — so its page is dressed in them. The
   * theme belongs to the release, not to the site: 1.0.203 and the landing page
   * keep their own.
   */
  theme: 'gold',

  title: 'Exhale 1.0.304',

  dek: 'The whole app rebuilt in Apple Music’s shape, music that plays with no network at all, updates that install themselves, and a long list of things that had been quietly wrong.',

  /** Read off `app/build.gradle.kts` and the signed universal APK. */
  facts: [
    { label: 'Version', value: '1.0.304' },
    { label: 'Version code', value: '304' },
    { label: 'Download', value: '38 MB' },
    { label: 'Architecture', value: 'Universal' },
    { label: 'Requires', value: 'Android 13' },
    { label: 'License', value: 'GPL-3.0' },
  ],

  /** The three screens this release is most visible on. */
  hero: {
    shots: [SHOT304.home, SHOT304.player, SHOT304.lyrics],
    caption:
      'Home, the player and the lyrics screen, captured from the 1.0.304 universal build.',
  },

  heroShots: SHOTS_304,
  shots: SHOTS_304,

  story: [
    {
      id: 'home',
      kicker: 'Home',
      title: 'It leads with what you play.',
      body: 'Top Picks for You is built from your own listening and says why each one is there. Underneath it, Recently Played — at Apple Music’s type sizes and shelf rhythm, because a shelf that is nearly the right size reads as a copy of one.',
      shot: SHOT304.home,
      caption: 'Home on the 1.0.304 build. Every recommendation names the artist it came from.',
    },
    {
      id: 'offline',
      kicker: 'Offline',
      title: 'It plays with the radio off.',
      body: 'Downloaded songs play with no network at all, and the queue skips to what is actually on the phone instead of stalling on the first track that is not. Save to device writes a tagged .m4a into Music/Exhale, cover art and synced lyrics embedded, so the song is a file every other player can read.',
      shot: SHOT304.downloads,
      flip: true,
      caption: 'Downloaded: the cover edge to edge, then Play and Shuffle as glass capsules.',
    },
    {
      id: 'lossless',
      kicker: 'Your own files',
      title: 'A better copy wins.',
      body: 'If a FLAC, WAV or AIFF of the song you asked for is already on the phone, Exhale plays that instead of the stream. Nothing to import and no second library to keep — the music you own is simply the version you get.',
      tone: 'wide',
    },
    {
      id: 'lyrics',
      kicker: 'Lyrics',
      title: 'On the word, not near it.',
      body: 'The line being sung lights a word at a time and the rest of the column falls out of focus behind it. Two long-standing faults went with the rebuild: the highlight running a whole line ahead on fast songs, and the flicker every time a line changed.',
      shot: SHOT304.lyrics,
      caption: 'Word-by-word highlighting, with depth-of-field on the lines you have not reached.',
    },
    {
      id: 'material',
      kicker: 'Material',
      title: 'One material, everywhere.',
      body: 'Settings, the account sheet, the menus, the album pages, the equalizer and the player’s panels are all the same grouped glass tables now — hairlines inset to the label, headers set quiet, a coloured glyph rather than a coloured tile on every row.',
      shot: SHOT304.settings,
      flip: true,
      caption: 'Settings: one plate, one row height, and the app’s own mark at the top.',
    },
    {
      id: 'updates',
      kicker: 'Updates',
      title: 'It updates itself.',
      body: 'Check, download and install without leaving the app or opening a browser, with real byte progress. About and Updates are one release poster: the gold key art, the wordmark, the build, and whether you are current.',
      shot: SHOT304.about,
      caption: 'About in 1.0.304 — hold the card and it turns over.',
    },
    {
      id: 'sound',
      kicker: 'Sound',
      title: 'A stage, not a switch.',
      body: 'Spatial audio ships on Cinema: a phone has two speakers, so the 5.1 and 7.1 switches other players show have nothing to drive, and the width of the stage is the thing they are reaching for. On OnePlus and OPPO it stands down, because OReality is already doing it.',
      tone: 'wide',
    },
    {
      id: 'get',
      kicker: 'Availability',
      title: 'One file, no account.',
      body: 'A universal APK — arm64, arm32 and both x86 targets in a single download, so there is nothing to pick. No store, no telemetry, and after the first sideload the app updates itself.',
      tone: 'wide',
    },
  ],

  everything: [
    {
      group: 'New',
      items: [
        'Offline playback, with the queue skipping to what is on the phone.',
        'Save to device: a tagged .m4a with cover art and synced lyrics.',
        'Prefer lossless files — your own FLAC, WAV or AIFF instead of the stream.',
        'In-app updates: download and install without a browser.',
        'The desktop build’s live artwork background, on every page with a subject.',
        'App language (Beta): twenty locales, through Android’s per-app locale API.',
        'Spatial audio stages: Natural, Wide and Cinema.',
        'Music Together over a phone hotspot, with a QR invite.',
        'A Sabrina Carpenter theme, with charms drifting behind the glass.',
        'Tempo and pitch, a sleep timer with a countdown dial, and a lyrics sync pill.',
        'Swipe a song left to queue it, right to play it next.',
      ],
    },
    {
      group: 'Improved',
      items: [
        'Home, the queue, menus, settings, the artist page and the charts, in Apple Music’s shape.',
        'Album and Downloads open on their cover, edge to edge.',
        'The equalizer, tempo, sleep timer and details panels use the app’s own controls.',
        'Outputs names devices the way their makers do, not by model number.',
        'Option menus open as iOS pull-downs at 44pt rows.',
        'Tab changes fade; a tapped player expansion uses a softer spring than a flung one.',
        'Only the lyric line being sung reads the clock, instead of the whole column.',
      ],
    },
    {
      group: 'Fixed',
      items: [
        'Songs stopped at about 0:29, then resumed.',
        'Lyrics ran a line ahead on fast songs.',
        'Lyrics flickered on every line change.',
        'The artist’s name was missing when a song was played from search.',
        'The dock’s “Mood & Genres” label ran into the selection capsule.',
        'Opening Library could crash on devices with a runtime shader.',
        'The settings search field floated a mini player above the mini player.',
        'System audio effects opened MusicFX instead of the phone’s own panel.',
        'Discord showed “Playing Exhale” with a stranger’s artwork.',
        'The Updates page could open without its title bar.',
        'The player’s standard slider never committed a seek.',
      ],
    },
  ],
}
