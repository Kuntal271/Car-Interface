#!/usr/bin/env bash
# Regenerate the "check with baseline" files for detekt and Android lint.
#
# Use it ONCE after merging branches that added code (their new findings would otherwise turn CI red), or after a deliberate rule
# change. Review `git diff config/` before committing: a baseline that grows because of a regression must be fixed, not re-baselined.
#   tools/rebaseline.sh            # needs network the first time (GB_ONLINE=1 is set for you)
set -euo pipefail
cd "$(dirname "$0")/.."
export GB_ONLINE=1
tools/gb.sh :app:detektBaselineDebug :vehicle:detektBaselineMain
rm -f config/lint/lint-baseline-app.xml
tools/gb.sh :app:lintDebug || true            # lint creates the baseline and then aborts on purpose ("new baseline file was created")
tools/gb.sh :app:detektDebug :vehicle:detektMain :app:detekt :vehicle:detekt :app:lintDebug :app:lintRelease    # the gates must now be green
git --no-pager diff --stat -- config/ || true
