#!/usr/bin/env python3
"""A/B frame-cost benchmark for the Cockpit app on ONE emulator or device (stdlib only).

Runs are INTERLEAVED (A B A B ...) so slow drift of a shared machine hits every variant alike, and every run records the
host and guest load average next to the numbers. Primary metric is the app-side CPU cost per frame (`app-cpu`, logged by
FrameMeter under logcat tag CockpitPerf): wall-clock totals on an emulator are inflated by GL translation and by whatever else
the host is doing. Secondary: process CPU ms per second (/proc/<pid>/stat, split per thread), frames per second
(dumpsys gfxinfo), GC count and allocated KB per second (art runtime stats, logged by FrameMeter), threads, PSS.

  export ANDROID_SERIAL=emulator-5554
  tools/perf/bench.py --apk base=artifacts/apk/base-release.apk --apk new=app/build/outputs/apk/release/app-release.apk \\
        --screen home,drive --runs 3 --secs 30
  tools/perf/bench.py --apk full=x.apk@full --apk light=x.apk@light --screen drive     # same APK, governor pinned to Full / Light
  tools/perf/bench.py --summary artifacts/bench/<file>.jsonl        # re-print a table from a saved run

Scenarios (--scenario): drive-sim / home-sim use the built-in simulator (20 Hz moving data, the worst case),
`static` = no data source (source=ObdBluetooth without an adapter => Link.Off => nothing changes; the idle head unit),
`obd` = ELM327 over TCP to 10.0.2.2:<--obd-port> (start the fake adapter first, see tools/perf/README.md).
"""
import argparse, json, os, re, statistics, subprocess, sys, time

PKG = "dev.kuntal.cockpit"
ACT = PKG + "/.MainActivity"


def adb(*args, check=True, timeout=120):
    serial = os.environ.get("ANDROID_SERIAL")
    if not serial:
        sys.exit("ANDROID_SERIAL is not set (refusing to talk to an arbitrary device)")
    r = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, timeout=timeout)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {r.stderr.strip() or r.stdout.strip()}")
    return r.stdout


def sh(cmd, **kw):
    return adb("shell", cmd, **kw)


def host_load():
    return os.getloadavg()[0]


def guest_load():
    try:
        return float(sh("cat /proc/loadavg").split()[0])
    except Exception:
        return -1.0


def pid():
    out = sh(f"pidof {PKG}", check=False).strip().split()
    return out[0] if out else None


def thread_ticks(p):
    """{(tid, comm): utime+stime ticks} for every thread of the process."""
    out = sh(f"for t in /proc/{p}/task/*; do cat $t/stat; done", check=False)
    res = {}
    for line in out.splitlines():
        m = re.match(r"(\d+) \((.*)\) \S (?:\S+ ){10}(\d+) (\d+)", line)
        if m:
            res[(m.group(1), m.group(2))] = int(m.group(3)) + int(m.group(4))
    return res


def group_name(comm):
    for pre in ("RenderThread", "FrameMeter", "frame-meter", "DefaultDispatch", "pool-", "Binder", "HeapTaskDa", "Jit thread", "ReferenceQueueD",
                "FinalizerDaemon", "FinalizerWatchd", "Signal Catcher", "Profile Saver", "hwuiTask", "Choreographer"):
        if comm.startswith(pre):
            return pre
    if comm == PKG[-15:] or comm.startswith("dev.kuntal"):
        return "main"
    return "other"


GFX_KEYS = {
    "frames": r"Total frames rendered: (\d+)", "janky": r"Janky frames: (\d+)", "missed_vsync": r"Number Missed Vsync: (\d+)",
    "slow_ui": r"Number Slow UI thread: (\d+)", "slow_draw": r"Number Slow issue draw commands: (\d+)",
    "gfx_p50": r"50th percentile: (\d+)ms", "gfx_p90": r"90th percentile: (\d+)ms", "gfx_p99": r"99th percentile: (\d+)ms",
}


def parse_gfx(txt):
    d = {}
    for k, rx in GFX_KEYS.items():
        m = re.search(rx, txt)
        d[k] = int(m.group(1)) if m else None
    return d


PERF_RX = re.compile(
    r"n=(\d+) total p50=([\d.]+) p99=([\d.]+) max=([\d.]+) over12=([\d.]+)% missed=([\d.]+)% \| app-cpu p50=([\d.]+) p99=([\d.]+) max=([\d.]+)"
    r"(?: \| gate=\w+ mode=(\w+))?(?: \| gc=(\d+) blk=(\d+) alloc=(\d+))?")


def parse_perf(log):
    rows = []
    for line in log.splitlines():
        if "CockpitPerf" not in line:
            continue
        m = PERF_RX.search(line)
        if m:
            g = m.groups()
            rows.append(dict(n=int(g[0]), tot_p50=float(g[1]), tot_p99=float(g[2]), tot_max=float(g[3]), over12=float(g[4]), missed=float(g[5]),
                             cpu_p50=float(g[6]), cpu_p99=float(g[7]), cpu_max=float(g[8]), mode=g[9],
                             gc=int(g[10]) if g[10] else None, blk=int(g[11]) if g[11] else None, alloc=int(g[12]) if g[12] else None))
    return rows


