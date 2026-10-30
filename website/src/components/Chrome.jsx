import { useMemo, useState } from 'react'
import { useScrolled } from '../hooks.js'
import { Link } from '../router.jsx'
import { MAINTAINER, RELEASES, REPO, TELEGRAM, TELEGRAM_CHANNEL } from '../content.js'
import { LATEST_RELEASE as RELEASE } from '../releases.js'
import { DownloadIcon, GithubIcon, Mark, TelegramIcon } from '../icons.jsx'

export const RELEASE_PATH = `/release/${RELEASE.version}`

/*
 * The parts every page wears.
 *
 * These lived inside App while there was only one page. There are two now, and
 * a footer that is right on one of them and stale on the other is worse than no
 * footer, so they moved here — one definition, both pages.
 */

/**
 * The announcement pill / popup.
 *
 * Floating frosted liquid glass capsule with shimmering New badge,
 * release highlights, and optional dismissal.
 */
export function AnnounceBar() {
  const [dismissed, setDismissed] = useState(false)

  if (dismissed) return null

  return (
    <aside className="announce-wrap" aria-label="Announcement">
      <Link className="announce" to={RELEASE_PATH}>
        <span className="announce-glow" aria-hidden="true" />
        <span className="announce-tag">
          <svg className="announce-spark" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
            <path d="M8 1l1.8 4.2L14 7l-4.2 1.8L8 13l-1.8-4.2L2 7l4.2-1.8L8 1z" />
          </svg>
          New
        </span>
        <span className="announce-text">
          <strong className="announce-ver">Exhale {RELEASE.version}</strong>
          <span className="announce-sep" aria-hidden="true">—</span>
          <span className="announce-body">Live glass, word-synced lyrics & now on Windows Desktop</span>
        </span>
        <span className="announce-go" aria-hidden="true">
          <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M6 3.5l4.5 4.5-4.5 4.5" />
          </svg>
        </span>
      </Link>
      <button
        type="button"
        className="announce-close"
        onClick={(e) => {
          e.preventDefault()
          e.stopPropagation()
          setDismissed(true)
        }}
        aria-label="Dismiss announcement"
        title="Dismiss announcement"
      >
        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M4 4l8 8M12 4l-8 8" />
        </svg>
      </button>
    </aside>
  )
}

/** The thin global bar. Deliberately quiet; it is about the project, not the page. */
export function TopBar() {
  const scrolled = useScrolled(20)
  return (
    <div className={`topbar ${scrolled ? 'topbar-scrolled' : ''}`}>
      <div className="topbar-glow" aria-hidden="true" />
      <nav className="topbar-inner" aria-label="Project">
        <Link className="wordmark" to="/" aria-label="Exhale, home">
          <Mark />
        </Link>
        <a href={RELEASES} target="_blank" rel="noreferrer">
          Download
        </a>
        <Link className="hide-sm" to={RELEASE_PATH}>
          {RELEASE.version}
        </Link>
        <a className="hide-sm" href={`${REPO}/releases`} target="_blank" rel="noreferrer">
          Releases
        </a>
        <a className="hide-sm" href={REPO} target="_blank" rel="noreferrer">
          Source
        </a>
        <Link className="hide-sm" to="/support">
          Support
        </Link>
        <a
          className="hide-sm"
          href={`${REPO}/blob/master/CONTRIBUTING.md`}
          target="_blank"
          rel="noreferrer"
        >
          Contributing
        </a>
        <a href={`${REPO}/blob/master/LICENSE`} target="_blank" rel="noreferrer">
          GPL-3.0
        </a>
        <a className="topicon" href={REPO} target="_blank" rel="noreferrer" aria-label="Source on GitHub">
          <GithubIcon />
        </a>
        <a
          className="topicon"
          href={TELEGRAM_CHANNEL}
          target="_blank"
          rel="noreferrer"
          aria-label="Exhale on Telegram"
        >
          <TelegramIcon />
        </a>
        <a
          className="topicon"
          href={RELEASES}
          target="_blank"
          rel="noreferrer"
          aria-label="Download the APK"
        >
          <DownloadIcon />
        </a>
      </nav>
    </div>
  )
}

/**
 * @param onNotes opens the short release-notes sheet. Only the home page has
 *   one mounted, so the two entries that open it are omitted without it rather
 *   than rendered dead.
 */
