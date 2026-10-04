/*
 * Everything the support page says.
 *
 * Same rule as content.js: nothing here is aspirational. Every setting named
 * below exists in strings.xml today, and every answer is one I would give if
 * someone asked directly — including the ones where the answer is "it does not
 * do that, and here is why."
 *
 * The FAQ is the important half of this page. Most support traffic is the same
 * six questions, and an answer read here is a thread neither side has to have.
 */

import { REPO, TELEGRAM, TELEGRAM_CHANNEL, VERSION } from './content.js'

const NEW_ISSUE = `${REPO}/issues/new`

/* --------------------------------------------------------------- the paths */

/**
 * The four things someone arriving at /support might want.
 *
 * `fields` are collected on this page and handed to GitHub prefilled. The id of
 * each one matches a field id in the matching .github/ISSUE_TEMPLATE/*.yml, and
 * the two have to stay in sync — a renamed id there is a silently empty box
 * here, with no error anywhere to say so.
 */
export const PATHS = [
  {
    id: 'bug',
    keywords: 'bug, crash, error, force close, freeze, not working',
    tag: 'Broken',
    title: 'Something is broken',
    body: 'A crash, a setting that will not stick, playback that stops when it should not.',
    template: 'bug_report.yml',
    labels: 'bug',
    titlePrefix: '[Bug] ',
    steps: ['What happened', 'Your device'],
    // The device note belongs on the pane that asks for the device.
    notePane: 1,
    note: 'The device and the ROM decide half of all Android audio bugs. Please fill them in even when they feel irrelevant.',
    fields: [
      {
        id: 'what',
        label: 'What happened',
        type: 'area',
        rows: 3,
        required: true,
        placeholder: 'Playback stops about a minute after I turn the screen off.',
      },
      {
        id: 'steps',
        label: 'Steps to reproduce',
        type: 'area',
        rows: 3,
        required: true,
        placeholder: '1. Start a song\n2. Lock the phone\n3. Wait',
      },
      {
        id: 'version',
        label: 'Exhale version',
        type: 'text',
        required: true,
        half: true,
        prefill: VERSION,
        hint: 'Settings → About',
      },
      {
        id: 'android',
        label: 'Android version',
        type: 'text',
        required: true,
        half: true,
        placeholder: 'Android 14',
      },
      {
        id: 'device',
        label: 'Device',
        type: 'text',
        required: true,
        half: true,
        placeholder: 'Pixel 8 Pro',
      },
      {
        id: 'rom',
        label: 'ROM or skin',
        type: 'text',
        required: true,
        half: true,
        placeholder: 'stock, HyperOS 2, One UI 6…',
      },
    ],
  },
  {
    id: 'idea',
    keywords: 'feature, request, suggestion, wish',
    tag: 'Idea',
    title: 'I have an idea',
    body: 'Something Exhale should do, or should do differently than it does now.',
    template: 'feature_request.yml',
    labels: 'enhancement',
    titlePrefix: '[Idea] ',
    steps: ['Your idea', 'Your build'],
    note: 'Describe the problem rather than the feature where you can. There is often a better answer than the one either of us thought of first.',
    fields: [
      {
        id: 'idea',
        label: 'What you would like',
        type: 'area',
        rows: 3,
        required: true,
        placeholder: 'A way to queue a whole artist without opening each album.',
      },
      {
        id: 'problem',
        label: 'What problem it solves',
        type: 'area',
        rows: 3,
        required: true,
        placeholder: 'What are you doing when you feel the lack of it?',
      },
      { id: 'version', label: 'Exhale version', type: 'text', half: true, prefill: VERSION },
    ],
  },
  {
    id: 'help',
    keywords: 'question, how do i, how to',
    tag: 'Help',
    title: 'I need help using it',
    body: 'Something is not behaving the way you expected, and you are not sure it is a bug.',
    template: 'question.yml',
    labels: 'question',
    titlePrefix: '[Question] ',
    steps: ['Your question', 'Your device'],
    faqFirst: true,
    note: 'Have a look at the questions below first — most of what gets asked is answered there, and you get the answer in a minute rather than a day.',
    fields: [
      {
        id: 'question',
        label: 'Your question',
        type: 'area',
        rows: 3,
        required: true,
        placeholder: 'How do I move my library to a new phone?',
      },
      { id: 'tried', label: 'What you have already tried', type: 'area', rows: 2 },
      { id: 'version', label: 'Exhale version', type: 'text', half: true, prefill: VERSION },
      {
        id: 'device',
        label: 'Device and ROM',
        type: 'text',
        half: true,
        placeholder: 'Pixel 8 Pro, stock Android 14',
      },
    ],
  },
  {
    id: 'other',
    keywords: 'telegram, security, vulnerability, translate, translation, license, contact',
    tag: 'Else',
    title: 'Something else',
    body: 'A translation, a security problem, a licensing question, or a conversation that does not belong in public.',
    external: true,
  },
]

