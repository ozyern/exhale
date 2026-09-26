import { useEffect } from 'react'
import App from './App.jsx'
import Release from './pages/Release.jsx'
import Support from './pages/Support.jsx'
import { RELEASE } from './release.js'
import { RELEASE_304 } from './release304.js'
import { useRoute } from './router.jsx'

/**
 * Three pages, two string comparisons.
 *
 * `/release` without a version resolves to the current one, so the short URL
 * keeps working after the next release rather than rotting into a 404 — and any
 * path that is neither a release nor support is the home page, which is what a
 * marketing site should do with a typo.
 */
const isRelease = (path) => path === '/release' || path.startsWith('/release/')

/**
 * Which release a path is asking for.
 *
 * `/release` is always the newest one, so the short URL keeps working after the
 * next release rather than rotting into a link to an old announcement. An
 * unknown version falls through to the newest too: a typo should land on the
 * current release, not on a 404.
 */
const RELEASES_BY_VERSION = { [RELEASE.version]: RELEASE, [RELEASE_304.version]: RELEASE_304 }
const LATEST = RELEASE_304

const releaseFor = (path) => {
  const version = path.replace(/^\/release\/?/, '').replace(/\/+$/, '')
  return RELEASES_BY_VERSION[version] ?? LATEST
}
const isSupport = (path) => path === '/support' || path.startsWith('/support/')

/**
 * The title and the canonical link are the two things a crawler and a preview
 * card read, and neither follows a client-side navigation on its own.
 */
const HEAD = {
  home: {
    title: 'Exhale — a music player that breathes',
    description:
      'A fast, open-source music player for Android. Live liquid glass, lyrics on the beat, and color that follows your album art.',
    path: '/',
  },
  // Filled in per release below: the title, the description and the canonical
  // URL all name the version being read, not whichever one this file imported.
  release: null,
  support: {
    title: 'Exhale — support',
    description:
      'Report a bug, suggest an idea, or find the answer outright. Background playback, audio quality, lyrics, backups and updates, answered.',
    path: '/support',
  },
}

export default function Site() {
  const path = useRoute()
  const page = isRelease(path) ? 'release' : isSupport(path) ? 'support' : 'home'
  const release = page === 'release' ? releaseFor(path) : null

  useEffect(() => {
    const head =
      page === 'release'
        ? {
            title: `Exhale ${release.version} — release notes`,
            description: release.dek,
            path: `/release/${release.version}`,
          }
        : HEAD[page]
    document.title = head.title

    const set = (selector, attribute, value) => {
      const node = document.head.querySelector(selector)
      if (node) node.setAttribute(attribute, value)
    }

    set('meta[name="description"]', 'content', head.description)
    set('meta[property="og:title"]', 'content', head.title)
    set('meta[property="og:description"]', 'content', head.description)
    set('meta[property="og:url"]', 'content', `https://exhale.ozyern.me${head.path}`)
    set('link[rel="canonical"]', 'href', `https://exhale.ozyern.me${head.path}`)
  }, [page, release])

  if (page === 'release') return <Release release={release} />
  if (page === 'support') return <Support />
  return <App />
}
