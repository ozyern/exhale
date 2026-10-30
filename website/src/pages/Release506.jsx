import { useEffect, useRef, useState } from 'react'
import { SiteFooter } from '../components/Chrome.jsx'
import { Link } from '../router.jsx'
import { ALL_RELEASES, releasePath } from '../releases.js'
import { RELEASES, REPO } from '../content.js'
import { useReveals } from '../hooks.js'
import { SHOT506 } from '../release506.js'
import '../components/keyhero.css'
import '../release506.css'

/*
 * The 1.0.506 page, in the key visual's language.
 *
 * Where it takes from:
 *  - the home page hero: the gold ribbons, the metallic numerals on black, a stage
 *    that holds while the scroll plays it;
 *  - OPPO's ColorOS 17 page: the chapter row that sticks, every chapter opening on
 *    one enormous line that brightens as it reaches the middle of the screen;
 *  - Apple's iOS page: the highlights gallery you page through, bento tiles of
 *    rounded glass, headlines in a gradient, devices that do the explaining.
 *
 * Every scroll-linked effect is a custom property written by one rAF-throttled
 * handler (see useScrollVars), so scrolling never re-renders React:
 *   [data-pin]    --p  0..1 across a tall section whose stage is sticky
 *   [data-view]   --v  0..1 from entering the bottom of the screen to leaving the top
 *   [data-centre] --c  1 at the middle of the screen, 0 a half-screen away
 * With reduced motion every one is set to its finished value and nothing pins.
 */

const reduced = () =>
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

const clamp = (n) => Math.min(1, Math.max(0, n))

function useScrollVars() {
  useEffect(() => {
    const root = document.querySelector('.a6')
    if (!root) return undefined
    if (reduced()) {
      root.dataset.static = 'true'
      root.querySelectorAll('[data-pin]').forEach((n) => n.style.setProperty('--p', '1'))
      root.querySelectorAll('[data-view]').forEach((n) => n.style.setProperty('--v', '0.5'))
      root.querySelectorAll('[data-centre]').forEach((n) => n.style.setProperty('--c', '1'))
      return undefined
    }
    // Every value glides to where the scroll puts it rather than jumping there: a wheel moves the
    // page in steps, and a stage that followed it step for step stuttered. Each frame closes a share
    // of the gap (EASE), which reads as weight, until everything has settled and the loop sleeps.
    const EASE = 0.12
    const tracks = [
      ['[data-pin]', '--p', (r, vh) => clamp(-r.top / Math.max(1, r.height - vh))],
      ['[data-view]', '--v', (r, vh) => clamp((vh - r.top) / (vh + r.height))],
      ['[data-centre]', '--c', (r, vh) => clamp(1 - Math.abs(r.top + r.height / 2 - vh / 2) / (vh * 0.5))],
    ]
    const current = new Map()
    let frame = 0
    const step = (snap = false) => {
      frame = 0
      const vh = window.innerHeight
      let moving = false
      for (const [selector, name, measure] of tracks) {
        root.querySelectorAll(selector).forEach((n) => {
          const target = measure(n.getBoundingClientRect(), vh)
          const key = `${name}`
          const map = current.get(n) ?? {}
          const was = map[key] ?? target
          const now = snap ? target : was + (target - was) * EASE
          map[key] = Math.abs(target - now) < 0.0005 ? target : now
          if (map[key] !== target) moving = true
          current.set(n, map)
          n.style.setProperty(name, map[key].toFixed(4))
          // Past half-way a pinned stage's controls have arrived and can be pressed.
          if (name === '--p') n.dataset.done = map[key] > 0.5 ? 'true' : 'false'
        })
      }
      if (moving) frame = requestAnimationFrame(() => step())
    }
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(() => step())
    }
    // ?y= (see below) is applied here, before the first reading, so a stage opened mid-scroll is
    // drawn there at once rather than on a frame a headless capture may never get.
    const y = Number(new URLSearchParams(window.location.search).get('y'))
    if (y > 0) window.scrollTo({ top: y, behavior: 'instant' })
    step(true)
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
      if (frame) cancelAnimationFrame(frame)
    }
  }, [])
}

/* --------------------------------------------------------------- devices */

/**
 * A phone, the key visual's own: its render's titanium body (`/hero/phone-frame.png`, the home
 * page's phone with its screen cut out) over the capture, which sits behind the glass exactly where
 * that phone's screen was. Until a capture exists, a lit placeholder takes its place.
 */
