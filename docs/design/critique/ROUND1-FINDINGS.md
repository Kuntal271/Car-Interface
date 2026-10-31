# Round 1 findings, per screen (spec v2, Ford theme)

Spec: docs/design/critique/REVIEW-PROMPT.md. Screens live in docs/design/site/screens/<name>.html.
Already fixed centrally (do NOT redo, do not edit assets/*): no monospace in the Ford theme, tab/pill gaps 12 px, sliders and sparklines in ice-cyan, selected dock item solid blue, `.num.lime / .v.lime / .val.lime / .big.lime` render white in the Ford theme, lit bar segments cyan, tach caption position, `.warn` radius 24, new icons: `image` (gallery), `fog` (fog lights), `storage`, cleaner `usb`.
Rule for any other cyan value that is a measured number: make it white. Theme-specific overrides go in the screen's own <style> prefixed with `html[data-theme="ford"]` so the Cockpit theme stays unchanged.
Level values show their range: volume `18` + dim `/ 30` (use `<span class="unit"> / 30</span>`), brightness `70` + `%` (`<span class="unit t">%</span>`).

## boot
- Loading bar must be FULL (100%) in the end state (static `noanim` render and after the animation), ~6 px tall with a lighter track; add a dim caption under it: "Starting vehicle services".
- "Welcome" colour white; subtitle "EcoSport · 1.0 EcoBoost" lighter (about 80% white).
- The prototype footer ("v0.1 · design preview", "tap to replay") must be hidden when the URL has raw=1 or embed=1 (it stays in normal viewing).

## home
- Left column bottom edge must equal right column bottom edge: make the Recent block 140 px high and `.rc` 110 px high.
- Fuel bar: data-n 12 -> 20 so 62% reads correctly.
- Radio tile: add a dim unit "MHz" after 93.5.
- "Queue ›" must be ice-cyan (it is a link): class `lime` instead of `white`.

## quick
- Brightness "70" -> "70%". Volume "18" white with dim "/ 30".
- Inactive tile icons dim (not cyan). Rename pill "Power" -> "Power menu". Scrim alpha .72 -> .8.

## standby
- Clock digits white; the colon must share the digits' vertical centre (currently about 36 px too low); colon colour: white too.
- Hint "Tap to wake · hold to power off": brighter (text colour at 70%), right edge aligned with the status strip right edge; date closer to the clock than to the logo (move it down ~30 px or the logo up).
- Label "Battery · off" -> "12 V battery".

## drive
- Replace the Battery tile (duplicates status-bar voltage) with Throttle: icon `gauge`, value `s.throttle` (percent, `unit t` %), same bar style, live-updated in the script.
- "Peak +1.04" -> "Peak +1.04 bar".
- Trip strip: exactly 12 px below the tile grid; boost card/tiles gaps 12 px.
- Shift-light bar (top): shorten it and add a right-aligned printed label "Shift 5,200 rpm" (dim, Barlow, 18 px+) on the same row.

## drive-boost
- History chart: left y-axis labels with unit (bar) and right y-axis with unit (rpm), x ticks in seconds, label the dashed baseline "0 bar". Series: boost = ice-cyan solid, rpm = white dashed; legend swatches match; no blue lines.
- Gauge: label the triangle marker "Peak" with its value; the cyan fill must end exactly at the current value (+0.95) on the printed scale.
- Remove the "ambient 31 °C" sub-line from the Intake air card (duplicates the status bar); keep "baro 100 kPa" on the manifold card.
- Card titles "Last 25 seconds" and "0–100 km/h" white (not dim).
- 0–100 card: "0.0 s" numeral white and at most 64 px so the boost value stays the primary; add "s" to Best and every run time; "Best 12.3 s" as a cyan callout; fill the empty space under the runs with a Run 4 row or a Reset pill.
- History card and timer card bottoms aligned, 24 px above the bottom edge.

## drive-cruise
- Shift-up cue: not amber (advisory). Use a ice-cyan outlined pill: "Shift up → gear 4".
- Coolant card: add end labels under the bar (C and H) and remove the big empty gap in the card.
- Range card scale "E 62 % F": "62%" white, E and F dim.
- Speed bar scale: right-align the "200" label to the bar end (no overhang).
- Top-right info pill: make it plain non-interactive dim text (no outline); remove the duplicated "· est." from the unit lines (the notice says it once).
- All four side cards: same header style (icon + label) and the same value size (72 px).

## overlays
- Banners (door, battery, shift) inset 24 px from the screen edge: left 24, width 1120; door banner top 68 (12 px under the status bar); battery banner bottom 24. Radius 24 comes from the theme.
- Shift banner text in sentence case: "Shift up · gear 4" (no all-caps literal).
- Volume OSD: value "18" white + dim "/ 30"; label "Volume".
- Incoming call is a modal: add the scrim (.dimmer) and align the card to the right column edges (x 652–1143).

## car
- One primary element: keep the climate temperature large, shrink the odometer numeral and the Outside "31" to `n-m`.
- Print the rear sensor distance (e.g. "0.6 m") next to the active rear sensor arc.
- Airflow buttons gap 12 px. Steering "-4° left": "4°" white, "left" dim, value white.
- Fog lights row icon: `fog`. Merge the two data-source notes into one (hanging indent, or no leading glyph).
- "Driver" label: horizontal, higher contrast, placed by the driver seat.

## trips
- "Reset" links ice-cyan. Selected-trip Distance "42.8" white; keep "Max boost +1.04 bar" as the cyan callout.
- Chart: y-axis labels with units (km/h left, bar right) and gridline values; x labels at their true time positions with ticks; end the boost line inside the plot padding. Series: speed = ice-cyan with area, boost = white dashed; legend matches.
- Hierarchy: the selected trip Distance is the primary; shrink Trip A and Trip B numerals; sub-lines fit on one line.

## diagnostics
- PID table values, Load tile "88" and "14 Hz" white. Command-guard pills: gap 12, text dim (not cyan). "Connected" pill: ice-cyan outline live-indicator style, not a blue fill.
- "500k" -> "500 kbit/s". Faults tile "—" -> "Not read" with a dim "Mode 03 blocked".

## parking
- Remove the developer copy "live feed is a vendor surface · overlay is ours" (use "Rear camera · live").
- "0.6 m" white. Proximity under 0.8 m: amber pill "Close" with dark text and the active sensor arc amber.
- Beep volume and Brightness sliders get printed values ("70%").
- Car icon in the sensor card must not be clipped at the top. Distance labels 2.0 / 1.0 / 0.5 m: white numerals with dim "m", one colour.

## media
- Volume "18" white with dim "/ 30".

## radio
- FM band chip selected: blue fill, white text. Selected preset card (P3): blue fill, white frequency and text.
- Preset frequencies and the +-0.1 step buttons carry "MHz" (dim unit). Signal: add a measured value next to "Strong" (e.g. "48 dBµV"). Dial: add the missing preset marker at 89.4. Preset grid bottom margin 24 px. RDS "NOW" -> "Now".
- "Save preset" is secondary (not the primary blue fill), so the frequency stays the single primary element.

## phone
- Missed calls: names stay white; add an amber "Missed" tag (amber fill, dark text) in the row. Call buttons in the Recents rows: fully round. Backspace button: right edge aligned with the keypad edge; use a backspace glyph (inline SVG), not a chevron.
- "Garage · Service desk" -> "Garage · service desk". Device card "82 %": add a battery icon.

## phone-call
- Hierarchy: the caller avatar/name is the primary; reduce the timer to at most 72 px and the End call button height to about 96 px. Number "+91 98765 43210" white.

## projection
- "62 Mb/s" white. "+ Add" ice-cyan link. "Connected" pill: ice-cyan outline live-indicator style like "Active".

## sound
- EQ: the 8 bands stay. All EQ values white with dB units ("+4 dB"), frequency labels with Hz/kHz ("60 Hz", "1 kHz"), scale ends "+12 dB" / "-12 dB". Bar graphics must match values: 0 dB = zero row only, -1 dB = one cyan segment below the zero row.
- Volume "18" white + dim "/ 30"; Subwoofer "+4 dB" white. Balance pad: add L and R labels and the range ("L 2 / 10 · F 3 / 10").

## apps
- Gallery icon: `image`. Search field and "Edit layout" same height and top edge; search field fully round. Grid bottom margin 24 px (tile height ~151 px).

## settings
- Brightness "70%" and Night brightness "30%". Selected category row: solid Ford-blue fill, white text, no gradient or left accent bar. Gauge refresh pills "30 fps" / "60 fps". Storage row icon: `storage`.

## settings-vehicle
- Card vertical spacing exactly 12 px. "Listening" pill: ice-cyan outline like "Live". "CAN 11/500" -> "CAN 11-bit · 500 kbit/s". Fix the orphan-word wraps (Canbox row and Record raw frames row): shorten copy or widen the block.
