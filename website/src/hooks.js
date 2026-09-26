import { useEffect, useState } from 'react'

const reduced = () =>
  typeof window !== 'undefined' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

/**
 * Reveal-on-scroll, as one observer for the whole page rather than one per
 * element. Elements opt in with `className="reveal"`; the observer adds `in`
 * once and then stops watching them, so a long page does not keep dozens of
 * live observations alive while you scroll past.
 */
export function useReveals() {
  useEffect(() => {
    const nodes = document.querySelectorAll('.reveal')
    if (reduced() || !('IntersectionObserver' in window)) {
      nodes.forEach((n) => n.classList.add('in'))
      return
    }

    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return
          entry.target.classList.add('in')
          io.unobserve(entry.target)
        })
      },
      { rootMargin: '0px 0px -12% 0px', threshold: 0.08 },
    )

    nodes.forEach((n) => io.observe(n))
    return () => io.disconnect()
  }, [])
}

/**
 * Links the hero object to the scroll.
 *
 * The hero recedes as you scroll away from it. Two custom properties written
 * from a rAF-throttled handler, so nothing here re-renders React — the browser
 * only re-composites a transform and an opacity.
 */
export function useHeroScroll() {
  useEffect(() => {
    if (reduced()) return
    const node = document.querySelector('.ribbon')
    if (!node) return

    // The fan below the statement drifts against the phone in front of it, so
    // the group separates as it passes the middle of the screen.
    const fan = document.querySelector('.fan')

    // Measured on resize, not on scroll. getBoundingClientRect inside a scroll
    // handler forces a layout every frame; the fan does not move in the
    // document, so its position only changes when the page reflows.
    let fanMid = 0
    const measure = () => {
      if (!fan) return
      const rect = fan.getBoundingClientRect()
      fanMid = rect.top + window.scrollY + rect.height / 2
    }

    let frame = 0
    const apply = () => {
      frame = 0
      const progress = Math.min(1, window.scrollY / (window.innerHeight * 0.9))
      node.style.setProperty('--hero-scale', (1 + progress * 0.12).toFixed(3))
      node.style.setProperty('--hero-fade', (1 - progress * 0.85).toFixed(3))

      if (fan) {
        const seen = window.scrollY + window.innerHeight / 2
        const away = (seen - fanMid) / (window.innerHeight * 0.8)
        fan.style.setProperty('--fan', Math.max(-1, Math.min(1, away)).toFixed(3))
      }
    }
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(apply)
    }

    const onResize = () => {
      measure()
      apply()
    }

    measure()
    apply()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onResize)
    // Images landing late change where the fan sits.
    window.addEventListener('load', onResize)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onResize)
      window.removeEventListener('load', onResize)
      if (frame) cancelAnimationFrame(frame)
    }
  }, [])
}

/** True once the page has moved at all — the nav uses it to lift onto its rim. */
export function useScrolled(after = 12) {
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    let frame = 0
    const onScroll = () => {
      if (frame) return
      // One state write per frame at most: `scroll` fires far faster than the
      // compositor can use, and every extra write is a React render.
      frame = requestAnimationFrame(() => {
        frame = 0
        setScrolled(window.scrollY > after)
      })
    }
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => {
      window.removeEventListener('scroll', onScroll)
      if (frame) cancelAnimationFrame(frame)
    }
  }, [after])

  return scrolled
}

/**
 * Whether a node is on screen. The two demos use it to stop their timers when
 * they scroll away — an offscreen lyric ticker is a wakeup every two seconds
 * for something nobody is looking at, which is exactly the thing the app's own
 * ticker was rewritten to avoid.
 */
export function useOnScreen(ref, margin = '0px') {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const node = ref.current
    if (!node) return
    if (!('IntersectionObserver' in window)) {
      setVisible(true)
      return
    }
    const io = new IntersectionObserver(
      ([entry]) => setVisible(entry.isIntersecting),
      { rootMargin: margin },
    )
    io.observe(node)
    return () => io.disconnect()
  }, [ref, margin])

  return visible
}

export { reduced as prefersReducedMotion }
