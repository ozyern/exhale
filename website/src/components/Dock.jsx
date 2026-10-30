import { useEffect, useRef, useState } from 'react'
import { HomeIcon, LibraryIcon, RadioIcon, SearchIcon } from '../icons.jsx'

const TABS = [
  { label: 'Home', Icon: HomeIcon },
  { label: 'Mood & Genres', short: 'Moods', Icon: RadioIcon },
  { label: 'Library', Icon: LibraryIcon },
]

/**
 * The dock, rebuilt for the browser.
 *
 * Same construction as the app's `LiquidTabBar`: equal-width slots, and a
 * glass capsule that is a separate layer sliding between them rather than a
 * background color switching from one tab to the next.
 *
 * Interactive dragging support: you can click any tab, or grab and drag the
 * capsule directly across the tabs with elastic spring physics.
 */
export default function Dock({
  active,
  onSelect,
  compact = false,
  withSearch = true,
}) {
  const [stretching, setStretching] = useState(false)
  const [dragPos, setDragPos] = useState(null)
  const timer = useRef(0)
  const dockRef = useRef(null)
  const dragStart = useRef(null)

  useEffect(() => () => window.clearTimeout(timer.current), [])

  const select = (index) => {
    if (index !== active) {
      setStretching(true)
      window.clearTimeout(timer.current)
      timer.current = window.setTimeout(() => setStretching(false), 320)
    }
    onSelect(index)
  }

  const handlePointerDown = (e) => {
    const dock = dockRef.current
    if (!dock) return
    const rect = dock.getBoundingClientRect()
    dragStart.current = {
      startX: e.clientX,
      startActive: active,
      slotWidth: (rect.width - 8) / TABS.length,
      moved: false,
    }
    e.currentTarget.setPointerCapture?.(e.pointerId)
  }

  const handlePointerMove = (e) => {
    if (!dragStart.current) return
    const { startX, startActive, slotWidth } = dragStart.current
    const deltaX = e.clientX - startX
    if (Math.abs(deltaX) > 4) {
      dragStart.current.moved = true
      const rawPos = startActive + deltaX / slotWidth
      // Elastic resistance past boundaries
      let clampedPos = rawPos
      if (rawPos < 0) {
        clampedPos = rawPos * 0.35
      } else if (rawPos > TABS.length - 1) {
        clampedPos = (TABS.length - 1) + (rawPos - (TABS.length - 1)) * 0.35
      }
      setDragPos(clampedPos)
    }
  }

  const handlePointerUp = () => {
    if (!dragStart.current) return
    const { moved } = dragStart.current
    if (moved && dragPos !== null) {
      const nearest = Math.max(0, Math.min(TABS.length - 1, Math.round(dragPos)))
      select(nearest)
    }
    dragStart.current = null
    setDragPos(null)
  }

  const isDragging = dragPos !== null
  const currentPos = isDragging ? dragPos : active
  const slot = `calc((100% - 8px) / ${TABS.length})`

  return (
    <div className="dock-row">
      <div
        className="dock glass"
        ref={dockRef}
        role="tablist"
        aria-label="Exhale navigation"
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
        style={{
          cursor: isDragging ? 'grabbing' : 'grab',
          touchAction: 'none',
          userSelect: 'none',
        }}
      >
        <span
          className="dock-capsule"
          aria-hidden="true"
          style={{
            width: slot,
            transform: `translateX(calc(${currentPos} * 100%)) scaleX(${
              isDragging ? 1.08 : stretching ? 1.12 : 1
            }) scaleY(${isDragging ? 0.94 : stretching ? 0.94 : 1})`,
            transition: isDragging
              ? 'none'
              : 'transform 0.48s cubic-bezier(0.18, 1, 0.22, 1), width 0.48s cubic-bezier(0.18, 1, 0.22, 1)',
          }}
        />
        {TABS.map((tab, index) => {
          const on = isDragging ? Math.round(dragPos) === index : index === active
          return (
            <button
              key={tab.label}
              type="button"
              role="tab"
              aria-selected={on}
              data-on={on}
              className="dock-tab"
              onClick={() => {
                if (!dragStart.current?.moved) select(index)
              }}
            >
              <tab.Icon filled={on} />
              <span>{compact ? tab.short ?? tab.label : tab.label}</span>
            </button>
          )
        })}
      </div>

      {withSearch && (
        <button
          type="button"
          className="dock-circle glass"
          aria-label="Search"
          onClick={() => onSelect(active)}
        >
          <SearchIcon />
        </button>
      )}
    </div>
  )
}
