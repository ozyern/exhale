/*
 * The support hero, assembled from borrowed parts, all MIT:
 *
 *   · the badge is Magic UI's Animated Shiny Text,
 *   · the one colored word is Magic UI's Aurora Text, in the site's own accents,
 *   · the sky behind is Magic UI's Flickering Grid, faded out toward the edges,
 *   · and the search is Origin UI's input (see help-search.jsx).
 *
 * The job of the whole thing is the search box. Everything else is there to
 * make it the obvious first move, with the four ways in right under it for
 * anyone who already knows they need to file something.
 */
import { Link } from '../../router.jsx'
import { AnimatedShinyText } from './animated-shiny-text.jsx'
import { AuroraText } from './aurora-text.jsx'
import { FlickeringGrid } from './flickering-grid.jsx'
import { HelpSearch } from './help-search.jsx'

const AURORA = ['var(--rose)', 'var(--ember)', 'var(--iris)', 'var(--cyan)']

/** Opens an answer in place, the same way arriving at its #fragment does. */
function openAnswer(id) {
  const node = document.getElementById(id)
  if (!node) return
  if (node.tagName === 'DETAILS') node.open = true
  window.history.replaceState(null, '', `#${id}`)
  node.scrollIntoView({ behavior: 'smooth' })
}

export function SupportHero({ version, releasePath, facts, faq, paths, onPick }) {
  return (
    <section className="relative isolate overflow-hidden" style={{ marginTop: 'calc(var(--bar-h) * -1)' }}>
      <div aria-hidden="true" className="-z-10 pointer-events-none absolute inset-0">
        <div className="absolute inset-0 bg-[radial-gradient(120%_85%_at_50%_0%,#111b3d_0%,#0a1026_38%,#05070f_62%,#000_82%)]" />
        <FlickeringGrid
          // Kept to the edges of the frame: dots running through the copy read as noise.
          className="absolute inset-0 [mask-image:radial-gradient(46%_58%_at_50%_46%,transparent_35%,#000_100%)] max-sm:opacity-70 max-sm:[mask-image:radial-gradient(95%_52%_at_50%_48%,transparent_55%,#000_100%)]"
          squareSize={3}
          gridGap={7}
          flickerChance={0.25}
          maxOpacity={0.34}
          color="rgb(196, 208, 255)"
        />
        <div className="absolute top-[26%] left-1/2 h-[360px] w-[min(760px,92vw)] -translate-x-1/2 rounded-full bg-[radial-gradient(closest-side,color-mix(in_oklab,var(--rose)_38%,transparent),transparent)] opacity-70 blur-3xl" />
        <div className="absolute inset-x-0 bottom-0 h-48 bg-linear-to-b from-transparent to-black" />
      </div>

      <div
        className="shell relative flex flex-col items-center text-center"
        style={{
          paddingTop: 'calc(var(--bar-h) + clamp(64px, 11vw, 136px))',
          paddingBottom: 'clamp(64px, 9vw, 112px)',
        }}
      >
        <Link
          to={releasePath}
          className="group inline-flex items-center rounded-full border border-white/10 bg-white/[0.04] px-4 py-1.5 text-[13px] backdrop-blur-md transition-colors hover:border-white/20 hover:bg-white/[0.08]"
        >
          <AnimatedShinyText className="inline-flex items-center gap-2">
            <span aria-hidden="true" className="size-1.5 rounded-full bg-primary shadow-[0_0_10px_var(--rose)]" />
            New in {version}
            <svg
              aria-hidden="true"
              className="transition-transform duration-300 group-hover:translate-x-0.5"
              width="12"
              height="12"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M5 12h14M13 6l6 6-6 6" />
            </svg>
          </AnimatedShinyText>
        </Link>

        <h1 className="mt-7 max-w-[20ch] text-balance font-semibold text-[clamp(44px,7.4vw,92px)] text-foreground leading-[1.02] tracking-[-0.035em]">
          <span className="sr-only">Exhale Support — </span>What can I <AuroraText colors={AURORA}>help</AuroraText> with?
        </h1>

        <p className="mt-6 max-w-[42ch] text-[clamp(17px,1.8vw,21px)] text-muted-foreground leading-[1.45]">
          Search the answers first. If it is not in there, pick what went wrong and GitHub opens with
          the form already filled in.
        </p>

        <HelpSearch
          faq={faq}
          paths={paths}
          onAnswer={openAnswer}
          onPath={onPick}
          className="mt-10 w-full max-w-[600px] text-left"
        />

        <div className="mt-5 flex max-w-[680px] flex-wrap justify-center gap-2">
          {paths.map((path) => (
            <button
              key={path.id}
              type="button"
              onClick={() => onPick(path.id)}
              className="cursor-pointer rounded-full border border-white/10 bg-white/[0.03] px-3.5 py-1.5 font-sans text-[13.5px] text-muted-foreground transition-colors hover:border-white/25 hover:text-foreground"
            >
              {path.title}
            </button>
          ))}
        </div>

        <dl className="mt-12 mb-0 flex flex-wrap items-center justify-center gap-x-8 gap-y-3 text-[13px]">
          {facts.map((fact) => (
            <div key={fact.label} className="flex items-baseline gap-2">
              <dt className="text-muted-foreground/80">{fact.label}</dt>
              <dd className="m-0 font-medium text-foreground">{fact.value}</dd>
            </div>
          ))}
        </dl>
      </div>
    </section>
  )
}
