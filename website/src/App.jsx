import { useCallback, useEffect, useRef, useState } from 'react'
import Changelog from './components/Changelog.jsx'
import { AnnounceBar, RELEASE_PATH, SiteFooter, TopBar } from './components/Chrome.jsx'
import Dock from './components/Dock.jsx'
import Features from './components/Features.jsx'
import Words from './components/Words.jsx'
import Phone from './components/Phone.jsx'
import Segments from './components/Segments.jsx'
import {
  ABOUT_FACTS,
  FEATURES,
  LYRICS_VIDEO,
  OPEN_FACTS,
  RELEASES,
  REPO,
  SHOT,
  SHOTS,
  SHOTS_304,
  SHOT304,
  TAGLINE,
  VERSION,
} from './content.js'
import { useHeroScroll, useOnScreen, useReveals, prefersReducedMotion } from './hooks.js'
import { Link } from './router.jsx'

const SECTIONS = [
  { id: 'overview', label: 'Overview' },
  { id: 'highlights', label: 'Highlights' },
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
          topbar is the project, and the segmented control is this page. */}
      <AnnounceBar />
      <TopBar />

      <div className="segbar">
        <Segments items={SECTIONS} active={active} onSelect={onSelect} />
      </div>
    </>
  )
}

/* ----------------------------------------------------------- hero device */

/**
 * The screens either side of the phone.
 *
 * `x` and `y` are multiples of a card's own size rather than the container's,
 * so the arrangement keeps its proportions at any width instead of collapsing
 * on a laptop and flying apart on a desktop.
 *
 * The outer pair are the two screens least like a player. Five near-identical
 * now-playing screens would read as one screenshot printed five times.
 */
const FAN = [
  { shot: SHOT.about, x: '-186%', y: '10%', r: '-9deg', s: 0.58, o: 0.5, crop: 'top center', d: 620 },
  { shot: SHOT.artist, x: '-130%', y: '3%', r: '-5.5deg', s: 0.76, o: 0.76, crop: '50% 18%', d: 540 },
  { shot: SHOT.lyrics, x: '-72%', y: '-2%', r: '-2.5deg', s: 0.95, o: 0.96, crop: '50% 30%', d: 460 },
  { shot: SHOT.player, x: '72%', y: '-2%', r: '2.5deg', s: 0.95, o: 0.96, crop: '50% 72%', d: 460 },
  { shot: SHOT.home, x: '130%', y: '3%', r: '5.5deg', s: 0.76, o: 0.76, crop: '50% 78%', d: 540 },
  { shot: SHOT.about, x: '186%', y: '10%', r: '9deg', s: 0.58, o: 0.5, crop: '50% 44%', d: 620 },
]

/**
 * The device under the headline, cycling on its own, flanked by the rest.
 *
 * One claim above, answered six ways at a glance before anyone has scrolled.
 * The phone cycles the screens; the cards hold them still.
 *
 * Answers the hero's pause button — someone who stopped the light at the top
 * of the page meant "stop moving", not "stop that one thing".
 */
function HeroDevice({ running }) {
  const [index, setIndex] = useState(0)
  const ref = useRef(null)
  const live = useOnScreen(ref, '140px')
  const awake = running && live && !prefersReducedMotion()

  useEffect(() => {
    if (!awake) return
    const id = window.setInterval(() => {
      setIndex((current) => (current + 1) % SHOTS.length)
    }, 3600)
    return () => window.clearInterval(id)
  }, [awake])

  return (
    <div className="hero-fan reveal" ref={ref} style={{ '--d': '360ms' }}>
      {/* Decorative: the phone in the middle carries all five of these in
          turn, with the alt text, so announcing them again would read the
          same app to a screen reader six times. */}
      <div className="fan" aria-hidden="true">
        {FAN.map((card, i) => (
          <figure
            className="fan-card"
            key={`${card.shot}-${i}`}
            style={{
              '--x': card.x,
              '--y': card.y,
              '--r': card.r,
              '--s': card.s,
              '--o': card.o,
              '--crop': card.crop,
              '--d': `${card.d}ms`,
            }}
          >
            <img src={SHOTS[card.shot].src} alt="" loading="lazy" decoding="async" />
          </figure>
        ))}
      </div>

      <div className="device-hero">
        <Phone shots={SHOTS} index={index} />
      </div>
    </div>
  )
}

/* -------------------------------------------------------------- lineup */

/**
 * The devices under the opening, the way Apple lays its lineup out: upright,
 * different sizes, on black, the big ones running off the edges of the page and
 * a small one in the middle that the eye lands on. Each rises in on its own beat.
 */
