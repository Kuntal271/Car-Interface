# Design review prompt (fixed; used for every page in every round)

Role: strict automotive HMI design reviewer. Output ONLY defect lines (no description, no praise).

Artifact: screen '{NAME}' - {PURPOSE} (1280x800 head unit, Ford theme).

SPEC v2 (report only violations of these rules, or visible bugs):
- S1 layout: 24 px outer margin and 24 px between top-level columns; 12 px between cards, tiles and pills within a group; card radius 24, pills fully round; banners are inset 24 px from the edges.
- S2 type: Barlow Condensed numerals and titles, Barlow labels; no monospace; sentence case; no tracked caps.
- S3 colour: white = every measured value and titles; dim = labels and units; ice-cyan = data fills (bars, arcs, sliders, chart lines), links/actions, live/armed indicators, Peak/Best callouts; Ford blue = structure, inactive segments, primary buttons AND selected controls (tabs, chips, toggles, dock item: blue fill with white text); amber = warnings, limits, redline only (never advisories); no red. Clarifications: a second chart series may be white dashed; the tachometer arc ramps from Ford blue to ice-cyan (signature exception); neutral controls (steppers, unselected pills, secondary buttons) use a glass fill with white glyph or text; a card caption (the small label at the top of a card, e.g. 'Volume', 'Coolant') is a label and is dim, while row titles inside lists are titles and are white; mock values need not agree across screens unless they sit on the same screen.
- S4 at most ONE Ford oval per screen.
- S5 (controls may show the value they control, e.g. volume slider + number) no value repeated that the status bar already shows (outside temperature, battery voltage, volume, clock) unless the screen is Car, Diagnostics or Standby. The status bar and dock are out of scope, including their contents (31 degrees, 14.2 V, 18) and the dock's position (sun icon = outside temperature; cyan dot = link up; the dock sits 10 px from the right edge by design).
- S6 every bar, gauge and chart shows its scale or a printed value; every number has a unit (level values such as volume and brightness show their range, e.g. 18 / 30, 70%).
- S7 hierarchy: one primary element per screen, others clearly smaller. Modal overlays (coolant, incoming call, reverse) scrim the background; transient OSDs (volume, climate) are non-blocking and have NO scrim, and covering background content is expected.
- S8 no clipped, overlapping or truncated text; no broken graphics; edges and baselines align within 2 px.

IGNORE: content hidden behind an overlay, text size and touch-target size (measured separately by probes), taste, empty space unless it hides a missing required element, prototype annotations (boot footer), Standby having no cards (intentional ambient mode).

Severity: P0 unreadable or broken; P1 clear spec violation or confusing; P2 minor spec violation.
Format per line: P0|P1|P2 | S# | location | problem | fix
If there is nothing to report, reply exactly: ZERO DEFECTS
