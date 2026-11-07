# ADR-0002: Lay out on a fixed 1280x800 design canvas and scale it

Status: Accepted, with the caveats below (design system commit c245c68, 2026-11-07)

## Context
There is exactly one target display (1280x800 landscape) and a pixel-exact design (21 screens, `docs/design/site`) that is compared with the
app by `tools/cmp.py`. Responsive layout would add measurement work on every frame for a display that never changes.

## Decision
`DesignCanvas` provides a 1280 x 800 coordinate space where 1 dp = 1 design px, scaled by `min(w/1280, h/800)` through an overridden
`LocalDensity` (density = scale, fontScale = 1). Screens place elements with `Modifier.at(x, y)` (absolute offsets) and fixed text sizes.
Centred letterboxing handles other aspect ratios.

## Consequences
+ Pixel fidelity to the design, trivially cheap layout, no density-dependent surprises.
- System font scale and accessibility text size are ignored by design; touch targets scale with the display: on a 1024x600 panel a 56 px
  target is 42 px. If the real unit is not 1280x800 at about 160 dpi, targets must be re-checked (recon M4).
- No safe-area handling: if the vendor keeps a system bar on screen the canvas is covered; wrap `DesignCanvas` in an insets-aware box then.
- About 100 absolute placements and 95 sized text calls are magic numbers by nature. Mitigation in the refactor plan: group per-screen layout
  constants into named values next to each screen, keep the design-token names from the design site, never scatter new literals.
- Compose previews cannot show the scaled canvas without a wrapper: provide one `PreviewCanvas` and add previews for components (refactor plan S15).
