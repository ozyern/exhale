/*
 * The 1.0.203 announcement, as a document.
 *
 * `content.js` holds what the landing page says about the app in general, and
 * `RELEASE_NOTES` in that file holds the short version of a release — four
 * groups, a sentence each, for someone standing at a download button.
 *
 * This is the third document, and it used to be a different genre from both: a
 * long read, twelve numbered sections and sixteen hundred words of why. That
 * was written for a reader who does not exist. Someone who opens a release page
 * wants to know what changed and what it looks like, and the honest place for
 * the engineering account is the commit messages and CHANGELOG.md — both of
 * which are one click away and neither of which is competing for their
 * attention.
 *
 * So it is a story now: eight chapters, a headline and two sentences each, and
 * real screenshots at the size a screenshot deserves. Everything that did not
 * survive the edit is still on the page, at the bottom, as one quiet list.
 *
 * Everything below is a commit. Nothing here is a plan.
 */

import { SHOT, SHOT203, SHOTS, SHOTS_203 } from './content.js'

export const RELEASE = {
  version: '1.0.203',
  /** Which hero the page draws. See `BreathField`. */
  field: 'breath',
  /** The hero's phones, and the set the chapters index into. */
  heroShots: SHOTS,
  shots: SHOTS_203,
  code: 203,
  date: 'September 5, 2026',
  dateISO: '2026-09-05',
  kicker: 'Release',
  read: '3 min read',

  title: 'Exhale 1.0.203',

  dek: 'The whole interface at your size, a library shaped like a library, and a player menu you reach the verb in. Everything that moved since the first public release.',

  /** Read off `app/build.gradle.kts` and the signed universal APK. */
  facts: [
    { label: 'Version', value: '1.0.203' },
    { label: 'Version code', value: '203' },
    { label: 'Download', value: '37 MB' },
    { label: 'Architecture', value: 'Universal' },
    { label: 'Requires', value: 'Android 13' },
    { label: 'License', value: 'GPL-3.0' },
  ],

  /** The three screens the release is most visible on, for the hero. */
  hero: {
    shots: [SHOT.home, SHOT.player, SHOT.lyrics],
    caption:
      'Home, the player and the lyrics screen, captured from the 1.0.203 universal build.',
  },

  /**
   * The page, as chapters.
   *
   * One idea each. `title` is short enough to read at display size, `body` is at
   * most two sentences, and `shot` — when there is one — is a real capture from
   * this build rather than an illustration of it.
   *
   * `tone: 'wide'` gives a chapter the full column and no image: they are the
   * beats between the pictures, and a page that is only pictures has no rhythm.
   *
   * `flip` puts the picture on the left. It is stated rather than derived: the
   * narrow chapters happen to sit at odd positions in this story, so a CSS
   * `nth-of-type(even)` rule looks like it alternates them and in fact never
   * fires once.
   */
  story: [
    {
      id: 'scale',
      kicker: 'Interface scale',
      title: 'Your size, not the phone’s.',
      body: 'Ten steps, previewed live on a scale model of your own screen, and applied only when you say so. Android’s Display Size for one app, because the size that suits a music player is not the size that suits everything else.',
      shot: SHOT203.home,
      caption: 'Home on the 1.0.203 build. The scale picker previews this exact page as you move through the steps.',
    },
    {
      id: 'library',
      kicker: 'Library',
      title: 'A library shaped like a library.',
      body: 'A large title, an inset list of destinations, and pinned collections in the same list. The filter chips became real pages with a working back gesture — a lit chip and an unlit chip differ by a fill color, which is not enough to say where you are.',
      tone: 'wide',
    },
    {
      id: 'menu',
      kicker: 'Player menu',
      title: 'One level deep.',
      body: 'Five round actions on top, one grouped list of places underneath. It used to be six nested containers before it reached a verb.',
      shot: SHOT203.menu,
      flip: true,
      caption: 'The rebuilt player menu. Sleep timer lives here now, reachable from every player design.',
    },
    {
      id: 'sleep',
      kicker: 'Sleep timer',
      title: 'Readable half asleep.',
      body: 'Every option carries the clock time it lands on, because “45 minutes” is a subtraction you should not have to do at midnight. The ring drains rather than fills — a ring that fills is measuring how long you have been awake.',
      tone: 'wide',
    },
    {
      id: 'light',
      kicker: 'Light',
      title: 'Lit, rather than painted.',
      body: 'Every page takes its color from whatever is playing, and the wash drifts a lap every thirty-odd seconds. Settings pages take the same ground as the page they sit on, so a large title is no longer a black slab across the top of a lit screen.',
      shot: SHOT203.about,
      caption: 'About, carrying the artwork’s color all the way through the title.',
    },
    {
      id: 'downloads',
      kicker: 'Downloads',
      title: 'As fast as the connection.',
      body: 'Downloads asked for the whole file in one open-ended request, which is the shape YouTube serves slowly on purpose. They ask for a range now, five run at once instead of three, and each byte is written to disk once rather than twice.',
      tone: 'wide',
    },
    {
      id: 'welcome',
      kicker: 'The update',
      title: 'It introduces itself.',
      body: 'The first launch after an update opens on the build number assembling itself out of a scattered star field — the figure at the top of this page — which holds, comes apart, and leaves the color it explodes into behind. Once per update, never on a fresh install.',
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

  /**
   * Everything the edit cut, kept as one list.
   *
   * The chapters above are the release; this is the receipt. It is deliberately
   * plain and deliberately last — the reader who wants it will scroll, and the
   * reader who does not should never have to skim past it.
   */
  everything: [
    {
      group: 'New',
      items: [
        'Interface scale — Settings → Appearance → Display.',
        'App icon packs: Gold, which the app now ships wearing, and Classic.',
        'A sleep timer in the player menu, counting songs as well as minutes.',
        'Refracting in-content glass on the search bar’s round controls.',
        'A welcome screen on the first launch after an update.',
        'This page.',
      ],
    },
    {
      group: 'Improved',
      items: [
        'The Library, rebuilt around pages instead of filter chips.',
        'The player menu is one level deep.',
        'Lyrics scroll 220ms ahead of the highlight, so a line arrives as it lights.',
        'Updates finish inside the app instead of in the system browser.',
        'Home arrives shelf by shelf, and says which shortcut is playing.',
        'The dock folds into itself rather than cross-fading between two docks.',
      ],
    },
    {
      group: 'Fixed',
      items: [
        'Downloads ran at about the speed of playback.',
        'The Library tab could strand you on a sub-page.',
        'A tap on Home could do nothing at all.',
        'Settings bars cut the artwork off dead.',
        'The player’s seek bar was stuck.',
        '“Update Now” crashed the app.',
        'The launcher icon was wrong under a square mask.',
      ],
    },
  ],
}
