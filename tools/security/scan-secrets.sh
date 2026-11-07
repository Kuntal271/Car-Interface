#!/usr/bin/env bash
# Secret / hygiene scan for the PUBLIC repo. Fails (exit 1) if a tracked file, or any blob in any branch's history (with --history),
# looks like a credential, or if a file type that must never be committed is tracked.
#   tools/security/scan-secrets.sh            # working tree (tracked files) + file-type checks   (CI runs this)
#   tools/security/scan-secrets.sh --history  # also every added line in `git log --all -p`        (slow; run before a release)
# Patterns: AWS access/secret keys, PEM private keys, GitHub/Slack/Google/OpenAI-style tokens, generic "password = ...".
set -euo pipefail
cd "$(dirname "$0")/../.."
PATTERNS='AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|aws_secret_access_key|aws_access_key_id|BEGIN ([A-Z]+ )?PRIVATE KEY|ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{20,}|gho_[A-Za-z0-9]{30,}|xox[baprs]-[A-Za-z0-9-]{10,}|AIza[0-9A-Za-z_-]{35}|sk-[A-Za-z0-9]{32,}|(password|passwd|secret|api[_-]?key|token)[A-Za-z_]* *[=:] *["'"'"'][^"'"'"' ]{8,}'
# keystore passwords are only ever read from the environment (see tools/release.sh); a literal in a gradle file is a leak
FORBIDDEN_FILES='(^|/)(local\.properties|\.env(\..*)?|keystore\.properties|id_rsa|id_ed25519)$|\.(jks|keystore|p12|pfx|pem|key|apk|aab|hprof)$'
fail=0
bad=$(git ls-files | grep -E "$FORBIDDEN_FILES" || true)
if [ -n "$bad" ]; then echo "FORBIDDEN tracked files:"; echo "$bad"; fail=1; fi
hits=$(git grep -nIE "$PATTERNS" -- . ':!tools/security/scan-secrets.sh' ':!docs/reviews' ':!docs/SECURITY.md' || true)
if [ -n "$hits" ]; then echo "SECRET-LIKE content in tracked files:"; echo "$hits" | cut -c1-200; fail=1; fi
if [ "${1:-}" = "--history" ]; then
  hh=$(git log --all -p --no-color -G"$PATTERNS" --format='commit %h' -- . ':!tools/security/scan-secrets.sh' ':!docs/reviews' ':!docs/SECURITY.md' | grep -E '^\+' | grep -E "$PATTERNS" || true)
  if [ -n "$hh" ]; then echo "SECRET-LIKE lines in history:"; echo "$hh" | cut -c1-200; fail=1; fi
  big=$(git rev-list --all --objects | git cat-file --batch-check='%(objecttype) %(objectsize) %(rest)' | awk '$1=="blob" && $2>5000000 {print $2, $3}')
  if [ -n "$big" ]; then echo "NOTE blobs over 5 MB in history (not secrets, but size):"; echo "$big"; fi
fi
if [ $fail -eq 0 ]; then echo "scan-secrets: clean"; fi
exit $fail