/** Where the last card sends people. Each one is a different door on purpose. */
export const ELSEWHERE = [
  {
    label: 'Telegram channel',
    body: 'New releases and test builds as they land, and news about what is coming next.',
    href: TELEGRAM_CHANNEL,
  },
  {
    label: 'Telegram',
    body: 'Anything that is not a bug report, and anything you would rather not post publicly.',
    href: TELEGRAM,
  },
  {
    label: 'Report a vulnerability',
    body: 'Privately, through GitHub security advisories, rather than as a public issue.',
    href: `${REPO}/security/advisories/new`,
  },
  {
    label: 'Translate Exhale',
    body: 'Strings are managed upstream on Weblate. CONTRIBUTING.md has the details.',
    href: `${REPO}/blob/master/CONTRIBUTING.md`,
  },
  {
    label: 'Browse open issues',
    body: 'Someone may have filed it already, and an existing thread moves faster than a new one.',
    href: `${REPO}/issues`,
  },
]

/* --------------------------------------------------------------- the facts */

/**
 * Three honest lines under the headline.
 *
 * Expectation-setting, not a boast. Someone who knows a reply takes days is
 * not someone who files the same bug three times on the second day, and the
 * reply time is the one thing a solo project cannot fake.
 */
export const FACTS = [
  { label: 'Maintained by', value: 'One person' },
  { label: 'Reports go to', value: 'GitHub Issues' },
  { label: 'Typical reply', value: 'Days, not hours' },
]

/* ------------------------------------------------------------- the process */

export const PROCESS = [
  {
    title: 'It becomes a public thread',
    body: 'Filed on GitHub under your own account, where you can follow it, add to it, and see the commit that closes it.',
  },
  {
    title: 'It gets read, then labelled',
    body: 'Usually within a few days. A bug that reproduces gets confirmed; one that does not gets questions rather than silence.',
  },
  {
    title: 'It ships in a release',
    body: 'Fixes land in the next build and are written up in the changelog with the reason they broke, not just the fact that they did.',
  },
]

/* ----------------------------------------------------------------- the FAQ */

/*
 * `keywords`, here and on PATHS, exist only for the search in the hero. They
 * are the words people actually type ("screen off" for a question that never
 * says it), and they are never shown.
 */

