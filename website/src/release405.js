/*
 * The 1.0.405 announcement.
 *
 * A different genre from 1.0.203 and 1.0.304: those were documents, read top to
 * bottom. This one is laid out the way OPPO lays out a ColorOS release — the
 * ColorOS 17 page in particular — a sticky row of chapters, a name lit on black,
 * each chapter opening on one enormous line, and the features as cards and tiles
 * rather than paragraphs. `layout: 'coloros'` is what tells Site.jsx to draw it
 * with `pages/ReleaseColorOS.jsx` instead of the document page.
 *
 * Android only. The Windows app shipped alongside this release but is its own
 * product with its own version line, and it is not what someone opening the
 * 1.0.405 page has installed.
 *
 * Every card is a commit since 1.0.304 and every screenshot is a capture from
 * the signed 1.0.405 universal build. Nothing here is a plan.
 */

const shot = (name, alt) => ({ src: `/shots/405-${name}.jpg`, alt })

export const SHOT405 = {
  home: shot('home', 'Home in 1.0.405: Top Picks for You, the mini player with its progress ring, and the glass dock.'),
  player: shot('player', 'The 1.0.405 player, the cover filling the top of the screen.'),
  video: shot('video', 'The music video playing in the player, under the top strip, over the song’s own audio.'),
  fullscreen: shot('fullscreen', 'The music video full screen in landscape.'),
  lyrics: shot('lyrics', 'Lyrics lighting a word at a time.'),
  library: shot('library', 'The redesigned Library: a Liked card with play, glass collection tiles and one destination list.'),
  link: shot('link', 'A Spotify link pasted into search, found here as the same song.'),
  lyricSearch: shot('lyricsearch', 'Searching a line of a song finds the song, with the line it matched.'),
  settings: shot('settingsjump', 'Settings search landing on Crossfade and lighting the row.'),
}

