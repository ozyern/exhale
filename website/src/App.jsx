import { useCallback, useEffect, useRef, useState } from 'react'
import Changelog from './components/Changelog.jsx'
import { AnnounceBar, RELEASE_PATH, SiteFooter, TopBar } from './components/Chrome.jsx'
import Dock from './components/Dock.jsx'
import Features from './components/Features.jsx'
import Phone from './components/Phone.jsx'
import Segments from './components/Segments.jsx'
import Words from './components/Words.jsx'
import {
  ABOUT_FACTS,
  FEATURES,
  OPEN_FACTS,
  RELEASES,
  REPO,
  SHOTS_304,
  SHOT304,
  TAGLINE,
  VERSION,
} from './content.js'
import { useOnScreen, useReveals, useScrollProgress, prefersReducedMotion } from './hooks.js'
import { Link } from './router.jsx'

/*
 * The landing page, built the way a product page is: a handful of full-width
 * moments, each one thing said large, in the app's own materials.
 *
 * The materials are the point. Exhale has a look — the gold key art from its
 * About screen, the gold mark, and a background lit by whatever is playing —
 * and a page in neutral black and grey could be advertising any player. So the
 * hero stands on the key art, the colour section is lit by an actual album
 * cover, and the accent everywhere is the mark's gold.
 */

const SECTIONS = [
  { id: 'overview', label: 'Overview' },
  { id: 'color', label: 'Color' },
  { id: 'lyrics', label: 'Lyrics' },
  { id: 'design', label: 'Design' },
  { id: 'download', label: 'Download' },
]

/** The screens the hero phone cycles through, in order. */
const HERO_CYCLE = [SHOT304.home, SHOT304.player, SHOT304.lyrics, SHOT304.about]

/* ------------------------------------------------------------------- hero */

function HeroPhone() {
  const [step, setStep] = useState(0)
  const ref = useRef(null)
  const live = useOnScreen(ref, '80px')

  useEffect(() => {
    if (!live || prefersReducedMotion()) return
    const id = window.setInterval(() => setStep((s) => (s + 1) % HERO_CYCLE.length), 3400)
    return () => window.clearInterval(id)
  }, [live])

  return (
    <div className="hx-device" ref={ref} data-scroll="1.1">
      <Phone shots={SHOTS_304} index={HERO_CYCLE[step]} />
    </div>
  )
}

function Hero({ onNotes }) {
  return (
    <section className="hx" id="overview">
      <div className="hx-art" aria-hidden="true" />
      <div className="hx-copy">
        <p className="hx-kicker reveal">{TAGLINE}</p>
        <h1 className="hx-title reveal" style={{ '--d': '80ms' }}>
          <img src="/media/wordmark.png" alt="Exhale" width="900" height="258" />
        </h1>
        <p className="hx-lede reveal" style={{ '--d': '160ms' }}>
          The music player for Android that breathes.
        </p>
        <div className="hx-actions reveal" style={{ '--d': '240ms' }}>
          <a className="btn btn-gold" href={RELEASES} target="_blank" rel="noreferrer">
            Download for Android
          </a>
          <button type="button" className="btn btn-glass" onClick={onNotes}>
            What’s new in {VERSION}
          </button>
        </div>
        <p className="hx-meta reveal" style={{ '--d': '300ms' }}>
          Free and open source · Android 13 and newer · No account
        </p>
      </div>
      <HeroPhone />
    </section>
  )
}

/* -------------------------------------------------------------- statement */

const STATEMENT =
  'Glass you can see through. Lyrics that land on the word. Color that comes from the song itself.'

/**
 * One sentence, lit a word at a time by the scroll.
 *
 * The section is tall and its text is pinned, so the reader scrolls *through*
 * it rather than past it. Each word's brightness is its own index against
 * `--p`, so the fill is continuous and runs backwards when you scroll up.
 */
function Statement() {
  const words = STATEMENT.split(' ')
  return (
    <section className="say" data-scroll="1.6">
      <p className="say-text" style={{ '--n': words.length }}>
        {words.map((word, i) => (
          <span key={`${word}-${i}`} style={{ '--i': i }}>
            {word}{' '}
          </span>
        ))}
      </p>
    </section>
  )
}

/* ------------------------------------------------------------------ color */

/**
 * Lit by the cover, the way the player is.
 *
 * The section's background is the player screenshot itself, blown up and
 * blurred until only its colour is left — which is exactly how the app builds
 * the live background behind its own screens.
 */
function Color() {
  const shot = SHOTS_304[SHOT304.player]
  return (
    <section className="hue" id="color">
      <div className="hue-light" aria-hidden="true" style={{ backgroundImage: `url(${shot.src})` }} />
      <div className="hue-inner shell">
        <div className="hue-copy">
          <p className="kicker reveal">Color</p>
          <h2 className="display reveal" style={{ '--d': '80ms' }}>
            The song brings its own color.
          </h2>
          <p className="lede reveal" style={{ '--d': '160ms' }}>
            Open a track and the player takes its palette from the cover: the
            artwork fills the screen, and the light behind every page drifts
            with it. Nothing is picked by hand. It is the artwork, sampled.
          </p>
        </div>
        <div className="hue-device reveal" style={{ '--d': '120ms' }}>
          <Phone shots={SHOTS_304} index={SHOT304.player} />
        </div>
      </div>
    </section>
  )
}

