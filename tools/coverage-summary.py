#!/usr/bin/env python3
"""Print JaCoCo coverage for :app and :vehicle from the XML reports (no dependencies, python3 stdlib only).

    tools/gb.sh :vehicle:jacocoTestReport :app:createDebugUnitTestCoverageReport -Pcoverage
    python3 tools/coverage-summary.py            # totals + per package
    python3 tools/coverage-summary.py --classes  # also every class below 100 % line coverage

Counters: INSTRUCTION, BRANCH, LINE, METHOD. Generated Compose code (ComposableSingletons, lambda classes) is counted as JaCoCo
reports it, so the UI packages read low: the meaningful numbers are the logic packages (data, perf, source, vehicle).
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPORTS = {
    "vehicle": ROOT / "vehicle/build/reports/jacoco/test/jacocoTestReport.xml",
    "app": ROOT / "app/build/reports/coverage/test/debug/report.xml",
}


def pct(counters, kind):
    c = counters.get(kind)
    if not c or c[0] + c[1] == 0:
        return None
    return 100.0 * c[1] / (c[0] + c[1])


def read(node):
    return {c.get("type"): (int(c.get("missed")), int(c.get("covered"))) for c in node.findall("counter")}


def fmt(v):
    return "  -  " if v is None else f"{v:5.1f}"


def main():
    show_classes = "--classes" in sys.argv
    for module, path in REPORTS.items():
        if not path.exists():
            print(f"{module}: no report at {path.relative_to(ROOT)} (run the Gradle tasks in the docstring)")
            continue
        root = ET.parse(path).getroot()
        total = read(root)
        print(f"\n== {module}   instr {fmt(pct(total, 'INSTRUCTION'))}%   branch {fmt(pct(total, 'BRANCH'))}%   line {fmt(pct(total, 'LINE'))}%   method {fmt(pct(total, 'METHOD'))}%")
        print(f"   {'package':58s} {'instr':>6s} {'branch':>6s} {'line':>6s}  lines(missed/covered)")
        for pkg in root.findall("package"):
            c = read(pkg)
            lm, lc = c.get("LINE", (0, 0))
            print(f"   {(pkg.get('name') or '').replace('/', '.'):58s} {fmt(pct(c, 'INSTRUCTION'))}  {fmt(pct(c, 'BRANCH'))}  {fmt(pct(c, 'LINE'))}  {lm}/{lc}")
            if show_classes:
                for cl in pkg.findall("class"):
                    cc = read(cl)
                    lp = pct(cc, "LINE")
                    if lp is not None and lp < 100.0:
                        print(f"       {(cl.get('name') or '').split('/')[-1]:54s} {fmt(pct(cc, 'INSTRUCTION'))}  {fmt(pct(cc, 'BRANCH'))}  {fmt(lp)}")


if __name__ == "__main__":
    main()
