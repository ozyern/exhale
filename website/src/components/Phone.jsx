import { useEffect, useRef } from 'react'
import { prefersReducedMotion } from '../hooks.js'

/**
 * A frame around real screenshots of the app.
 *
 * Every screen stays mounted and cross-fades on `data-on`, so the frame never
 * resizes between beats and the device doesn't jump while it's pinned.
 *
 * Fitted with realistic camera punch-hole and specular glass glare.
 */

/**
 * A screen recording layered over the stills.
 */
function PhoneVideo({ src, poster, on, running }) {
  const ref = useRef(null)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    if (on && running && !prefersReducedMotion()) {
      const started = el.play()
      if (started?.catch) started.catch(() => {})
    } else {
      el.pause()
      if (!on) el.currentTime = 0
    }
  }, [on, running])

  return (
    <video
      ref={ref}
      className="phone-shot"
      data-on={on}
      src={src}
      poster={poster}
      muted
      loop
      playsInline
      preload="metadata"
      aria-hidden="true"
    />
  )
}

export default function Phone({ shots, index, video }) {
  const phoneRef = useRef(null)

  const handleMouseEnter = (e) => {
    e.currentTarget._cachedRect = e.currentTarget.getBoundingClientRect()
  }

  const handleMouseMove = (e) => {
    if (prefersReducedMotion()) return
    let rect = e.currentTarget._cachedRect
    if (!rect) {
      rect = e.currentTarget.getBoundingClientRect()
      e.currentTarget._cachedRect = rect
    }
    const x = (e.clientX - rect.left) / rect.width
    const y = (e.clientY - rect.top) / rect.height
    const rx = ((0.5 - y) * 10).toFixed(2)
    const ry = ((x - 0.5) * 10).toFixed(2)
    e.currentTarget.style.setProperty('--rx', `${rx}deg`)
    e.currentTarget.style.setProperty('--ry', `${ry}deg`)
    e.currentTarget.style.setProperty('--gx', `${(x * 100).toFixed(1)}%`)
    e.currentTarget.style.setProperty('--gy', `${(y * 100).toFixed(1)}%`)
  }

  const handleMouseLeave = (e) => {
    e.currentTarget.style.setProperty('--rx', '0deg')
    e.currentTarget.style.setProperty('--ry', '0deg')
  }

  return (
    <div
      ref={phoneRef}
      className="phone"
      onMouseEnter={handleMouseEnter}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
    >
      <div className="phone-screen">
        <div className="phone-camera" aria-hidden="true" />
        <div className="phone-glare" aria-hidden="true" />
        {shots.map((shot, i) => (
          <img
            key={shot.src}
            className="phone-shot"
            data-on={i === index && !video?.on}
            src={shot.src}
            alt={i === index ? shot.alt : ''}
            aria-hidden={i !== index}
            width="592"
            height="1322"
            loading={i === 0 ? 'eager' : 'lazy'}
            decoding="async"
          />
        ))}

        {video ? <PhoneVideo {...video} /> : null}
      </div>
    </div>
  )
}
