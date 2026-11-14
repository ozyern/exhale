---
id: 006-artist-chroma-tint
status: pending
iteration: 0
iteration_cap: 5   # subjective/continuous: visual polish/contrast quality has no clean stop condition
worker_model: opus
worker_effort: high
verifier_model: opus
verifier_effort: high
depends_on: []
---

## Goal

Artist page background uses a reference-style color mesh, and everything on
the page needing contrast against it gets an adaptive chroma tint derived
from that same mesh/hero color.

## Acceptance criteria

- Artist page background renders via the mesh-gradient approach (see
  `MeshGradientBackground.kt`, `FLUID_MESH` preference, `the reference app/`
  reference clone for the original look).
- These elements are tinted with a color adaptively derived from the current
  mesh/hero color (via `ColorEngineCore.kt`/the color engine, not a hardcoded
  palette): artist name in list rows, the large artist-page title, the
  divider, all body text, the back button, and every top-bar icon button.
- Tint must stay legibly high-contrast against the mesh background across
  both light and dark themes and across a range of source artist images
  (test with at least 3 visually distinct artists — dark image, bright
  image, monochrome image).
- Scoped to the artist page only for now — do not propagate to
  album/playlist pages in this task.

## Context

Color derivation: `app/src/main/kotlin/com/ozyern/exhale/ui/theme/colorengine/ColorEngineCore.kt`
and the Swift reference under `app/src/main/kotlin/com/ozyern/Color Engine/`.
Artist screen: `ui/screens/artist/ArtistScreen.kt`. the reference app reference for
the mesh look: `the reference app/app/src/main/java/com/music/reference/ui/`.
