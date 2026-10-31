import { Fragment } from 'react'

/**
 * A heading that fills in a word at a time. One span each, staggered through a
 * custom property so it still runs off the page's single reveal observer.
 *
 * The space sits between the spans, not inside them: each span is an inline
 * block, and a trailing space inside one collapses, running the words together.
 * `accent` gilds that many words at the end, in the hero's gold.
 */
export default function Words({ text, className = '', step = 55, accent = 0 }) {
  const words = text.split(' ')
  return (
    <span className={`words reveal ${className}`.trim()}>
      {words.map((word, index) => (
        <Fragment key={`${word}-${index}`}>
          <span
            className={index >= words.length - accent ? 'gold' : undefined}
            style={{ '--d': `${index * step}ms` }}
          >
            {word}
          </span>
          {index < words.length - 1 ? ' ' : null}
        </Fragment>
      ))}
    </span>
  )
}
