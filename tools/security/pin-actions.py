#!/usr/bin/env python3
"""Pin every third-party GitHub Action in .github/workflows/*.yml to the commit SHA its tag points to.

    python3 tools/security/pin-actions.py            # print the changes (dry run)
    python3 tools/security/pin-actions.py --write    # rewrite the files:  uses: actions/checkout@v4  ->  uses: actions/checkout@<40-hex> # v4

Why: a tag such as @v4 can be moved by whoever controls the action's repository (or an attacker who takes it over) and then runs new code
with this repo's workflow permissions. A commit SHA cannot be moved. Dependabot (.github/dependabot.yml) keeps pinned SHAs current.

It needs network access to github.com (`git ls-remote`, read-only, no credentials), which the review sandbox did not have, so it has
NOT been run against the real repository: run it once as the owner, review the diff, commit. Resolution prefers the peeled commit of an
annotated tag (`refs/tags/X^{}`). Local actions (`./...`) and already pinned ones are left alone.
"""
import re
import subprocess
import sys
from pathlib import Path

USES = re.compile(r"^(?P<pre>\s*-?\s*uses:\s*)(?P<repo>[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)(?P<sub>/[^@\s]+)?@(?P<ref>[^\s#]+)(?P<post>.*)$")
SHA = re.compile(r"^[0-9a-f]{40}$")


def ls_remote(repo, ref):
    out = subprocess.run(["git", "ls-remote", f"https://github.com/{repo}.git", f"refs/tags/{ref}", f"refs/tags/{ref}^{{}}"],
                         capture_output=True, text=True, timeout=60, check=True).stdout
    return out


def resolve(repo, ref, runner=ls_remote):
    """-> commit sha for tag [ref] of [repo] (peeled if annotated), or None."""
    peeled = plain = None
    for line in runner(repo, ref).splitlines():
        sha, name = line.split()[:2]
        if name.endswith("^{}"):
            peeled = sha
        else:
            plain = sha
    return peeled or plain


def rewrite(text, runner=ls_remote):
    out, changes = [], []
    for line in text.splitlines(keepends=True):
        m = USES.match(line.rstrip("\n"))
        if m and not SHA.match(m["ref"]) and not m["repo"].startswith("."):
            sha = resolve(m["repo"], m["ref"], runner)
            if sha is None:
                raise SystemExit(f"cannot resolve {m['repo']}@{m['ref']}")
            new = f"{m['pre']}{m['repo']}{m['sub'] or ''}@{sha} # {m['ref']}\n"
            changes.append((line.rstrip("\n"), new.rstrip("\n")))
            line = new
        out.append(line)
    return "".join(out), changes


def main():
    write = "--write" in sys.argv
    total = 0
    for f in sorted(Path(".github/workflows").glob("*.yml")):
        new, changes = rewrite(f.read_text())
        for old, nw in changes:
            print(f"{f}:\n  - {old.strip()}\n  + {nw.strip()}")
        total += len(changes)
        if write and changes:
            f.write_text(new)
    print(f"{total} action reference(s) {'pinned' if write else 'would be pinned (run with --write)'}")


if __name__ == "__main__":
    main()