/* ----------------------------------------------------------------- lyrics */

function Lyrics() {
  const shot = SHOTS_304[SHOT304.lyrics]
  return (
    <section className="ly shell" id="lyrics">
      <div className="ly-panel reveal">
        <div className="ly-light" aria-hidden="true" style={{ backgroundImage: `url(${shot.src})` }} />
        <div className="ly-copy">
          <p className="kicker">Lyrics</p>
          <h2 className="display">Every word, on time.</h2>
          <p className="lede">
            Synced lyrics that light a word at a time, with the lines you have
            not reached falling out of focus behind them. Matched on title and
            artist, not duration alone — this song’s words, not a same-length
            stranger’s.
          </p>
        </div>
        <div className="ly-device" data-scroll="1">
          <Phone shots={SHOTS_304} index={SHOT304.lyrics} />
        </div>
      </div>
    </section>
  )
}

/* ----------------------------------------------------------------- design */

/**
 * A tile with a real screen rising out of its bottom edge.
 *
 * No device frame: at this size a frame is a border around a screenshot that
 * spends its pixels on bezel. The tile crops the screen instead, so it reads as
 * the app coming up through the page.
 */
function ShotTile({ kicker, title, body, shot, delay = 0 }) {
  return (
    <article className="ft reveal" style={{ '--d': `${delay}ms` }}>
      <div className="ft-copy">
        <p className="ft-kicker">{kicker}</p>
        <h3 className="ft-title">{title}</h3>
        <p className="ft-body">{body}</p>
      </div>
      <div className="ft-shot">
        <img
          src={SHOTS_304[shot].src}
          alt={SHOTS_304[shot].alt}
          width="720"
          height="1584"
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
    <section className="dz shell" id="design">
      <div className="dz-head">
        <p className="kicker reveal">Design</p>
        <h2 className="display reveal" style={{ '--d': '80ms' }}>
          Built like the phone it runs on.
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
          kicker="Home"
          title="It leads with what you play."
          body="Top Picks for You, learned from your own listening, then Recently Played — at Apple Music’s rhythm."
          shot={SHOT304.home}
        />
        <ShotTile
          kicker="Offline"
          title="It plays with the radio off."
          body="Downloads play with no network at all, and the queue skips to what is actually on the phone."
          shot={SHOT304.downloads}
          delay={90}
        />
        <ShotTile
          kicker="Settings"
          title="One material, everywhere."
          body="Grouped glass tables, hairlines inset to the label, and a coloured glyph on every row."
          shot={SHOT304.settings}
        />
        <ShotTile
          kicker="About"
          title="Every build, a poster."
          body="The gold key art, the wordmark, the build you are on — and whether it is current. It updates itself."
          shot={SHOT304.about}
          delay={90}
        />
      </div>
    </section>
  )
}

/* ---------------------------------------------------------------- numbers */

const NUMBERS = [
  { value: '0', label: 'ads, ever' },
  { value: '0', label: 'accounts to make' },
  { value: '1', label: 'APK for every phone' },
  { value: '20', label: 'languages' },
]

function Numbers() {
  return (
    <section className="nums shell" aria-label="Exhale in numbers">
      {NUMBERS.map((n, i) => (
        <div className="num reveal" key={n.label} style={{ '--d': `${i * 80}ms` }}>
          <b>{n.value}</b>
          <span>{n.label}</span>
        </div>
      ))}
    </section>
  )
}

/* ------------------------------------------------------------------- page */

export default function App() {
  const [section, setSection] = useState(0)
  const [notes, setNotes] = useState(false)

  // Stable, because the sheet's effect takes it as a dependency and a fresh
  // identity every render would tear the scroll lock down and rebuild it.
  const closeNotes = useCallback(() => setNotes(false), [])
  const openNotes = useCallback(() => setNotes(true), [])

  useReveals()
  useScrollProgress()

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
    <div id="top" className="home">
      {/* Three tiers, narrowing as they go: the release strip is news, the
          topbar is the project, and the segmented control is this page. */}
      <AnnounceBar />
      <TopBar />
      <div className="segbar">
        <Segments items={SECTIONS} active={section} onSelect={goToSection} />
      </div>

      <main>
        <Hero onNotes={openNotes} />
        <Statement />
        <Color />
        <Lyrics />
        <Design />
        <Numbers />

        <section className="tour-features shell" id="features">
          <h2 className="bigtitle">
            <Words text="And everything else." />
          </h2>
          <Features items={FEATURES} />
        </section>

        <section className="finale shell" id="download">
          <div className="getit">
            <div className="getit-art" aria-hidden="true" />
            <img className="getit-icon reveal" src="/media/icon.png" alt="" width="120" height="120" />
            <h2 className="display">
              <Words text="Put it on your phone." />
            </h2>
            <p className="lede reveal" style={{ '--d': '160ms' }}>
              One APK for every Android 13 phone. No account, no ads, nothing
              phoning home, and no store deciding whether you may have it.
            </p>

            <div className="btnrow reveal" style={{ '--d': '240ms' }}>
              <a className="btn btn-gold" href={RELEASES} target="_blank" rel="noreferrer">
                Download v{VERSION}
              </a>
              <button type="button" className="btn btn-glass" onClick={openNotes}>
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
                <b>cd</b> Exhale{'\n'}
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