def scenario_args(scn, screen, obd_port, extra_set):
    sets = {"static": "source=ObdBluetooth,bt=", "sim": "source=Simulator",
            "obd": f"source=ObdTcp,tcpHost=10.0.2.2,tcpPort={obd_port}"}[scn]
    if extra_set:
        sets += "," + extra_set
    return ["--es", "screen", screen, "--ez", "perf", "true", "--es", "set", sets]


_installed = {"apk": None}


def one_run(apk, label, screen, scn, a, perfmode):
    if apk and _installed["apk"] != apk:
        adb("install", "-r", "-d", apk, timeout=180)
        if a.compile != "none":
            sh(f"cmd package compile -m {a.compile} -f {PKG}", check=False, timeout=240)
        _installed["apk"] = apk
    sh(f"am force-stop {PKG}")
    adb("logcat", "-c")
    time.sleep(1.0)
    args = scenario_args(scn, screen, a.obd_port, a.set)
    if a.page:
        args += ["--es", "page", a.page]
    if perfmode:
        args += ["--es", "perfmode", perfmode]
    if a.noperf:
        args = [x for x in args if x != "perf"]
        i = args.index("--ez"); del args[i:i + 3]     # drop `--ez perf true`: no FrameMeter logging, no overlay (idle measurements)
    hl0, gl0 = host_load(), guest_load()
    out = sh(f"am start -W -n {ACT} " + " ".join(args))
    start_ms = int(m.group(1)) if (m := re.search(r"TotalTime: (\d+)", out)) else None
    time.sleep(a.warm)
    p = pid()
    if not p:
        raise RuntimeError("app is not running after start (crash?): " + sh("logcat -d -s AndroidRuntime:E | tail -20", check=False))
    sh(f"dumpsys gfxinfo {PKG} reset", check=False)
    t0 = thread_ticks(p); w0 = time.time()
    time.sleep(a.secs)
    t1 = thread_ticks(p); w1 = time.time()
    wall = w1 - w0
    gfx = parse_gfx(sh(f"dumpsys gfxinfo {PKG}", check=False))
    perf = parse_perf(adb("logcat", "-d", "-s", "CockpitPerf:I"))
    # steady-state = reports after the warm-up; use the last one (a full rolling window) for percentiles
    last = perf[-1] if perf else {}
    first = perf[0] if perf else {}
    gc_s = alloc_kb_s = None
    if last.get("gc") is not None and len(perf) >= 2:
        span = (len(perf) - 1) * 5.0
        gc_s = (last["gc"] - first["gc"]) / span
        alloc_kb_s = (last["alloc"] - first["alloc"]) / 1024.0 / span
    by = {}
    for k, v in t1.items():
        d = (v - t0.get(k, 0)) * 10.0     # ticks (10 ms) -> ms
        by[group_name(k[1])] = by.get(group_name(k[1]), 0.0) + d
    cpu_ms_s = sum(by.values()) / wall
    mem = sh(f"dumpsys meminfo {PKG}", check=False)
    pss = int(m.group(1)) if (m := re.search(r"TOTAL PSS:\s+(\d+)", mem)) or (m := re.search(r"TOTAL\s+(\d+)", mem)) else None
    heap = int(m.group(1)) if (m := re.search(r"Java Heap:\s+(\d+)", mem)) else None
    rec = dict(variant=label, screen=screen, scenario=scn, ts=int(time.time()), host_load=round(hl0, 2), guest_load=gl0,
               start_ms=start_ms, wall=round(wall, 1), fps=round(gfx["frames"] / wall, 2) if gfx.get("frames") is not None else None,
               cpu_ms_s=round(cpu_ms_s, 1),
               main_ms_frame=round(by.get("main", 0.0) / wall / (gfx["frames"] / wall), 2) if gfx.get("frames") else None,
               rt_ms_frame=round(by.get("RenderThread", 0.0) / wall / (gfx["frames"] / wall), 2) if gfx.get("frames") else None, thr={k: round(v / wall, 1) for k, v in sorted(by.items())}, threads=len(t1),
               gc_s=gc_s, alloc_kb_s=alloc_kb_s, pss_kb=pss, java_heap_kb=heap, mode=last.get("mode"),
               n=last.get("n"), cpu_p50=last.get("cpu_p50"), cpu_p99=last.get("cpu_p99"), cpu_max=last.get("cpu_max"),
               tot_p50=last.get("tot_p50"), tot_p99=last.get("tot_p99"), over12=last.get("over12"), **{k: gfx[k] for k in ("janky", "missed_vsync", "slow_ui", "slow_draw")})
    return rec


