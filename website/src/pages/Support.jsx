import { useEffect, useRef, useState } from 'react'
import { SiteFooter, TopBar } from '../components/Chrome.jsx'
import { REPO } from '../content.js'
import { ELSEWHERE, FACTS, FAQ, PATHS, PROCESS, issueUrl } from '../support.js'
import { useReveals } from '../hooks.js'
import { ArrowRightIcon, GithubIcon } from '../icons.jsx'
import { DoorCard, SHINE } from '../components/ui/door-card.jsx'
import { FloatField } from '../components/ui/float-field.jsx'
import { PaneStepper } from '../components/ui/pane-stepper.jsx'
import { ShineBorder } from '../components/ui/shine-border.jsx'
import { SupportHero } from '../components/ui/support-hero.jsx'
import { LATEST_RELEASE as RELEASE } from '../releases.js'

/*
 * Support.
 *
 * The site is static — GitHub Pages, no backend, nothing that can receive a
 * POST — so this page does not collect anything. It asks what you want, gives
 * the answer outright when there is one, and otherwise hands you to GitHub with
 * the issue form already filled in.
 *
 * That constraint turned out to be the right shape anyway. Issues gives threads,
 * status, search and duplicate-spotting for free and forever, where a form
 * backend would have given an inbox. The one real cost is that filing needs a
 * GitHub account, which the page says plainly rather than springing on you at
 * the last step.
 *
 * On the shape: the release page opens with a full screen of object because a
 * release is an event you arrive at to look at. This is a page you arrive at to
 * *use*, usually with something already broken, so the hero is mostly a search
 * box over the answers, with the four ways in directly under it. Nobody annoyed
 * enough to file a bug should have to scroll past decoration to do it.
 *
 * The FAQ is the important half. Most support volume is the same handful of
 * questions, and every one answered here is a thread that never has to exist.
 * If only half of this page is kept current, keep that half.
 */

/** 01, 02 — the ordinal, not a count. */
const ord = (i) => String(i + 1).padStart(2, '0')

/* ---------------------------------------------------------------- chooser */

/**
 * One path, as a full-width band.
 *
 * Tiles would have fitted in a row and read as four equal options in a
 * settings screen. These are the page's whole purpose, so they get the width,
 * and the number down the left gives the set an order to be read in.
 */
function PathRow({ path, index, active, onPick }) {
  return (
    <button
      type="button"
      className="sp-row"
      data-active={active}
      aria-expanded={active}
      aria-controls={`sp-panel-${path.id}`}
      onClick={() => onPick(path.id)}
    >
      <span className="sp-row-n" aria-hidden="true">
        {ord(index)}
      </span>
      <span className="sp-row-say">
        <b className="sp-row-h">{path.title}</b>
        <span className="sp-row-b">{path.body}</span>
      </span>
      <span className="sp-row-go" aria-hidden="true">
        <ArrowRightIcon />
      </span>
    </button>
  )
}

/* ------------------------------------------------------------------- form */

/**
 * A field, from Origin UI (components/ui/float-field.jsx).
 *
 * Optional is still the thing marked rather than required: seven asterisks down
 * a form announce the normal case, and the exception is what is worth the ink.
 */
function Field({ field, value, onChange }) {
  return (
    <FloatField
      id={`sp-${field.id}`}
      label={field.label}
      optional={!field.required}
      hint={field.hint}
      multiline={field.type === 'area'}
      rows={field.rows || 3}
      required={field.required}
      placeholder={field.placeholder}
      value={value}
      onChange={(event) => onChange(field.id, event.target.value)}
    />
  )
}

/**
 * One path's composer.
 *
 * Two panes rather than one column of seven boxes, split the way the person
 * is already thinking: what went wrong, then what they are holding. The split
 * is read off the data — the short metadata fields are the ones already
 * marked `half` — so a path with nothing to ask about the device simply has
 * one pane and no rail.
 *
 * Deliberately not a step-per-field wizard. Somebody reporting a bug wants to
 * see their whole account of it at once; being made to press Next seven times
 * to say one thing is worse than the boxes were.
 *
 * Keyed on the path id by the caller, so switching category remounts this and
 * the values reset. Carrying a half-typed bug report across into an idea would
 * be worse than losing it.
 */