export const RELEASE_405 = {
  version: '1.0.405',
  code: 405,
  date: 'October 11, 2026',
  dateISO: '2026-10-11',
  kicker: 'Release',
  read: '3 min read',

  /** Draw this release with the ColorOS-style page. */
  layout: 'coloros',

  title: 'Exhale 1.0.405',

  /** The two-part line under the name, as ColorOS 17 has “超流畅 更懂你”. */
  tagline: ['Ultra fluid.', 'Knows your music.'],

  dek: 'Liquid glass in every page, a player that flies out of the dock, music videos over the song’s own sound, and a search that finds a song from a link or a line of it.',

  facts: [
    { label: 'Version', value: '1.0.405' },
    { label: 'Version code', value: '405' },
    { label: 'Download', value: '41 MB' },
    { label: 'Architecture', value: 'Universal' },
    { label: 'Requires', value: 'Android 13 or later' },
    { label: 'License', value: 'GPL-3.0' },
  ],

  /** The three phones under the name: Home, the music video, the lyrics. */
  heroShots: [SHOT405.home, SHOT405.video, SHOT405.lyrics],

  /** The chapter row along the top. Each id is a section on the page. */
  chapters: [
    { id: 'highlights', label: 'Highlights' },
    { id: 'fluid', label: 'Fluid' },
    { id: 'knows', label: 'Knows your music' },
    { id: 'sound', label: 'Sound & picture' },
    { id: 'care', label: 'Care' },
    { id: 'get', label: 'Get 1.0.405' },
  ],

  /** The cards that slide across under “Highlights”, one claim each. */
  highlights: [
    {
      kicker: 'Fluid design',
      title: 'Glass that bends the page beneath it.',
      body: 'Settings groups, surfaces and controls refract whatever is behind them, live, on every page.',
      shot: SHOT405.library,
      tint: '#ff9ec7',
    },
    {
      kicker: 'Player',
      title: 'The cover flies out of the dock.',
      body: 'It lifts out of the mini player on a curve while the player rises in behind it, and flies home when you close it.',
      shot: SHOT405.player,
      tint: '#ffb27a',
    },
    {
      kicker: 'Music video',
      title: 'The video, over the song’s own sound.',
      body: 'The artist’s own video, picked over fan uploads, playing in step with the stream you chose.',
      shot: SHOT405.video,
      tint: '#c9b2ff',
    },
    {
      kicker: 'Search',
      title: 'Paste a link. Get the song.',
      body: 'Spotify, Apple Music, Deezer, Tidal, SoundCloud or YouTube — the same song or album, here.',
      shot: SHOT405.link,
      tint: '#8fe3b5',
    },
    {
      kicker: 'Lyrics search',
      title: 'Sing a line. Find the song.',
      body: '“His palms are sweaty” is Lose Yourself, with the line it matched shown under it.',
      shot: SHOT405.lyricSearch,
      tint: '#ffd36e',
    },
  ],

  /**
   * The chapters after the highlights. Each opens on one enormous line and then
   * lays its features out as tiles: `shot` tiles carry a capture, `stat` tiles a
   * single number, and the rest are words.
   */
  sections: [
    {
      id: 'fluid',
      eyebrow: 'Fluid',
      title: 'Everything moves like it has weight.',
      lede: 'Springs without wobble, glass that answers the finger, and nothing that just appears.',
      tiles: [
        {
          size: 'tall',
          title: 'A dock made of jelly.',
          body: 'The tab pill stretches between tabs and can be dragged; held, it bends the tabs and the page under it.',
          shot: SHOT405.home,
        },
        {
          size: 'stat',
          stat: '30',
          unit: 'fps',
          title: 'The moving artwork behind every page moves only while music plays, and sleeps with the app.',
        },
        {
          title: 'Menus grow from your finger.',
          body: 'Long-press menus open out of the held point over a blurred page; pop-ups grow from their button, one highlight sliding under the finger.',
        },
        {
          title: 'Pages slide, not snap.',
          body: 'Library views, search results and settings pages push in and fold away, with the title coming back at the top.',
        },
        {
          title: 'The player opens without a stall.',
          body: 'It is built before the spring starts, and the cover’s full size is already fetched, so the flight never lands on a grey square.',
        },
        {
          size: 'full',
          title: 'Settings that take you to the setting.',
          body: 'Search lands on the row itself — even one below the fold — and lights it, so the eye goes straight to it.',
          shot: SHOT405.settings,
        },
      ],
    },
    {
      id: 'knows',
      eyebrow: 'Knows your music',
      title: 'Ask for it the way you remember it.',
      lede: 'A link from another app, a line you half remember, a playlist you built somewhere else.',
      tiles: [
        {
          size: 'tall',
          title: 'A line of a song is enough.',
          body: 'Lyrics are searched alongside titles, typed the way people remember them — misspelt, unpunctuated, Hindi in Latin letters.',
          shot: SHOT405.lyricSearch,
        },
        {
          size: 'stat',
          stat: '50',
          unit: '/ 50',
          title: 'Songs matched importing Today’s Top Hits from Spotify. Paste a public playlist link, or start from Library.',
        },
        {
          title: 'Top Picks open instantly.',
          body: 'Last session’s picks show at once while fresh ones load, and they load first instead of after the rest of Home.',
        },
        {
          title: 'Songs come first.',
          body: 'Search puts songs ahead of videos, and the field can sit at the top of the page or stay down on the dock.',
        },
        {
          title: '“Play … on Exhale.”',
          body: 'Gemini or Assistant finds the song, artist, album, playlist or genre and plays on into its radio.',
        },
      ],
    },
    {
      id: 'sound',
      eyebrow: 'Sound & picture',
      title: 'Bigger sound. A real picture.',
      lede: 'The song’s own stream stays the sound; everything else is built around it.',
      tiles: [
        {
          size: 'tall',
          title: 'Lyrics, on the word.',
          body: 'Word-timed sources get time to answer, lyrics that cannot belong to the recording are passed over, and the always-on display lights words too.',
          shot: SHOT405.lyrics,
        },
        {
          size: 'wide',
          title: 'Full screen, the right way up.',
          body: 'Landscape with its controls, and back to the player upright with the video still playing.',
          shot: SHOT405.fullscreen,
          landscape: true,
        },
        {
          title: '8D that passes behind your head.',
          body: 'Wide mixes stay intact and loud songs no longer distort: the middle orbits, the sides stay, and a limiter replaces the clipper.',
        },
        {
          title: 'One-tap Sound presets.',
          body: 'Sped up, Nightcore, Slowed, Daycore and Vaporwave, with the pitch following the speed and the lyrics clock following both.',
        },
        {
          size: 'stat',
          stat: '6',
          unit: '%',
          title: 'The most Automix will ever stretch a song to meet the next one on the beat.',
        },
        {
          title: 'Cast to the big screen.',
          body: 'Chromecast from the device sheet: the TV follows its song, pauses, seeks and skips, and the phone stays the clock.',
        },
        {
          title: 'Covers that move.',
          body: 'Moving artwork from Apple Music and Tidal is back, and Spotify Canvas joins it for connected accounts.',
        },
      ],
    },
    {
      id: 'care',
      eyebrow: 'Care',
      title: 'Quietly, it keeps going.',
      lede: 'The fixes you will never see, because the thing they fixed just works now.',
      tiles: [
        {
          size: 'stat',
          stat: '<1',
          unit: 's',
          title: 'For a song you have never played to start, at the best quality your account gets.',
        },
        {
          title: 'Your queue survives.',
          body: 'The saved queue was never actually written. Now it is, so after Android closes the app your song is still there, at the second you left it.',
        },
        {
          title: 'Songs that fail try again.',
          body: 'A stream that will not decode is fetched from another client before it is skipped, and the app tells you when it skips.',
        },
        {
          title: 'Offline, automatically.',
          body: 'With no internet, Home lists what the phone can play, with Play and Shuffle — or switch it on in Storage.',
        },
        {
          title: 'Rests in the background.',
          body: 'Polling waits for a visible screen, the level meter idles unread, and the music video stops decoding.',
        },
        {
          title: 'No crash on resume.',
          body: 'Resuming a song no longer crashes on a spring that dipped below zero, and a stream with no length or codecs no longer takes the app down.',
        },
      ],
    },
  ],

  everything: [
    {
      group: 'New',
      items: [
        'Music video in the player, full screen in landscape.',
        'Paste a Spotify, Apple Music, Deezer, Tidal, SoundCloud or YouTube link into search.',
        'Import public Spotify playlists.',
        'Find a song by a line of its lyrics.',
        'Settings search lands on the setting and lights it.',
        'Jelly dock style, with labels, compact and touch-glow options.',
        '8D audio and one-tap Sound presets.',
        'Chromecast.',
        'Offline mode, automatic with no internet.',
        'AMOLED black theme.',
        'Voice: “play … on Exhale” from Gemini or Assistant.',
        'Word-by-word lyrics on the always-on display.',
      ],
    },
    {
      group: 'Improved',
      items: [
        'Real liquid glass in pages, not only the dock.',
        'Library redesigned: Liked hero card, glass tiles, one destination list.',
        'The cover flies from the dock into the player and back.',
        'The player sheet moves like Apple Music’s.',
        'Long-press and pop-up menus grow from where you touched.',
        'Settings icons drawn the way ColorOS draws them.',
        'Mini player: play and pause in a ring that is the song’s progress.',
        'The moving artwork runs at 30 fps and only while music plays.',
        'Songs not played before start in under a second.',
      ],
    },
    {
      group: 'Fixed',
      items: [
        'The queue was never saved, so it never came back after Android closed the app.',
        'Resuming a song could crash.',
        'Songs sat at 00:00 or stopped in runs.',
        'Fan lyric videos were picked over the artist’s own.',
        'Full-screen video dropped the video and stayed sideways on the way out.',
        'Lyrics ran ahead, read twice, or stopped loading after one failure.',
        'A pasted link said “No results found” under its own match.',
        'A black band across the top of inner pages.',
        'Lag opening the player from glass-heavy pages.',
        'Automix could stretch a song by up to 15%.',
      ],
    },
  ],
}
