/*
 * Inline SVG only — no icon font, no sprite request. Every glyph on the page
 * is one of these, drawn on a 24 grid with a 1.7 stroke so the dock's icons
 * sit at the same optical weight as the app's.
 */

const s = {
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.7,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
}

const Svg = ({ children, ...rest }) => (
  <svg viewBox="0 0 24 24" aria-hidden="true" {...rest}>
    {children}
  </svg>
)

export const HomeIcon = ({ filled }) => (
  <Svg>
    <path
      {...s}
      fill={filled ? 'currentColor' : 'none'}
      d="M3.6 10.4 12 3.8l8.4 6.6V19a1.4 1.4 0 0 1-1.4 1.4H5a1.4 1.4 0 0 1-1.4-1.4z"
    />
  </Svg>
)

export const RadioIcon = ({ filled }) => (
  <Svg>
    <circle cx="12" cy="12" r={filled ? 3.2 : 2.4} {...s} fill={filled ? 'currentColor' : 'none'} />
    <path {...s} d="M7.4 7.4a6.5 6.5 0 0 0 0 9.2M16.6 7.4a6.5 6.5 0 0 1 0 9.2" />
    <path {...s} opacity=".55" d="M4.6 4.6a10.4 10.4 0 0 0 0 14.8M19.4 4.6a10.4 10.4 0 0 1 0 14.8" />
  </Svg>
)

export const LibraryIcon = ({ filled }) => (
  <Svg>
    <rect x="3.4" y="4.4" width="4" height="15.2" rx="1.4" {...s} fill={filled ? 'currentColor' : 'none'} />
    <rect x="9.6" y="4.4" width="4" height="15.2" rx="1.4" {...s} fill={filled ? 'currentColor' : 'none'} />
    <path {...s} fill={filled ? 'currentColor' : 'none'} d="m16.2 6.1 3.1-.8a1.4 1.4 0 0 1 1.7 1l2 11a1.4 1.4 0 0 1-1 1.6l-1.5.4z" />
  </Svg>
)

export const SearchIcon = () => (
  <Svg>
    <circle cx="11" cy="11" r="6.4" {...s} />
    <path {...s} d="m16 16 4 4" />
  </Svg>
)

export const PlayIcon = () => (
  <Svg>
    <path fill="currentColor" d="M8 5.6a.9.9 0 0 1 1.4-.75l8 6.4a.9.9 0 0 1 0 1.5l-8 6.4A.9.9 0 0 1 8 18.4z" />
  </Svg>
)

export const PauseIcon = () => (
  <Svg>
    <rect x="7" y="5" width="3.6" height="14" rx="1.4" fill="currentColor" />
    <rect x="13.4" y="5" width="3.6" height="14" rx="1.4" fill="currentColor" />
  </Svg>
)

export const PrevIcon = () => (
  <Svg width="22" height="22">
    <path fill="currentColor" d="M16.6 5.6a.9.9 0 0 0-1.4-.75l-7.4 5.9V6a.9.9 0 0 0-1.8 0v12a.9.9 0 0 0 1.8 0v-4.75l7.4 5.9a.9.9 0 0 0 1.4-.75z" />
  </Svg>
)

export const NextIcon = () => (
  <Svg width="22" height="22">
    <path fill="currentColor" d="M7.4 5.6a.9.9 0 0 1 1.4-.75l7.4 5.9V6a.9.9 0 0 1 1.8 0v12a.9.9 0 0 1-1.8 0v-4.75l-7.4 5.9a.9.9 0 0 1-1.4-.75z" />
  </Svg>
)

export const DownloadIcon = () => (
  <Svg width="17" height="17">
    <path {...s} d="M12 3.8v11m0 0 4-4m-4 4-4-4M4.6 17.6v1.2a1.4 1.4 0 0 0 1.4 1.4h12a1.4 1.4 0 0 0 1.4-1.4v-1.2" />
  </Svg>
)

export const GithubIcon = () => (
  <Svg width="17" height="17">
    <path
      fill="currentColor"
      d="M12 2.2a9.8 9.8 0 0 0-3.1 19.1c.5.1.7-.2.7-.5v-1.7c-2.7.6-3.3-1.3-3.3-1.3-.5-1.1-1.1-1.4-1.1-1.4-.9-.6.1-.6.1-.6 1 .1 1.5 1 1.5 1 .9 1.5 2.3 1.1 2.9.8.1-.6.3-1.1.6-1.3-2.2-.3-4.5-1.1-4.5-4.9 0-1.1.4-2 1-2.7-.1-.3-.4-1.3.1-2.7 0 0 .8-.3 2.7 1a9.4 9.4 0 0 1 5 0c1.9-1.3 2.7-1 2.7-1 .5 1.4.2 2.4.1 2.7.6.7 1 1.6 1 2.7 0 3.8-2.3 4.6-4.5 4.9.3.3.7 1 .7 1.9v2.8c0 .3.2.6.7.5A9.8 9.8 0 0 0 12 2.2"
    />
  </Svg>
)

