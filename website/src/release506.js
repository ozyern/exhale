/*
 * The 1.0.506 announcement.
 *
 * Drawn by `pages/Release506.jsx` (`layout: 'aurum'`), in the key visual's own
 * language — the gold ribbons, the metallic numerals, the black stage — rather
 * than as a document or the ColorOS page 1.0.405 used. Android and Windows both:
 * 1.0.506 is the release the Windows app grew up in.
 *
 * Every claim is a commit since 1.0.405. Every screenshot is a capture of 1.0.506
 * on a OnePlus phone or on Windows 11.
 */

const shot = (name, alt) => ({ src: `/shots/506-${name}.jpg`, alt })

export const SHOT506 = {
  about: shot('about', 'About Exhale 1.0.506: the wordmark on gold ribbons, "Exhale is up to date."'),
  update: shot('update', 'Software update: version up to date, with Highlights and View update notes.'),
  player: shot('player', 'The player on Bed Chem by Sabrina Carpenter: Spatial Audio on, 8D in the sound strip, the video a tap away.'),
  lyrics: shot('lyrics', 'Word-synced lyrics in the player, the current line lit.'),
  search: shot('search', 'Search on its category grid, the FLAC tile first, the folded dock below.'),
  winPlayer: shot('win-player', 'Exhale for Windows: the player over the cover’s own blurred wash.'),
  winVideo: shot('win-video', 'The music video in 2160p, with its lyrics laid over the picture.'),
  winHome: shot('win-home', 'Exhale for Windows: Home beside an Apple Music style sidebar, the player capsule floating below.'),
}

