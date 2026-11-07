#!/usr/bin/env python3
"""A deliberately hostile fake ELM327 adapter (TCP) for testing how Cockpit behaves when the OBD adapter is rogue.

    python3 tools/security/hostile_adapter.py --mode endless --port 35040        # then point the app's Wi-Fi source at <mac>:35040
    python3 tools/security/hostile_adapter.py --list

Stdlib only. It binds 127.0.0.1 by default (the Android emulator reaches the Mac as 10.0.2.2). Every command line the app sends is
printed (repr, so control characters are visible) with the mode and a running byte count: that log is the proof that the app only ever
sends ELM327 AT setup commands and mode 01 reads, whatever the adapter answers.

Modes (all of them first answer a valid-looking session where it makes the attack more convincing):
  noprompt    answers every command with "OK" but never sends the '>' prompt          -> client must time out, not hang
  endless     after the first command streams 'A' forever, no prompt, as fast as it can -> client must cap memory
  drip        one byte per second forever, no prompt (every gap is short)               -> client needs a TOTAL deadline
  longline    one 4 MB line, then '>'                                                    -> client must cap the line
  control     replies full of NUL / ESC / BEL / 0xFF / U+202E / CR-LF storms, then '>'    -> nothing may be shown as a value
  nodata      valid rpm/speed for N replies, then "NO DATA" for ever                      -> old numbers must not stay "live"
  nodataflood "NO DATA\\r" x 100000 in every reply                                       -> cap
  echoinject  echoes the command, then forged "41 0C ..." lines before and after         -> parser must take the first well-formed answer only
  signs       "41 0C -1 -1", "41 0D +F", Unicode digits                                  -> must decode to nothing, never negative values
  absurd      "41 0C FF FF" (16383.75 rpm), "41 42 FF FF" (65.5 V), speed 255            -> sentinels must not be shown
  instant     everything supported, valid answers with zero latency                      -> client must not spin a core
  garbage     random bytes with a prompt                                                 -> nothing shown, no crash
  flap        valid, but closes the socket after every 5th reply                         -> backoff, no tight reconnect loop
"""
import argparse
import asyncio
import os
import random
import sys
import time

MODES = {
    "noprompt": "OK, never '>'",
    "endless": "endless 'A', no prompt",
    "drip": "1 byte/s, no prompt",
    "longline": "4 MB line then '>'",
    "control": "control characters",
    "nodata": "valid, then NO DATA for ever",
    "nodataflood": "huge NO DATA replies",
    "echoinject": "forged answers around the echo",
    "signs": "signed / non-ASCII digits",
    "absurd": "sentinel values",
    "instant": "zero-latency valid answers",
    "garbage": "random bytes",
    "flap": "drops the link every 5 replies",
}


def valid_reply(cmd: str, state: dict) -> bytes:
    """What a healthy adapter would say (everything below 0x20 supported; rpm 1726, speed 50, coolant 83, ...)."""
    c = cmd.strip().upper().replace(" ", "")
    table = {
        "0100": "41 00 BE 3F A8 13", "0120": "41 20 80 00 00 01", "0140": "41 40 7A 1C 80 00", "0160": "41 60 00 00 00 00",
        "010C": "41 0C 1A F8", "010D": "41 0D 32", "0105": "41 05 7B", "010B": "41 0B 64", "0111": "41 11 80", "0104": "41 04 40",
        "010F": "41 0F 3C", "012F": "41 2F 80", "0142": "41 42 37 8C", "0133": "41 33 64", "0146": "41 46 46", "015C": "41 5C 78",
    }
    if c.startswith("AT"):
        return b"\rELM327 v1.5\r\rOK\r" if c == "ATZ" else b"OK\r"
    return (table.get(c, "NO DATA") + "\r").encode()