function HeroLineup() {
  return (
    <div className="lineup" aria-hidden="true">
      <div className="table reveal" style={{ '--d': '160ms' }}>
        <div className="obj hw-tab">
          <div className="obj-screen">
            <img src="/media/keyart.jpg" alt="" decoding="async" />
          </div>
        </div>
        <div className="obj hw-phone hw-side">
          <div className="obj-screen">
            <img src={SHOTS_304[SHOT304.lyrics].src} alt="" decoding="async" />
          </div>
        </div>
        <div className="obj hw-phone hw-hero">
          <div className="obj-screen">
            <img src={SHOTS_304[SHOT304.home].src} alt="" decoding="async" />
          </div>
        </div>
        <div className="obj hw-wrist">
          <div className="hw-wrist-face">
            <span className="hw-wrist-time">8:12</span>
            <img src="/logo.png" alt="" decoding="async" />
          </div>
        </div>
      </div>
    </div>
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
  { shot: SHOTS_304[SHOT304.downloads], lead: 'It plays with the radio off.', rest: 'Downloads play with no network at all, cover edge to edge.' },
  { shot: SHOTS[SHOT.artist], lead: 'Artists, the way they should look.', rest: 'Portrait, story and the songs that matter, on one page.' },
  { shot: SHOTS_304[SHOT304.settings], lead: 'One material, everywhere.', rest: 'Grouped glass tables all the way down to Settings.' },
  { shot: SHOTS_304[SHOT304.about], lead: 'It updates itself.', rest: 'Check, download and install without leaving the app.' },
]

function Highlights() {
  const rail = useRef(null)
  const [at, setAt] = useState(0)

  useEffect(() => {
    const node = rail.current
    if (!node) return undefined
    const onScroll = () => {
      const first = node.firstElementChild
      if (!first) return
      const pitch = first.getBoundingClientRect().width + 20
      setAt(Math.round(node.scrollLeft / pitch))
    }
    node.addEventListener('scroll', onScroll, { passive: true })
    return () => node.removeEventListener('scroll', onScroll)
  }, [])

  const goTo = (index) => {
    const node = rail.current
    const card = node?.children[index]
    if (!node || !card) return
    const clamped = Math.max(0, Math.min(HIGHLIGHTS.length - 1, index))
    const target = node.children[clamped]
    node.scrollTo({
      left: target.offsetLeft - node.firstElementChild.offsetLeft,
      behavior: prefersReducedMotion() ? 'auto' : 'smooth',
    })
  }

  return (
    <section className="hl" id="highlights">
      <h2 className="hl-title shell reveal">Get the highlights.</h2>
      <div className="hl-rail" ref={rail}>
        {HIGHLIGHTS.map((item, index) => (
          <article className="hl-card" key={item.lead}>
            <p className="hl-cap">
              <b>{item.lead}</b> {item.rest}
            </p>
            <div className="hl-shot">
              <img src={item.shot.src} alt={item.shot.alt} loading={index < 3 ? 'eager' : 'lazy'} decoding="async" />
            </div>
          </article>
        ))}
      </div>
      <div className="hl-controls shell">
        <div className="hl-dots" role="tablist" aria-label="Highlights">
          {HIGHLIGHTS.map((item, index) => (
            <button
              key={item.lead}
              type="button"
              className="hl-dot"
              data-on={index === at}
              aria-label={item.lead}
              onClick={() => goTo(index)}
            />
          ))}
        </div>
        <div className="hl-arrows">
          <button type="button" className="hl-arrow" aria-label="Previous" disabled={at <= 0} onClick={() => goTo(at - 1)}>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M14.5 6 8.5 12l6 6" /></svg>
          </button>
          <button
            type="button"
            className="hl-arrow"
            aria-label="Next"
            disabled={at >= HIGHLIGHTS.length - 1}
            onClick={() => goTo(at + 1)}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9.5 6 6 6-6 6" /></svg>
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
  return (
    <article className="ft reveal" style={{ '--d': `${delay}ms` }}>
      <div className="ft-copy">
        <p className="ft-kicker">{kicker}</p>
        <h3 className="ft-title">{title}</h3>
        <p className="ft-body">{body}</p>
      </div>
      <div className="ft-shot" data-crop={crop}>
        <img
          src={SHOTS_304[shot].src}
          alt={SHOTS_304[shot].alt}
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
          kicker="Settings"
          title="One material, everywhere."
          body="Grouped glass tables, hairlines inset to the label, and a coloured glyph on every row."
          shot={SHOT304.settings}
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
        {/* Apple's product-page opening: the name small, three short lines
            large, one grey sentence, then the devices on black. */}
        <section className="os-hero" id="overview">
          <div className="shell os-hero-copy">
            <p className="os-hero-name reveal">Exhale {VERSION}</p>
            <h1 className="os-hero-title reveal" style={{ '--d': '80ms' }}>
              <span>Music that breathes.</span>
              <span>Truly beautiful.</span>
              <span>Truly yours.</span>
            </h1>
            <p className="os-hero-sub reveal" style={{ '--d': '160ms' }}>
              {VERSION} is rolling out now, free for Android 13 and newer.
            </p>
          </div>

          <HeroLineup />
        </section>

        <Highlights />

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
            <img className="getit-icon reveal" src="/logo.png" alt="" width="96" height="96" />
            <h2 className="headline">
              <Words text="Put it on your phone." />
            </h2>
            <p className="lede reveal" style={{ '--d': '160ms' }}>
              One APK for every Android 13 phone. No account, no ads, nothing
              phoning home, and no store deciding whether you may have it.
            </p>

            <div className="btnrow reveal" style={{ '--d': '240ms' }}>
              <a className="btn" href={RELEASES} target="_blank" rel="noreferrer">
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
