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
  about: { src: '/shots/405-about.jpg', alt: 'About: the Exhale 1.0.405 card over gold light, saying Exhale is up to date, above the maintainer and Connect.' },
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
  { shot: { src: '/shots/home-listen-now.jpg', alt: 'Listen Now: Top Picks for You over Recents, under the glass dock.' }, lead: 'Home leads with what you play.', rest: 'Top Picks for You is learned from your own listening, and says why each one is there.' },
  { shot: { src: '/shots/player-color.jpg', alt: 'The player tinted from the cover: Bad for Business, the controls in its warm brown.' }, lead: 'The song brings its own color.', rest: 'The cover fills the player, and the controls take their tint from it.' },
  { shot: SHOTS_304[SHOT304.lyrics], lead: 'Lyrics land on the word.', rest: 'The line being sung lights up as it is sung, and the rest fall out of focus.' },
  { shot: { src: '/shots/downloaded.jpg', alt: 'Downloaded: three songs saved to the phone, ready with no network.' }, lead: 'It plays with the radio off.', rest: 'Downloads play with no network at all, cover edge to edge.' },
  { shot: SHOT_405.artist, lead: 'Artists, the way they should look.', rest: 'Portrait, story and the songs that matter, on one page.' },
  { shot: SHOT_405.settings, lead: 'One material, everywhere.', rest: 'Grouped glass tables all the way down to Settings.' },
  { shot: SHOT_405.about, lead: 'It updates itself.', rest: 'Check, download and install without leaving the app.' },
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
    const apply = () => {
      frame = 0
      const rect = node.getBoundingClientRect()
      const vh = window.innerHeight
      // 0 while the section is arriving; 1 once it has been scrolled half its pinned travel.
      const travel = Math.max(1, rect.height - vh)
      const p = reduced ? 1 : Math.min(1, Math.max(0, -rect.top / (travel * 0.55)))
      node.style.setProperty('--film', p.toFixed(4))
    }
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(apply)
    }
    apply()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
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
            src="/media/film-405.mp4"
            poster="/media/film-405-poster.jpg"
            muted
            loop
            playsInline
            preload="metadata"
            aria-label="Exhale 1.0.405: the player flying out of the dock, the dock folding as the page scrolls, lyrics lighting a word at a time, and horizontal lyrics."
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
function ShotTile({ kicker, title, body, shot, crop = 'top', delay = 0 }) {
  // An index into the 1.0.304 set, or a shot of its own.
  const pic = typeof shot === 'number' ? SHOTS_304[shot] : shot
  return (
    <article className="ft reveal" style={{ '--d': `${delay}ms` }}>
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
        <article className="ft ft-wide reveal">
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

export default function App() {
  const [ambient, setAmbient] = useState(!prefersReducedMotion())
  const [section, setSection] = useState(0)
  const [notes, setNotes] = useState(false)

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
        <KeyHero
          id="overview"
          version={LATEST_RELEASE.version.split('.').pop()}
          fullVersion={LATEST_RELEASE.version}
          line="Ultra fluid. Knows your music."
          title="Ultra fluid. Knows your music."
          body="Live glass on every surface, lyrics that land on the beat, and color that follows whatever is playing. Open source, for Android 13 and newer."
          primary={
            <a className="kh-pill" href={APK_URL} target="_blank" rel="noreferrer">
              Download {LATEST_RELEASE.version}
            </a>
          }
          secondary={
            <Link className="kh-link" to={RELEASE_PATH}>
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
              <pre className="code">
                <b>git</b> clone {REPO}.git{'\n'}
                <b>cd</b> exhale{'\n'}
                <b>./gradlew</b> assembleUniversalDebug
              </pre>
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
