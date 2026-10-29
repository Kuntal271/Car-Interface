#!/usr/bin/env python3
"""Passive Canbox -> head-unit UART frame decoder (Raise 0x2E, Hiworld 0x5A 0xA5).

READ-ONLY by design: this tool only ever reads from the serial port, it never writes.
Use it to take a "frame census" of what the Canbox actually sends (38400 8N1, 3.3 V TTL).

Live:    canbox_decode.py --port /dev/cu.usbserial-XXXX --proto raise --log capture.jsonl
Replay:  canbox_decode.py --replay capture.hex --proto raise       (hex text, any whitespace)

Checksums
  Raise   : ck = ((type + len + sum(data)) & 0xFF) ^ 0xFF          verified on 4 captured frames
  Hiworld : ck = (len + type + sum(data) - 1) & 0xFF               from smartgauges/canbox source,
                                                                    NOT yet verified on a real capture
"""
import argparse
import collections
import json
import sys
import time
from dataclasses import dataclass

RAISE_HDR = 0x2E
HIWORLD_HDR = bytes([0x5A, 0xA5])
MAX_LEN = 0x60  # sanity cap so a garbage length byte cannot stall the parser


def raise_checksum(body):
    """body = type, len, data..."""
    return (sum(body) & 0xFF) ^ 0xFF


def hiworld_checksum(body):
    """body = len, type, data..."""
    return (sum(body) - 1) & 0xFF


@dataclass
class Frame:
    proto: str
    ts: float
    type: int
    data: bytes
    raw: bytes


class FrameParser:
    """Streaming parser with checksum-based resync. Feed bytes, get complete valid frames."""

    def __init__(self, proto, clock=time.time):
        if proto not in ("raise", "hiworld"):
            raise ValueError("proto must be 'raise' or 'hiworld'")
        self.proto = proto
        self.clock = clock
        self.buf = bytearray()
        self.bad = 0       # frames dropped on checksum/length failure
        self.skipped = 0   # bytes discarded while hunting for a header

    def feed(self, chunk):
        self.buf.extend(chunk)
        out = []
        while True:
            f = self._next()
            if f is None:
                return out
            out.append(f)

    def _next(self):
        b = self.buf
        while True:
            if self.proto == "raise":
                i = b.find(bytes([RAISE_HDR]))
                if i < 0:
                    self.skipped += len(b)
                    b.clear()
                    return None
                if i:
                    self.skipped += i
                    del b[:i]
                if len(b) < 3:
                    return None
                n = b[2]
                if n > MAX_LEN:
                    self.bad += 1
                    del b[:1]
                    continue
                total = 4 + n
                if len(b) < total:
                    return None
                if raise_checksum(bytes(b[1:3 + n])) == b[3 + n]:
                    f = Frame("raise", self.clock(), b[1], bytes(b[3:3 + n]), bytes(b[:total]))
                    del b[:total]
                    return f
                self.bad += 1
                del b[:1]
            else:
                i = b.find(HIWORLD_HDR)
                if i < 0:
                    keep = 1 if b and b[-1] == HIWORLD_HDR[0] else 0
                    self.skipped += len(b) - keep
                    del b[:len(b) - keep]
                    return None
                if i:
                    self.skipped += i
                    del b[:i]
                if len(b) < 4:
                    return None
                n = b[2]
                if n > MAX_LEN:
                    self.bad += 1
                    del b[:1]
                    continue
                total = 5 + n
                if len(b) < total:
                    return None
                if hiworld_checksum(bytes(b[2:4 + n])) == b[4 + n]:
                    f = Frame("hiworld", self.clock(), b[3], bytes(b[4:4 + n]), bytes(b[:total]))
                    del b[:total]
                    return f
                self.bad += 1
                del b[:1]


# ---- naming tables (from open-source head-unit/canbox code; dialect-dependent!) -------------
RAISE_NAMES = {
    0x01: "panel key", 0x14: "backlight", 0x16: "vehicle speed info (VW dialect)",
    0x20: "steering wheel key", 0x21: "air conditioning", 0x22: "rear radar", 0x23: "front radar",
    0x24: "base info / doors", 0x25: "park assist / parking status", 0x26: "date-time (Ford) | steering angle (VW)",
    0x27: "language (Ford) | amplifier (VW)", 0x29: "steering angle (MQB)", 0x30: "protocol version string",
    0x40: "SYNC version", 0x41: "vehicle info (VW dialect: sub 01 doors, 02 rpm/speed/volt/temp/odo, 03 warnings)",
    0x50: "SYNC menu", 0x51: "SYNC menu item", 0x52: "SYNC play time", 0x53: "SYNC call time",
    0x70: "SYNC SRT up", 0x71: "SYNC SRT down", 0x72: "SYNC SRT short", 0x78: "SYNC status", 0x79: "SYNC request switch",
}
HIWORLD_NAMES = {
    0x00: "heartbeat", 0x11: "SWC / steering angle+park flags", 0x12: "doors/body", 0x21: "?",
    0x22: "radar", 0x23: "radar", 0x24: "radar", 0x26: "steering angle", 0x31: "climate",
    0x33: "trip", 0x34: "trip", 0x39: "TPMS", 0x41: "radar (MQB) / alerts", 0x42: "alerts",
    0x68: "settings", 0x69: "car config", 0x6A: "car config", 0x81: "amplifier", 0x82: "amplifier",
    0x90: "model discovery", 0x91: "media text", 0x92: "media text", 0xA6: "clock sync", 0xCA: "model discovery", 0xEA: "alerts",
}
FORD_SWC = {
    0x01: "VOL+", 0x02: "VOL-", 0x03: "FF", 0x04: "FR", 0x07: "SRC", 0x0E: "UP", 0x0F: "DOWN", 0x10: "LEFT",
    0x11: "RIGHT", 0x12: "OK", 0x34: "RADIO", 0x37: "MENU", 0x39: "PHONE", 0x52: "PICKUP", 0x53: "HANGUP",
    0x5A: "MUTE", 0x66: "PLAY/PAUSE", 0x67: "MEDIA", 0x70: "TUNE+", 0x71: "TUNE-", 0xF0: "VOL+ (2)", 0xF1: "VOL- (2)",
}


