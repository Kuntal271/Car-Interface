#!/usr/bin/env python3
"""Run by CI when a job fails: turn what failed into GitHub annotations and a step summary.

The raw Gradle log is long and (for a public repo) only downloadable with a token; the annotations on the run page are not. This reads the
reports the build already wrote: failing JUnit tests, detekt findings (xml), Android lint errors (xml). Never fails the job itself.
"""
import glob
import os
import xml.etree.ElementTree as ET

LIMIT = 10          # GitHub shows about 10 annotations per step
found = []


def esc(s: str) -> str:
    return s.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def note(title: str, msg: str) -> None:
    found.append((title, msg))
    if len([f for f in found if f[0] == title]) <= LIMIT:
        print(f"::error title={esc(title)}::{esc(msg[:900])}")


def safe_parse(path):
    try:
        return ET.parse(path).getroot()
    except Exception as e:                                   # a half-written report must not hide the others
        print(f"::warning::could not read {path}: {e}")
        return None


for x in sorted(glob.glob("*/build/test-results/**/*.xml", recursive=True)):
    root = safe_parse(x)
    if root is None:
        continue
    for c in root.iter("testcase"):
        f = c.find("failure") if c.find("failure") is not None else c.find("error")
        if f is not None:
            first = (f.text or "").strip().splitlines()[:6]
            note("test failed", f"{c.get('classname')}.{c.get('name')}: {f.get('message') or ''} | {' / '.join(s.strip() for s in first[1:5])}")

for x in sorted(glob.glob("*/build/reports/detekt/*.xml")):
    root = safe_parse(x)
    if root is None:
        continue
    for fl in root.iter("file"):
        for e in fl.iter("error"):
            note("detekt", f"{os.path.relpath(fl.get('name'))}:{e.get('line')} {e.get('source')}: {e.get('message')}")

for x in sorted(glob.glob("*/build/reports/lint-results-*.xml")):
    root = safe_parse(x)
    if root is None:
        continue
    for i in root.iter("issue"):
        if i.get("severity") in ("Error", "Fatal"):
            loc = i.find("location")
            note("lint", f"{i.get('id')}: {i.get('message')} @ {loc.get('file') if loc is not None else '?'}:{loc.get('line') if loc is not None else '?'}")

summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary and found:
    with open(summary, "a") as fh:
        fh.write(f"### {len(found)} problem(s) found in the build reports\n")
        for t, m in found[:50]:
            fh.write(f"- **{t}**: {m[:300]}\n")
if not found:
    print("::notice::no failing tests, detekt or lint errors in the reports: the failure was in the build itself (compile, download, tool crash)")
