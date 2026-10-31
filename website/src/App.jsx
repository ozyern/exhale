import { useCallback, useEffect, useRef, useState } from 'react'
import Changelog from './components/Changelog.jsx'
import { AnnounceBar, RELEASE_PATH, SiteFooter, TopBar } from './components/Chrome.jsx'
import Dock from './components/Dock.jsx'
import Features from './components/Features.jsx'
import Words from './components/Words.jsx'
import HighlightSlider from './components/HighlightSlider.jsx'
import Segments from './components/Segments.jsx'
import Phone from './components/Phone.jsx'
import KeyHero from './components/KeyHero.jsx'
import {
  ABOUT_FACTS,
  APK_URL,
  FEATURES,
  LYRICS_VIDEO,
  OPEN_FACTS,
  RELEASES,
  REPO,
  SHOT,
  SHOTS,
  SHOTS_304,
  SHOT304,
  VERSION,
} from './content.js'
import { useHeroScroll, useOnScreen, useReveals, prefersReducedMotion } from './hooks.js'
import { Link } from './router.jsx'
import { LATEST_RELEASE } from './releases.js'

/** Screens from the 1.0.405 build, taken on a phone. */
const SHOT_405 = {
  artist: { src: '/shots/405-artist.jpg', alt: 'Sabrina Carpenter’s artist page: her portrait full bleed, Play between follow and shuffle, the latest album, then Top songs.' },
  settings: { src: '/shots/405-settings.jpg', alt: 'Library: the Exhale card, then playlists, artists, albums and songs as grouped glass tables.' },
}

/** Screens from the 1.0.506 build, taken on a phone. */
const SHOT_506 = {
  home: { src: '/shots/506-home.jpg', alt: 'Home: Top Picks for You over Recents, the mini player above the glass tab bar.' },
  lyrics: { src: '/shots/506-lyrics-word.jpg', alt: 'Word-synced lyrics for Good Graces, the sung words lit, above Spatial Audio and the 8D control.' },
  about: { src: '/shots/506-about-card.jpg', alt: 'About: the Exhale 1.0.506 card over gold light, saying Exhale is up to date.' },
}

const SECTIONS = [
  { id: 'overview', label: 'Overview' },
  { id: 'highlights', label: 'Highlights' },
  { id: 'film', label: 'Ultra fluid' },
  { id: 'lyrics', label: 'Lyrics' },
  { id: 'design', label: 'Design' },
  { id: 'features', label: 'Features' },
  { id: 'download', label: 'Download' },
]

/* ------------------------------------------------------------------ bars */

function Bars({ active, onSelect }) {
  return (
    <>
      {/* Three tiers, narrowing as they go: the release strip is news, the
          topbar is the project, and the floating segmented capsule is this page. */}
      <AnnounceBar />
      <TopBar />

      <div className="segbar">
        <Segments items={SECTIONS} active={active} onSelect={onSelect} />
      </div>
    </>
  )
}

/* ------------------------------------------------------------ highlights */

/**
 * "Get the highlights." The release in a row of tall tiles you swipe through:
 * a sentence at the top of each, its lead in white, and the screen it is about
 * rising out of the bottom. Arrows and dots underneath, as on Apple's pages.
 */
const HIGHLIGHTS = [
  { shot: SHOT_506.home, lead: 'Home leads with what you play.', rest: 'Top Picks for You is learned from your own listening, and says why each one is there.' },
  { shot: { src: '/shots/player-color.jpg', alt: 'The player tinted from the cover: Bad for Business, the controls in its warm brown.' }, lead: 'The song brings its own color.', rest: 'The cover fills the player, and the controls take their tint from it.' },
  { shot: SHOT_506.lyrics, lead: 'Lyrics land on the word.', rest: 'The line being sung lights up as it is sung, and the rest fall out of focus.' },
  { shot: { src: '/shots/downloaded.jpg', alt: 'Downloaded: three songs saved to the phone, ready with no network.' }, lead: 'It plays with the radio off.', rest: 'Downloads play with no network at all, cover edge to edge.' },
  { shot: SHOT_405.artist, lead: 'Artists, the way they should look.', rest: 'Portrait, story and the songs that matter, on one page.' },
  { shot: SHOT_405.settings, lead: 'One material, everywhere.', rest: 'Grouped glass tables all the way down to Settings.' },
  { shot: SHOT_506.about, lead: 'It updates itself.', rest: 'Check, download and install without leaving the app.' },
]

/* ------------------------------------------------------------------ film */

/**
 * "Ultra fluid." The 1.0.405 film, as ColorOS's "smooth to the touch" section
 * shows its own: the title over a framed card, and as the page scrolls the
 * card grows — wider, square-cornered — until the film fills the window and
 * holds there a moment before the page moves on.
 *
 * Scroll-linked through one custom property written from a rAF-throttled
 * handler, so scrolling never re-renders React. Plays muted while on screen.
 */
