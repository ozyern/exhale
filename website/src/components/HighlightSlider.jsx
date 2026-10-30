import { useEffect, useRef, useState } from 'react'
import { prefersReducedMotion, useOnScreen } from '../hooks.js'

/** How long each highlight holds before the rail moves on by itself. */
const HL_HOLD_MS = 5200

/**
 * "Get the highlights." The release in a row of tall dark tiles you swipe through: a sentence at
 * the top of each, its lead in white, and the screen it is about rising out of the bottom. Below,
 * ColorOS's controls: a capsule of dots whose active one is a bar filling while it holds, and two
 * large round arrows.
 *
 * Enhanced with desktop mouse grab-to-scroll, dynamic cursor specular spotlight on cards,
 * and fluid spring transitions.
 */
export default function HighlightSlider({ id = 'highlights', title = 'Get the highlights.', items }) {
  const rail = useRef(null)
  const section = useRef(null)
  const [at, setAt] = useState(0)
  const [auto, setAuto] = useState(!prefersReducedMotion())
  const live = useOnScreen(section, '0px')

  const dragRef = useRef({
    isDown: false,
    startX: 0,
    scrollLeft: 0,
    moved: false,
  })

  useEffect(() => {
    if (!auto || !live) return undefined
    const timer = window.setTimeout(() => goTo(at >= items.length - 1 ? 0 : at + 1, true), HL_HOLD_MS)
    return () => window.clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto, live, at, items.length])

  useEffect(() => {
    const onWindowMouseUp = () => {
      if (dragRef.current.isDown) {
        dragRef.current.isDown = false
        const node = rail.current
        if (node) {
          node.style.cursor = 'grab'
          node.style.scrollSnapType = 'x mandatory'
        }
      }
    }
    window.addEventListener('mouseup', onWindowMouseUp)
    return () => window.removeEventListener('mouseup', onWindowMouseUp)
  }, [])

  useEffect(() => {
    const node = rail.current
    if (!node) return undefined
    let frame = 0
    let pitch = 0

    const updatePitch = () => {
      const first = node.firstElementChild
      if (first) pitch = first.offsetWidth + 20
    }
    updatePitch()

    const onScroll = () => {
      if (frame) return
      frame = requestAnimationFrame(() => {
        frame = 0
        if (!pitch) updatePitch()
        if (pitch > 0) {
          const next = Math.round(node.scrollLeft / pitch)
          setAt((prev) => (prev !== next ? next : prev))
        }
      })
    }

    node.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', updatePitch)
    return () => {
      node.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', updatePitch)
      if (frame) cancelAnimationFrame(frame)
    }
  }, [])

  const goTo = (index, byItself = false) => {
    if (!byItself) setAuto(false)
    const node = rail.current
    if (!node || !node.children.length) return
    const clamped = Math.max(0, Math.min(items.length - 1, index))
    const target = node.children[clamped]
    node.scrollTo({
      left: target.offsetLeft - node.firstElementChild.offsetLeft,
      behavior: prefersReducedMotion() ? 'auto' : 'smooth',
    })
  }

  // Mouse grab-and-drag scrolling for desktop
  const handleMouseDown = (e) => {
    const node = rail.current
    if (!node) return
    dragRef.current = {
      isDown: true,
      startX: e.pageX - node.offsetLeft,
      scrollLeft: node.scrollLeft,
      moved: false,
    }
    setAuto(false)
  }

  const handleMouseMove = (e) => {
    if (!dragRef.current.isDown) return
    const node = rail.current
    if (!node) return
    e.preventDefault()
    const x = e.pageX - node.offsetLeft
    const walk = (x - dragRef.current.startX) * 1.4
    if (Math.abs(walk) > 4) {
      dragRef.current.moved = true
      node.scrollLeft = dragRef.current.scrollLeft - walk
    }
  }

  const handleMouseUp = () => {
    dragRef.current.isDown = false
  }

  const handleCardMouseEnter = (e) => {
    e.currentTarget._cachedRect = e.currentTarget.getBoundingClientRect()
  }

  const handleCardSpotlight = (e) => {
    let rect = e.currentTarget._cachedRect
    if (!rect) {
      rect = e.currentTarget.getBoundingClientRect()
      e.currentTarget._cachedRect = rect
    }
    e.currentTarget.style.setProperty('--card-mx', `${(e.clientX - rect.left).toFixed(1)}px`)
    e.currentTarget.style.setProperty('--card-my', `${(e.clientY - rect.top).toFixed(1)}px`)
  }

  return (
    <section className="hl" id={id} ref={section}>
      {title ? <h2 className="hl-title shell reveal">{title}</h2> : null}
      <div
        className="hl-rail"
        ref={rail}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        style={{ cursor: dragRef.current.isDown ? 'grabbing' : 'grab' }}
      >
        {items.map((item, index) => (
          <article
            className="hl-card"
            key={item.lead}
            onMouseEnter={handleCardMouseEnter} onMouseMove={handleCardSpotlight}
          >
            <p className="hl-cap">
              {item.kicker ? <span className="hl-kick">{item.kicker}</span> : null}
              <b>{item.lead}</b> {item.rest}
            </p>
            <div className="hl-shot">
              <img
                src={item.shot.src}
                alt={item.shot.alt}
                loading={index < 3 ? 'eager' : 'lazy'}
                decoding="async"
                draggable={false}
              />
            </div>
          </article>
        ))}
      </div>
      <div className="hl-controls shell">
        <div className="hl-dots" role="tablist" aria-label={title || 'Highlights'}>
          {items.map((item, index) => (
            <button
              key={item.lead}
              type="button"
              className="hl-dot"
              data-on={index === at}
              data-auto={auto && live}
              style={{ '--hl-hold': `${HL_HOLD_MS}ms` }}
              aria-label={item.lead}
              onClick={() => goTo(index)}
            >
              {/* The active dot is a bar that fills while it holds, as ColorOS's does. */}
              {index === at && <span className="hl-dot-fill" key={`${at}-${auto}`} />}
            </button>
          ))}
        </div>
        <div className="hl-arrows">
          <button
            type="button"
            className="hl-arrow"
            aria-label="Previous"
            disabled={at <= 0}
            onClick={() => goTo(at - 1)}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M14.5 6 8.5 12l6 6" /></svg>
          </button>
          <button
            type="button"
            className="hl-arrow"
            aria-label="Next"
            disabled={at >= items.length - 1}
            onClick={() => goTo(at + 1)}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9.5 6 6 6-6 6" /></svg>
          </button>
        </div>
      </div>
    </section>
  )
}