export const TelegramIcon = () => (
  <Svg width="17" height="17">
    <path
      fill="currentColor"
      d="M20.7 4.1 2.9 11c-1.2.5-1.2 1.2-.2 1.5l4.6 1.4 1.7 5.4c.2.6.1.8.7.8.5 0 .7-.2 1-.5l2.2-2.2 4.6 3.4c.9.5 1.5.2 1.7-.8l3-14.3c.3-1.3-.5-1.8-1.5-1.6Zm-2.9 3.6-8.5 7.7-.3 3.5-1.6-5 9.8-6.2c.5-.3.9-.1.6 0Z"
    />
  </Svg>
)

export const ChevronIcon = () => (
  <Svg width="16" height="16" className="chev">
    <path {...s} d="m10 6 6 6-6 6" />
  </Svg>
)

export const CloseIcon = () => (
  <Svg width="17" height="17">
    <path {...s} d="m7 7 10 10M17 7 7 17" />
  </Svg>
)

export const GlassIcon = () => (
  <Svg width="20" height="20">
    <rect x="3" y="6.5" width="18" height="11" rx="5.5" {...s} />
    <path {...s} opacity=".6" d="M6.5 9.5h4" />
  </Svg>
)

export const PaletteIcon = () => (
  <Svg width="20" height="20">
    <path {...s} d="M12 3.6a8.4 8.4 0 1 0 0 16.8c1 0 1.6-.7 1.6-1.5 0-.5-.2-.8-.5-1.1-.3-.3-.4-.6-.4-1 0-.8.6-1.4 1.5-1.4h1.5a4.7 4.7 0 0 0 4.7-4.7c0-4-3.8-7.1-8.4-7.1" />
    <circle cx="8" cy="10" r="1.2" fill="currentColor" />
    <circle cx="12" cy="7.6" r="1.2" fill="currentColor" />
    <circle cx="16" cy="10" r="1.2" fill="currentColor" />
  </Svg>
)

export const LyricsIcon = () => (
  <Svg width="20" height="20">
    <path {...s} d="M5 6.5h14M5 11h9M5 15.5h11" />
  </Svg>
)

export const WaveIcon = () => (
  <Svg width="20" height="20">
    <path {...s} d="M4 12v0M8 8.5v7M12 5.5v13M16 8.5v7M20 12v0" />
  </Svg>
)

export const LinkIcon = () => (
  <Svg width="20" height="20">
    <path {...s} d="M10.5 13.5a3.5 3.5 0 0 0 5 0l3-3a3.5 3.5 0 0 0-5-5l-1.2 1.2" />
    <path {...s} d="M13.5 10.5a3.5 3.5 0 0 0-5 0l-3 3a3.5 3.5 0 0 0 5 5l1.2-1.2" />
  </Svg>
)

export const CloudIcon = () => (
  <Svg width="20" height="20">
    <path {...s} d="M7.2 18.4a4 4 0 0 1-.3-8 5.2 5.2 0 0 1 10 1.3 3.4 3.4 0 0 1-.6 6.7z" />
  </Svg>
)

export const ArrowLeftIcon = () => (
  <Svg width="18" height="18">
    <path {...s} d="m14 5-7 7 7 7" />
  </Svg>
)

export const ArrowRightIcon = () => (
  <Svg width="18" height="18">
    <path {...s} d="m10 5 7 7-7 7" />
  </Svg>
)

export const ReplayIcon = () => (
  <Svg width="18" height="18">
    <path {...s} d="M4.6 10.2a7.5 7.5 0 1 1 1.2 5.4" />
    <path {...s} d="M3.4 5.6v4.6h4.6" />
  </Svg>
)

export const MoonIcon = () => (
  <Svg width="20" height="20">
    <path {...s} d="M20 13.4A8.4 8.4 0 1 1 10.6 4a6.9 6.9 0 0 0 9.4 9.4" />
  </Svg>
)

/**
 * The app's own icon, not a redrawing of it.
 *
 * This was an inline SVG approximation while the real asset was 1.9 MB of
 * 1254px PNG — far too heavy to put in a navigation bar. It is now served at
 * 96px (14 KB) from `assets/icon.png`, which is the actual launcher icon the
 * app ships, so the site and the phone agree about what Exhale looks like.
 */
export const Mark = () => (
  <img className="mark" src="/logo.png" alt="" width="25" height="25" />
)