function Film({ running }) {
  const section = useRef(null)
  const video = useRef(null)
  const live = useOnScreen(section, '0px')

  useEffect(() => {
    const node = section.current
    if (!node) return undefined
    const reduced = prefersReducedMotion()
    let frame = 0
    let travel = 1
    let nodeTop = 0
    const measure = () => {
      const rect = node.getBoundingClientRect()
      nodeTop = window.scrollY + rect.top
      travel = Math.max(1, (rect.height - window.innerHeight) * 0.55)
    }
    const apply = () => {
      frame = 0
      const dist = window.scrollY - nodeTop
      const p = reduced ? 1 : Math.min(1, Math.max(0, dist / travel))
      node.style.setProperty('--film', p.toFixed(4))
    }
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(apply)
    }
    const onResize = () => {
      measure()
      apply()
    }
    measure()
    apply()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onResize)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onResize)
      if (frame) cancelAnimationFrame(frame)
    }
  }, [])

  useEffect(() => {
    const v = video.current
    if (!v) return
    if (live && running) v.play().catch(() => {})
    else v.pause()
  }, [live, running])

  const watch = () => {
    const v = video.current
    if (!v) return
    v.currentTime = 0
    v.play().catch(() => {})
    v.requestFullscreen?.().catch(() => {})
  }

  return (
    <section className="film" id="film" ref={section}>
      <div className="film-stick">
        <div className="film-head">
          <p className="film-kicker">The system runs smoothly</p>
          <h2 className="film-title">
            Ultra fluid. <span className="film-grad">Knows your music.</span>
          </h2>
        </div>
        <div className="film-frame">
          <video
            ref={video}
            src="/media/film-506.mp4"
            poster="/media/film-506-poster.jpg"
            muted
            loop
            playsInline
            preload="metadata"
            aria-label="The Exhale 1.0.506 promo film."
          />
          <button type="button" className="film-watch" onClick={watch}>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5.5v13l11-6.5z" /></svg>
            Watch the film
          </button>
        </div>
      </div>
    </section>
  )
}

/* ------------------------------------------------------------------ tour */

/**
 * Lyrics, as one wide panel.
 *
 * The claim is entirely in the timing, so this is the one place on the page
 * that plays the recording rather than showing a still — and it plays only
 * while the panel is on screen and the page has not been paused.
 */
function LyricsPanel({ running }) {
  const ref = useRef(null)
  const live = useOnScreen(ref, '0px')

  return (
    <section className="tour-lyrics reveal" id="lyrics" ref={ref}>
      <div className="tour-copy">
        <div className="live-pill" aria-label="Live synced playback indicator">
          <span className="eq-bars" aria-hidden="true">
            <span className="eq-bar" />
            <span className="eq-bar" />
            <span className="eq-bar" />
            <span className="eq-bar" />
          </span>
          <span>Live Synced Audio</span>
        </div>
        <p className="kicker">Lyrics</p>
        <h2 className="headline">Every word, on time.</h2>
        <p className="lede">
          Synced lyrics that light a word at a time, with the lines you have
          not reached falling out of focus behind them. Matched on title and
          artist, not duration alone — this song’s words, not a same-length
          stranger’s.
        </p>
        <p className="tour-note">A screen recording of the shipping build, at normal speed.</p>
      </div>
      <div className="tour-lyrics-device">
        <Phone shots={SHOTS} index={SHOT.lyrics} video={{ ...LYRICS_VIDEO, on: true, running: running && live }} />
      </div>
    </section>
  )
}

/**
 * A tile with a real screen rising out of its bottom edge.
 *
 * No device frame: at this size a frame is a border around a screenshot that
 * spends its pixels on bezel. The screen is cropped by the tile instead, so it
 * reads as the app coming up through the page rather than a picture of a
 * phone lying on it.
 */
const handleCardMouseEnter = (e) => {
  e.currentTarget._cachedRect = e.currentTarget.getBoundingClientRect()
}

const handleCardSpotlight = (e) => {
  let rect = e.currentTarget._cachedRect
  if (!rect) {
    rect = e.currentTarget.getBoundingClientRect()
    e.currentTarget._cachedRect = rect
  }
  e.currentTarget.style.setProperty('--card-mx', `${(e.clientX - rect.left).toFixed(1)}px`)
  e.currentTarget.style.setProperty('--card-my', `${(e.clientY - rect.top).toFixed(1)}px`)
}

