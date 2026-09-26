/*
 * Every release that has its own page, newest first.
 *
 * One list, so "latest" is decided in one place. The announce bar, the footer,
 * the support form's version box and the short `/release` URL all read it from
 * here — a new release is one import and one line, not a hunt through four
 * files for whichever of them still names the last one.
 */

import { RELEASE } from './release.js'
import { RELEASE_304 } from './release304.js'

export const ALL_RELEASES = [RELEASE_304, RELEASE]

export const LATEST_RELEASE = ALL_RELEASES[0]

export const releasePath = (release) => `/release/${release.version}`

/** The GitHub release a version was tagged as. See RELEASING.md. */
export const tagUrl = (repo, release) => `${repo}/releases/tag/v${release.version}`

/** An unknown version lands on the newest one: a typo should not be a 404. */
export const releaseByVersion = (version) =>
  ALL_RELEASES.find((release) => release.version === version) ?? LATEST_RELEASE
