# Cockpit design site

Static HTML/CSS/JS, no build step, no network calls. Everything is relative, so the `site/` folder can be published as-is (any static host).

## View it

    tools/design.sh serve        # http://127.0.0.1:8750/  (loopback only)

- `index.html`            overview and screen gallery
- `prototype.html`        bezel + all screens + simulated engine (keys: 1-9, [ ], L live, S scenario)
- `design-system.html`    palette with measured contrast, type, grid, shapes, icons, motion, data states
- `components.html`       every building block
- `specs.html`            data sources, integration risk, Compose structure, overlay priorities, open questions
- `screens/<name>.html`   each screen at 1280x800 (add `?live=1` to animate, `?t=<seconds>` to pick a point in the 100 s drive cycle)

## Themes

`?theme=ford` (default) or `?theme=cockpit`; the switch at the top right of every doc page and the prototype does the same and remembers it. Boot animation: `screens/boot.html` (click to replay); `?bt=1.7` freezes it at 1.7 s for screenshots (`tools/design.sh shotq boot boot-1.7 "bt=1.7&noanim=0"`).

## Check it

    tools/design.sh shotall      # screenshots every screen into site/shots (headless Chrome)
    tools/design.sh qaall        # overflow / clipping check on every screen (prints QA-CLEAN or the offenders)
    tools/design.sh bench drive  # scripting cost of one frame's update (not GPU raster)
    python3 tools/design-check.py   # every local link resolves, no external URLs, registry matches files

## Add a screen

1. Copy a file in `screens/`, keep `<div class="stage"><div class="frame" data-active="...">`.
2. Register it in `site/assets/screens.js` (id, title, group, data sources, risk, Compose name, states).
3. `tools/design.sh shot <id>` then `tools/design.sh qa <id>`.

## Notes

- Simulator in `assets/ui.js` (`UI.Sim`) mirrors `vehicle/DriveCycle.kt` but with higher revs, for visual drama. Retune the Kotlin ratios to match if you want identical demos.
- `UI.qa` is the overflow detector behind `design.sh qa`. It ignores absolutely positioned elements and inline spans.
- Open Design skills (`design/dashboard`, `design/critique`) are installed in Hermes. A critique pass has deliberately not been run yet: review the pages first, then run it.
