# Round 2 findings, per screen (spec v2 incl. clarifications)

Spec: docs/design/critique/REVIEW-PROMPT.md. Screens: docs/design/site/screens/<name>.html.
Already fixed centrally (do NOT redo, do not edit assets/*): default `.badge` text is white; `.mono` and `.unit` render in Barlow Condensed; open doors / tailgate / bonnet in the car drawing are amber in the Ford theme; parking radar arcs: lit arcs ice-cyan, closest ring amber, inactive blue tint; `.v.lime.best` / `.num.lime.best` stay ice-cyan (Peak/Best callout); `.lbl.lime` / `.lbl.white` now win over the generic `.lbl` dim colour (so do not rely on the old `.queue` override).
Measured facts (do not "fix" these, they were reviewer misjudgements): Home load bar is 7/8 lit at 88% (correct); Parking tab gaps are exactly 12 px; Settings sliders and pill groups are fine.

## car
- P1 The climate card is view only, but the airflow buttons look like live controls. Render the four airflow items as non-interactive indicators (no button chrome, no `btn`/`a` element): active = ice-cyan icon with a thin cyan outline (live indicator), inactive = dim icon. The "A/C on" pill becomes an ice-cyan outline live indicator (not a blue fill).
- Status list: every value white (also "Off" and "Released").
- "Vehicle · top view" card title: white, Barlow Condensed (title style).
- Front parking-sensor arcs: print the front distance with a unit like the rear one ("1.8 m"), same style.

## parking
- "Object" label: dark text on an amber chip (never amber text on a dark fill).
- "Rear camera · live" pill: ice-cyan live indicator (cyan dot + text, cyan outline), not a blue fill.
- The "Object" box must not touch the left guideline: move it clear or draw it above the line with a solid backing.

## overlays
- State 6 (incoming call): gap between Answer and Decline exactly 12 px; caller name in Barlow Condensed (title style).
- State 7 (shift banner): not amber (it is an advisory). Ford theme: Ford-blue fill (`var(--accent)`) with white text; position, inset and radius unchanged; Cockpit unchanged.

## quick
- The bottom of the left column (the All settings / Power menu pills end at y 368) and the bottom of the tile grid (354) must align within 2 px. Let the tile grid end where the pill row ends; keep 12 px gaps and no overflow.

## drive
- Trip strip "0–100 best 12.3 s": the value carries class `best` so it stays ice-cyan (Best callout).
- Units: "°C", "%", "bar" in the tiles and Boost card must sit on the numeral baseline (they currently look 6–10 px high); trip-strip values to units use one fixed 6 px gap ("131 km/h" and "12.3 s" the same).
- Tach redline hatch: look at the bottom end of the amber hatch (near the "7" mark, about x 490–520, y 555–590): there is a stray white sliver / uneven end. If it comes from screen markup fix it; if it comes from assets/ui.js (UI.tachFord) report it to me and do not edit.

## drive-boost
- "Source · OBD MAP – BARO" note: dim text (no periwinkle).

## home
- "Queue ›": ice-cyan link (class `lime`, remove the `.queue` override; the generic `.lbl.lime` rule now applies).
- Coolant and Engine load cards: the mini bar is flush against the unit. Make the bars 64 px wide with 6 segments (data-n 6) and at least 12 px gap from the number.

## media
- Elapsed and remaining times and queue durations/track numbers are measured values: white (they are `.mono`, now Condensed). Queue header "12 tracks" gets a position indicator: "1–8 of 12".

## phone-call
- The audio meter: print a level, e.g. "Mic level −18 dB" (meter + printed value). The caller number "+91 98765 43210" in Barlow Condensed. Control icons (Mute, Keypad, Hold, Add call, Phone): white (not periwinkle).

## projection
- Stat row: values white, units dim and separate: "5" + dim "GHz", "62" + dim "Mb/s". Connection diagram dotted line: symmetric end gaps around the Wi-Fi icon.

## radio
- "RDS" chip: ice-cyan outline like "Stereo". Numerals in Barlow Condensed: dial scale labels (88 ... 108, in the SVG use font-family var(--f-display)), signal "48", step "0.1" values.

## settings-vehicle
- The "Service reminder" stepper row is 4 px out of line with the others (long label pushes the minus button): rename the label to "Service every" so all nine rows have identical geometry.

## sound
- Subwoofer slider: add the range as a dim caption ("−10 to +10 dB").
- Balance · Fader card: same 24 px inner padding as the Subwoofer card for title, pad and header value; right edges aligned with the Subwoofer card.
- The 0 dB reference segment in the EQ bars: dim (var(--dim) at ~45%) instead of neutral grey.

## trips
- Selected trip list row: solid Ford-blue fill (`var(--accent)`) with white text (currently a teal gradient from `.tr.on`): override background-image too.
- "Recorded by Cockpit" chip: dim text, neutral outline.
- Replace the "→" in "18:12 → 19:20" with an en dash "18:12 – 19:20" (arrow renders from a fallback font).
- The moments list must agree with the chart: peak boost time at the boost maximum, top speed time at the speed maximum, longest stop where speed is zero.
