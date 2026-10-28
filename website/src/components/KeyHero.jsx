import { useEffect, useRef } from 'react'
import { prefersReducedMotion } from '../hooks.js'
import './keyhero.css'

/**
 * The first screen, in the shape of OPPO's ColorOS 16 page.
 *
 * A key visual fills the window: glossy shapes in the app's own four colors
 * and the version as a big pale numeral over them. Scrolling holds the stage
 * and closes the visual into a disc in the middle, while screens of the app
 * come in around it, faded at first and then clear, and the disc settles above
 * a title, a paragraph and one pill. Then the page moves on.
 *
 * One scroll variable, --kp (0..1), drives all of it from CSS; the handler only
 * writes that and the two sizes the disc needs, once a frame at most.
 */

/** The screens around the disc: where each sits on the stage, and how deep it is for the parallax. */
const DEVICES = [
  { src: '/shots/405-fullscreen.jpg', wide: true, x: '-6%', y: '50%', w: 'min(44vw, 640px)', depth: 1.1, cls: 'kh-dev-tab' },
  { src: '/shots/405-player.jpg', x: '18%', y: '-14%', w: 'min(17vw, 250px)', depth: 0.7, rot: 90, cls: 'kh-dev-top' },
  { src: '/shots/405-lyrics.jpg', x: '70%', y: '1%', w: 'min(17vw, 250px)', depth: 0.9 },
  { src: '/shots/405-home.jpg', x: '67%', y: '76%', w: 'min(17vw, 250px)', depth: 1.3, rot: -90 },
  { src: '/shots/405-library.jpg', x: '-3%', y: '-22%', w: 'min(13vw, 190px)', depth: 0.5, cls: 'kh-dev-far' },
]

export default function KeyHero({ id, version, fullVersion = version, name = 'Exhale', line, title, body, primary, secondary }) {
  const box = useRef(null)

  useEffect(() => {
    const node = box.current
    if (!node) return
    if (prefersReducedMotion()) {
      node.dataset.static = 'true'
      node.style.setProperty('--kp', '1')
      return
    }

    let frame = 0
    const update = () => {
      frame = 0
      const rect = node.getBoundingClientRect()
      const run = Math.max(1, rect.height - window.innerHeight)
      const p = Math.min(1, Math.max(0, -rect.top / run))
      node.style.setProperty('--kp', p.toFixed(4))
      // The copy is invisible before it settles; nothing in it should take a click then.
      node.dataset.settled = p > 0.62 ? 'true' : 'false'
    }
    const size = () => {
      // The disc starts as a circle that covers the whole window, corner to corner.
      const w = window.innerWidth
      const h = window.innerHeight
      node.style.setProperty('--kh-cover', `${Math.ceil(Math.hypot(w, h) / 2) + 2}px`)
      node.style.setProperty('--kh-min', `${Math.min(w, h)}px`)
      update()
    }
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update)
    }
    size()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', size)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', size)
      if (frame) cancelAnimationFrame(frame)
    }
  }, [])

  return (
    <section className="kh" id={id} ref={box}>
      <div className="kh-stage">
        <div className="kh-devices" aria-hidden="true">
          {DEVICES.map((d) => (
            <div
              key={d.src}
              className={`kh-dev ${d.wide ? 'kh-dev-wide' : ''} ${d.cls ?? ''}`}
              style={{ '--x': d.x, '--y': d.y, '--w': d.w, '--depth': d.depth, '--rot': `${d.rot ?? 0}deg` }}
            >
              <img src={d.src} alt="" loading="lazy" decoding="async" />
            </div>
          ))}
        </div>

        <div className="kh-kv">
          <div className="kh-art" aria-hidden="true">
            <span className="kh-shape kh-shape-sky" />
            <span className="kh-shape kh-shape-moon" />
            <span className="kh-shape kh-shape-sun" />
          </div>
          <div className="kh-mark">
            <p className="kh-num" aria-hidden="true">
              {version}
            </p>
            <h1 className="kh-name">
              <span className="kh-sr">{name} — A music player that breathes (v{fullVersion})</span>
              <span aria-hidden="true">{name}</span>
            </h1>
            <p className="kh-line">{line}</p>
          </div>
        </div>

        <div className="kh-copy">
          <h2 className="kh-title">{title}</h2>
          <p className="kh-body">{body}</p>
          <div className="kh-actions">
            {primary}
            {secondary}
          </div>
        </div>
      </div>
    </section>
  )
}