export const RELEASE_506 = {
  version: '1.0.506',
  code: 506,
  date: 'October 31, 2026',
  dateISO: '2026-10-31',
  kicker: 'Release',
  read: '4 min read',

  /** Draw this release with the key-visual page. */
  layout: 'aurum',

  title: 'Exhale 1.0.506',
  tagline: ['Silky smooth.', 'Hear it all around you.'],
  dek: 'For the first time, Exhale on Windows — in the shape of Apple Music. And on Android: spatial audio on every phone, lyrics that flow a letter at a time, a dock that folds like glass and a FLAC store.',

  /** Above the lockup's title: what is new about this release before anything else. */
  eyebrow: 'Now on Windows',

  facts: [
    { label: 'Version', value: '1.0.506' },
    { label: 'Version code', value: '506' },
    { label: 'Android', value: '13 or later, universal' },
    { label: 'Windows', value: '10 and 11, 64-bit' },
    { label: 'License', value: 'GPL-3.0' },
  ],

  chapters: [
    { id: 'windows', label: 'Windows' },
    { id: 'sound', label: 'Sound' },
    { id: 'lyrics', label: 'Lyrics' },
    { id: 'dock', label: 'Dock' },
    { id: 'search', label: 'Search' },
    { id: 'get', label: 'Get 1.0.506' },
  ],

  sound: {
    eyebrow: 'Sound',
    line: 'Music that fills the room.',
    beats: [
      { k: 'Spatial Audio', t: 'On from the first song.', b: 'Exhale’s spatial stage now starts on, on every phone, OnePlus and OPPO included. Switch it off once and it stays off.' },
      { k: '8D', t: 'The whole stage, turning.', b: 'The mix travels around your head, a strip away under the player beside the headphone and person modes.' },
      { k: 'Clean path', t: 'Nothing done to it that it didn’t ask for.', b: 'No forced resampling and no drop to 16-bit on the way out. A USB DAC gets the source’s own samples, bit for bit.' },
      { k: 'Controls', t: 'Every button, at once.', b: 'Bluetooth play and pause answer the first press, and the OnePlus game sidebar’s controls work again.' },
    ],
  },

  lyrics: {
    eyebrow: 'Lyrics',
    line: ['Still', 'I', 'choose', 'to', 'be', 'in', 'love', 'with', 'you'],
    beats: [
      { k: 'Flow', t: 'A letter at a time.', b: 'Lyrics light letter by letter again, on time, with the next line drawing in on its run-up.' },
      { k: 'Music video', t: 'They follow the video.', b: 'Exhale finds where the song starts inside its music video, so a long intro no longer throws the lyrics off.' },
      { k: 'Right song', t: 'Wrong lyrics turned away.', b: 'A source has to match the title and the artist before its lyrics are shown.' },
    ],
  },

  dock: {
    eyebrow: 'The dock',
    line: 'Folds away. Unfolds on cue.',
    beats: [
      { k: 'Fold', t: 'Scroll down and it folds into glass.', b: 'Your page and the song pill side by side, search in its own circle. Scroll up and it opens again.' },
      { k: 'Search', t: 'The dock becomes the search field.', b: 'Magnifier, prompt and microphone in the bar itself; the arrow appears only on results.' },
      { k: 'Swipe', t: 'Slide along it to change page.', b: 'The glass slot follows your finger and lets go the moment you do. Album and artist pages keep the tab they came from lit.' },
    ],
  },

  search: {
    eyebrow: 'Search',
    line: 'Find it. In lossless.',
    tiles: [
      { stat: 'FLAC', t: 'A store of its own.', b: 'Concert tapes, netlabels, 78s, Creative Commons and classical recordings, streamed as FLAC and never through YouTube.' },
      { stat: '1st', t: 'The song you typed, first.', b: 'Search leads with the actual song rather than a cover, a live cut or a lyric video.' },
      { stat: '↗', t: 'Back on the category grid.', b: 'Explore, suggestions and albums under one switch, swipeable.' },
    ],
  },

  windows: {
    eyebrow: 'Exhale for Windows',
    intro: 'For the first time.',
    line: 'Apple Music’s shape. On your PC.',
    lede: 'Exhale’s first Windows release: the same library, lyrics and sound as on your phone, in a desktop app built the way Apple Music is on the Mac — and as quick as it looks.',
    beats: [
      { k: 'Sidebar', t: 'A pane of glass down the side.', b: 'Search, Home, New and Radio, your Library with Pins, the FLAC Store and your playlists, with you at the foot. Drag it wider, fold its sections, hide it.' },
      { k: 'Player', t: 'A capsule that floats.', b: 'Shuffle and the transport, the song with its progress, then lyrics, queue, output and volume, in one frosted capsule over the page.' },
      { k: 'Music video', t: 'Up to 2160p, lyrics on the picture.', b: 'Lined up with the song, so a long intro never throws the lyrics off. Close the player and it keeps playing in the dock’s artwork.' },
      { k: 'Dolby', t: 'Atmos to your receiver.', b: 'Dolby Digital Plus, TrueHD and the Atmos they carry go out untouched to an HDMI receiver or soundbar; stereo can be upmixed for Windows Sonic or Dolby Atmos.' },
      { k: 'Lossless', t: 'Your own files, first.', b: 'Point it at your lossless folders and the copy you own plays in place of the stream whenever it matches.' },
      { k: 'Output', t: 'Exclusive, resampled right.', b: 'WASAPI exclusive mode and a high-quality resampler, with the output and format you are actually getting shown in the player.' },
      { k: 'Windows', t: 'At home on the desktop.', b: 'The keyboard’s media keys, the volume overlay and Windows’ own media panel all drive it; it can sit in the tray and play on with its window closed.' },
      { k: 'Together', t: 'Discord and Last.fm.', b: 'What you are playing on your Discord profile, and every song scrobbled to Last.fm.' },
      { k: 'Smooth', t: 'Built to keep up with the screen.', b: 'Scrolling, page changes and the glass are tuned for high refresh rate displays, and lighter on PCs that need it.' },
      { k: 'Updates', t: 'Updates itself.', b: 'Each release carries the Windows setup beside the Android app. Exhale finds a new one, checks it against its checksum and installs it.' },
    ],
  },

  everything: [
    {
      group: 'Sound',
      items: [
        'Spatial Audio on by default on every device',
        'No forced resampling or 16-bit output',
        'Bit-perfect USB DAC output',
        'Bluetooth play/pause answers the first press',
        'OnePlus game sidebar media controls work again',
      ],
    },
    {
      group: 'Lyrics',
      items: [
        'Letter-by-letter flow restored, with 1.0.405 timing',
        'Lyrics aligned to the song inside its music video',
        'Wrong-song lyrics rejected (title and artist must match)',
        'Lock-screen lyrics kept through metadata refreshes',
        'Landscape lyrics over the cover, blended into its wash',
      ],
    },
    {
      group: 'Design',
      items: [
        'Folding dock with search morph and glass levels',
        'Album and playlist pages open out of their tile',
        'Pop-out player menu and iOS controls module',
        'Apple-style settings button',
        'New launch intro and colour-washed Liked and Offline pages',
      ],
    },
    {
      group: 'Search & library',
      items: [
        'FLAC store on Android and Windows',
        'Search leads with the song you typed',
        'Search page rebuilt: Explore, suggestions, albums',
        'Imported playlists stay in the Library',
      ],
    },
    {
      group: 'Windows — first release',
      items: [
        'Apple Music style sidebar with Pins, folding sections and resizing',
        'Floating player capsule',
        'Music video up to 2160p with lyrics, lined up with the song',
        'Music video keeps playing in the dock',
        'Dolby passthrough, Atmos detection and spatial upmix',
        'Lossless copies from your own folders',
        'WASAPI exclusive mode and a high-quality resampler',
        'Media keys, Windows media panel and tray',
        'Discord status and Last.fm scrobbling',
        'In-app updates, from the same releases as Android',
      ],
    },
  ],
}
