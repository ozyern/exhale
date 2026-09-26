import { useMemo } from 'react'
import { Link } from '../router.jsx'
import { MAINTAINER, RELEASES, REPO, TELEGRAM } from '../content.js'
import { RELEASE_304 as RELEASE } from '../release304.js'
import { DownloadIcon, GithubIcon, Mark } from '../icons.jsx'

export const RELEASE_PATH = `/release/${RELEASE.version}`

/*
 * The parts every page wears.
 *
 * These lived inside App while there was only one page. There are two now, and
 * a footer that is right on one of them and stale on the other is worse than no
 * footer, so they moved here — one definition, both pages.
 */

/**
 * The announcement strip.
 *
 * Full width, one sentence, and the only thing on the page above the nav. A
 * release is news exactly once; this is where news goes, and it is a link
 * rather than a banner you have to dismiss, because nothing here interrupts
 * anything.
 */
export function AnnounceBar() {
  return (
    <Link className="announce" to={RELEASE_PATH}>
      <span className="announce-tag">New</span>
      <span className="announce-text">
        Exhale {RELEASE.version} — Apple Music’s shape across the whole app,
        offline playback, and updates that install themselves
      </span>
      <span className="announce-go" aria-hidden="true">
        ›
      </span>
    </Link>
  )
}

/** The thin global bar. Deliberately quiet; it is about the project, not the page. */
export function TopBar() {
  return (
    <div className="topbar">
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
          <img src="/logo.png" alt="" width="30" height="30" />
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
                <a href="/#material">Material</a>
              </li>
              <li>
                <a href="/#lyrics">Lyrics</a>
              </li>
              <li>
                <a href="/#color">Color</a>
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
                <a href={REPO.replace('/Exhale', '')} target="_blank" rel="noreferrer">
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
