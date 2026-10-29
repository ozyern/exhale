import { useEffect, useRef } from 'react'
import { prefersReducedMotion } from '../hooks.js'
import './keyhero.css'

/**
 * KeyHero — Flagship ColorOS 17 multi-device scroll hero.
 *
 * Sequence:
 * - Scroll 0: Plain golden ribbons background with big metallic "405 Exhale" mark.
 *   No phones visible.
 * - Scroll 0 -> 0.35: Middle phone smoothly appears and scales in while the 405
 *   mark shrinks and merges into the phone's screen.
 * - Scroll 0.28 -> 0.72: The other 4 authentic OPPO devices fan out smoothly
 *   from behind the middle phone into their ColorOS showcase layout.
 * - Scroll 0.72 -> 0.80: All 5 devices locked in full showcase layout.
 * - Scroll 0.80 -> 1.00: Devices gracefully lift and fade out completely; copy
 *   and CTA rise into the center with zero overlap.
 */

const SURROUNDING_DEVICES = [
  {
    id: 'tablet-left',
    src: '/hero/oppo_dev_tablet_left.png',
    alt: 'Exhale on OPPO Find N foldable tablet',
    x: '6.83%',
    y: '27.36%',
    w: '29.42%',
    h: '63.06%',
    dx: '28.60%',
    dy: '-2.44%',
    depth: 1.1,
    zIndex: 3,
    tStart: 0.28,
    tDur: 0.44,
  },
  {
    id: 'tablet-right',
    src: '/hero/oppo_dev_tablet_right.png',
    alt: 'Exhale lyrics on tablet: Manchild by Sabrina Carpenter',
    x: '62.47%',
    y: '-2%',
    w: '37.53%',
    h: '55.99%',
    dx: '-31.10%',
    dy: '30.45%',
    depth: 0.95,
    zIndex: 2,
    tStart: 0.30,
    tDur: 0.44,
  },
  {
    id: 'phone-top',
    src: '/hero/oppo_dev_phone_top.png',
    alt: 'Exhale word-synced lyrics on landscape OPPO phone',
    x: '16.04%',
    y: '-4%',
    w: '33.32%',
    h: '19.40%',
    dx: '17.44%',
    dy: '50.75%',
    depth: 0.85,
    zIndex: 4,
    tStart: 0.32,
    tDur: 0.42,
  },
  {
    id: 'phone-bottom',
    src: '/hero/oppo_dev_phone_bottom.png',
    alt: 'Exhale music player on landscape OPPO phone',
    x: '62.43%',
    y: '66.38%',
    w: '29.86%',
    h: '27.90%',
    dx: '-27.22%',
    dy: '-23.88%',
    depth: 1.25,
    zIndex: 4,
    tStart: 0.34,
    tDur: 0.42,
  },
]

export default function KeyHero({
  id = 'overview',
  version = '405',
  fullVersion = '1.0.405',
  name = 'Exhale',
  line = 'Silky Smooth. Knows your music.',
  title = 'Ultra fluid. Knows your music.',
  body = 'Live glass on every surface, lyrics that land on the beat, and color that follows whatever is playing. Open source, for Android 13 and newer.',
  primary,
  secondary,
}) {
  const box = useRef(null)

  useEffect(() => {
    const node = box.current
    if (!node) return

    const queryKp = new URLSearchParams(window.location.search).get('kp')
    if (queryKp !== null) {
      const p = Math.min(1, Math.max(0, parseFloat(queryKp) || 0))
      node.style.setProperty('--kp', p.toFixed(4))
      node.dataset.settled = p > 0.68 ? 'showcase' : 'intro'
      return
    }

    if (prefersReducedMotion()) {
      node.dataset.static = 'true'
      node.style.setProperty('--kp', '0.85')
      return
    }

    let frame = 0
    const update = () => {
      frame = 0
      const rect = node.getBoundingClientRect()
      const run = Math.max(1, rect.height - window.innerHeight)
      const p = Math.min(1, Math.max(0, -rect.top / run))
      node.style.setProperty('--kp', p.toFixed(4))
      node.dataset.settled = p > 0.68 ? 'showcase' : 'intro'
    }
    const size = () => {
      const w = window.innerWidth
      const h = window.innerHeight
      node.style.setProperty('--kh-w', `${w}px`)
      node.style.setProperty('--kh-h', `${h}px`)
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
        {/* Plain initial key visual at scroll 0: Big metallic 405 + Exhale on gold ribbon */}
        <div className="kh-plain-kv" aria-hidden="true">
          <p className="kh-plain-num">{version}</p>
          <h1 className="kh-plain-name">{name}</h1>
          <p className="kh-plain-line">{line}</p>
        </div>

        {/* The 5 authentic OPPO devices stage */}
        <div className="kh-devices" aria-hidden="true">
          {/* Surrounding 4 devices fan out from behind the middle phone */}
          {SURROUNDING_DEVICES.map((d) => (
            <div
              key={d.id}
              className={`kh-dev kh-dev-${d.id}`}
              style={{
                '--x': d.x,
                '--y': d.y,
                '--w': d.w,
                '--h': d.h,
                '--dx': d.dx,
                '--dy': d.dy,
                '--depth': d.depth,
                '--z': d.zIndex ?? 1,
                '--t-start': d.tStart,
                '--t-dur': d.tDur,
              }}
            >
              <img src={d.src} alt={d.alt} loading="eager" decoding="async" />
            </div>
          ))}

          {/* Middle Phone: Appears during scroll, anchors center */}
          <div
            className="kh-dev kh-dev-phone-middle"
            style={{
              '--x': '42.89%',
              '--y': '30.06%',
              '--w': '14.50%',
              '--h': '52.78%',
              '--depth': 1.0,
              '--z': 5,
            }}
          >
            <img
              src="/hero/oppo_dev_phone_middle.png"
              alt="Exhale 405 on portrait flagship phone"
              className="kh-mid-app"
              loading="eager"
              decoding="async"
            />
          </div>
        </div>

        {/* Brand Lockup & Actions — rises in directly beneath the showcase devices like OPPO ColorOS 17 */}
        <div className="kh-brand-lockup">
          <h2 className="kh-brand-title">
            <span className="kh-brand-name">{name}</span>
            <span className="kh-brand-ver">{version}</span>
          </h2>
          <p className="kh-brand-line">{line}</p>
          <div className="kh-brand-actions">
            {primary}
            {secondary}
          </div>
        </div>
      </div>
    </section>
  )
}