function ShotTile({ kicker, title, body, shot, crop = 'top', delay = 0 }) {
  // An index into the 1.0.304 set, or a shot of its own.
  const pic = typeof shot === 'number' ? SHOTS_304[shot] : shot
  return (
    <article className="ft reveal" style={{ '--d': `${delay}ms` }} onMouseEnter={handleCardMouseEnter} onMouseMove={handleCardSpotlight}>
      <div className="ft-copy">
        <p className="ft-kicker">{kicker}</p>
        <h3 className="ft-title">{title}</h3>
        <p className="ft-body">{body}</p>
      </div>
      <div className="ft-shot" data-crop={crop}>
        <img
          src={pic.src}
          alt={pic.alt}
          width="720"
          height="1280"
          loading="lazy"
          decoding="async"
        />
      </div>
    </article>
  )
}

function Design() {
  const [dock, setDock] = useState(0)

  return (
    <section className="tour-design shell" id="design">
      <div className="tour-head">
        <p className="kicker reveal">Design</p>
        <h2 className="headline reveal" style={{ '--d': '80ms' }}>
          Built like the phone
          <br />
          it runs on.
        </h2>
      </div>

      <div className="ft-grid">
        {/* The one tile you can put your hands on: the dock is rebuilt in
            markup rather than photographed, and a screenshot can't be
            dragged. */}
        <article className="ft ft-wide reveal" onMouseEnter={handleCardMouseEnter} onMouseMove={handleCardSpotlight}>
          <div className="ft-copy">
            <p className="ft-kicker">The material</p>
            <h3 className="ft-title">Liquid glass, not a picture of one.</h3>
            <p className="ft-body">
              The dock, the sheets and the search field blur what is actually
              behind them and bend it at the rim, every frame. Drag the
              capsule — it stretches the way it travels and overshoots before
              it settles.
            </p>
          </div>
          <div className="dock-demo ft-dock">
            <div className="dock-demo-art" aria-hidden="true">
              <span />
              <span />
              <span />
            </div>
            <Dock active={dock} onSelect={setDock} compact />
          </div>
        </article>

        <ShotTile
          kicker="Color"
          title="The song brings its own color."
          body="The player takes its palette from the cover, with the artwork filling the screen behind the controls."
          shot={SHOT304.player}
          crop="middle"
        />
        <ShotTile
          kicker="Offline"
          title="It plays with the radio off."
          body="Downloads play with no network at all, and the queue skips to what is actually on the phone."
          shot={SHOT304.downloads}
          delay={90}
        />
        <ShotTile
          kicker="Home"
          title="It leads with what you play."
          body="Top Picks for You, learned from your own listening, then Recently Played — at Apple Music’s rhythm."
          shot={SHOT304.home}
        />
        <ShotTile
          kicker="Library"
          title="One material, everywhere."
          body="Grouped glass tables, hairlines inset to the label, and a coloured glyph on every row."
          shot={SHOT_405.settings}
          delay={90}
        />
      </div>
    </section>
  )
}

/* ------------------------------------------------------------------ page */

function CopyCodeBlock({ text, children }) {
  const [copied, setCopied] = useState(false)
  const onCopy = () => {
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(text).then(() => {
        setCopied(true)
        setTimeout(() => setCopied(false), 2200)
      }).catch(() => {})
    }
  }

  return (
    <div className="code-wrap">
      <pre className="code">{children}</pre>
      <button
        type="button"
        className="code-copy-btn"
        onClick={onCopy}
        aria-label="Copy build commands to clipboard"
      >
        {copied ? (
          <>
            <svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg>
            <span>Copied!</span>
          </>
        ) : (
          <>
            <svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2" /><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" /></svg>
            <span>Copy</span>
          </>
        )}
      </button>
    </div>
  )
}

