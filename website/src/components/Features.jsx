/*
 * Everything that is real but is not a headline, as one quiet grid.
 *
 * It replaces a self-running rail of eight large cards. The rail made every
 * feature a slide you had to wait for, and at any moment it showed one and a
 * half of them; a grid shows all twelve at once, which is the honest size for
 * things that each take one line to say.
 *
 * The glyphs are drawn on the same 24 grid at the same 1.7 stroke as
 * `icons.jsx`, so they sit at the dock's optical weight.
 */

const s = {
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.7,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
}

const GLYPHS = {
  update: (
    <>
      <path {...s} d="M20 12a8 8 0 1 1-2.34-5.66" />
      <path {...s} d="M20 4.5v4h-4" />
      <path {...s} d="M12 8.2v5.2M9.6 11l2.4 2.4 2.4-2.4" />
    </>
  ),
  lossless: (
    <>
      <path {...s} d="M4 12h1.5M7.5 8v8M11 5v14M14.5 8.5v7M18 10.5v3M20 12h0" />
    </>
  ),
  save: (
    <>
      <path {...s} d="M12 4v10.5M7.8 10.6 12 14.8l4.2-4.2" />
      <path {...s} d="M4.5 16.5v1.6A1.9 1.9 0 0 0 6.4 20h11.2a1.9 1.9 0 0 0 1.9-1.9v-1.6" />
    </>
  ),
  spatial: (
    <>
      <circle {...s} cx="12" cy="12" r="2.2" />
      <path {...s} d="M7.8 7.8a6 6 0 0 0 0 8.4M16.2 7.8a6 6 0 0 1 0 8.4" />
      <path {...s} d="M5 5a10 10 0 0 0 0 14M19 5a10 10 0 0 1 0 14" />
    </>
  ),
  eq: (
    <>
      <path {...s} d="M6 4v16M12 4v16M18 4v16" />
      <circle {...s} cx="6" cy="14" r="2" fill="#000" />
      <circle {...s} cx="12" cy="8" r="2" fill="#000" />
      <circle {...s} cx="18" cy="15.5" r="2" fill="#000" />
    </>
  ),
  moon: <path {...s} d="M19.5 14.6A8 8 0 0 1 9.4 4.5a8 8 0 1 0 10.1 10.1z" />,
  together: (
    <>
      <circle {...s} cx="9" cy="8.5" r="3" />
      <circle {...s} cx="16.5" cy="9.5" r="2.4" />
      <path {...s} d="M3.8 19a5.2 5.2 0 0 1 10.4 0M14.6 14.4a4.2 4.2 0 0 1 5.6 4.1" />
    </>
  ),
  car: (
    <>
      <path {...s} d="M4.5 16.5v-4l1.9-5a1.6 1.6 0 0 1 1.5-1h8.2a1.6 1.6 0 0 1 1.5 1l1.9 5v4z" />
      <path {...s} d="M4.5 12.5h15M6.5 16.5v2M17.5 16.5v2" />
      <circle cx="8" cy="14.5" r="0.9" fill="currentColor" />
      <circle cx="16" cy="14.5" r="0.9" fill="currentColor" />
    </>
  ),
  scrobble: (
    <>
      <path {...s} d="M9 17.5V6.2l10-2v11" />
      <circle {...s} cx="6.6" cy="17.5" r="2.4" />
      <circle {...s} cx="16.6" cy="15.2" r="2.4" />
    </>
  ),
  discord: (
    <>
      <path {...s} d="M5 6.5a14 14 0 0 1 4-1.3l.6 1.3h4.8l.6-1.3a14 14 0 0 1 4 1.3c1.7 2.7 2.5 5.6 2.2 9.4a13 13 0 0 1-4.2 2.1l-1-1.6M5 6.5c-1.7 2.7-2.5 5.6-2.2 9.4A13 13 0 0 0 7 18l1-1.6" />
      <path {...s} d="M7.6 15.6c2.9 1.3 5.9 1.3 8.8 0" />
      <circle cx="9.2" cy="11.8" r="1.2" fill="currentColor" />
      <circle cx="14.8" cy="11.8" r="1.2" fill="currentColor" />
    </>
  ),
  language: (
    <>
      <circle {...s} cx="12" cy="12" r="8.2" />
      <path {...s} d="M3.8 12h16.4M12 3.8c2.3 2.3 3.4 5 3.4 8.2s-1.1 5.9-3.4 8.2c-2.3-2.3-3.4-5-3.4-8.2s1.1-5.9 3.4-8.2z" />
    </>
  ),
  backup: (
    <>
      <path {...s} d="M7 18.5h10.2a3.8 3.8 0 0 0 .6-7.6 5.8 5.8 0 0 0-11.2-1.2A4.5 4.5 0 0 0 7 18.5z" />
      <path {...s} d="M12 16v-5.4M9.7 12.6 12 10.3l2.3 2.3" />
    </>
  ),
}

export default function Features({ items }) {
  return (
    <ul className="fx-grid">
      {items.map((item, i) => (
        <li className="fx reveal" key={item.title} style={{ '--d': `${(i % 4) * 60}ms` }}>
          <span className="fx-glyph">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              {GLYPHS[item.glyph]}
            </svg>
          </span>
          <b className="fx-title">{item.title}</b>
          <span className="fx-body">{item.body}</span>
        </li>
      ))}
    </ul>
  )
}
