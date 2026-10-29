import { useEffect, useRef } from 'react'
import { prefersReducedMotion } from '../hooks.js'
import './keyhero.css'

/**
 * KeyHero — Flagship Exhale 405 multi-device scroll hero.
 *
 * Sequence (matching Exhale 405 hero):
 * - Scroll 0.00: Fullscreen golden ribbons background with big metallic "405 Exhale" mark.
 * - Scroll 0.00 -> 0.15: Initial entrance zoom-out:
 *   Center phone appears scaled in close (scale ~2.10) framing the background,
 *   while the metallic 405 mark smoothly shrinks and dissolves into the phone's screen.
 * - Scroll 0.15 -> 0.45: [LONG SOLO PHONE HOLD]
 *   The phone stays alone in the center for an extended scroll duration (~150vh),
 *   commanding the entire screen at prominent portrait hero scale (easing 1.75 -> 1.45).
 *   No companion devices are visible yet.
 * - Scroll 0.45 -> 0.74: [SHOWCASE DOCKING]
 *   The center phone smoothly settles from 1.45 down to 1.00 into its showcase dock.
 *   Simultaneously, the 4 companion showcase devices glide in smoothly from their OWN
 *   respective off-screen sides (tablet from left, phone from top, tablet from right,
 *   phone from bottom) to frame the center phone.
 * - Scroll 0.74 -> 0.88: All 5 devices locked in full showcase layout with brand lockup & CTA.
 * - Scroll 0.84 -> 0.98: Golden ribbon background smoothly fades to black.
 */

const SURROUNDING_DEVICES = [
  {
    id: 'tablet-left',
    src: '/hero/oppo_dev_tablet_left.png',
    alt: 'Exhale on showcase Find N foldable tablet',
    x: '6.83%',
    y: '27.36%',
    w: '29.42%',
    h: '63.06%',
    dx: '-260%',
    dy: '0%',
    depth: 1.05,
    zIndex: 3,
    tStart: 0.45,
    tDur: 0.27,
  },
  {
    id: 'tablet-right',
    src: '/hero/oppo_dev_tablet_right.png',
    alt: 'Exhale lyrics on tablet: Manchild by Sabrina Carpenter',
    x: '62.47%',
    y: '-2%',
    w: '37.53%',
    h: '55.99%',
    dx: '220%',
    dy: '-40%',
    depth: 0.95,
    zIndex: 2,
    tStart: 0.47,
    tDur: 0.25,
  },
  {
    id: 'phone-top',
    src: '/hero/oppo_dev_phone_top.png',
    alt: 'Exhale word-synced lyrics on landscape showcase phone',
    x: '16.04%',
    y: '-4%',
    w: '33.32%',
    h: '19.40%',
    dx: '0%',
    dy: '-280%',
    depth: 0.90,
    zIndex: 4,
    tStart: 0.43,
    tDur: 0.29,
  },
  {
    id: 'phone-bottom',
    src: '/hero/oppo_dev_phone_bottom.png',
    alt: 'Exhale music player on landscape showcase phone',
    x: '62.43%',
    y: '66.38%',
    w: '29.86%',
    h: '27.90%',
    dx: '80%',
    dy: '240%',
    depth: 1.15,
    zIndex: 4,
    tStart: 0.49,
    tDur: 0.23,
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
      node.dataset.settled = p > 0.74 ? 'showcase' : 'intro'
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
      node.dataset.settled = p > 0.74 ? 'showcase' : 'intro'
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

        {/* The 5 authentic showcase devices stage */}
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

        {/* Brand Lockup & Actions — rises in directly beneath the showcase devices like Exhale 405 */}
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