function Phone({ shot, className = '', style }) {
  return (
    <div className={`a6-phone ${className}`} style={style}>
      {shot?.src ? (
        <img className="a6-phone-shot" src={shot.src} alt={shot.alt} loading="lazy" decoding="async" />
      ) : (
        <div className="a6-phone-shot a6-placeholder" role="img" aria-label={`${shot?.alt ?? 'Screenshot'} (coming soon)`}>
          <span className="a6-placeholder-mark">506</span>
          <span className="a6-placeholder-label">{shot?.alt ?? 'Screenshot'}</span>
          <span className="a6-placeholder-note">Screenshot coming soon</span>
        </div>
      )}
      <img className="a6-phone-frame" src="/hero/phone-frame.png" alt="" aria-hidden="true" decoding="async" />
    </div>
  )
}

/** A window on the Windows desktop: a thin frame and the capture. */
function Window({ shot, className = '' }) {
  return (
    <figure className={`a6-window ${className}`}>
      <img src={shot.src} alt={shot.alt} loading="lazy" decoding="async" />
    </figure>
  )
}

/* ---------------------------------------------------------------- header */

/**
 * The page's own header, as Apple's iOS page and OPPO's ColorOS 17 page have one: the name on the
 * left, the chapters across the middle with the one being read lit, and the download on the right.
 * Clear over the key visual; a bar of frosted glass with a hairline once the page moves under it.
 * It stands in for the site's link row, which sat over the hero as a line of small grey words.
 */
function Header({ chapters }) {
  const [active, setActive] = useState(null)
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    const pick = () => {
      setScrolled((prev) => (prev === window.scrollY > 24 ? prev : window.scrollY > 24))
      const line = window.innerHeight * 0.4
      let current = null
      for (const c of chapters) {
        const node = document.getElementById(c.id)
        if (node && node.getBoundingClientRect().top <= line) current = c.id
      }
      setActive((prev) => (prev === current ? prev : current))
    }
    let frame = 0
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(() => { frame = 0; pick() })
    }
    pick()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => {
      window.removeEventListener('scroll', onScroll)
      if (frame) cancelAnimationFrame(frame)
    }
  }, [chapters])

  return (
    <header className={`a6-head ${scrolled ? 'is-scrolled' : ''}`}>
      <div className="a6-head-inner">
        <Link className="a6-head-brand" to="/" aria-label="Exhale, home">
          <img src="/logo.png" alt="" width="26" height="26" />
          <span>Exhale</span>
          <b>1.0.506</b>
        </Link>
        <nav className="a6-head-links" aria-label="Chapters">
          {chapters.filter((c) => c.id !== 'get').map((c) => (
            <a key={c.id} href={`#${c.id}`} className={active === c.id ? 'is-active' : ''} aria-current={active === c.id ? 'true' : undefined}>
              {c.label}
            </a>
          ))}
        </nav>
        <a className="a6-head-cta" href="#get">Download</a>
      </div>
    </header>
  )
}

/* ------------------------------------------------------------------ hero */