class Session:
    def __init__(self, mode, reader, writer, stats):
        self.mode, self.r, self.w, self.stats = mode, reader, writer, stats
        self.n = 0
        self.peer = writer.get_extra_info("peername")

    def log(self, msg):
        print(f"{time.strftime('%H:%M:%S')} [{self.mode}] {self.peer} {msg}", flush=True)

    async def send(self, data: bytes):
        self.w.write(data)
        self.stats["sent"] += len(data)
        await self.w.drain()

    async def reply(self, cmd: str):
        m = self.mode
        self.n += 1
        if m == "noprompt":
            await self.send(b"OK\r")
        elif m == "endless":
            self.log("streaming 'A' without end")
            chunk = b"A" * 65536
            while True:
                await self.send(chunk)
        elif m == "drip":
            self.log("dripping one byte per second")
            while True:
                await self.send(b"1")
                await asyncio.sleep(1)
        elif m == "longline":
            await self.send(b"4" * (4 << 20) + b"\r>")
        elif m == "control":
            junk = bytes([0, 27, 7, 255, 254, 13, 10, 13, 10, 0]) * 40 + "\u202e41 0C 1A F8\u202d".encode() + b"\x00\x00\r"
            await self.send(junk + valid_reply(cmd, {}).replace(b" ", b"\x00") + b"\r>")
        elif m == "nodata":
            ok = self.n <= 40 or cmd.upper().startswith("AT") or cmd == "0100"
            await self.send((valid_reply(cmd, {}) if ok else b"NO DATA\r") + b"\r>")
        elif m == "nodataflood":
            await self.send(b"NO DATA\r" * 100_000 + b">")
        elif m == "echoinject":
            await self.send(cmd.encode() + b"\r41 0C FF FF\r41 0D FF\r" + valid_reply(cmd, {}) + b"41 0C 00 00\r\r>")
        elif m == "signs":
            c = cmd.upper()
            forged = {"010C": b"41 0C -1 -1\r", "010D": b"41 0D +F\r", "0105": b"41 05 \xd9\xa3\xd9\xa2\r", "0142": b"41 42 -1 -1\r"}
            await self.send(forged.get(c, valid_reply(cmd, {})) + b"\r>")
        elif m == "absurd":
            c = cmd.upper()
            forged = {"010C": b"41 0C FF FF\r", "0142": b"41 42 FF FF\r", "010D": b"41 0D FF\r", "010B": b"41 0B FF\r", "0105": b"41 05 FF\r"}
            await self.send(forged.get(c, valid_reply(cmd, {})) + b"\r>")
        elif m == "instant":
            await self.send(valid_reply(cmd, {}) + b"\r>")
        elif m == "garbage":
            await self.send(os.urandom(random.randint(1, 300)).replace(b">", b"") + b"\r>")
        elif m == "flap":
            await self.send(valid_reply(cmd, {}) + b"\r>")
            if self.n % 5 == 0:
                self.log("dropping the link")
                self.w.close()
                raise ConnectionResetError

    async def run(self):
        self.stats["conns"] += 1
        self.log(f"connected (#{self.stats['conns']})")
        buf = b""
        try:
            while True:
                data = await self.r.read(4096)
                if not data:
                    break
                buf += data
                while b"\r" in buf:
                    line, buf = buf.split(b"\r", 1)
                    self.stats["cmds"] += 1
                    text = line.decode("latin-1")
                    self.log(f"cmd {text!r}   (total sent {self.stats['sent']} B)")
                    await self.reply(text)
        except (ConnectionError, asyncio.CancelledError):
            pass
        finally:
            self.log("closed")
            try:
                self.w.close()
            except Exception:
                pass


async def main_async(args):
    stats = {"conns": 0, "cmds": 0, "sent": 0}

    async def handler(r, w):
        await Session(args.mode, r, w, stats).run()

    server = await asyncio.start_server(handler, args.host, args.port)
    print(f"hostile ELM327 [{args.mode}: {MODES[args.mode]}] on {args.host}:{args.port}", flush=True)
    async with server:
        await server.serve_forever()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--mode", choices=sorted(MODES), default="noprompt")
    ap.add_argument("--port", type=int, default=35040)
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--list", action="store_true")
    args = ap.parse_args()
    if args.list:
        for k, v in sorted(MODES.items()):
            print(f"{k:12s} {v}")
        return 0
    try:
        asyncio.run(main_async(args))
    except KeyboardInterrupt:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
