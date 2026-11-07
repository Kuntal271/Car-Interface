#!/usr/bin/env python3
"""Self-test of hostile_adapter.py: each mode really behaves as documented (so a 'client survived' result means something).
Run: cd tools/security && python3 -m unittest -v test_hostile_adapter"""
import socket
import subprocess
import sys
import time
import unittest
from pathlib import Path

HERE = Path(__file__).parent
PORT = 35941


class Adapter:
    def __init__(self, mode):
        self.mode = mode

    def __enter__(self):
        self.p = subprocess.Popen([sys.executable, str(HERE / "hostile_adapter.py"), "--mode", self.mode, "--port", str(PORT)],
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(50):
            try:
                socket.create_connection(("127.0.0.1", PORT), 0.2).close()
                break
            except OSError:
                time.sleep(0.1)
        return self

    def __exit__(self, *a):
        self.p.terminate()
        self.p.wait(5)

    def ask(self, cmd, timeout=1.0, limit=1 << 16):
        s = socket.create_connection(("127.0.0.1", PORT), 2)
        s.settimeout(timeout)
        s.sendall(cmd.encode() + b"\r")
        out = b""
        end = time.time() + timeout
        try:
            while len(out) < limit and time.time() < end and b">" not in out[-8:]:
                d = s.recv(65536)
                if not d:
                    break
                out += d
        except socket.timeout:
            pass
        s.close()
        return out


class HostileAdapterTest(unittest.TestCase):
    def test_noprompt_never_prompts(self):
        with Adapter("noprompt") as a:
            r = a.ask("010C", 0.5)
            self.assertIn(b"OK", r)
            self.assertNotIn(b">", r)

    def test_endless_streams_more_than_a_megabyte_without_prompt(self):
        with Adapter("endless") as a:
            r = a.ask("010C", 2, limit=1 << 20)
            self.assertGreaterEqual(len(r), 1 << 20)
            self.assertNotIn(b">", r)

    def test_drip_sends_one_byte_at_a_time(self):
        with Adapter("drip") as a:
            r = a.ask("010C", 2.5)
            self.assertTrue(1 <= len(r) <= 6, len(r))   # about one byte per second

    def test_longline_is_4mb(self):
        with Adapter("longline") as a:
            self.assertGreaterEqual(len(a.ask("010C", 3, limit=5 << 20)), 4 << 20)

    def test_nodata_goes_silent_after_valid_answers(self):
        with Adapter("nodata") as a:
            s = socket.create_connection(("127.0.0.1", PORT), 2)
            s.settimeout(1)
            replies = []
            for _ in range(45):
                s.sendall(b"010C\r")
                buf = b""
                while not buf.endswith(b">"):
                    buf += s.recv(4096)
                replies.append(buf)
            self.assertIn(b"41 0C 1A F8", replies[0])
            self.assertIn(b"NO DATA", replies[-1])

    def test_signs_and_absurd_forge_what_they_say(self):
        with Adapter("signs") as a:
            self.assertIn(b"41 0C -1 -1", a.ask("010C"))
        with Adapter("absurd") as a:
            self.assertIn(b"41 0C FF FF", a.ask("010C"))
            self.assertIn(b"41 42 FF FF", a.ask("0142"))

    def test_echoinject_wraps_the_answer_in_forgeries(self):
        with Adapter("echoinject") as a:
            r = a.ask("010C")
            self.assertTrue(r.startswith(b"010C\r41 0C FF FF"))

    def test_flap_closes_after_five(self):
        with Adapter("flap") as a:
            s = socket.create_connection(("127.0.0.1", PORT), 2)
            s.settimeout(1)
            closed = False
            for _ in range(8):
                try:
                    s.sendall(b"010D\r")
                    if not s.recv(4096):
                        closed = True
                        break
                except (ConnectionError, socket.timeout):
                    closed = True
                    break
            self.assertTrue(closed)


if __name__ == "__main__":
    unittest.main()