export default function App() {
  const [ambient, setAmbient] = useState(!prefersReducedMotion())
  const [section, setSection] = useState(0)
  const [notes, setNotes] = useState(false)

  useEffect(() => {
    const scrollParam = new URLSearchParams(window.location.search).get('scroll')
    if (scrollParam) {
      window.scrollTo(0, parseInt(scrollParam, 10))
    }
  }, [])

  // Stable, because the sheet's effect takes it as a dependency and a fresh
  // identity every render would tear the scroll lock down and rebuild it.
  const closeNotes = useCallback(() => setNotes(false), [])
  const openNotes = useCallback(() => setNotes(true), [])

  useReveals()
  useHeroScroll()

  // Scroll spy: the capsule tracks where you are, not only where you clicked.
  //
  // Clicks need a lock. Smooth-scrolling from Overview to Download crosses
  // every section between them and the capsule stutters through four positions
  // before landing, which reads as broken animation. So a click names its
  // destination and everything else is ignored until that destination arrives.
  const pending = useRef(null)

  useEffect(() => {
    if (!('IntersectionObserver' in window)) return
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return
          if (pending.current) {
            if (entry.target.id !== pending.current) return
            pending.current = null
          }
          const index = SECTIONS.findIndex((s) => s.id === entry.target.id)
          if (index >= 0) setSection(index)
        })
      },
      { rootMargin: '-45% 0px -50% 0px', threshold: 0 },
    )
    SECTIONS.forEach(({ id }) => {
      const node = document.getElementById(id)
      if (node) io.observe(node)
    })
    return () => io.disconnect()
  }, [])

  // The lock always expires, or a destination that never arrives (an anchor
  // already on screen, an interrupted scroll) would leave the spy deaf.
  const goToSection = (index) => {
    pending.current = SECTIONS[index]?.id ?? null
    setSection(index)
    window.setTimeout(() => {
      pending.current = null
    }, 1400)
  }

  return (
    <div id="top">
      <Bars active={section} onSelect={goToSection} />

      <main>
        <div className="ambient-mesh" aria-hidden="true">
          <div className="ambient-orb ambient-orb-1" />
          <div className="ambient-orb ambient-orb-2" />
          <div className="ambient-orb ambient-orb-3" />
        </div>
        <KeyHero
          id="overview"
          version="506"
          fullVersion="1.0.506"
          line="Silky Smooth. Knows your music."


          title="Ultra fluid. Knows your music."
          body="Live glass on every surface, lyrics that land on the beat, and color that follows whatever is playing. Open source, for Android 13 and newer."
          primary={
            <a className="kh-pill kh-pill-primary" href={APK_URL} target="_blank" rel="noreferrer">
              Download Exhale
            </a>
          }
          secondary={
            <Link className="kh-pill kh-pill-secondary" to={RELEASE_PATH}>
              What’s new ›
            </Link>
          }
        />
        <HighlightSlider items={HIGHLIGHTS} />

        <Film running={ambient} />

        <div className="tour shell">
          <LyricsPanel running={ambient} />
        </div>

        <Design />

        <section className="tour-features shell" id="features">
          <h2 className="bigtitle">
            <Words text="And everything else." />
          </h2>
          <Features items={FEATURES} />
        </section>

        <section className="finale shell" id="download">
          <div className="getit">
            <img className="getit-icon reveal" src="/logo.png" alt="Exhale app icon" width="96" height="96" />
            <h2 className="headline">
              <Words text="Put it on your phone." />
            </h2>
            <p className="lede reveal" style={{ '--d': '160ms' }}>
              One APK for every Android 13 phone. No account, no ads, nothing
              phoning home, and no store deciding whether you may have it.
            </p>

            <div className="btnrow reveal" style={{ '--d': '240ms' }}>
              <a className="btn" href={APK_URL} target="_blank" rel="noreferrer">
                Download v{VERSION}
              </a>
              <button type="button" className="btn btn-ghost" onClick={openNotes}>
                What&rsquo;s new
              </button>
            </div>

            {/* Two documents, and the difference is length. The sheet is four
                groups and a sentence each, for someone deciding. This is the
                long read, for someone who has already decided. */}
            <Link className="textlink getit-read" to={RELEASE_PATH}>
              Read the full {VERSION} release notes <i>&rsaquo;</i>
            </Link>

            <dl className="facts reveal" style={{ '--d': '300ms' }}>
              {ABOUT_FACTS.map((fact) => (
                <div key={fact.label}>
                  <dt>{fact.label}</dt>
                  <dd>{fact.value}</dd>
                </div>
              ))}
            </dl>
          </div>

          <div className="open">
            <div className="open-copy reveal">
              <p className="ft-kicker">Open source</p>
              <h3 className="ft-title">Yours to read. Yours to compile.</h3>
              <dl className="open-facts">
                {OPEN_FACTS.map((fact) => (
                  <div key={fact.value}>
                    <dt>{fact.value}</dt>
                    <dd>{fact.body}</dd>
                  </div>
                ))}
              </dl>
            </div>
            <div className="open-build reveal" style={{ '--d': '90ms' }}>
              <p className="ft-kicker">Build it yourself</p>
              <CopyCodeBlock text={`git clone ${REPO}.git\ncd exhale\n./gradlew assembleUniversalDebug`}>
                <b>git</b> clone {REPO}.git{'\n'}
                <b>cd</b> exhale{'\n'}
                <b>./gradlew</b> assembleUniversalDebug
              </CopyCodeBlock>
              <p className="subnote">
                Sideloading asks for permission once; the app checks GitHub for
                its own updates after that.
              </p>
            </div>
          </div>
        </section>
      </main>

      <SiteFooter onNotes={openNotes} />


      <Changelog open={notes} onClose={closeNotes} />
    </div>
  )
}
