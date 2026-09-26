import { useEffect, useRef } from 'react'
import { prefersReducedMotion } from '../hooks.js'

/*
 * The 1.0.304 hero: the build number read through moving glass.
 *
 * 1.0.203's field was a star field — the number assembling itself out of
 * scattered light. It was the right object for that release and it would be the
 * wrong one twice: a site that opens every announcement on the same trick stops
 * announcing anything. This release is about one material applied to a whole
 * app, so the hero is that material. The number is printed once, on a warm
 * field, and three slabs of glass drift across it; where a slab covers the
 * number it is redrawn displaced, brighter and softer, the way type looks
 * through something thick.
 *
 * Everything is canvas 2D. No shaders, no WebGL context to lose, and no
 * dependency: the whole effect is a clip path, a translate and a blur, which
 * every browser has had for a decade and which costs nothing on a phone.
 *
 * Honest about motion: with `prefers-reduced-motion` the slabs are placed once
 * and never move, so the composition is still the composition — one still frame
 * of it, rather than a blank rectangle.
 */

/** The slabs, as fractions of the canvas: where they start and how they drift. */
const PANES = [
  { x: -0.18, y: 0.08, w: 0.46, h: 1.5, rot: -0.28, speed: 0.019, phase: 0.0 },
  { x: 0.34, y: -0.22, w: 0.34, h: 1.7, rot: 0.22, speed: -0.014, phase: 1.9 },
  { x: 0.74, y: 0.16, w: 0.40, h: 1.4, rot: -0.15, speed: 0.011, phase: 3.4 },
]

/** The warm field behind everything: three blobs, drifting at their own pace. */
const BLOBS = [
  { x: 0.24, y: 0.34, r: 0.62, hue: '255, 186, 74', speed: 0.05, phase: 0.4 },
  { x: 0.72, y: 0.28, r: 0.54, hue: '255, 138, 46', speed: -0.037, phase: 2.2 },
  { x: 0.52, y: 0.78, r: 0.70, hue: '164, 92, 255', speed: 0.028, phase: 4.1 },
]

function roundedRectPath(ctx, x, y, w, h, r) {
  const radius = Math.min(r, w / 2, h / 2)
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.arcTo(x + w, y, x + w, y + h, radius)
  ctx.arcTo(x + w, y + h, x, y + h, radius)
  ctx.arcTo(x, y + h, x, y, radius)
  ctx.arcTo(x, y, x + w, y, radius)
  ctx.closePath()
}

export default function GlassField({ text, tall = false, replayKey = 0 }) {
  const canvas = useRef(null)

  useEffect(() => {
    const el = canvas.current
    if (!el) return
    const ctx = el.getContext('2d', { alpha: true })
    if (!ctx) return

    const reduced = prefersReducedMotion()
    let frame = 0
    let width = 0
    let height = 0
    let dpr = 1
    const started = performance.now()

    const measure = () => {
      const rect = el.getBoundingClientRect()
      // Capped at 2: beyond that this is a lot of full-screen blur for a
      // difference nobody can see, and phones are exactly where that costs.
      dpr = Math.min(window.devicePixelRatio || 1, 2)
      width = Math.max(1, Math.round(rect.width))
      height = Math.max(1, Math.round(rect.height))
      el.width = Math.round(width * dpr)
      el.height = Math.round(height * dpr)
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    }

    /** The number, drawn at whatever size the box can carry. */
    const drawNumber = (alpha, blur, dx) => {
      const size = Math.min(width * (tall ? 0.52 : 0.4), height * 0.72)
      ctx.save()
      ctx.globalAlpha = alpha
      if (blur) ctx.filter = `blur(${blur}px)`
      ctx.font = `700 ${size}px "Linotte", ui-rounded, system-ui, sans-serif`
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      ctx.fillStyle = '#fff'
      ctx.fillText(text, width / 2 + dx, height / 2)
      ctx.restore()
    }

    const render = (now) => {
      const t = reduced ? 6.2 : (now - started) / 1000

      ctx.clearRect(0, 0, width, height)

      // ---- the field -------------------------------------------------------
      ctx.save()
      for (const blob of BLOBS) {
        const drift = Math.sin(t * blob.speed + blob.phase)
        const cx = (blob.x + drift * 0.05) * width
        const cy = (blob.y + Math.cos(t * blob.speed * 1.3 + blob.phase) * 0.045) * height
        const r = blob.r * Math.max(width, height) * 0.62
        const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, r)
        g.addColorStop(0, `rgba(${blob.hue}, 0.42)`)
        g.addColorStop(0.55, `rgba(${blob.hue}, 0.12)`)
        g.addColorStop(1, `rgba(${blob.hue}, 0)`)
        ctx.fillStyle = g
        ctx.fillRect(0, 0, width, height)
      }
      ctx.restore()

      // ---- the number, printed on the field --------------------------------
      drawNumber(0.9, 0, 0)

      // ---- the glass -------------------------------------------------------
      for (const pane of PANES) {
        const travel = reduced ? 0 : Math.sin(t * pane.speed * Math.PI + pane.phase)
        const x = (pane.x + travel * 0.06) * width
        const y = pane.y * height
        const w = pane.w * width
        const h = pane.h * height
        const cx = x + w / 2
        const cy = y + h / 2

        ctx.save()
        ctx.translate(cx, cy)
        ctx.rotate(pane.rot + travel * 0.01)
        ctx.translate(-cx, -cy)
        roundedRectPath(ctx, x, y, w, h, Math.min(w, h) * 0.12)
        ctx.clip()

        // What the slab bends: the same number, displaced and softened. Drawn
        // inside the clip, so it only exists where the glass is.
        drawNumber(0.55, 10, w * 0.055)

        // The pane itself: brighter along the leading edge, gone by the far one.
        const sheen = ctx.createLinearGradient(x, y, x + w, y + h)
        sheen.addColorStop(0, 'rgba(255, 255, 255, 0.16)')
        sheen.addColorStop(0.45, 'rgba(255, 255, 255, 0.05)')
        sheen.addColorStop(1, 'rgba(255, 255, 255, 0.01)')
        ctx.fillStyle = sheen
        ctx.fillRect(x - w, y - h, w * 3, h * 3)
        ctx.restore()

        // The rim, outside the clip so it draws as a hairline rather than as
        // half of one: a clipped stroke loses the outer half of its width.
        ctx.save()
        ctx.translate(cx, cy)
        ctx.rotate(pane.rot + travel * 0.01)
        ctx.translate(-cx, -cy)
        roundedRectPath(ctx, x, y, w, h, Math.min(w, h) * 0.12)
        const rim = ctx.createLinearGradient(x, y, x, y + h)
        rim.addColorStop(0, 'rgba(255, 255, 255, 0.34)')
        rim.addColorStop(0.5, 'rgba(255, 255, 255, 0.10)')
        rim.addColorStop(1, 'rgba(255, 255, 255, 0.02)')
        ctx.strokeStyle = rim
        ctx.lineWidth = 1
        ctx.stroke()
        ctx.restore()
      }

      if (!reduced) frame = requestAnimationFrame(render)
    }

    measure()
    frame = requestAnimationFrame(render)

    const onResize = () => {
      measure()
      if (reduced) render(performance.now())
    }
    window.addEventListener('resize', onResize)

    return () => {
      cancelAnimationFrame(frame)
      window.removeEventListener('resize', onResize)
    }
  }, [text, tall, replayKey])

  return <canvas className="gf" ref={canvas} aria-hidden="true" />
}
