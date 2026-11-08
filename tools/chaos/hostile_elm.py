#!/usr/bin/env python3
"""Hostile fake ELM327 adapter for chaos-testing the Cockpit OBD client.

Usage:  tools/.venv/bin/python tools/chaos/hostile_elm.py --port 35020 --mode garbage
        ... --mode cycle:valid,nodata,garbage --cycle-s 6      (switch mode every 6 s)

The app talks "cmd\\r" and expects the reply to end with the '>' prompt.  Every mode below breaks one assumption.

  valid        well-behaved adapter: ATxx -> OK, 0100/0120/0140 bitmaps, plausible drive-cycle values
  garbage      random bytes, no framing
  noprompt     plausible reply but the '>' prompt never comes
  endless      after the first command, stream bytes forever with no newline and no '>'   (memory growth)
  endless_nl   same, but with newlines
  halfline     "41 0C 1A" then silence (connection stays open)
  badecho      echo ON and wrong echo (echo of a different command)
  nodata       ATxx -> OK, every PID read -> NO DATA
  unable       0100 -> SEARCHING... / UNABLE TO CONNECT   (ignition off / car not on the bus)
  buserr       BUS INIT: ... ERROR / CAN ERROR / STOPPED / BUFFER FULL / ? / DATA ERROR
  absurd       valid framing, absurd values (rpm 0xFFFF, speed 0xFF, coolant -40, MAP 0xFF, battery 65.535 V ...)
  signs        valid framing, but hex digits spelled with signs ("410D-1", "410C+F+F") to trip lenient parsers
  slowdrip     valid replies, one byte every --drip-ms (default 1500: under a 2 s read timeout, so it never fires)
  reset        send half a reply then RST the connection
  closeloop    accept, then close immediately
  fast         valid replies with no delay, no CR/LF noise, many frames per command
  huge         valid replies preceded by --huge-mb (default 8) MB of junk lines before '>'
  hang         accept, read, and never answer (like SIGSTOP on a real adapter)
  stale        valid for the first --stale-after seconds, then every PID read answers NO DATA
"""
import argparse, os, random, socket, struct, sys, threading, time

P = argparse.ArgumentParser()
P.add_argument("--port", type=int, default=35020)
P.add_argument("--mode", default="valid")
P.add_argument("--cycle-s", type=float, default=8.0)
P.add_argument("--drip-ms", type=int, default=1500)
P.add_argument("--huge-mb", type=int, default=8)
P.add_argument("--stale-after", type=float, default=10.0)
P.add_argument("--seed", type=int, default=1)
P.add_argument("--quiet", action="store_true")
A = P.parse_args()
rnd = random.Random(A.seed)
T0 = time.time()
MODES = ["valid", "garbage", "noprompt", "endless", "endless_nl", "halfline", "badecho", "nodata", "unable", "buserr",
         "absurd", "signs", "slowdrip", "reset", "closeloop", "fast", "huge", "hang", "stale"]
cycle = A.mode.split(":", 1)[1].split(",") if A.mode.startswith("cycle:") else None
stats = {"conns": 0, "cmds": 0, "bytes_out": 0}


def mode_now():
    if cycle:
        return cycle[int((time.time() - T0) / A.cycle_s) % len(cycle)]
    return A.mode


def log(*a):
    if not A.quiet:
        print(time.strftime("%H:%M:%S"), *a, flush=True)


# ---- valid answers -------------------------------------------------------------------------------------------------
BITMAPS = {"0100": "41 00 18 3A 80 01", "0120": "41 20 00 02 20 01", "0140": "41 40 44 00 00 10"}


def drive(t):
    ph = t % 60
    if ph < 30: v = 100.0 * ph / 30
    elif ph < 40: v = 100.0
    elif ph < 55: v = 100.0 * (1 - (ph - 40) / 15)
    else: v = 0.0
    return v, max(850.0, v * 40 + 850)


def valid_reply(cmd, t=None):
    c = cmd.upper()
    if c.startswith("AT"):
        return "ELM327 v1.5" if c.startswith("ATZ") else ("OK" if not c.startswith("ATI") else "ELM327 v1.5")
    if c in BITMAPS: return BITMAPS[c]
    v, rpm = drive(time.time() - T0 if t is None else t)
    r = int(rpm * 4)
    table = {"0104": "41 04 40", "0105": "41 05 85", "010B": "41 0B 78", "010C": "41 0C %02X %02X" % (r >> 8, r & 255),
             "010D": "41 0D %02X" % int(v), "010F": "41 0F 4E", "0111": "41 11 30", "012F": "41 2F 9E",
             "0133": "41 33 64", "0142": "41 42 37 AA", "0146": "41 46 47", "015C": "41 5C 78"}
    return table.get(c, "NO DATA")


def absurd_reply(cmd):
    c = cmd.upper()
    if c.startswith("AT") or c in BITMAPS: return valid_reply(c)
    return {"010C": "41 0C FF FF", "010D": "41 0D FF", "0105": "41 05 00", "010B": "41 0B FF", "0133": "41 33 00",
            "0104": "41 04 FF", "0111": "41 11 FF", "012F": "41 2F FF", "0142": "41 42 FF FF", "0146": "41 46 FF",
            "010F": "41 0F 00", "015C": "41 5C FF"}.get(c, "NO DATA")