function Hero({ release }) {
  const box = useRef(null)

  // The stage drifts a little after the pointer, as the key visual's does, eased so it never twitches.
  useEffect(() => {
    const node = box.current
    if (!node || reduced() || window.matchMedia('(hover: none)').matches) return undefined
    let frame = 0
    let tx = 0
    let ty = 0
    let x = 0
    let y = 0
    const tick = () => {
      frame = 0
      x += (tx - x) * 0.08
      y += (ty - y) * 0.08
      node.style.setProperty('--mx', `${x.toFixed(2)}px`)
      node.style.setProperty('--my', `${y.toFixed(2)}px`)
      if (Math.abs(tx - x) > 0.05 || Math.abs(ty - y) > 0.05) frame = requestAnimationFrame(tick)
    }
    const onMove = (e) => {
      tx = (e.clientX / window.innerWidth - 0.5) * 44
      ty = (e.clientY / window.innerHeight - 0.5) * 32
      if (!frame) frame = requestAnimationFrame(tick)
    }
    window.addEventListener('mousemove', onMove, { passive: true })
    return () => {
      window.removeEventListener('mousemove', onMove)
      if (frame) cancelAnimationFrame(frame)
    }
  }, [])

  return (
    <section className="a6-hero" data-pin ref={box}>
      <div className="a6-hero-stage">
        <div className="a6-hero-bg" aria-hidden="true" />
        <div className="a6-hero-vignette" aria-hidden="true" />

        <div className="a6-hero-kv">
          <p className="kh-plain-num a6-hero-num">506</p>
          <h1 className="kh-plain-name">Exhale</h1>
          <p className="kh-plain-line">What’s new in 1.0.506</p>
        </div>

        <div className="a6-hero-devices" aria-hidden="true">
          {/* Windows beside Android: the PC app's About page on the key visual's laptop. */}
          <div className="a6-hero-laptop a6-hero-left">
            <img src="/hero/506-laptop.png" alt="Exhale for Windows: Settings, About Exhale 1.0.506" decoding="async" />
          </div>
          <Phone shot={SHOT506.lyrics} className="a6-hero-phone a6-hero-right" />
          <Phone shot={SHOT506.about} className="a6-hero-phone a6-hero-mid" />
        </div>

        <div className="a6-hero-lockup">
          <p className="a6-eyebrow">{release.eyebrow} · {release.date}</p>
          <h2 className="a6-hero-title">
            <span>{release.tagline[0]}</span> <span className="a6-gold">{release.tagline[1]}</span>
          </h2>
          <p className="a6-hero-dek">{release.dek}</p>
          <div className="a6-hero-actions">
            <a className="kh-pill kh-pill-primary" href="#get">Get 1.0.506</a>
            <a className="kh-pill kh-pill-secondary" href="#highlights">See the highlights</a>
          </div>
        </div>

        <div className="a6-scroll-cue" aria-hidden="true"><span /></div>
      </div>
    </section>
  )
}

/* ------------------------------------------------------------ highlights */

const HIGHLIGHTS = [
  { win: SHOT506.winHome, kicker: 'New · Windows', title: 'Exhale, on your PC. For the first time.', tint: '#ffb300' },
  { win: SHOT506.winVideo, kicker: 'Windows', title: 'Music videos in 2160p, lyrics on the picture.', tint: '#ff7043' },
  { shot: SHOT506.player, kicker: 'Spatial Audio', title: 'On from the very first song.', tint: '#ffb300' },
  { shot: SHOT506.lyrics, kicker: 'Lyrics', title: 'A letter at a time, right on the beat.', tint: '#ff8a65' },
  { shot: SHOT506.search, kicker: 'FLAC', title: 'A lossless store, built in.', tint: '#4dd0e1' },
  { shot: SHOT506.update, kicker: 'Updates', title: 'Highlights and notes, on the phone.', tint: '#ffd54f' },
  { shot: SHOT506.about, kicker: 'Design', title: 'Gold, glass and a new wordmark.', tint: '#ffca28' },
]

function Highlights() {
  const rail = useRef(null)
  const [index, setIndex] = useState(0)

  useEffect(() => {
    const node = rail.current
    if (!node) return undefined
    const onScroll = () => {
      const card = node.firstElementChild
      if (!card) return
      const step = card.getBoundingClientRect().width + 20
      setIndex(Math.round(node.scrollLeft / step))
    }
    node.addEventListener('scroll', onScroll, { passive: true })
    return () => node.removeEventListener('scroll', onScroll)
  }, [])

  const go = (i) => {
    const node = rail.current
    const card = node?.children[i]
    if (card) node.scrollTo({ left: card.offsetLeft - node.offsetLeft - 24, behavior: reduced() ? 'auto' : 'smooth' })
  }

  return (
    <section className="a6-highlights" id="highlights">
      <h2 className="a6-section-title reveal">Get the highlights.</h2>
      <div className="a6-rail" ref={rail}>
        {HIGHLIGHTS.map((h) => (
          <article className="a6-card" key={h.kicker} style={{ '--tint': h.tint }}>
            <p className="a6-card-kicker">{h.kicker}</p>
            <h3 className="a6-card-title">{h.title}</h3>
            {h.win ? (
              <Window shot={h.win} className="a6-card-window" />
            ) : (
              <Phone shot={h.shot} className="a6-card-phone" />
            )}
          </article>
        ))}
      </div>
      <div className="a6-rail-controls">
        <div className="a6-dots" role="tablist" aria-label="Highlights">
          {HIGHLIGHTS.map((h, i) => (
            <button key={h.kicker} type="button" role="tab" aria-selected={i === index} aria-label={h.kicker}
              className={i === index ? 'is-on' : ''} onClick={() => go(i)} />
          ))}
        </div>
        <div className="a6-paddles">
          <button type="button" aria-label="Previous" disabled={index === 0} onClick={() => go(index - 1)}>‹</button>
          <button type="button" aria-label="Next" disabled={index >= HIGHLIGHTS.length - 1} onClick={() => go(index + 1)}>›</button>
        </div>
      </div>
    </section>
  )
}

