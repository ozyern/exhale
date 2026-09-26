import { useEffect, useRef, useState } from 'react'
import { SiteFooter, TopBar } from '../components/Chrome.jsx'
import { Link } from '../router.jsx'
import { ALL_RELEASES, LATEST_RELEASE, releasePath, tagUrl } from '../releases.js'
import { RELEASES, REPO } from '../content.js'
import { useReveals } from '../hooks.js'
import { ArrowLeftIcon, LinkIcon, ReplayIcon } from '../icons.jsx'
import BreathField from '../components/BreathField.jsx'
import GlassField from '../components/GlassField.jsx'

/*
 * The release announcement.
 *
 * This was a long read — an article measure, a rail you could jump from, and
 * twelve numbered sections of engineering account. It was well written and it
 * was the wrong document. Someone who opens a release page wants to know what
 * changed and what it looks like; the reasoning belongs in the commits and in
 * CHANGELOG.md, both of which are linked from the bottom of this page and
 * neither of which is competing with a screenshot for their attention.
 *
 * So it is the same shape as the rest of the site now: enormous type, one claim
 * per screen, an object rather than a heading. Eight chapters, a headline and
 * two sentences each, real captures from the build at the size a screenshot
 * deserves — and everything the edit cut kept at the bottom as one plain list,
 * because a release note that quietly loses items is worse than a long one.
 *
 * What it keeps from the rest of the site: the black, the greys, the three
 * retinting custom properties, and the rule that the weight comes from the size
 * rather than from the font.
 */

/* ------------------------------------------------------------------- hero */

/**
 * A word that arrives one letter at a time, from the inside out.
 *
 * `from` says which end is nearest the number, and that letter goes first, so
 * both words appear to be pushed outward by the thing between them rather than
 * typed left to right on either side of it. It is a small thing that costs one
 * custom property and is the difference between two labels and a title.
 */
function Word({ text, from = 'start' }) {
  const letters = [...text]
  return (
    <span className="rh-word" aria-hidden="true">
      {letters.map((ch, i) => (
        <span
          key={`${ch}-${i}`}
          style={{ '--i': from === 'end' ? letters.length - 1 - i : i }}
        >
          {ch}
        </span>
      ))}
    </span>
  )
}

/**
 * The title, as an object.
 *
 * The whole first screen is the release: a field of light in the middle and
 * the name split to the two outside edges, so the type frames the object
 * instead of sitting on top of it. Nothing else is above the fold — no nav
 * bar over the artwork, no scroll hint, no button. The one thing a reader can
 * do here is look at it, and then scroll.
 *
 * It carries the h1, so the article below does not repeat the name at poster
 * size two screens running.
 */
