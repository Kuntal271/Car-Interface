# Design system — "Midnight / Cobalt / Lime" (locked 2026-10-30 by owner)

Owner brief: midnight-black background, electric cobalt blue for the lows, acid lime to highlight. Strict three-colour theme. Typography-specific ("food-specific" in the brief; read as FONT-specific — confirm). Stylish, on-point patterns.

## 1. Palette (three hues + neutrals)

| token | hex | role | contrast on bg |
|-------|-----|------|----------------|
| midnight | #05070D | background, text-on-lime | — |
| cobalt | #2457FF | the lows: base/low/cold/idle ranges, inactive structure, tracks, vacuum side of boost, hairlines | 3.7:1 (graphics only) |
| cobalt-lt | #3D6BFF | cobalt when it must carry small icons/strokes | 4.6:1 |
| lime | #C8FF00 | highlight: live value, needle tip, peak, selection/focus, shift light, warnings | 17.0:1 |

Neutrals (do not count as extra colours; tints of the three): surface-1 #0A0F1E, surface-2 #101830 (cobalt-black), text #EAF0FF, dim text #8190B8 (6.3:1), hairline = cobalt at 30% alpha.

Rules
1. Cobalt = low / cold / idle / vacuum / inactive. Lime = high / hot / active / peak / selected / attention. White-ish text carries normal values.
2. No gradients across hues (a cobalt-to-lime blend passes through teal/green and breaks the three-colour rule). Use hard-stop segments: cobalt below a threshold, lime at/above it.
3. Cobalt is never used for body text (3.7:1). It is for fills, arcs, strokes >= 3 dp, large numerals.
4. Warnings stay in-palette: inverted lime block (midnight text on lime, 17:1) + icon + words + slow pulse (1 Hz, never faster). No red. (Decision flagged: owner may allow a single red exception for critical overheat/low-battery.)
5. Day theme: none in v1. Brightness comes from the unit's own dimming.

## 2. Typography (font-specific)

- Display / numerals: Big Shoulders Display (ExtraBold/Black), OFL — condensed, industrial, sporty. Speed readout, RPM numerals, gauge labels.
- UI text: Space Grotesk (Medium/Bold), OFL, `tnum` on for any changing number.
- Mono data (CAN/diagnostic screens): JetBrains Mono, OFL.
- Bundle TTFs in app/src/main/res/font. Download from the google/fonts repo at M2 and verify each font's OFL licence file before committing. Fallback if unavailable: Barlow Condensed + Inter.
- Scale (sp): speed 200 · RPM numeral 28 · tile value 56 · tile label 18 (min) · dock label 18 · body 22. Nothing smaller than 18 sp.

## 3. Pattern language (what makes it feel designed)

1. Chamfered cards: 45-degree notch on one corner (top-right), 12 dp cut — drawn once with a GenericShape path; no shadows or blur anywhere.
2. Segmented bars: every linear gauge is LED-style segments (cobalt unlit/low, lime lit/high) — cheap to draw, very legible.
3. Hairline grid: 1 dp cobalt-30% rules separate regions; corner ticks (L-brackets) mark the active card.
4. Big condensed numerals aligned to a baseline grid; units in dim small caps.
5. Motion = state only: needle smoothing, 120 ms focus moves, 1 Hz warning pulse. No parallax, no blur, no particle effects (weak GPU).
6. 8 dp spacing grid; 24 dp safe margin; 72 dp minimum touch target on the dock, 64 dp elsewhere.
7. RHD ergonomics: India is right-hand drive, the screen sits to the driver's left, so the nearest edge is the RIGHT edge. Dock = vertical rail on the right; primary actions right-biased. Setting to mirror for LHD.

## 3b. Final design pass (supersedes the numbers above where they differ)

Built as a navigable static site: `docs/design/site/` (open `index.html` via `tools/design.sh serve`, see `docs/design/README.md`). 21 screens at 1280x800 plus design-system, components, specs and a clickable prototype.

- Text floor: 18 px for everything a driver must read. One exception: 16 px JetBrains Mono for non-critical provenance captions and ids.
- cobalt-lt (#3D6BFF) is 4.3:1 on cards (4.0:1 on surface-2), so it is for icons and graphics, or text of 24 px and larger. Small text uses text (#EAF0FF) or dim (#8190B8, 6.0:1 on cards).
- Chamfer is 14 px on cards, 12 px on buttons. Dock is 112 px wide, 8 slots of about 93 px, on the right edge.
- Warning = filled lime block with midnight text, pulsing at 1 Hz. Lime is therefore not used for "normal on" states on the Car screen (those are white text); it marks deviations, selection and live highs.
- Gear, fuel use, range and consumption are estimates and are labelled EST. Fields the car may never send are hidden, never shown as zero.
- Tach lime zone starts at 4,500 rpm, redline 6,000, scale 7,000, shift light from 5,200 (all editable in Settings > Vehicle).
- Owner confirmed the font reading of the brief ("it's right"), though the wording was ambiguous. Bundled fonts are OFL: Big Shoulders Display, Space Grotesk, JetBrains Mono.

## 4. Screen sketches (1280x800)

Drive: tach arc 270 degrees, cobalt track, lime progress after the threshold (default 4500 rpm), shift-light segments across the top; centre speed numerals (lime); right column: boost bar (vacuum = cobalt segments below zero, positive boost = lime segments), coolant, intake, battery, fuel as segmented tiles; bottom strip: trip km / avg / max / max boost / 0-100 timer.
Home: left 60% context card (Now Playing or last app), right 40% three live vitals; right-edge dock rail: Maps, Media, Phone, Drive, Apps, Settings.
Car (new, from Canbox): top-down car with door/trunk state (lime = open), A/C state and set temps, parking-radar arcs, outside temperature, steering-key echo for debugging.