/* -------------------------------------------------------- shared pieces */

/** One enormous line that brightens as it crosses the middle of the screen (ColorOS 17). */
function BigLine({ eyebrow, children }) {
  return (
    <header className="a6-bigline" data-centre>
      <p className="a6-eyebrow">{eyebrow}</p>
      <h2>{children}</h2>
    </header>
  )
}

function Beats({ beats }) {
  return (
    <div className="a6-beats">
      {beats.map((b) => (
        <div className="a6-beat reveal" key={b.k}>
          <p className="a6-beat-k">{b.k}</p>
          <h3>{b.t}</h3>
          <p>{b.b}</p>
        </div>
      ))}
    </div>
  )
}

/* ----------------------------------------------------------------- sound */

function Sound({ data }) {
  return (
    <section className="a6-chapter" id="sound">
      <BigLine eyebrow={data.eyebrow}>{data.line}</BigLine>
      <div className="a6-split">
        <div className="a6-split-device">
          <div className="a6-rings" aria-hidden="true">
            <span /><span /><span /><span />
          </div>
          <Phone shot={SHOT506.player} className="a6-split-phone" />
        </div>
        <Beats beats={data.beats} />
      </div>
    </section>
  )
}

/* ---------------------------------------------------------------- lyrics */

function Lyrics({ data }) {
  const words = data.line
  return (
    <section className="a6-chapter a6-lyrics" id="lyrics">
      <div className="a6-pin" data-pin style={{ '--n': words.length }}>
        <div className="a6-pin-stage">
          <p className="a6-eyebrow">{data.eyebrow}</p>
          <p className="a6-lyric-line" aria-label={words.join(' ')}>
            {words.map((w, i) => (
              <span key={`${w}-${i}`} className="a6-word" style={{ '--i': i }} aria-hidden="true">{w}</span>
            ))}
          </p>
          <div className="a6-lyrics-devices">
            <Phone shot={SHOT506.lyrics} className="a6-lyrics-phone" />
            <Window shot={SHOT506.winVideo} className="a6-lyrics-window" />
          </div>
        </div>
      </div>
      <Beats beats={data.beats} />
    </section>
  )
}

/* ------------------------------------------------------------------ dock */

/**
 * The dock, shown on its own screens rather than drawn: on the left the folded dock as it really
 * sits under a page, and on the right the player opening out of that dock's song pill — its screen
 * grows from a sliver at the foot of the display to the whole of it as the stage plays.
 */
function Dock({ data }) {
  return (
    <section className="a6-chapter a6-dockch" id="dock">
      <div className="a6-pin" data-pin>
        <div className="a6-pin-stage">
          <p className="a6-eyebrow">{data.eyebrow}</p>
          <h2 className="a6-pin-title">{data.line}</h2>
          <div className="a6-dock-scene">
            <figure className="a6-dock-step a6-dock-fold">
              <Phone shot={SHOT506.search} className="a6-dock-phone" />
              <figcaption><b>Folds away</b>Scroll, and the dock gathers into your page, the song and search.</figcaption>
            </figure>
            <figure className="a6-dock-step a6-dock-unfold">
              <Phone shot={SHOT506.player} className="a6-dock-phone" />
              <figcaption><b>Unfolds on cue</b>Tap the song, and the player opens out of it.</figcaption>
            </figure>
          </div>
        </div>
      </div>
      <Beats beats={data.beats} />
    </section>
  )
}

/* ---------------------------------------------------------------- search */

function Search({ data }) {
  return (
    <section className="a6-chapter" id="search">
      <BigLine eyebrow={data.eyebrow}>{data.line}</BigLine>
      <div className="a6-bento">
        <div className="a6-tile a6-tile-shot reveal">
          <Phone shot={SHOT506.search} className="a6-tile-phone" />
        </div>
        {data.tiles.map((t) => (
          <div className="a6-tile reveal" key={t.t}>
            <p className="a6-tile-stat">{t.stat}</p>
            <h3>{t.t}</h3>
            <p>{t.b}</p>
          </div>
        ))}
      </div>
    </section>
  )
}