function SplitTitle({ release, onReplay }) {
  const [key, setKey] = useState(0)
  const [tall, setTall] = useState(false)
  const box = useRef(null)

  // One decision, read off the box itself rather than off the viewport.
  //
  // The canvas needs it to know whether to set the digits in a row or one per
  // line, and the stylesheet needs it to know whether the two words sit either
  // side of the number or at opposite corners of it. A media query would be a
  // second opinion, and a short landscape window is exactly where the two
  // would disagree — the words stacked over a number that is still in a row.
  useEffect(() => {
    const node = box.current
    if (!node) return
    const read = () => {
      const rect = node.getBoundingClientRect()
      setTall(rect.height / Math.max(1, rect.width) > 1.12)
    }
    read()

    // Both, because they fail in different places. ResizeObserver catches the
    // box changing without the window doing anything; `resize` catches the
    // window when the observer's callback is not being delivered, which is
    // what happens whenever the rendering pipeline is throttled.
    window.addEventListener('resize', read)
    window.addEventListener('orientationchange', read)
    const ro = 'ResizeObserver' in window ? new ResizeObserver(read) : null
    ro?.observe(node)

    return () => {
      window.removeEventListener('resize', read)
      window.removeEventListener('orientationchange', read)
      ro?.disconnect()
    }
  }, [])

  const replay = () => {
    setKey((n) => n + 1)
    onReplay?.()
  }

  return (
    <div className="rh" ref={box} data-tall={tall} data-field={release.field}>
      {/* Each release brings its own field. 1.0.203 assembles its number out
          of a star field; 1.0.304 prints it once and drags glass across it. A
          site that opens every announcement on the same trick stops
          announcing anything. */}
      {release.field === 'glass' ? (
        <GlassField text={String(release.code)} tall={tall} replayKey={key} />
      ) : (
        <BreathField text={String(release.code)} tall={tall} replayKey={key} />
      )}

      {/* The build number in the middle is drawn on the canvas, which is
          aria-hidden, and the two words are split into per-letter spans — so
          the heading carries the whole thing once, as text, for anything that
          reads the page rather than looks at it. */}
      {/* Keyed on the replay, so pressing it plays the words in again with
          the field rather than leaving them sitting there already arrived.
          The letters are split into spans and hidden from assistive tech; the
          heading's real text is the one line in the middle. */}
      <h1 className="rh-type" key={key}>
        <Word text="Exhale" from="end" />{' '}
        <span className="rh-said">Exhale {release.version}, Universal</span>{' '}
        <Word text="Universal" />
      </h1>

      <button
        type="button"
        className="rh-replay"
        onClick={replay}
        aria-label="Play the animation again"
      >
        <ReplayIcon />
      </button>
    </div>
  )
}

/**
 * Three real captures on a lit ground, under the facts.
 *
 * The object above is the release; this is the app. Announcing a version with
 * nothing but generative artwork is a poster, and a poster is not evidence.
 */
function ReleaseShots({ hero, shots }) {
  return (
    <figure className="rl-hero reveal">
      {/* The stage clips; the caption sits outside it, so the phones can run
          off the bottom edge without taking the words with them. */}
      <div className="rl-hero-stage">
        <div className="rl-hero-ground" aria-hidden="true">
          <span className="rl-hero-blob rl-hero-blob-a" />
          <span className="rl-hero-blob rl-hero-blob-b" />
          <span className="rl-hero-blob rl-hero-blob-c" />
        </div>

        <div className="rl-hero-fan">
          {hero.shots.map((index, i) => (
            <div className="rl-hero-slot" key={index} data-slot={i}>
              <img
                src={shots[index].src}
                alt={i === 1 ? shots[index].alt : ''}
                aria-hidden={i !== 1}
                width="592"
                height="1322"
                loading={i === 1 ? 'eager' : 'lazy'}
                decoding="async"
              />
            </div>
          ))}
        </div>
      </div>

      <figcaption>{hero.caption}</figcaption>
    </figure>
  )
}

/* --------------------------------------------------------------- chapters */

/**
 * One chapter.
 *
 * A kicker, a headline, two sentences, and — usually — one screenshot at a size
 * you can actually read the app in. `wide` chapters have no image and take the
 * full column instead: they are the beats between the pictures, and a page that
 * is only pictures has no rhythm to it.
 *
 * The image is the argument. Everything here that could have been a paragraph
 * explaining a screenshot is a screenshot.
 */
function Chapter({ chapter, index, shots }) {
  const wide = chapter.tone === 'wide'

  return (
    <section className="rl-chap" id={chapter.id} data-wide={wide} data-flip={!!chapter.flip}>
      <div className="rl-chap-say reveal">
        <p className="rl-chap-kick">{chapter.kicker}</p>
        <h2 className="rl-chap-h">{chapter.title}</h2>
        <p className="rl-chap-p">{chapter.body}</p>
      </div>

      {!wide && (
        <figure className="rl-chap-fig reveal" style={{ '--d': '120ms' }}>
          <div className="rl-chap-stage">
            <img
              src={shots[chapter.shot].src}
              alt={shots[chapter.shot].alt}
              width="780"
              height="1390"
              loading={index === 0 ? 'eager' : 'lazy'}
              decoding="async"
            />
          </div>
          <figcaption>{chapter.caption}</figcaption>
        </figure>
      )}
    </section>
  )
}