COLS = [("start_ms", "start ms", 0), ("fps", "fps", 1), ("cpu_p50", "app-cpu p50", 2), ("cpu_p99", "app-cpu p99", 1), ("cpu_ms_s", "proc cpu ms/s", 0),
        ("main_ms_frame", "main ms/frame", 2), ("rt_ms_frame", "render ms/frame", 2),
        ("gc_s", "gc/s", 2), ("alloc_kb_s", "alloc KB/s", 0), ("pss_kb", "PSS KB", 0), ("tot_p50", "total p50", 1), ("host_load", "host load", 1)]


def med_rng(vals, nd):
    vals = [v for v in vals if v is not None]
    if not vals:
        return None, None, None
    return round(statistics.median(vals), nd), round(min(vals), nd), round(max(vals), nd)


def summary(recs, out=sys.stdout):
    groups = {}
    for r in recs:
        groups.setdefault((r["scenario"], r["screen"]), {}).setdefault(r["variant"], []).append(r)
    for (scn, screen), variants in groups.items():
        print(f"\n== {scn} / {screen}   (median [min..max], n runs per variant: {', '.join(f'{v}={len(rs)}' for v, rs in variants.items())})", file=out)
        names = list(variants)
        head = f"{'metric':<16}" + "".join(f"{n:>28}" for n in names) + ("   verdict" if len(names) > 1 else "")
        print(head, file=out)
        for key, title, nd in COLS:
            cells, stats = [], []
            for n in names:
                m, lo, hi = med_rng([r.get(key) for r in variants[n]], nd)
                stats.append((m, lo, hi))
                cells.append("-" if m is None else f"{m} [{lo}..{hi}]")
            verdict = ""
            if len(names) > 1 and stats[0][0] is not None and stats[1][0] is not None and key not in ("host_load",):
                (m0, lo0, hi0), (m1, lo1, hi1) = stats[0], stats[1]
                if m0:
                    pct = (m1 - m0) / m0 * 100
                    overlap = not (hi1 < lo0 or lo1 > hi0)
                    verdict = f"{pct:+.0f}%" + ("  (within noise)" if overlap else "")
            print(f"{title:<16}" + "".join(f"{c:>28}" for c in cells) + ("   " + verdict if verdict else ""), file=out)
        # per-thread CPU
        keys = sorted({k for rs in variants.values() for r in rs for k in r["thr"]})
        print("thread cpu ms/s: " + "; ".join(f"{k}: " + "/".join(str(med_rng([r["thr"].get(k) for r in variants[n]], 1)[0]) for n in names) for k in keys), file=out)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--apk", action="append", default=[], help="label=path (repeat for A/B); the first is the baseline")
    ap.add_argument("--screen", default="home,drive", help="comma list of home,drive,apps,settings")
    ap.add_argument("--scenario", default="sim", choices=["sim", "static", "obd"])
    ap.add_argument("--runs", type=int, default=3)
    ap.add_argument("--secs", type=int, default=30, help="measurement window after warm-up")
    ap.add_argument("--warm", type=int, default=8)
    ap.add_argument("--compile", default="speed", help="cmd package compile -m <x> after install ('none' = leave as installed)")
    ap.add_argument("--perfmode", default="full", help="pin PerfGovernor: full | light | auto (auto = let the governor decide)")
    ap.add_argument("--noperf", action="store_true", help="do not pass --ez perf true (idle/fps/CPU only; no CockpitPerf lines)")
    ap.add_argument("--page", default=None, help="settings page: display|connections|vehicle|about")
    ap.add_argument("--set", default="", help="extra settings, e.g. theme=Midnight")
    ap.add_argument("--obd-port", default="35010")
    ap.add_argument("--out", default=None)
    ap.add_argument("--summary", default=None, help="print a table from a saved .jsonl and exit")
    a = ap.parse_args()
    if a.summary:
        summary([json.loads(l) for l in open(a.summary) if l.strip()]); return
    apks = []
    for x in a.apk:                                  # label=path[@mode]: the same APK in full and light mode makes two variants
        label, path = x.split("=", 1)
        mode = a.perfmode
        if "@" in path:
            path, mode = path.rsplit("@", 1)
        apks.append((label, path, None if mode == "auto" else mode))
    if not apks:
        apks = [("installed", "", a.perfmode)]
    out = a.out or f"artifacts/bench/{time.strftime('%Y%m%d-%H%M%S')}-{a.scenario}.jsonl"
    os.makedirs(os.path.dirname(out), exist_ok=True)
    recs = []
    with open(out, "a") as f:
        for i in range(a.runs):
            for screen in a.screen.split(","):
                for label, path, mode in apks:
                    r = one_run(path, label, screen, a.scenario, a, mode)
                    recs.append(r); f.write(json.dumps(r) + "\n"); f.flush()
                    print(f"run {i + 1}/{a.runs} {screen:<8} {label:<10} fps={r['fps']} app-cpu p50={r['cpu_p50']} p99={r['cpu_p99']} proc={r['cpu_ms_s']}ms/s "
                          f"gc/s={r['gc_s']} load={r['host_load']}", flush=True)
    summary(recs)
    print(f"\nraw: {out}")


if __name__ == "__main__":
    main()
