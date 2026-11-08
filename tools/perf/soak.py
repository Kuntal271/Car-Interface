#!/usr/bin/env python3
"""Soak test: run the app for N minutes on the simulator (20 Hz data) and sample memory, threads, fds and CPU every --every seconds.

  export ANDROID_SERIAL=emulator-5554
  tools/perf/soak.py --minutes 20 --screen drive --cycle 90        # visit home/drive/apps/settings every 90 s (leak check on navigation)
  tools/perf/soak.py --minutes 20 --screen home --scenario static   # idle head unit: no data source

Writes artifacts/soak/<stamp>.csv and prints a slope table (KB or count per minute, least squares over the second half of the run, so
warm-up growth is excluded). A leak shows as a persistent positive slope on Java heap / PSS / threads / fds.
"""
import argparse, os, re, subprocess, sys, time

PKG = "dev.kuntal.cockpit"
DOCK = {"home": 0, "drive": 1, "apps": 6, "settings": 7}        # dock tile index; tiles are 91 px apart, first centre y = 56 + 8 + 3 + 42


def adb(*a, check=False):
    ser = os.environ.get("ANDROID_SERIAL")
    if not ser:
        sys.exit("ANDROID_SERIAL is not set")
    return subprocess.run(["adb", "-s", ser, *a], capture_output=True, text=True, timeout=120).stdout


def sh(c):
    return adb("shell", c)


def sample(pid):
    mem = sh(f"dumpsys meminfo {PKG}")
    def g(rx):
        m = re.search(rx, mem)
        return int(m.group(1)) if m else -1
    pss = g(r"TOTAL PSS:\s+(\d+)") if "TOTAL PSS" in mem else g(r"TOTAL\s+(\d+)")
    java = g(r"Java Heap:\s+(\d+)"); native = g(r"Native Heap:\s+(\d+)"); gfx = g(r"Graphics:\s+(\d+)")
    views = g(r"Views:\s+(\d+)"); acts = g(r"Activities:\s+(\d+)")
    stat = sh(f"cat /proc/{pid}/stat").split(")")[-1].split()
    ticks = int(stat[11]) + int(stat[12])
    threads = len(sh(f"ls /proc/{pid}/task").split())
    fd_out = sh(f"ls /proc/{pid}/fd 2>&1")
    fds = -1 if ("Permission denied" in fd_out or not fd_out.strip()) else len(fd_out.split())      # -1: not readable for the shell user (needs root)
    return dict(pss=pss, java=java, native=native, gfx=gfx, views=views, acts=acts, threads=threads, fds=fds, ticks=ticks)


def slope(xs, ys):
    n = len(xs)
    if n < 3:
        return float("nan")
    mx = sum(xs) / n; my = sum(ys) / n
    den = sum((x - mx) ** 2 for x in xs)
    return sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / den if den else float("nan")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--minutes", type=float, default=20)
    ap.add_argument("--every", type=int, default=30)
    ap.add_argument("--screen", default="drive")
    ap.add_argument("--scenario", default="sim", choices=["sim", "static"])
    ap.add_argument("--apk", default=None)
    ap.add_argument("--cycle", type=int, default=0, help="seconds between screen changes via dock taps (0 = stay)")
    ap.add_argument("--noperf", action="store_true")
    a = ap.parse_args()
    if a.apk:
        adb("install", "-r", "-d", a.apk)
        sh(f"cmd package compile -m speed -f {PKG}")
    sets = "source=Simulator" if a.scenario == "sim" else "source=ObdBluetooth,bt="
    sh(f"am force-stop {PKG}")
    adb("logcat", "-c")
    perf = [] if a.noperf else ["--ez", "perf", "true"]
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity", "--es", "screen", a.screen, "--es", "set", sets, "--es", "perfmode", "full", *perf)
    time.sleep(8)
    pid = sh(f"pidof {PKG}").split()[0]
    out = f"artifacts/soak/{time.strftime('%Y%m%d-%H%M%S')}-{a.scenario}-{a.screen}.csv"
    os.makedirs("artifacts/soak", exist_ok=True)
    rows = []; t0 = time.time(); nextcycle = t0 + a.cycle; order = list(DOCK); ci = order.index(a.screen) if a.screen in order else 0
    with open(out, "w") as f:
        f.write("t_s,pss_kb,java_kb,native_kb,graphics_kb,views,activities,threads,fds,cpu_ticks,load\n")
        while time.time() - t0 < a.minutes * 60:
            now = time.time() - t0
            if a.cycle and time.time() >= nextcycle:
                ci = (ci + 1) % len(order); y = 56 + 8 + 91 * DOCK[order[ci]] + 3 + 42
                sh(f"input tap 1224 {y}"); nextcycle += a.cycle
            s = sample(pid)
            rows.append((now, s))
            f.write(f"{now:.0f},{s['pss']},{s['java']},{s['native']},{s['gfx']},{s['views']},{s['acts']},{s['threads']},{s['fds']},{s['ticks']},{os.getloadavg()[0]:.1f}\n"); f.flush()
            print(f"t={now:5.0f}s pss={s['pss']} java={s['java']} native={s['native']} gfx={s['gfx']} threads={s['threads']} fds={s['fds']} load={os.getloadavg()[0]:.1f}", flush=True)
            time.sleep(a.every)
    half = rows[len(rows) // 2:]
    xs = [r[0] / 60 for r in half]
    print("\nslope over the second half of the run (per minute):")
    for k in ("pss", "java", "native", "gfx", "views", "threads", "fds"):
        if rows[0][1][k] < 0:
            continue
        print(f"  {k:<8} {slope(xs, [r[1][k] for r in half]):+8.2f}   (start {rows[0][1][k]}, end {rows[-1][1][k]})")
    cpu = (rows[-1][1]["ticks"] - rows[0][1]["ticks"]) * 10 / (rows[-1][0] - rows[0][0])
    print(f"  process cpu: {cpu:.1f} ms per second on average")
    gc = [l for l in adb("logcat", "-d", "-s", "CockpitPerf:I").splitlines() if "gc=" in l]
    if len(gc) >= 2:
        g0 = int(re.search(r"gc=(\d+)", gc[0]).group(1)); g1 = int(re.search(r"gc=(\d+)", gc[-1]).group(1))
        print(f"  GCs: {g1 - g0} over {(len(gc) - 1) * 5} s; crashes in log: {adb('logcat', '-d', '-s', 'AndroidRuntime:E').count('FATAL')}")
    print("csv:", out)


if __name__ == "__main__":
    main()