/**
 * The receipt.
 *
 * Three columns of one-liners, set small and quiet, after the story is over.
 * Nothing in it is new information — it is the same release described at the
 * length someone scanning a diff wants rather than at the length someone
 * deciding whether to install wants.
 */
function Everything({ groups, version }) {
  return (
    <section className="rl-all reveal" id="everything">
      <h2 className="rl-all-h">Everything in {version}</h2>
      <div className="rl-all-grid">
        {groups.map((group) => (
          <div className="rl-all-col" key={group.group}>
            <p className="rl-all-tag">{group.group}</p>
            <ul>
              {group.items.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </section>
  )
}

/**
 * The About screen's release poster, at the end of the page it announced.
 *
 * Only for a release that has one: the gold key art, the mark and the
 * wordmark are 1.0.304's own dress, so the page closes on the same card the
 * app shows once it is installed.
 */
function Poster({ version, href }) {
  return (
    <section className="rl-poster reveal" aria-label={`Get Exhale ${version}`}>
      <img className="rl-poster-mark" src="/media/icon.png" alt="" width="104" height="104" />
      <img className="rl-poster-word" src="/media/wordmark.png" alt="Exhale" width="900" height="258" />
      <p className="rl-poster-ver">{version}</p>
      <div className="rl-poster-actions">
        <a className="btn" href={href} target="_blank" rel="noreferrer">
          Download {version}
        </a>
        <a className="btn btn-ghost" href={REPO} target="_blank" rel="noreferrer">
          Read the source
        </a>
      </div>
    </section>
  )
}

function CopyLink({ version }) {
  const [done, setDone] = useState(false)

  useEffect(() => {
    if (!done) return
    const id = window.setTimeout(() => setDone(false), 1800)
    return () => window.clearTimeout(id)
  }, [done])

  const copy = async () => {
    const url = `https://exhale.ozyern.me/release/${version}`
    try {
      await navigator.clipboard.writeText(url)
      setDone(true)
    } catch {
      // Denied, insecure context, or an old browser. Selecting the address bar
      // is the fallback everyone already knows, so say nothing and do nothing.
    }
  }

  return (
    <button type="button" className="rl-copy" onClick={copy} data-done={done}>
      <LinkIcon />
      {done ? 'Link copied' : 'Copy link'}
    </button>
  )
}

/* ------------------------------------------------------------------- page */

export default function Release({ release = LATEST_RELEASE }) {
  const shots = release.shots
  const latest = release === LATEST_RELEASE
  // Every other release with a page, for the cards at the bottom.
  const others = ALL_RELEASES.filter((other) => other !== release)
  const barRef = useRef(null)

  useReveals()

  // Every page mount starts at the top. Without this, arriving from the home
  // page's announcement strip drops you wherever the home page was scrolled to.
  useEffect(() => {
    const hash = window.location.hash.slice(1)
    const target = hash && document.getElementById(hash)
    if (target) target.scrollIntoView()
    else window.scrollTo(0, 0)
  }, [])

  // Reading progress, written straight to a custom property from a throttled
  // handler. Nothing here re-renders React; the browser scales one bar.
  useEffect(() => {
    const bar = barRef.current
    if (!bar) return

    let frame = 0
    const apply = () => {
      frame = 0
      const doc = document.documentElement
      const span = doc.scrollHeight - window.innerHeight
      const read = span > 0 ? window.scrollY / span : 0
      bar.style.setProperty('--read', Math.max(0, Math.min(1, read)).toFixed(4))
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

  return (
    <div id="top" className="release" data-theme={release.theme}>
      <TopBar />
      <div className="rl-progress" ref={barRef} aria-hidden="true">
        <span />
      </div>

      <main>
        <article>
          <SplitTitle release={release} />

          <header className="rl-head">
            <div className="rl-measure">
              <Link className="rl-back" to="/">
                <span className="rl-back-arrow" aria-hidden="true">
                  <ArrowLeftIcon />
                </span>
                Exhale
              </Link>

              <p className="rl-eyebrow reveal">
                <span>{release.kicker}</span>
                <time dateTime={release.dateISO}>{release.date}</time>
                <span className="rl-read">{release.read}</span>
              </p>

              {/* An old announcement is still worth reading, but nobody who
                  lands on one from a search result should leave thinking it is
                  the version they would get. */}
              {!latest && (
                <Link
                  className="textlink rl-newer reveal"
                  to={releasePath(LATEST_RELEASE)}
                  style={{ '--d': '45ms' }}
                >
                  A newer release is out: Exhale {LATEST_RELEASE.version} <i>&rsaquo;</i>
                </Link>
              )}

              <p className="rl-dek reveal" style={{ '--d': '90ms' }}>
                {release.dek}
              </p>

              <div className="rl-actions reveal" style={{ '--d': '180ms' }}>
                {/* `releases/latest` is only honest on the latest page. An
                    older one says "Download 1.0.203", so it links the file
                    that was tagged 1.0.203. */}
                <a
                  className="btn"
                  href={latest ? RELEASES : tagUrl(REPO, release)}
                  target="_blank"
                  rel="noreferrer"
                >
                  Download {release.version}
                </a>
                <a className="btn btn-ghost" href={REPO} target="_blank" rel="noreferrer">
                  Read the source
                </a>
                <CopyLink version={release.version} />
              </div>

              <dl className="rl-facts reveal" style={{ '--d': '260ms' }}>
                {release.facts.map((fact) => (
                  <div key={fact.label}>
                    <dt>{fact.label}</dt>
                    <dd>{fact.value}</dd>
                  </div>
                ))}
              </dl>
            </div>

            <ReleaseShots hero={release.hero} shots={release.heroShots} />
          </header>

          <div className="rl-story">
            {release.story.map((chapter, index) => (
              <Chapter chapter={chapter} index={index} shots={shots} key={chapter.id} />
            ))}

            <Everything groups={release.everything} version={release.version} />

            {release.theme === 'gold' && (
              <Poster
                version={release.version}
                href={latest ? RELEASES : tagUrl(REPO, release)}
              />
            )}

            <div className="rl-tail">
              <h2 className="rl-all-h">More</h2>
              <div className="rl-more-grid">
                  <Link className="rl-more-card" to="/">
                    <span className="rl-more-tag">Overview</span>
                    <b>Exhale — a music player that breathes</b>
                    <span className="rl-more-body">
                      Live glass on every surface, lyrics that land on the beat,
                      and color that follows whatever is playing.
                    </span>
                  </Link>
                  {others.map((other) => (
                    <Link className="rl-more-card" to={releasePath(other)} key={other.version}>
                      <span className="rl-more-tag">
                        {other === LATEST_RELEASE ? 'Latest release' : 'Earlier release'}
                      </span>
                      <b>Exhale {other.version}</b>
                      <span className="rl-more-body">{other.date}</span>
                    </Link>
                  ))}
                  <a
                    className="rl-more-card"
                    href={`${REPO}/blob/master/CHANGELOG.md`}
                    target="_blank"
                    rel="noreferrer"
                  >
                    <span className="rl-more-tag">Changelog</span>
                    <b>Every fix, with the reason it broke</b>
                    <span className="rl-more-body">
                      The full document, written for someone reading the diff
                      rather than deciding whether to install.
                    </span>
                  </a>
                  <a
                    className="rl-more-card"
                    href={`${REPO}/blob/master/CONTRIBUTING.md`}
                    target="_blank"
                    rel="noreferrer"
                  >
                    <span className="rl-more-tag">Contributing</span>
                    <b>Build it, change it, send it back</b>
                    <span className="rl-more-body">
                      The toolchain, the flavors, the signing setup and what a
                      good pull request looks like here.
                    </span>
                  </a>
              </div>
            </div>
          </div>
        </article>
      </main>

      <SiteFooter />
    </div>
  )
}