/* --------------------------------------------------------------- windows */

function Windows({ data }) {
  return (
    <section className="a6-chapter a6-win" id="windows">
      <div className="a6-pin a6-win-pin" data-pin>
        <div className="a6-pin-stage">
          <p className="a6-win-intro">{data.intro}</p>
          <p className="a6-eyebrow">{data.eyebrow}</p>
          <h2 className="a6-pin-title">{data.line}</h2>
          <p className="a6-win-lede">{data.lede}</p>
          {/* The key visual's own laptop render, with Exhale for Windows on its screen. */}
          <div className="a6-laptop">
            <img src="/hero/oppo_dev_tablet_left.png" alt={SHOT506.winHome.alt} loading="lazy" decoding="async" />
          </div>
        </div>
      </div>
      <div className="a6-win-gallery">
        <Window shot={SHOT506.winPlayer} className="reveal" />
        <Window shot={SHOT506.winVideo} className="reveal" />
      </div>
      <Beats beats={data.beats} />
    </section>
  )
}

/* ------------------------------------------------------------------- get */

function GetIt({ release }) {
  return (
    <section className="a6-chapter a6-get" id="get">
      <BigLine eyebrow="Get 1.0.506">Free. Open source. Yours.</BigLine>
      <div className="a6-get-cards">
        <a className="a6-get-card reveal" href={RELEASES} target="_blank" rel="noreferrer">
          <p className="a6-eyebrow">Android</p>
          <h3>Exhale for Android</h3>
          <p>One universal APK for Android 13 and later. After the first install it updates itself.</p>
          <span className="kh-pill kh-pill-primary">Download</span>
        </a>
        {/* The same release as Android's: the setup is attached to it beside the APK. */}
        <a className="a6-get-card reveal" href={RELEASES} target="_blank" rel="noreferrer">
          <p className="a6-eyebrow">Windows</p>
          <h3>Exhale for Windows</h3>
          <p>A setup for Windows 10 and 11, in the same release as the Android app. It updates itself too.</p>
          <span className="kh-pill kh-pill-secondary">Download for Windows</span>
        </a>
      </div>

      <dl className="a6-facts reveal">
        {release.facts.map((f) => (
          <div key={f.label}>
            <dt>{f.label}</dt>
            <dd>{f.value}</dd>
          </div>
        ))}
      </dl>

      <div className="a6-all reveal" id="everything">
        <h3 className="a6-all-h">Everything in {release.version}</h3>
        <div className="a6-all-grid">
          {release.everything.map((g) => (
            <div key={g.group}>
              <p className="a6-eyebrow">{g.group}</p>
              <ul>
                {g.items.map((item) => <li key={item}>{item}</li>)}
              </ul>
            </div>
          ))}
        </div>
      </div>

      <div className="a6-more">
        {ALL_RELEASES.filter((r) => r !== release).map((other) => (
          <Link className="a6-more-card reveal" to={releasePath(other)} key={other.version}>
            <span>Earlier release</span>
            <b>Exhale {other.version}</b>
            <i>{other.date}</i>
          </Link>
        ))}
        <a className="a6-more-card reveal" href={`${REPO}/blob/master/CHANGELOG.md`} target="_blank" rel="noreferrer">
          <span>Changelog</span>
          <b>Every fix, with the reason it broke</b>
          <i>On GitHub</i>
        </a>
      </div>
    </section>
  )
}

/* ------------------------------------------------------------------ page */

export default function Release506({ release }) {
  useReveals()
  useScrollVars()

  useEffect(() => {
    const hash = window.location.hash.slice(1)
    const target = hash && document.getElementById(hash)
    // ?y=1200 opens the page scrolled that far, as the hero takes ?kp: for checking a stage mid-play.
    const y = Number(new URLSearchParams(window.location.search).get('y'))
    if (target) target.scrollIntoView()
    else window.scrollTo({ top: Number.isFinite(y) ? y : 0, behavior: 'instant' })
  }, [])

  return (
    <div id="top" className="a6">
      <Header chapters={release.chapters} />
      <main>
        <Hero release={release} />
        <Highlights />
        <Windows data={release.windows} />
        <Sound data={release.sound} />
        <Lyrics data={release.lyrics} />
        <Dock data={release.dock} />
        <Search data={release.search} />
        <GetIt release={release} />
      </main>
      <SiteFooter />
    </div>
  )
}