def signs_reply(cmd):
    c = cmd.upper()
    if c.startswith("AT") or c in BITMAPS: return valid_reply(c)
    return {"010C": "410C+F+F", "010D": "410D-1", "0105": "4105-F", "010B": "410B-1", "0133": "4133+0",
            "0104": "4104-1", "0111": "4111-1", "012F": "412F-1", "0142": "4142-1-1", "0146": "4146-1",
            "010F": "410F-1", "015C": "415C-1"}.get(c, "NO DATA")


# ---- connection handler --------------------------------------------------------------------------------------------
def send(conn, data):
    if isinstance(data, str): data = data.encode("latin-1", "replace")
    conn.sendall(data)
    stats["bytes_out"] += len(data)


def rst(conn):
    try:
        conn.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, struct.pack("ii", 1, 0))
        conn.close()
    except OSError:
        pass


def handle(conn, addr):
    stats["conns"] += 1
    m0 = mode_now()
    log("conn", addr, "mode", m0)
    buf = b""
    try:
        if m0 == "closeloop":
            conn.close(); return
        conn.settimeout(30)
        while True:
            d = conn.recv(256)
            if not d: break
            buf += d
            while b"\r" in buf:
                raw, buf = buf.split(b"\r", 1)
                cmd = raw.decode("latin-1").strip()
                if not cmd: continue
                stats["cmds"] += 1
                if respond(conn, cmd, mode_now()) == "stop": return
    except (OSError, socket.timeout):
        pass
    finally:
        try: conn.close()
        except OSError: pass


def respond(conn, cmd, m):
    CR = "\r"
    if m == "valid" or m == "fast":
        send(conn, valid_reply(cmd) + CR + CR + ">")
    elif m == "garbage":
        send(conn, bytes(rnd.getrandbits(8) for _ in range(rnd.randint(1, 400))))
        if rnd.random() < .3: send(conn, ">")
    elif m == "noprompt":
        send(conn, valid_reply(cmd) + CR)
    elif m in ("endless", "endless_nl"):
        chunk = (b"A" * 1023 + b"\n") if m == "endless_nl" else b"A" * 1024
        while True: send(conn, chunk)                     # until the app hangs up
    elif m == "halfline":
        send(conn, "41 0C 1A")
        time.sleep(3600)
    elif m == "badecho":
        send(conn, "0100" + CR + valid_reply(cmd) + CR + ">" if rnd.random() < .5 else "AT Z" + CR + "OK" + CR + ">")
    elif m == "nodata":
        send(conn, (valid_reply(cmd) if cmd.upper().startswith("AT") else "NO DATA") + CR + CR + ">")
    elif m == "unable":
        if cmd.upper().startswith("AT"): send(conn, "OK" + CR + CR + ">")
        else: send(conn, "SEARCHING..." + CR + "UNABLE TO CONNECT" + CR + CR + ">")
    elif m == "buserr":
        send(conn, rnd.choice(["BUS INIT: ...ERROR", "CAN ERROR", "STOPPED", "BUFFER FULL", "?", "DATA ERROR", "ERR94", "LV RESET", "<DATA ERROR", "FB ERROR", "BUS BUSY"]) + CR + CR + ">")
    elif m == "absurd":
        send(conn, absurd_reply(cmd) + CR + CR + ">")
    elif m == "signs":
        send(conn, signs_reply(cmd) + CR + CR + ">")
    elif m == "slowdrip":
        for b in (valid_reply(cmd) + CR + CR + ">").encode():
            send(conn, bytes([b])); time.sleep(A.drip_ms / 1000)
    elif m == "reset":
        send(conn, "41 0C 1"); rst(conn); return "stop"
    elif m == "huge":
        line = (b"X" * 78 + b"\r")
        for _ in range(A.huge_mb * 1024 * 1024 // len(line)): send(conn, line)
        send(conn, valid_reply(cmd) + CR + ">")
    elif m == "hang":
        time.sleep(3600)
    elif m == "stale":
        if time.time() - T0 < A.stale_after: send(conn, valid_reply(cmd) + CR + CR + ">")
        else: send(conn, (valid_reply(cmd) if cmd.upper().startswith("AT") or cmd.upper() in BITMAPS else "NO DATA") + CR + CR + ">")
    else:
        send(conn, "?" + CR + ">")


def main():
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", A.port))
    srv.listen(64)
    log("hostile ELM on", A.port, "mode", A.mode)
    def reporter():
        while True:
            time.sleep(10); log("stats", stats, "mode", mode_now())
    threading.Thread(target=reporter, daemon=True).start()
    while True:
        c, a = srv.accept()
        threading.Thread(target=handle, args=(c, a), daemon=True).start()


if __name__ == "__main__":
    try: main()
    except KeyboardInterrupt: pass
