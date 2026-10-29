#!/usr/bin/env bash
# Dev-only ELM327 adapter emulator on TCP :35000 (package is CC-BY-NC-SA: never bundled into the app).
# From the Android emulator the host is reachable at 10.0.2.2:35000.
set -euo pipefail
cd "$(dirname "$0")"
[ -d .venv ] || python3 -m venv .venv
.venv/bin/pip install -q ELM327-emulator
exec .venv/bin/python -m elm -n 35000 -s car