function PathPanel({ path }) {
  // Seeded from the fields themselves so the version box arrives filled in:
  // nobody knows their build number off the top of their head, and an empty
  // required field is what makes people abandon a form.
  const [values, setValues] = useState(() =>
    Object.fromEntries(path.fields.map((f) => [f.id, f.prefill || ''])),
  )
  const [pane, setPane] = useState(0)
  const [sent, setSent] = useState(false)

  const set = (id, value) => setValues((prev) => ({ ...prev, [id]: value }))

  // The short fields are already marked `half` in the data, and that is the
  // same distinction the panes need — so it is reused rather than restated.
  const story = path.fields.filter((f) => !f.half)
  const meta = path.fields.filter((f) => f.half)
  const panes = meta.length ? [story, meta] : [story]
  const last = pane === panes.length - 1

  const noTitle = !(values.__title || '').trim()
  const gaps = (list) =>
    list.filter((f) => f.required && !(values[f.id] || '').trim()).map((f) => f.label)

  const here = pane === 0 ? (noTitle ? ['Summary'] : []).concat(gaps(story)) : gaps(meta)
  const everywhere = (noTitle ? ['Summary'] : []).concat(gaps(path.fields))
  const ready = everywhere.length === 0

  const { url, tooLong } = issueUrl(path, values)

  const advance = (event) => {
    event.preventDefault()
    if (here.length) return
    if (!last) {
      setPane((n) => n + 1)
      return
    }
    if (!ready) return
    // A new tab rather than a navigation: the reporter still has everything
    // they typed sitting on this page if GitHub asks them to sign in first.
    window.open(url, '_blank', 'noopener,noreferrer')
    setSent(true)
  }

  return (
    <div className="sp-stage">
      {/* Frost needs something to be frosted over. There is nothing behind a
          sheet on a black page, so the light is put there deliberately. */}
      <div className="sp-glow" aria-hidden="true" />

      <form className="sp-panel" id={`sp-panel-${path.id}`} onSubmit={advance}>
        <ShineBorder duration={16} shineColor={SHINE} />

        {panes.length > 1 ? (
          <PaneStepper
            steps={panes.map((list, i) => ({
              title: path.steps?.[i] || `Step ${i + 1}`,
              // What the pane asks for, read off the fields rather than restated.
              detail: (i === 0 ? ['Summary'] : []).concat(list.map((f) => f.label)).join(' · '),
            }))}
            value={pane}
            onChange={setPane}
            // Forward only once this pane is answered. Back is never gated —
            // re-reading what you already wrote is not something to hold a
            // person to.
            locked={here.length > 0}
          />
        ) : null}

        {path.note && pane === (path.notePane ?? 0) ? (
          <p className="sp-note">{path.note}</p>
        ) : null}

        {pane === 0 ? (
          <div className="sp-pane">
            <FloatField
              id="sp-title"
              lead
              label="Summary"
              hint="One line. It becomes the issue title."
              required
              value={values.__title || ''}
              placeholder={
                path.id === 'idea'
                  ? 'Queue an entire artist in one action'
                  : 'Playback stops with the screen off'
              }
              onChange={(event) => set('__title', event.target.value)}
            />

            {story.map((field) => (
              <Field key={field.id} field={field} value={values[field.id] || ''} onChange={set} />
            ))}
          </div>
        ) : (
          <div className="sp-pane sp-pane-meta">
            {meta.map((field) => (
              <Field key={field.id} field={field} value={values[field.id] || ''} onChange={set} />
            ))}
          </div>
        )}

        {tooLong ? (
          <p className="sp-warn">
            That is longer than a browser will carry reliably. Shorten it here
            and paste the rest once the GitHub form opens — there is no limit on
            that side.
          </p>
        ) : null}

        <div className="sp-actions">
          {pane > 0 ? (
            <button type="button" className="sp-back" onClick={() => setPane((n) => n - 1)}>
              Back
            </button>
          ) : null}

          <button type="submit" className="btn" disabled={here.length > 0}>
            {last ? <GithubIcon /> : null}
            {last ? 'Continue on GitHub' : 'Next'}
          </button>

          <p className="sp-meta">
            {here.length
              ? `Still needed: ${here.join(', ')}.`
              : last
                ? 'Opens the form filled in. Nothing is filed until you press submit there.'
                : `Then ${(path.steps?.[1] || 'your device').toLowerCase()}.`}
          </p>
        </div>

        {sent ? (
          <p className="sp-sent" role="status">
            Opened in a new tab. If it did not appear, your browser blocked the
            popup —{' '}
            <a href={url} target="_blank" rel="noreferrer">
              open it directly
            </a>
            . Signing in first is fine; what you typed is still here.
          </p>
        ) : null}
      </form>
    </div>
  )
}

/** The last path is not a form. It is four different doors. */
function Elsewhere() {
  return (
    <div className="sp-panel" id="sp-panel-other">
      <div className="sp-else">
        {ELSEWHERE.map((item) => (
          <DoorCard key={item.label} href={item.href} title={item.label} body={item.body} />
        ))}
      </div>
    </div>
  )
}

/* -------------------------------------------------------------------- faq */

/**
 * Native <details>, not a state machine.
 *
 * Open-by-URL-fragment, findable by the browser's own in-page search even while
 * collapsed, and keyboard-operable without a line of JavaScript. A custom
 * accordion would be all of that reimplemented, worse.
 */
