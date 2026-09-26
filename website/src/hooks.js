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
 * Scroll progress, handed to CSS.
 *
 * Every element marked `data-scroll` gets `--p`: 0 when its top edge meets
 * the bottom of the window, 1 when it has scrolled `data-scroll` window-heights
 * further (1 if the attribute is empty). The stylesheet does the rest — a phone
 * rising, a sentence filling in word by word — so a scroll never re-renders
 * React; one rAF-throttled handler writes a number per element.
 *
 * Reduced motion gets every value at 1: the finished state, with nothing
 * moving on the way there.
 */
export function useScrollProgress() {
  useEffect(() => {
    const nodes = [...document.querySelectorAll('[data-scroll]')]
    if (!nodes.length) return
    if (reduced()) {
      nodes.forEach((n) => n.style.setProperty('--p', '1'))
      return
    }

    let frame = 0
    const apply = () => {
      frame = 0
      const vh = window.innerHeight
      for (const node of nodes) {
        const span = (parseFloat(node.dataset.scroll) || 1) * vh
        const top = node.getBoundingClientRect().top
        const p = Math.min(1, Math.max(0, (vh - top) / span))
        node.style.setProperty('--p', p.toFixed(4))
      }
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