def _bit(v, n):
    return (v >> n) & 1


def describe(f):
    """One human-readable line. Decoders marked (guess) are unverified for this car."""
    names = RAISE_NAMES if f.proto == "raise" else HIWORLD_NAMES
    head = "%s 0x%02X %-34s len=%-2d %s" % (f.proto, f.type, names.get(f.type, "UNKNOWN"), len(f.data), f.data.hex(" "))
    d = f.data
    extra = ""
    if f.proto == "raise":
        if f.type == 0x20 and len(d) >= 2:
            extra = "key=%s state=%s" % (FORD_SWC.get(d[0], "0x%02X" % d[0]), {0: "release", 1: "press", 2: "hold"}.get(d[1], d[1]))
        elif f.type == 0x24 and len(d) >= 2:  # Ford dialect bit layout (RZC_FordSeriesManager)
            extra = "doors(Ford) FL=%d FR=%d RL=%d RR=%d trunk=%d" % (_bit(d[0], 6), _bit(d[0], 7), _bit(d[0], 4), _bit(d[0], 5), _bit(d[0], 3))
        elif f.type == 0x41 and len(d) >= 1 and d[0] == 0x02 and len(d) >= 13:  # VW dialect, smartgauges
            rpm = d[1] << 8 | d[2]
            spd = (d[3] << 8 | d[4]) / 100.0
            volt = (d[5] << 8 | d[6]) / 100.0
            temp = (d[7] << 8 | d[8]) / 10.0
            odo = d[9] << 16 | d[10] << 8 | d[11]
            extra = "(VW dialect) rpm=%d speed=%.2f V=%.2f temp=%.1f odo=%d lowfuel=%d" % (rpm, spd, volt, temp, odo, d[12])
        elif f.type == 0x21 and len(d) >= 6:  # Ford dialect
            def t(v):
                return "LO" if v == 0 else "HI" if v == 0x7F else ("%.1f" % (15.5 + (v - 0x1F) * 0.5) if 0x1F <= v <= 0x3B else "?")
            ext = d[5] - 256 if d[5] > 127 else d[5]  # (guess) two's complement
            extra = "(Ford) AC=%d L=%s R=%s ext=%s(guess)" % (_bit(d[0], 7), t(d[2]), t(d[3]), ext)
        elif f.type == 0x30:
            extra = "version=%r" % d.decode("utf-8", "replace")
    return head + ("   | " + extra if extra else "")


class Census:
    """Counts per frame type + distinct payloads; the point of the capture."""

    def __init__(self):
        self.count = collections.Counter()
        self.first = {}
        self.last = {}
        self.payloads = collections.defaultdict(set)

    def add(self, f):
        k = f.type
        self.count[k] += 1
        self.first.setdefault(k, f.ts)
        self.last[k] = f.ts
        if len(self.payloads[k]) < 5000:
            self.payloads[k].add(f.data)

    def report(self, proto):
        names = RAISE_NAMES if proto == "raise" else HIWORLD_NAMES
        lines = ["type  frames  distinct  rate/s   name"]
        for k, n in sorted(self.count.items()):
            span = max(self.last[k] - self.first[k], 1e-9)
            rate = (n - 1) / span if n > 1 else 0.0
            lines.append("0x%02X  %6d  %8d  %6.2f   %s" % (k, n, len(self.payloads[k]), rate, names.get(k, "UNKNOWN")))
        return "\n".join(lines)


def _hex_bytes(text):
    return bytes(int(x, 16) for x in text.split())


def run(args):
    parser = FrameParser(args.proto)
    census = Census()
    log = open(args.log, "a") if args.log else None

    def handle(chunk):
        for f in parser.feed(chunk):
            census.add(f)
            if log:
                log.write(json.dumps({"t": f.ts, "proto": f.proto, "type": f.type, "data": f.data.hex()}) + "\n")
            if not args.quiet:
                print(describe(f))

    try:
        if args.replay:
            with open(args.replay) as fh:
                handle(_hex_bytes(fh.read()))
        else:
            import serial  # pyserial (pip install pyserial); imported lazily so tests need no deps
            with serial.Serial(args.port, args.baud, timeout=0.2) as ser:
                t_end = time.time() + args.seconds if args.seconds else None
                while t_end is None or time.time() < t_end:
                    chunk = ser.read(256)
                    if chunk:
                        handle(chunk)
    except KeyboardInterrupt:
        pass
    finally:
        if log:
            log.close()
    print("\n" + census.report(args.proto))
    print("bad_frames=%d skipped_bytes=%d" % (parser.bad, parser.skipped))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--proto", choices=["raise", "hiworld"], required=True)
    ap.add_argument("--port")
    ap.add_argument("--baud", type=int, default=38400)
    ap.add_argument("--replay")
    ap.add_argument("--log")
    ap.add_argument("--seconds", type=int, default=0)
    ap.add_argument("--quiet", action="store_true")
    args = ap.parse_args(argv)
    if not args.port and not args.replay:
        ap.error("need --port or --replay")
    run(args)


if __name__ == "__main__":
    main()
