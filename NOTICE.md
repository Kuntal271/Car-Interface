# Notices

The MIT licence in `LICENSE` covers the code and documentation written for this project. It does **not** cover the items below.

## Trademarks (not licensed)

Ford, the Ford Blue Oval and related marks are registered trademarks of Ford Motor Company. Blaupunkt, Palm Bay, Android, Android Auto and Apple CarPlay are trademarks of their respective owners.

This is an independent, non-commercial personal project. It is **not affiliated with, authorised by or endorsed by** Ford Motor Company, Blaupunkt or any other manufacturer. The Blue Oval appears only so the interface can be seen as it looks on the author's own car (`docs/design/site/assets/brand/`, `docs/design/site/assets/brand.js` and the screenshots in `docs/design/site/shots/`). No trademark right is granted by this repository. If you hold rights in any of these marks and want them removed, open an issue and they will be removed.

The logo file is the flat vector `Ford_logo_flat.svg` from Wikimedia Commons; its navy is lifted slightly so it reads on a dark screen.

## Fonts (SIL Open Font License 1.1)

Barlow, Barlow Condensed, Big Shoulders Display, JetBrains Mono and Space Grotesk are bundled in `docs/design/site/assets/fonts/`, each with its licence text (`OFL-*.txt`). Ford's own typeface (FordAntenna) is proprietary and is not included; Barlow stands in for it.

## Third-party tools

The ELM327 emulator used for the integration test (`tools/elm-emu.sh`) is the open-source `ELM327-emulator` Python package, installed on demand into `tools/.venv` and not committed.

## Safety

Experimental software for a car head unit, provided without warranty. It is read-only toward the vehicle (ELM327 `AT` configuration commands and OBD-II Mode 01 reads only, enforced in code and tests). It is not safety-certified: do not operate the display while driving.