export function SiteFooter({ onNotes }) {
  const year = useMemo(() => new Date().getFullYear(), [])

  return (
    <footer className="footer">
      <div className="shell">
        <div className="footer-mark">
          <img src="/logo.png" alt="Exhale" width="30" height="30" />
          Exhale
        </div>

        <div className="footer-grid">
          <div>
            <h4>Exhale</h4>
            <ul>
              <li>
                <Link to="/">Overview</Link>
              </li>
              <li>
                <a href="/#lyrics">Lyrics</a>
              </li>
              <li>
                <a href="/#design">Design</a>
              </li>
              <li>
                <a href="/#features">Features</a>
              </li>
              <li>
                <a href="/#download">Download</a>
              </li>
            </ul>
          </div>
          <div>
            <h4>This release</h4>
            <ul>
              <li>
                <Link to={RELEASE_PATH}>Exhale {RELEASE.version}</Link>
              </li>
              {onNotes ? (
                <li>
                  <button type="button" onClick={onNotes}>
                    What&rsquo;s new, in short
                  </button>
                </li>
              ) : null}
              <li>
                <a href={`${REPO}/blob/master/CHANGELOG.md`} target="_blank" rel="noreferrer">
                  Full changelog
                </a>
              </li>
              <li>
                <a href={RELEASES} target="_blank" rel="noreferrer">
                  Download the APK
                </a>
              </li>
            </ul>
          </div>
          <div>
            <h4>Source</h4>
            <ul>
              <li>
                <a href={REPO} target="_blank" rel="noreferrer">
                  Repository
                </a>
              </li>
              <li>
                <a href={`${REPO}/releases`} target="_blank" rel="noreferrer">
                  Releases
                </a>
              </li>
              <li>
                <Link to="/support">Support</Link>
              </li>
              <li>
                <a href={TELEGRAM_CHANNEL} target="_blank" rel="noreferrer">
                  Telegram channel
                </a>
              </li>
              <li>
                <a href={`${REPO}/issues`} target="_blank" rel="noreferrer">
                  Open issues
                </a>
              </li>
              <li>
                <a
                  href={`${REPO}/blob/master/CONTRIBUTING.md`}
                  target="_blank"
                  rel="noreferrer"
                >
                  Contributing
                </a>
              </li>
            </ul>
          </div>
          <div>
            <h4>Built on</h4>
            <ul>
              <li>
                <a href="https://github.com/Arturo254/OpenTune" target="_blank" rel="noreferrer">
                  OpenTune
                </a>
              </li>
              <li>
                <a href="https://lrclib.net" target="_blank" rel="noreferrer">
                  LRCLIB
                </a>
              </li>
              <li>
                <a
                  href="https://github.com/Kyant0/AndroidLiquidGlass"
                  target="_blank"
                  rel="noreferrer"
                >
                  AndroidLiquidGlass
                </a>
              </li>
              <li>
                <a href="https://github.com/chrisbanes/haze" target="_blank" rel="noreferrer">
                  Haze
                </a>
              </li>
            </ul>
          </div>
          <div>
            <h4>Maintainer</h4>
            <ul>
              <li>
                {MAINTAINER.name} — {MAINTAINER.role}
              </li>
              <li>
                <a href={REPO.replace(/\/exhale$/i, '')} target="_blank" rel="noreferrer">
                  GitHub {MAINTAINER.handle}
                </a>
              </li>
              <li>
                <a href={TELEGRAM} target="_blank" rel="noreferrer">
                  Telegram {MAINTAINER.handle}
                </a>
              </li>
              <li>
                <a href={`${REPO}/blob/master/CODE_OF_CONDUCT.md`} target="_blank" rel="noreferrer">
                  Code of conduct
                </a>
              </li>
            </ul>
          </div>
        </div>

        <div className="footer-legal">
          <p>
            © {year} ozyern. Exhale is free software under the GNU GPL v3, built on
            the work of OpenTune.
          </p>
          <p>
            Not affiliated with, endorsed by, or connected to Apple, Google or
            YouTube. Album titles, artists and lyrics shown on this site are
            placeholders written for the demo.
          </p>
        </div>
      </div>
    </footer>
  )
}
