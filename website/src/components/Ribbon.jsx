import { useEffect, useRef } from 'react'
import { PauseIcon, PlayIcon } from '../icons.jsx'
import { prefersReducedMotion } from '../hooks.js'

/**
 * Five lens-shaped ribbons of light weaving through each other, on canvas.
 *
 * CSS can't do this. An ellipse only translates, rotates and scales, so the
 * shape never actually changes and it ends up looking like a lava lamp.
 *
 * Drawn crisp at reduced scale with `lighter` compositing; the blur is a CSS
 * filter on the element rather than `ctx.filter`, which would re-run a CPU
 * convolution per path per frame. Stops when off screen, hidden or paused.
 */
export default function Ribbon({ playing, onToggle }) {
  const canvasRef = useRef(null)
  const wrapRef = useRef(null)
  const playingRef = useRef(playing)
  playingRef.current = playing

  useEffect(() => {
    const canvas = canvasRef.current
    const wrap = wrapRef.current
    if (!canvas || !wrap) return

    const ctx = canvas.getContext('2d', { alpha: true })
    if (!ctx) return

    const styles = getComputedStyle(document.documentElement)
    const hue = (name, fallback) =>
      styles.getPropertyValue(name).trim() || fallback

    // Each band drifts on its own period, so they pass through each other and
    // the color order keeps inverting. `base` is the resting position, `drift`
    // how far it wanders. Both are needed: base alone and the order never
    // changes, drift alone and all five pile up and sum to white. Periods share
    // no common factors so the pattern doesn't visibly repeat.
    const bands = [
      { css: hue('--iris', '#7b6cff'), base: -0.1, drift: 0.105, period: 9, phase: 0, freq: 1, xphase: 0, speed: 0.16, thick: 0.095, alpha: 0.8 },
      { css: hue('--cyan', '#38d3d6'), base: -0.05, drift: 0.095, period: 11.5, phase: 1.7, freq: 1.25, xphase: 1.1, speed: -0.13, thick: 0.078, alpha: 0.7 },
      { css: '#fff2dc', base: 0, drift: 0.055, period: 7.5, phase: 3.1, freq: 1.1, xphase: 2.4, speed: 0.1, thick: 0.055, alpha: 0.55 },
      { css: hue('--ember', '#ff8a6b'), base: 0.05, drift: 0.095, period: 13, phase: 4.4, freq: 1.35, xphase: 3.3, speed: -0.17, thick: 0.078, alpha: 0.7 },
      { css: hue('--rose', '#ed5564'), base: 0.1, drift: 0.105, period: 10, phase: 5.6, freq: 0.95, xphase: 4.9, speed: 0.14, thick: 0.095, alpha: 0.8 },
    ]

    const parse = (css) => {
      const hex = css.replace('#', '').trim()
      const full = hex.length === 3 ? hex.split('').map((c) => c + c).join('') : hex
      return [
        parseInt(full.slice(0, 2), 16),
        parseInt(full.slice(2, 4), 16),
        parseInt(full.slice(4, 6), 16),
      ]
    }

    const rgb = bands.map((band) => parse(band.css))

    // Halo, core, spine. Alphas sum to just under 1 for a single band; the
    // headroom left over is what crossings spend reaching white. `fade` is
    // where each pass peaks along its own length.
    const PASSES = [
      { width: 2.05, alpha: 0.28, fade: [0.14, 0.52, 0.9] },
      { width: 1, alpha: 0.7, fade: [0.2, 0.58, 1] },
      // Narrow and hot. Five of these overlapping give the white core.
      { width: 0.4, alpha: 0.36, fade: [0.26, 0.62, 0.99] },
    ]

    // Pulled in from the authored spread so they overlap at the waist.
    const SPREAD = 0.82

    const TAU = Math.PI * 2
    let w = 0
    let h = 0
    // Segment count follows the canvas: a crest needs enough points to stay a
    // curve, and on a phone there are barely enough pixels for half as many.
    let steps = 88

    const resize = () => {
      const rect = wrap.getBoundingClientRect()

      // Resolution rises as the element shrinks. 55% is fine on a 1020px hero
      // because the blur hides what's missing; at phone size it left each band
      // ~3px tall and the whole thing read as a loading bar.
      const scale = rect.width < 560 ? 0.9 : 0.55
      steps = rect.width < 560 ? 56 : 88

      w = Math.max(1, Math.round(rect.width * scale))
      h = Math.max(1, Math.round(rect.height * scale))
      canvas.width = w
      canvas.height = h

      // Blur scales with the object. A fixed 9px is a soft edge at desktop size
      // and wider than the bands themselves on a phone.
      const blur = Math.min(13, Math.max(3.5, rect.height * 0.041))
      wrap.style.setProperty('--ribbon-blur', `${blur.toFixed(2)}px`)
    }

    resize()
    const observer = new ResizeObserver(resize)
    observer.observe(wrap)

    const draw = (t) => {
      ctx.clearRect(0, 0, w, h)
      // Additive: every crossing climbs towards white the way real light does,
      // so the bright core is produced by the weave rather than painted in.
      ctx.globalCompositeOperation = 'lighter'

      // Terms shared by every band. These are what make five paths read as one
      // object rather than five ribbons that happen to be near each other.

      // Amplitude and thickness together, or it reads as a zoom, not an inhale.
      const breath = 0.84 + 0.16 * Math.sin((t / 13) * TAU)

      const tilt = 0.052 + Math.sin((t / 21) * TAU) * 0.038

      // Slightly out of phase with the breath, so the brightest moment isn't
      // also the widest.
      const bloom = 0.88 + 0.12 * Math.sin((t / 9.5) * TAU + 1.2)

      // Opens from a flat line. Starting mid-motion looks like a video that was
      // already playing.
      const raw = Math.min(1, t / 1.6)
      const intro = raw * raw * (3 - 2 * raw)

      // One shared path with an S in it that everything rides, so a band's own
      // drift is a departure from a common line. Two components on unrelated
      // periods so the S migrates rather than standing still.
      const spine = (u) =>
        Math.sin(u * Math.PI * 0.92 + t * 0.17) * h * 0.055 +
        Math.sin(u * Math.PI * 1.9 - t * 0.11) * h * 0.021

      // A flat ribbon rotating in space goes thin where it turns edge-on, and
      // that pinch travels along its length. One multiply, and it's most of
      // what separates a ribbon from a stack of glowing bars.
      const pinch = (u, band) =>
        0.42 +
        0.58 * Math.abs(Math.sin(u * Math.PI * 0.85 + t * 0.13 + band.xphase * 0.12))

      bands.forEach((band, index) => {
        const [r, g, b] = rgb[index]

        // Drift is the weave, the two waves are the flex along its length.
        // Two rather than one: a single sine is a shape the eye solves in a
        // couple of seconds.
        const centre = (u) =>
          h * 0.5 +
          spine(u) * intro +
          h * band.base * SPREAD * breath +
          (u - 0.5) * h * tilt +
          Math.sin((t / band.period) * TAU + band.phase) * h * band.drift * breath * intro +
          Math.sin(u * TAU * band.freq + band.xphase + t * band.speed) * h * 0.045 * intro +
          Math.sin(u * TAU * band.freq * 2.3 + band.xphase * 1.7 - t * band.speed * 0.63) *
            h *
            0.017 *
            intro

        // Lens: full through the middle, pointed at both ends. Below 1 the
        // exponent leaves a blunt edge; above it the band comes to a point.
        const half = (u) =>
          Math.sin(Math.PI * u) ** 0.86 *
          h *
          band.thick *
          pinch(u, band) *
          (0.32 + 0.68 * breath) *
          intro

        // One pass at a single width gives a band with an edge. Stacking a
        // faint wide halo under a narrow core gives a bright middle falling
        // away, without a second blur.
        for (const pass of PASSES) {
          const gradient = ctx.createLinearGradient(0, 0, w, 0)
          const peak = band.alpha * pass.alpha * bloom * intro
          const [rise, crest, end] = pass.fade
          gradient.addColorStop(0, `rgba(${r},${g},${b},0)`)
          gradient.addColorStop(rise, `rgba(${r},${g},${b},${peak * 0.5})`)
          gradient.addColorStop(crest, `rgba(${r},${g},${b},${peak})`)
          gradient.addColorStop(end, `rgba(${r},${g},${b},0)`)
          if (end < 1) gradient.addColorStop(1, `rgba(${r},${g},${b},0)`)
          ctx.fillStyle = gradient

          ctx.beginPath()
          for (let i = 0; i <= steps; i += 1) {
            const u = i / steps
            const y = centre(u) - half(u) * pass.width
            if (i === 0) ctx.moveTo(u * w, y)
            else ctx.lineTo(u * w, y)
          }
          for (let i = steps; i >= 0; i -= 1) {
            const u = i / steps
            ctx.lineTo(u * w, centre(u) + half(u) * pass.width)
          }
          ctx.closePath()
          ctx.fill()
        }
      })

      ctx.globalCompositeOperation = 'source-over'
    }

    let frame = 0
    let clock = 0
    let last = performance.now()
    let onScreen = true

    const tick = (now) => {
      const dt = Math.min(0.05, (now - last) / 1000)
      last = now
      if (playingRef.current) clock += dt
      draw(clock)
      frame = onScreen ? requestAnimationFrame(tick) : 0
    }

    // Reduced motion gets one frame of the shape, never a loop.
    if (prefersReducedMotion()) {
      draw(0)
    } else {
      frame = requestAnimationFrame(tick)
    }

    // Some browsers still service rAF in a hidden tab.
    const onVisibility = () => {
      if (document.hidden) {
        if (frame) cancelAnimationFrame(frame)
        frame = 0
      } else if (onScreen && !frame && !prefersReducedMotion()) {
        last = performance.now()
        frame = requestAnimationFrame(tick)
      }
    }
    document.addEventListener('visibilitychange', onVisibility)

    const io = new IntersectionObserver(
      ([entry]) => {
        onScreen = entry.isIntersecting
        if (onScreen && !frame && !prefersReducedMotion()) {
          last = performance.now()
          frame = requestAnimationFrame(tick)
        }
      },
      { rootMargin: '80px' },
    )
    io.observe(wrap)

    return () => {
      if (frame) cancelAnimationFrame(frame)
      document.removeEventListener('visibilitychange', onVisibility)
      observer.disconnect()
      io.disconnect()
    }
  }, [])

  return (
    <div className="ribbon-wrap">
      <div className="ribbon" ref={wrapRef}>
        <canvas className="ribbon-canvas" ref={canvasRef} aria-hidden="true" />
      </div>

      <button
        type="button"
        className="ambient-toggle"
        onClick={onToggle}
        aria-label={playing ? 'Pause the animation' : 'Play the animation'}
      >
        {playing ? <PauseIcon /> : <PlayIcon />}
      </button>
    </div>
  )
}