function Faq() {
  return (
    <section className="sp-faq" id="questions">
      <div className="sp-sec-head reveal">
        <p className="kicker">Answers</p>
        <h2 className="headline-sm">Most of it is already known</h2>
        <p className="sp-sec-dek">
          These are the questions that actually arrive, with the real answers —
          including the ones where the honest answer is that Exhale cannot do it
          and here is why.
        </p>
      </div>

      <div className="sp-faq-list">
        {FAQ.map((item, i) => (
          <details className="sp-q reveal" key={item.id} id={item.id}>
            <summary>
              <span className="sp-q-n" aria-hidden="true">
                {ord(i)}
              </span>
              <span className="sp-q-t">{item.q}</span>
              <span className="sp-q-mark" aria-hidden="true" />
            </summary>
            <div className="sp-q-a">
              {item.a.map((para, j) => (
                <p key={j}>{para}</p>
              ))}
            </div>
          </details>
        ))}
      </div>
    </section>
  )
}

/* ---------------------------------------------------------------- process */

/** What actually happens after you press the button. Said plainly, once. */
function Process() {
  return (
    <section className="sp-process">
      <div className="sp-sec-head reveal">
        <p className="kicker">After you send it</p>
        <h2 className="headline-sm">One person reads these</h2>
        <p className="sp-sec-dek">
          Not a queue, not a bot, and not a team taking shifts. Worth knowing
          what that means for how long it takes.
        </p>
      </div>

      <ol className="sp-steps">
        {PROCESS.map((step, i) => (
          <li className="sp-step reveal" key={step.title} style={{ '--d': `${i * 70}ms` }}>
            <span className="sp-step-n" aria-hidden="true">
              {ord(i)}
            </span>
            <b>{step.title}</b>
            <span>{step.body}</span>
          </li>
        ))}
      </ol>
    </section>
  )
}

/* ------------------------------------------------------------------- page */

export default function Support() {
  const [picked, setPicked] = useState(null)
  const panelRef = useRef(null)

  useReveals()

  useEffect(() => {
    const hash = window.location.hash.slice(1)
    const target = hash && document.getElementById(hash)
    if (target) {
      // A link straight to a question opens it. Landing on a collapsed
      // <details> having followed a link to it reads as a broken anchor.
      if (target.tagName === 'DETAILS') target.open = true
      target.scrollIntoView()
    } else {
      window.scrollTo(0, 0)
    }
  }, [])

  // Bring the opened panel into view, but only for a real choice — not on the
  // first paint, where nothing is picked and there is nothing to show.
  useEffect(() => {
    if (!picked || !panelRef.current) return
    panelRef.current.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [picked])

  const path = PATHS.find((p) => p.id === picked) || null

  return (
    <div id="top" className="support">
      <TopBar />

      <main>
        <SupportHero
          version={RELEASE.version}
          releasePath={`/release/${RELEASE.version}`}
          facts={FACTS}
          faq={FAQ}
          paths={PATHS}
          onPick={(id) => setPicked(id)}
        />

        <section className="sp-choose shell">
          <div className="sp-rows">
            {PATHS.map((p, i) => (
              <PathRow
                key={p.id}
                path={p}
                index={i}
                active={picked === p.id}
                onPick={(id) => setPicked((prev) => (prev === id ? null : id))}
              />
            ))}
          </div>

          <div ref={panelRef}>
            {path ? (
              path.external ? (
                <Elsewhere />
              ) : (
                <>
                  <p className="sp-public">
                    Anything filed here is <b>public and permanent</b>. Leave out
                    account details, tokens and anything from a cookie field —
                    and note that GitHub will ask you to sign in before you can
                    submit.
                  </p>
                  {path.faqFirst ? (
                    <p className="sp-public sp-public-quiet">
                      Worth scanning <a href="#questions">the questions below</a>{' '}
                      first. Eight of them cover most of what gets asked.
                    </p>
                  ) : null}
                  <PathPanel key={path.id} path={path} />
                </>
              )
            ) : null}
          </div>
        </section>

        <div className="shell">
          <Process />
          <Faq />
        </div>

        <section className="sp-tail shell reveal">
          <h2 className="rl-all-h">Still stuck</h2>
          <div className="rl-more-grid">
            <a className="rl-more-card" href={`${REPO}/issues`} target="_blank" rel="noreferrer">
              <span className="rl-more-tag">Issues</span>
              <b>See what is already open</b>
              <span className="rl-more-body">
                Your bug may be filed and half-fixed already, and an existing
                thread moves faster than a new one.
              </span>
            </a>
            <a
              className="rl-more-card"
              href={`${REPO}/blob/master/CHANGELOG.md`}
              target="_blank"
              rel="noreferrer"
            >
              <span className="rl-more-tag">Changelog</span>
              <b>Every fix, with the reason it broke</b>
              <span className="rl-more-body">
                If something changed behaviour between two versions, it is
                written down here.
              </span>
            </a>
            <a
              className="rl-more-card"
              href={`${REPO}/blob/master/CONTRIBUTING.md`}
              target="_blank"
              rel="noreferrer"
            >
              <span className="rl-more-tag">Contributing</span>
              <b>Fix it yourself</b>
              <span className="rl-more-body">
                The toolchain, the flavors, the signing setup, and what a good
                pull request looks like here.
              </span>
            </a>
          </div>
        </section>
      </main>

      <SiteFooter />
    </div>
  )
}