export const FAQ = [
  {
    id: 'background',
    keywords: 'screen off, lock screen, stops, pauses, cuts out, killed, battery, background playback, notification',
    q: 'Music stops when I lock the phone or switch apps',
    a: [
      'This is almost always the ROM rather than Exhale. Android lets manufacturers kill background services aggressively, and music players are the first thing to go.',
      'Two things fix it nearly every time. Set Exhale to Unrestricted under Android Settings → Apps → Exhale → Battery, and make sure the notification permission is granted — playback runs behind a foreground-service notification, and on some builds losing the notification takes the playback with it.',
      'Xiaomi and HyperOS additionally need Autostart enabled. One UI keeps a separate "Deep sleeping apps" list that Exhale has to stay out of. OnePlus, Oppo and Realme hide the same switch under battery optimization. If it still dies after all of that, file it as a bug and say which ROM — that detail is most of the diagnosis.',
    ],
  },
  {
    id: 'codec',
    keywords: 'audio quality, sound quality, bitrate, kbps, opus, aac, premium',
    q: 'Why does everything play as Opus at about 140 kbps?',
    a: [
      'Because on a free account that is the best stream there is. YouTube serves exactly two music renditions to a signed-out or non-Premium client: Opus at roughly 130 to 160 kbps, and AAC at 128. Exhale already asks every client it knows about and keeps the best answer, which is the Opus.',
      'The 256 kbps AAC rendition is real, but it is gated behind Premium on YouTube’s side. No setting in any app reaches it without a subscription. That is a server decision, not a client one, and anyone telling you otherwise is selling something.',
      'Settings → Player → Codec lets you prefer AAC regardless, which is the default. It decodes in hardware on every Android device, where Opus is decoded in software, and it is the setting that unlocks 256 kbps if you are signed in with Premium. Worth knowing that on a free account it means 128 kbps — a lower number than the Opus it replaces. Choose "Highest bitrate" if you would rather have the fatter stream whatever the codec.',
      'One catch either way: audio is cached per song, not per codec, so anything you have already played keeps serving the bytes it was first fetched with. Clear the cache to hear the change on old music.',
    ],
  },
  {
    id: 'login',
    keywords: 'sign in, log in, account, google, premium',
    q: 'Do I need a YouTube account?',
    a: [
      'No. Search, playback, playlists and downloads all work signed out, and that is how most people run it.',
      'Signing in changes three things: recommendations start reflecting your own history, Premium-only albums become visible, and — with an actual Premium subscription — the higher-bitrate AAC stream becomes reachable. Nothing else in the app depends on it.',
    ],
  },
  {
    id: 'apk',
    keywords: 'download, install, arm64, armeabi, universal',
    q: 'Which APK should I download?',
    a: [
      'Take the arm64 one. Every Android phone made in roughly the last eight years is arm64, and that build is meaningfully smaller because it carries one set of native libraries instead of all of them.',
      'The universal APK is the safe answer if you are not sure, or if you are installing onto something unusual. The armeabi build is for genuinely old 32-bit hardware.',
    ],
  },
  {
    id: 'update',
    keywords: 'update, upgrade, new version, play store, lose data, keep library',
    q: 'How do I update, and will I lose my library?',
    a: [
      'You will not lose anything. Every release is signed with the same key, so a new APK installs straight over the old one and your library, playlists, downloads and settings stay exactly where they are.',
      'Exhale checks for releases itself — turn on update notifications in settings and it downloads in the background, then hands the file to the installer when it is ready. You can also just take the APK from the releases page whenever you feel like it.',
      'It is not on the Play Store and will not be. Google does not accept clients that stream YouTube this way.',
    ],
  },
  {
    id: 'backup',
    keywords: 'backup, restore, transfer, new phone, migrate, export',
    q: 'How do I move everything to a new phone?',
    a: [
      'Settings → Backup and restore. It writes a single file holding your library, playlists and settings; copy it across, restore it on the new device, done.',
      'Downloaded audio is deliberately not in that file — it would make the backup enormous, and re-downloading is quick. Everything that took you time to build is in there.',
    ],
  },
  {
    id: 'lyrics',
    keywords: 'lyrics, synced, out of sync, wrong lyrics, missing lyrics, lrclib, kugou',
    q: 'Lyrics are missing, wrong, or out of time',
    a: [
      'Lyrics come from LRCLIB and KuGou, both community-maintained, and each can be switched on or off independently in settings. When one has no entry for a track, enabling the other often finds it.',
      'Timing belongs to the uploaded lyric file rather than to the app, so a track that drifts will drift the same way in every app reading the same source. LRCLIB takes corrections, and fixing it there fixes it everywhere.',
    ],
  },
  {
    id: 'import',
    keywords: 'import, spotify, apple music, transfer playlists',
    q: 'Can I import my existing playlists?',
    a: [
      'Yes — Import playlist, from the library screen. Paste a YouTube or YouTube Music playlist link and it pulls the tracks across.',
      'Playlists from services that are not YouTube have no import path today, because there is no link Exhale can read. If that is what you were hoping for, file it as an idea and name the service.',
    ],
  },
]

/* ------------------------------------------------------------- the prefill */

/**
 * A GitHub "new issue" URL with the form already filled in.
 *
 * Issue *forms* prefill by field id rather than by dumping one blob into the
 * body, which is the whole reason the templates are .yml: every value below
 * lands in its own labelled box, so the reporter arrives at a filled form
 * instead of a wall of markdown they have to edit around.
 *
 * The budget below is why logs are not collected on this page. GitHub stops
 * honouring prefill somewhere north of 8KB of URL and it fails *silently* — the
 * page loads, the boxes are simply empty — so anything unbounded is left to the
 * GitHub side, where it is a normal textarea with no limit at all.
 */
const URL_BUDGET = 6000

export function issueUrl(path, values) {
  const params = new URLSearchParams()
  params.set('template', path.template)
  if (path.labels) params.set('labels', path.labels)

  const title = (values.__title || '').trim()
  if (title) params.set('title', path.titlePrefix + title)

  for (const field of path.fields) {
    const value = (values[field.id] || '').trim()
    if (value) params.set(field.id, value)
  }

  const url = `${NEW_ISSUE}?${params.toString()}`
  return { url, tooLong: url.length > URL_BUDGET }
}
